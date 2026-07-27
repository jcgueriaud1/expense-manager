# Domain Glossary

The ubiquitous language for the Expense Management app. Terms here are the exact
names used in code (entities, DTOs, methods) and in specs. Keep this in sync as
the domain sharpens.

## Core entities

- **Expense Report** — the aggregate root. A collection of expense lines
  submitted by a user for approval, with a lifecycle status and a derived total.
  Owned by exactly one user. Versioned for optimistic locking (ADR-0011).
  Report-level fields (V1): a required user-entered **Report Date** (defaults to
  today; distinct from the audit `createdAt`; the natural sort key and primary
  label in My Reports) and optional **Additional Information** free text. No
  title. The total is derived (sum of line amounts), not stored.
- **Expense Line** — a single expense within a report: **expense type**, gross
  **unit price** (`amount`), a **quantity**, **VAT rate**, optional **comment**,
  optional receipt image. Belongs to one report; editable only while the report
  is editable — i.e. in `DRAFT` or `REJECTED` (see Resubmit), never in
  `SUBMITTED` or `APPROVED`. A line has **no business date** (its timing is the
  audit `createdAt`; the report's `reportDate` is the meaningful date) and **no
  description** (the optional comment is the only free text). `amount` is the
  gross **unit price** (per-item total paid), required and **non-zero** —
  negatives are allowed for credits/corrections (never a negative quantity). The
  line **gross** is derived: `amount × quantity` (HALF_UP scale 2). The VAT rate
  defaults from the expense type but is manually overridable and **required**;
  the VAT amount is **derived** from the gross (`gross − gross/(1+rate)`,
  HALF_UP scale 2), not stored. See **Quantity** (ADR-0023).
- **Expense Type** — admin-editable config classifying a line (e.g. Travel
  allowance, Taxi/transport, Accommodation, Restaurant/meals, Office
  supplies/goods, Publications, Other — the general catch-all). Has a display
  **order**, an **active** flag
  (inactive types are hidden from new lines but preserved on historical ones),
  and a **required default VAT rate**. Replaces the plan's earlier flat
  "Category" list (plan 2.2).
- **VAT Rate** — admin-editable config: a rate value (2026 seed: 25.5, 13.5, 10, 0), a
  display **order**, and an **active** flag ("old rate" = inactive, hidden from
  new lines but kept on historical ones so past reports retain their original
  rate). Seeded from the Finnish (Verohallinto) rates.
- **Receipt** — a scanned document attached to an expense line: **0..1 per line,
  always optional** (V1). A JPEG, PNG, or PDF (≤ 10 MB), stored as Postgres bytea
  in a **separate `receipt` table** alongside its `content_type`, `filename`, and
  `size_bytes` (ADR-0009, ADR-0021). The file type is verified by **magic bytes**,
  not the browser's claim. Attachable at any time (buffered in the working copy,
  written on save — no "save first" step); replaceable (overwrite, no history) and
  removable, but only while the report is `DRAFT`/`REJECTED` — view-only in
  `SUBMITTED`/`APPROVED`. Images preview inline; PDFs open/download. The bytes are
  never carried in a DTO — the UI holds only a receipt *summary* and streams the
  blob on demand (ADR-0021).
- **User** — a local record linked to a Google identity by `sub`. Holds email,
  display name, role(s), and an `enabled` flag. Distinct from the Google account.
- **Allowance Rate Config** — editable, per-year reference data: domestic
  per-diem amounts, kilometre compensation rate, and foreign per-diem rates by
  country (seeded from the Verohallinto decision). Editable by an admin at
  runtime; not hard-coded.

## Status & lifecycle

- **Report Status** — one of: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`.
- **Submit** — owner action moving `DRAFT → SUBMITTED`. Requires ≥1 line; locks
  lines from further editing.
- **Approve** — admin action moving `SUBMITTED → APPROVED`.
- **Reject** — admin action moving `SUBMITTED → REJECTED` with a mandatory
  **Rejection Comment**.
- **Resubmit** — owner action on a `REJECTED` report: edit, then move back to
  `SUBMITTED`.
- **Delete** — owner action, allowed only while `DRAFT` (hard delete). Reports in
  `SUBMITTED`/`APPROVED`/`REJECTED` are never deletable (audit trail). There is
  no **withdraw** in V1: once submitted, only an admin moves the report.
- **Status History** — an ordered log of **Status Change** entries owned by the
  report aggregate (modelled from Phase 2). Visible in report/approval detail
  (display UI lands in Phase 5).
- **Status Change** — one transition entry: `fromStatus`, `toStatus`, the acting
  user, a timestamp, and an optional comment (mandatory on reject, Phase 5).
  `submit()` appends the first entry (`DRAFT → SUBMITTED`); approve/reject/
  resubmit append theirs in Phase 5.

## Roles & access

- **USER** — create/edit/view/submit own reports.
- **ADMIN** — everything a USER can, plus view all users, manage roles, revoke
  access (`enabled=false`), review the submitted queue, approve/reject.
- **Auto-provisioning** — first successful Google login from the `vaadin.com`
  workspace creates a `USER` record automatically (ADR-0007).
- **Revoke** — set `enabled=false`; the user is refused at login regardless of
  their Google account.

## Money & allowances

- **Amount** — a monetary value: `BigDecimal` scale 2, EUR (ADR-0010). On an
  **Expense Line** it is the gross **unit price** (per item), not the line total
  (ADR-0023).
- **Quantity** — the count on an **Expense Line**: `BigDecimal(19,2)`, strictly
  `> 0`, default `1`. The line gross is `amount × quantity` (HALF_UP scale 2).
  Manual lines take any positive quantity (whole or fractional); generated lines
  carry real quantities where meaningful — kilometre (`km`), foreign per-diem
  (`days`), split domestic per-diem (`full days` / `partial days`) — and `1` for
  meal and parking. Never negative: credits use a negative unit price (ADR-0023).
- **VAT** — value-added tax. Captured per line as a **VAT Rate** (config
  reference, defaulted from the line's Expense Type, overridable, required); the
  VAT amount is derived from the gross amount, never stored. See **Expense Type**
  and **VAT Rate** under Core entities.
- **Per Diem** — daily allowance for a trip. **Domestic** is auto-calculated and
  generates **two lines** — a full-day line (`full days × full rate`) and a
  partial-day line (`partial days × partial rate`) — so each is an honest
  `quantity × unit price` (ADR-0023); the full+partial mix per trip is
  unchanged. The **free-meal halving** halves the **unit price** of those lines
  (the per-diem is halved when a free meal was provided), keeping quantity an
  honest day count. **Foreign** uses a country-rate lookup (`days × country
  rate`), generated as a full-day line. Either kind's day count may carry a
  **Quantity Override**.
- **Kilometre Compensation** — allowance for driving, rate × kilometres.
- **Quantity Override** — a user-entered replacement for the **Quantity** of a
  travel-generated allowance line, carrying a **mandatory reason**, used when the
  auto-calculation doesn't fit the trip. Only the *count* is overridable: the unit
  price stays statutory and server-computed, so the client never sends money
  (ADR-0024). Available on the per-diem lines (full and partial, domestic and
  foreign) and the meal line only — the kilometre distance and the parking fee are
  edited on the **Travel Calculator** itself. Whole numbers; `0` suppresses the
  line; an override can never create a line the rules did not earn (that is
  manual-line territory). Cleared, after confirmation, when a trip edit changes the
  calculated count.
- **Travel Calculator** — trip-level inputs (dates/times, route, purpose,
  free-meal/km/parking) that auto-generate expense lines. Referenced from the
  ProCountor flow; the foreign-trip default-to-Finnish-per-diem weakness is a
  concrete "do better" opportunity.

## Meta

- **Finding** — a logged observation of friction/gap, per the brief's taxonomy
  (Spec, AI, Vaadin, Tooling/Template, Docs, Verification,
  Deployment/Observability, UX-spec). See [findings.md](findings.md).
- **Preview environment** — auto-updates to the latest build for stakeholder
  inspection.
- **Staging/prod-like environment** — stable, promoted deliberately.

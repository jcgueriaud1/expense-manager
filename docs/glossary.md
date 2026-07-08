# Domain Glossary

The ubiquitous language for the Expense Management app. Terms here are the exact
names used in code (entities, DTOs, methods) and in specs. Keep this in sync as
the domain sharpens.

## Core entities

- **Expense Report** — the aggregate root. A collection of expense lines
  submitted by a user for approval, with a lifecycle status and totals. Owned by
  exactly one user. Versioned for optimistic locking (ADR-0011).
- **Expense Line** — a single expense within a report: date, category,
  description, amount, VAT, optional receipt image, optional comment. Belongs to
  one report; only editable while the report is in `draft`.
- **Receipt** — an image attached to an expense line, stored as Postgres bytea
  (ADR-0009), with a size cap and validated content type.
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
- **Status History** — the record of transitions (who, when, comment) visible in
  report/approval detail.

## Roles & access

- **USER** — create/edit/view/submit own reports.
- **ADMIN** — everything a USER can, plus view all users, manage roles, revoke
  access (`enabled=false`), review the submitted queue, approve/reject.
- **Auto-provisioning** — first successful Google login from the `vaadin.com`
  workspace creates a `USER` record automatically (ADR-0007).
- **Revoke** — set `enabled=false`; the user is refused at login regardless of
  their Google account.

## Money & allowances

- **Amount** — a monetary value: `BigDecimal` scale 2, EUR (ADR-0010).
- **VAT** — value-added tax captured per line.
- **Per Diem** — daily allowance for a trip. **Domestic** is auto-calculated
  (including the **free-meal halving** — the per-diem is halved when a free meal
  was provided). **Foreign** uses a country-rate lookup or a manual override.
- **Kilometre Compensation** — allowance for driving, rate × kilometres.
- **Manual Override** — an operator-entered allowance adjustment with a mandatory
  explanation, used when auto-calculation doesn't fit.
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

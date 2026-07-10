# ADR-0019 — Report edit/save model: whole-aggregate, in-memory until first save

**Status:** Accepted — the receipt consequence below (*"save before attaching"*)
is **retired by [ADR-0020](0020-receipt-entity-upload-serving.md)**: receipts are
buffered in the working copy and persisted on save, so no save-first step exists.

## Context
The report detail view (plan 2.6) lets a user build and edit a report and its
lines. Three shapes were possible:

1. **Per-line operations** — `addLine`/`editLine`/`removeLine` each hit the
   service and persist immediately, one transaction each.
2. **Whole-aggregate save, persist an empty DRAFT immediately** — "New" INSERTs an
   empty report; every edit is a whole-aggregate UPDATE.
3. **Whole-aggregate save, in-memory until first save** — "New" opens a transient
   working copy; the first save INSERTs the whole aggregate.

This choice sets the service API, how optimistic locking (ADR-0011) is exercised,
and how the detail view holds state (Binder + Signals, ADR-0015).

## Decision
Adopt **whole-aggregate save, in-memory until first save** (option 3):

- The detail view holds a **`ReportDetailDto` working copy**. Lines are
  added/edited/removed **in memory** (inline Grid row editor); the net/VAT/gross
  totals recompute live via Signals.
- **Service API (thin, ADR-0006):**
  - `create(dto) -> id` — first save, INSERTs the whole aggregate.
  - `update(id, dto, version)` — whole-aggregate UPDATE; the line collection is
    **reconciled by nullable line id** (match → update, null → insert, missing →
    orphan-remove), mapped manually (ADR-0003).
  - `submit(id, version)` — `DRAFT|REJECTED → SUBMITTED`, appends a StatusChange.
  - `delete(id)` — allowed only while `DRAFT`.
- Every write carries the aggregate `@Version`; a stale write throws
  `OptimisticLockException` → "reload" UX (ADR-0011).

Routing: `/report` (new, transient) and `/report/{id}` (load). The first
successful save navigates from `/report` to `/report/{id}`.

## Consequences
- One transaction per user-visible save; the aggregate boundary and the
  transaction boundary coincide — the cleanest fit for the rich aggregate and
  `@Version`.
- **No empty-draft rows**: a report only exists once it has been saved with
  content. Abandoning "New" leaves nothing behind.
- **Trade-off — receipts (Phase 3) need a persisted line.** Because lines live in
  memory until first save, uploading a receipt requires saving the report first
  ("save before attaching a receipt"). Accepted; revisit in the Phase 3 spec if
  the UX bites (log a finding).
- Collection reconciliation by nullable id is manual-mapping boilerplate; if it
  becomes painful, reconsider per the ADR-0003 escape hatch.

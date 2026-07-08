# ADR-0011 — Optimistic locking (@Version) on the report aggregate

**Status:** Accepted

## Context
Real races exist: a user edits/resubmits a rejected report while an admin still
has it open; two admins act on the same submitted report. Last-write-wins
silently loses updates or approves a version that no longer exists — a
correctness/trust bug in an approval tool.

## Decision
Use **optimistic locking via a `@Version` column on `ExpenseReport`** (the
aggregate root; lines are versioned with it). A stale write throws
`OptimisticLockException`, translated into a friendly "this report changed,
please reload" UX.

Pessimistic locking is rejected (overkill, awkward across instances).

## Consequences
- No DB locks held; correct across multiple app instances (blue-green).
- The conflict-handling UX is itself a Vaadin finding worth documenting.

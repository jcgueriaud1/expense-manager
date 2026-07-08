# ADR-0006 — Rich domain, ExpenseReport aggregate root

**Status:** Accepted

## Context
The report has real rules: a lifecycle state machine
(`draft → submitted → approved`/`rejected`, `rejected → resubmit`), invariants
(no submitting an empty report; no editing lines after submit; only the owner
edits; only an admin approves/rejects), and derived values (line totals, VAT,
allowances). An anemic model scatters these across services and re-checks (or
forgets) invariants in multiple places.

## Decision
Adopt a **rich domain model**:
- `ExpenseReport` is an **aggregate root** owning its `ExpenseLine`s and
  enforcing its own transitions: `submit()`, `approve()`, `reject(comment)`,
  and edit guards. Illegal transitions throw domain exceptions.
- **Services** are thin: transactions, cross-aggregate orchestration, DTO
  mapping, and authorization delegation.
- **Allowance calculation** is a **pure, stateless domain service** — a function
  of trip inputs + rate config — testable without a database.

## Consequences
- Each invariant has one authoritative home.
- The rules most likely to be wrong get fast, DB-free unit tests (layer 1,
  ADR-0012).
- Authorization ("who") stays out of the domain — it lives in the security
  layer (ADR-0008). The domain only enforces state validity.

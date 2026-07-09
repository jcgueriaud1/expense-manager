# ADR-0018 — Expense Type & VAT Rate as editable reference config

**Status:** Accepted

## Context
Plan item 2.2 originally proposed a flat, hard-coded category list (Travel, Meal,
Accommodation, Office/supplies, Other). Two forces broke that:

1. **VAT is per-line and rate-driven.** Each line captures a Finnish VAT rate, and
   the natural rate depends on what was bought (transport/accommodation/meals →
   reduced rate; goods → general rate; travel allowances → 0%). A flat category
   enum carries none of that.
2. **Finnish VAT rates are statutory and change most years** — general 24 % →
   25.5 % (1 Sept 2024); reduced 14 % → 13.5 % (1 Jan 2026). Hard-coding rates (or
   category→rate mappings) as enums means a code change + redeploy every time the
   law changes, and destroys the rate history of already-filed lines.

## Decision
Replace the flat category list with **two admin-editable reference tables**, both
seeded via Flyway and both edited at runtime through an **ADMIN-only CRUD screen
built in Phase 2** (method-secured, ADR-0008):

- **`VatRate`** — `value` (`numeric`, e.g. 25.5), `displayOrder`, `active`.
  Seed (2026): **25.5 / 13.5 / 10 / 0**.
- **`ExpenseType`** — `name`, `displayOrder`, `active`, and a **required**
  `defaultVatRate` FK → `VatRate`. Seed: Travel allowance → 0; Taxi/transport →
  13.5; Accommodation → 13.5; Restaurant/meals → 13.5; Parking/supplies/goods →
  25.5; Publications → 10.

An **`ExpenseLine`** references an `ExpenseType` (required) and a `VatRate`
(required). The line's rate **defaults from the expense type but is manually
overridable**. The VAT amount is **derived** (`amount − amount/(1+rate)`,
HALF_UP scale 2, ADR-0010), never stored.

**Rate history is preserved by the `active` flag, not by per-year versioning.**
When a rate changes, deactivate the old row and add the new one; historical lines
keep their FK to the rate they were filed under, so past reports never
re-compute. New lines only offer `active` rows.

## Consequences
- The law can change without a schema or code change — an admin deactivates and
  adds rates; filed reports are unaffected.
- Simpler than the per-year rate config used for allowances (ADR-0005): the line
  stores its own rate reference, so no effective-date lookup is needed for VAT.
- Two config domains + an admin CRUD screen land in Phase 2, widening it beyond
  the bare report loop. Accepted deliberately: the report loop is meaningless
  without types/rates to file against, and the editing UI is cheap next to the
  seed.
- Exact seed values must be verified against the Verohallinto decision for the
  target year before the migration ships (finding F-003).
- Supersedes the flat-category intent of plan 2.2; the glossary term "Category"
  is retired in favour of "Expense Type".

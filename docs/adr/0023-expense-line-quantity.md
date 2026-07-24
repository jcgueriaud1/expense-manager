# ADR-0023 — Expense-line quantity: `amount` becomes a unit price, gross is derived

**Status:** Accepted

## Context
An expense line historically stored a single gross `amount` (the total paid),
with net/VAT derived from it (ADR-0010) and the report total being the sum of
line amounts. Users want to record a **quantity** on a line (e.g. 3 nights, 2
taxis, 12.5 km) so the line reads like an invoice line rather than a
pre-multiplied lump.

Introducing quantity forces a choice about what `amount` *means* and how the
generated (travel) lines — which already arrive pre-computed by the
`AllowanceCalculator` — express their own multipliers. The domestic per-diem is
the awkward case: its gross is `full days × full rate + partial days × partial
rate`, optionally halved for a free meal — not a single `unit × quantity`.

## Decision
Adopt a **unit-price × quantity** line model:

- **`amount` is redefined as the gross unit price (each); `quantity` is new.**
  `gross = amount × quantity` (HALF_UP, scale 2). **Net/VAT derive from the
  gross**, not the unit price. The `amount` column name and type are kept
  (repurposed, not renamed) — a one-column migration adding `quantity`.
- **`quantity`: `numeric(19,2)`, strictly `> 0`, default `1`.** Existing rows
  backfill to `1`, so every stored value is unchanged (old gross = unit price ×
  1). A line is valid when unit price ≠ 0 **and** quantity > 0 (⇒ non-zero
  gross). **Credits/corrections stay on a negative unit price**, never a negative
  quantity.
- **Generated lines carry real quantities.** Kilometre: `quantity = km`,
  `unit = €/km rate`. Foreign per-diem: `quantity = days`, `unit = country
  rate`. Meal / parking: `quantity = 1`.
- **Domestic per-diem splits into two generated kinds** — `PER_DIEM_FULL`
  (`full days × full rate`) and `PER_DIEM_PARTIAL` (`partial days × partial
  rate`) — replacing the single `PER_DIEM`. Both remain tax-free / 0 % VAT and
  group into the per-diem subtotal. The full+partial *mix per trip* (the
  Verohallinto model) is unchanged; the split is per line so each line is an
  honest `days × per-day rate`.
- **Free-meal halving halves the unit price** (not the quantity), so quantity
  stays an honest day count. Each line rounds HALF_UP scale 2 independently.
- **UI:** the line editor shows `Unit price (gross, each)` + `Quantity`
  (default 1) + a live read-only `Line total`. The line card shows the
  `qty × unit = gross` breakdown **only when quantity ≠ 1**; a quantity-1 line
  looks exactly as before.

## Considered options
- **Quantity as informational metadata** (does not multiply) — rejected: a
  quantity that enters no calculation surprises anyone who has seen an invoice
  and earns no place in the VAT/total maths.
- **Rename `amount` → `unitPrice`** — rejected: a larger rename across DTO, spec,
  form model, tests, and a column rename, for no behavioural gain over
  repurposing the existing column.
- **Per-diem line pinned to `quantity = 1`** (keep one line holding the full
  computed gross) — rejected: quantity would be decorative exactly where a trip
  is most quantity-shaped; splitting keeps every generated line honest.
- **Halve the quantity instead of the unit price** — rejected: it re-corrupts
  the day count the split exists to keep clean.

## Consequences
- `ExpenseLine`, `ExpenseLineDto`, `ExpenseLineSpec`, `ExpenseLineFormModel`,
  `GeneratedLineSpec`, and `AllowanceCalculator` all gain a unit-price/quantity
  shape (km returns rate + distance; domestic per-diem returns full and partial
  separately); totals grouping and tests follow.
- **Known limitation — km rate precision.** The €/km rate is stored as the
  line's scale-2 unit price; a future rate with >2 decimals (e.g. `€0.575/km`)
  would round. Current rates are scale 2, so no drift today. Log a finding if a
  finer rate ever ships.
- **Known limitation — per-line halving rounding.** Halving each per-diem line
  independently may differ by a cent from the old single-halving of the combined
  gross. Accepted as the new truth (still statutorily defensible).

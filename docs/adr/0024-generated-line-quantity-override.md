# ADR-0024 — Travel-generated lines are corrected by overriding the *quantity*, never the amount

**Status:** Accepted

## Context
A `Travel` holds only trip *inputs* and carries no money; every save re-runs the
`AllowanceCalculator` server-side and regenerates the trip's `ExpenseLine`s from
scratch, matched by `GeneratedLineKind` (ADR-0006, ADR-0019, ADR-0023). Those
lines are read-only in the UI apart from attaching a receipt, and
`GeneratedLineSpec` states the contract plainly: **the client never sends money.**

Users need to correct a generated line when the statutory calculation doesn't fit
the trip — most often "the calculator gave me 3 full days, but the Wednesday was
personal". Today the only remedies are to distort the trip inputs (move the return
time until the day count comes out right) or to add a compensating manual line,
both of which lie in the audit trail.

The obvious framing — "let the user edit the amount" — was the starting point and
was rejected. The framing that survived is narrower and load-bearing: **the unit
price is the law; the quantity is the judgement call.**

## Decision

**1. Override the quantity only.** The unit price stays statutory,
admin-configured and server-computed. This preserves the "client never sends
money" contract verbatim: the client sends a *count*.

**2. Overridable kinds: `PER_DIEM_FULL`, `PER_DIEM_PARTIAL`, `MEAL`.** The scope
test is not *"is it calculated?"* (all five kinds are) but *"can the trip inputs
already express the answer?"* — `KILOMETRE`'s quantity **is** `Travel.kilometres`
and `PARKING`'s amount **is** `Travel.parkingFees`, so those keep a single home on
the trip; per-diem and meal are reachable only through statutory rules and a
boolean, so they get an override.

**3. An override rescales an earned line; it can never conjure one.** `0`
suppresses the line — the only way to express "keep the 2 full days, drop the
partial leftover". Overriding a kind the calculator did not earn does nothing.
This falls out of the existing `GeneratedLineSpec.isEarned()` gate at no cost:
an unearned rule zeroes the *unit price*, not just the count. It also preserves
the per-diem/meal interlock that `AllowanceCalculator.mealAllowance` enforces
(the two are mutually exclusive under the Finnish rule via `notEligible`); a
conjured per-diem would sit alongside a meal allowance with nothing to catch it.

**4. Stored on `Travel`, as an input** — a `@MapKeyEnumerated`
`Map<GeneratedLineKind, QuantityOverride>` of an `@Embeddable (quantity, reason)`,
backed by a new `travel_override` table. The override is a trip input like
`kilometres`, so `Travel` remains the sole home of inputs and generated lines
remain pure derivations.

**5. Mandatory reason on every override**, unconditionally.

**6. Cleared, per kind, when the calculated count moves.** Saving the trip
recalculates; if the calculated quantity for an overridden kind differs from
before, that kind's override is cleared behind a confirm dialog naming the change
(`full days 3 → 5`). Editing an override never changes the calculation, so the two
surfaces never fight. A **Reset to calculated** action on the row
(`overrides.remove(kind)`) reverts without touching the trip.

**7. Whole numbers ≥ 0; `PER_DIEM_PARTIAL` capped at 1.** All three quantities are
counts of discrete things, and `PerDiemComponent.days` is already an `int`.
`allowanceDays` yields at most one leftover *by construction*, so "2 partial days"
is arithmetically incoherent rather than merely generous. No ceiling on full days
or meals — any ceiling would re-derive the rule the override exists to escape.

**8. The persisted `comment` describes the effective figures and names the
baseline.** `"Domestic per-diem: 2 × full day (€54.00) = €108.00 — overridden from
3 days: the Wednesday was personal"`. Quantity, gross and comment agree, and the
statutory baseline survives for anyone reading the database, an export, or the
ProCountor hand-off — not just a viewer of `ReportDetailView`.

**9. UI:** edited on the generated-line row via a small dialog (the shape
`TravelLineReceiptDialog` already established), with an "Overridden" badge and the
reason on the row. `previewTravel` applies overrides; `TravelEditorDialog` previews
an **overrides-stripped** copy for its calculated baseline — the same call decision
6's comparison needs.

## Considered options
- **Override the amount** — rejected: breaks the "client never sends money"
  contract, and lets the user restate a statutory rate rather than a claim.
- **Make `KILOMETRE` overridable too** (override the €/km rate) — rejected: the
  distance is already editable on the trip, and the rate is the law.
- **Delete `Travel.kilometres`** so the kilometre line's quantity becomes the
  distance's single home — conceptually tidier, rejected: moves a field out of the
  trip dialog and costs a migration for no user-visible gain.
- **Store a `quantityOverridden` flag on `ExpenseLine`** — rejected: regeneration
  would have to read the line it is about to overwrite, making the line both input
  and output, and `previewTravel` could not see the override at all (the dialog
  would show 3 days while the saved report showed 2).
- **Detach the line to a manual line on first edit** — rejected: the detached kind
  is then absent from `TravelSpec.generatedLines()`, so the next save regenerates a
  statutory line beside it (doubled), and `countsInNetVat()` keys off
  `generatedKind`, so a detached allowance would silently start counting in Net/VAT.
- **Six nullable columns on `travel`** instead of the keyed table — rejected:
  `TravelSpec` and `TravelDto` are already 13 components with 14 construction sites;
  this takes them to 19. The keyed map adds one component each, enforces the
  quantity-implies-reason pair once, and makes the overridable-kind list data
  rather than schema.
- **Sticky-forever overrides** (survive any trip change, badge as stale) —
  rejected in favour of clear-on-recalculation: sticky lets an override go dormant
  and later resurrect at a number the user never re-requested.
- **Silent clearing on trip change** — rejected: that is the same data loss without
  consent. The confirm dialog is what makes clearing acceptable.
- **Deltas** (store `−1`, apply to the recalculated count) — rejected: meaningless
  at the zero boundary, and cannot express "exactly 2, whatever you calculate".
- **Reason required only when overriding upward** — rejected: a validation rule
  whose trigger is a recomputed value is unpredictable, and a saved override could
  become invalid without anyone editing it.

## Consequences
- New Flyway migration (`travel_override`), one new component each on `TravelSpec`
  and `TravelDto`, override fields on `GeneratedLineView`, and one comment-composing
  method in `ExpenseReportService`. `AllowanceCalculator` stays pure and untouched.
- Editability gating is free: the override rides `TravelSpec` →
  `reconcileTravels` → `assertLinesEditable`, so it is DRAFT/REJECTED-only like
  every other line edit.
- **Suppressing a line destroys its receipt.** `V6__receipts.sql` declares
  `expense_line_id ... on delete cascade` because `ExpenseLine` has no back-reference,
  so an override of `0` orphan-removes the line and Postgres deletes the blob. This
  is warned and confirmed on the override path (`MEAL` is exactly where a receipt is
  plausible), and `ReportDetailView.pendingTravelReceipts` is pruned to match so no
  stale `GeneratedLineRef` reaches the service. The pre-existing case — a trip edit
  that drops a kind entirely — is unchanged and out of scope here.
- **A suppressed line still needs a row** (found while implementing #132). Removing
  the line is the point, but a kind whose row simply vanished would leave the user no
  record of what they dropped and no way back to *Reset to calculated*. So the costing
  returns the suppressed kinds separately — `TravelDto.suppressedLines`, rebuilt from
  the overrides-stripped baseline on load too — and the detail view draws them after
  the earned rows: badged "Removed", at €0.00, with the reason and the statutory count.
  They are views only; nothing persists them and no total sums them, so the earned-line
  gate stays the single arbiter of what a report contains.
- **An overridden line silently re-prices when an admin edits the year's rates.**
  The override is a count and rate changes move the unit price, so decision 6 never
  fires. Correct, but it means an override can outlive a rate change unnoticed.
- The trip dialog's preview shows *calculated* figures while the report row shows
  *effective* ones. Deliberate — it is what makes the `3 → 5` clearing warning
  legible — and the preview line is annotated where an override is in force.

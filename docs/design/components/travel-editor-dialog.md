# Travel editor dialog

**Category:** composite
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `com.vaadin.expensemanager.report.ui.TravelEditorDialog`, `TravelFormModel`;
`styles.css` — `.travel-preview`, `.travel-preview-amount` (to be replaced by
`.trip-totals`, see below)
**Design:** node `253:10597` › `Dialog` — the `Edit Travel Info` state

## Overview

The focused modal editor for **one trip** (glossary: Travel Calculator): departure and
return, destination country and destinations, purpose, the kilometre and parking inputs,
the eligibility / free-lunch / meal-allowance / charge-to-customer flags — and, below the
inputs, the **calculated** allowance lines the trip earns, recomputed server-side on every
change (ADR-0006, ADR-0024). Opened from the report detail by *Add travel* or by editing a
trip row.

**When to reach for a sibling instead.**

| Instead of | Use |
|---|---|
| Editing a manual expense line | [`line-editor-dialog.md`](line-editor-dialog.md) |
| Attaching a receipt to a generated line | `TravelLineReceiptDialog` — receipt only, no figures |
| Correcting a generated line's count | `GeneratedLineOverrideDialog` (ADR-0024) |

Like the line editor it is *not* built on the shared [`editor-dialog.md`](editor-dialog.md)
scaffold — it needs a live server preview and a clearing confirm the scaffold's API does
not take — but the scaffold's **rules bind it**: an always-enabled Save, a top-of-form
error summary, and the three error paths recorded there.

## Anatomy

| Part | Element | Grid |
|---|---|---|
| Overlay | `Dialog`, `::part(overlay)` | — |
| Header title | `setHeaderTitle(…)` — `Edit travel info` / `Add travel info` | — |
| Error summary | [`error-summary.md`](error-summary.md) — **undrawn**, code origin (ADR-0020) | above the form |
| `DESTINATIONS` eyebrow | `Span` + `.section-label` | row 1, colspan 2 |
| Departure / Return | `DateTimePicker` ×2, 15-minute step | row 2, col 1 / col 2 |
| Destination country | `ComboBox<String>` | row 3, col 1 |
| Destinations | `TextField` — the design labels it `City`, see [Q4](#destinations-not-city) | row 3, col 2 |
| Travel purpose | `TextField` | row 4, colspan 2 |
| Rule | `Hr` + `.form-rule` | row 5, colspan 2 |
| `EXPENSES` eyebrow | `Span` + `.section-label` | row 6, colspan 2 |
| Charge expenses from customer? | `Checkbox` | row 7, colspan 2 |
| Kilometre allowance (km) / Parking fees (€) | `BigDecimalField` ×2 | row 8, col 1 / col 2 |
| Daily allowance | `RadioButtonGroup`, three options, vertical | row 9, colspan 2 |
| Pay meal allowance? | `Checkbox`, helper text kept | row 10, colspan 2 |
| Rule | `Hr` + `.form-rule` | row 11, colspan 2 |
| Trip totals | `.trip-totals` — one row per earned line, `Trip total` last | row 12, colspan 2 |
| Cancel / Save | [`button.md`](button.md) — secondary, then `PRIMARY` | footer |

The design hides the header's close button (`I253:10597;3701:12281`), a third footer
button (`I253:10597;3701:11669`) and a third checkbox (`…;253:10609`). All three stay
unbuilt; Escape and Cancel are the exits.

## Layout — two sections in one grid

```
┌────────────────────────────────────────────┐
│ DESTINATIONS                               │  eyebrow, colspan 2
├─────────────────────┬──────────────────────┤
│ Departure  [d][t]   │ Return  [d][t]       │  DateTimePicker ×2
├─────────────────────┼──────────────────────┤
│ Destination country │ Destinations         │
├─────────────────────┴──────────────────────┤
│ Travel purpose                             │  colspan 2
├────────────────────────────────────────────┤
│ ─────────────────── rule ──────────────────│  20 above, 20 below
│ EXPENSES                                   │
│ [ ] Charge expenses from customer?         │
├─────────────────────┬──────────────────────┤
│ Kilometre allowance │ Parking fees (€)     │
├─────────────────────┴──────────────────────┤
│ Daily allowance                            │  radio group, vertical
│  (•) Full daily allowance                  │
│  ( ) Halved, free lunch provided           │
│  ( ) Not eligible                          │
│ [ ] Pay meal allowance?                    │
│ ─────────────────── rule ──────────────────│
│ Kilometre allowance             €66.00     │  .trip-totals
│                        120 km × €0.550/km  │
│ ───────────────────────────────────────────│  rule between rows
│ Meal allowance                  €13.50     │
│ ───────────────────────────────────────────│
│ Parking                        €100.00     │
│ ───────────────────────────────────────────│
│ Trip total                     €179.50     │
└────────────────────────────────────────────┘
```

The same two-column grid the line editor settled, with the same two gaps, so the two
dialogs share one rhythm. **Field order is the design's**, and it differs from the code
in three places: country sits *beside* destinations rather than above it; the
charge-to-customer flag opens the Expenses section rather than closing the form; and the
eligibility choice sits between the money fields and the meal-allowance flag.

Structure goes through the Java API, never `getStyle()`
([`../../theming-layouts.md`](../../theming-layouts.md)):

```java
form.setResponsiveSteps(new ResponsiveStep("0", 1), new ResponsiveStep("24rem", 2));
form.setColumnSpacing("var(--em-section-gap)");   // 40
form.setRowSpacing("var(--em-card-padding)");     // 20
form.setColspan(purposeField, 2);                  // and every eyebrow, rule, checkbox, the radio group, the totals
```

The one-column step below 24rem is the app's, not the design's — the frame draws no
small-screen state, and two 242px fields do not survive a phone (ADR-0020).

**Eyebrows and rules live inside the grid**, as colspan-2 rows, so the form's own 20px row
gap supplies every clearance the frame draws: the rule sits 20 below the purpose field and
20 above the `EXPENSES` eyebrow, which is exactly the frame's `y` arithmetic (249 → 269 →
289). No margin is set on either.

## The totals block

The frame draws the earned lines as **rows of the form**, not as a box: no surface, no
padding, no border, no radius that renders (`totals-box` carries `rounded-[12px]` on a
transparent frame). Rows are 20px apart with a 1px rule *between* them — none above the
first, since the section rule already sits there — and `Trip total` is the **last** row.

That inverts the code's `.travel-preview`, an accent-tinted box with the total as a heading
on top. [`travel-card.md`](travel-card.md) let the preview keep its accent because the frame
it surveyed did not draw this dialog. This one does, and paints no accent anywhere: **the
accent surface is withdrawn**, the same call that frame made for the trip row itself.

The text is [`totals-card.md`](totals-card.md)'s, reused rather than re-decided — same
label/value classes, same sizes, same weight decision:

| Row part | Class | Token |
|---|---|---|
| Label | `.totals-row-label` | `--aura-font-size-s`, `--vaadin-text-color-secondary` |
| Value | `.totals-row-value` | `--aura-font-size-m`, `--aura-font-weight-medium`, `--vaadin-text-color` |
| Breakdown under the value (`120 km × €0.550/km`) | `.muted` | `--aura-font-size-s`, secondary — right-aligned under the value |
| Total label | `.totals-total-row` | `--aura-font-size-m`, `--aura-font-weight-semibold` |
| Total value | `.totals-grand` | `--aura-font-size-l`, `--aura-font-weight-semibold` |

The frame draws the total label in Inter **Bold** (700) and the value in Inter **Extra
Bold** (800) against its own `--lumo-font-family` binding; Aura's scale stops at 600 and
the variable wins over the drawn text (F-069, ADR-0025 decision 4).

The row labels are `GeneratedLineKind.label()` — `Kilometre allowance`, `Meal allowance`,
`Parking`, and the two per-diem kinds the frame's mock data happens not to earn. The
breakdown line is the generated line's own explanation string. Both already match the
frame word for word.

Three states the frame does not draw are **code origin** and stay (ADR-0024, issue #133):
the *Overridden: …* note under a kind carrying a Quantity Override; the note for an
override on a kind the trip no longer earns; and the `No allowances for this trip` line in
place of the rows when nothing is earned. The block hides while the dates are incomplete
or the range is invalid; Save surfaces the reason.

## Destinations, not City

The frame pairs `Destination Country` with **`City`**, a single place. The domain field is
`destinations`: required free text, placeholder *e.g. Helsinki, Espoo*, a route that may
name several places (glossary: Travel Calculator). Relabelling the field `City` would
misdescribe the data it holds and contradict its own placeholder, so **the label stays
`Destinations`** — settled in the app's favour — and the pairing is reported to the
designer as a defect.

## Eligibility is one choice with three answers

The frame draws a horizontal two-option radio group, `Trip not eligible for daily
allowance` / `Free lunch provided`, in place of the code's two checkboxes. Its intent is
right — the two flags are mutually exclusive (issue #93: a free lunch only *halves* a
per-diem the trip is eligible for) — but two options cannot express the **default** case,
eligible with no free lunch, and a Vaadin radio group cannot be un-picked. As drawn it
would be unrecoverable after the first click.

Decided: a **three-option `RadioButtonGroup`**, one field bound to both booleans:

| Option | `notEligibleForAllowance` | `freeLunch` |
|---|---|---|
| Full daily allowance *(default)* | false | false |
| Halved, free lunch provided | false | true |
| Not eligible | true | false |

**Vertical**, not the drawn horizontal — an accepted infidelity: the longest label is
~250px and three of them will not fit the 524px row, and vertical is both Aura's default
and the Vaadin guidance. The group carries the label **`Daily allowance`**, which the frame
omits; a group label is the accessibility floor, not a choice.

`Pay meal allowance?` stays a checkbox as drawn, with its cascade (checking it selects
*Not eligible*; selecting an eligible option unchecks it) and its helper text — code origin,
ADR-0020, because a cascade with no explanation reads as a glitch.

## Tokens used

| Property | Token | Value |
|---|---|---|
| Dialog width | — | `35rem` (560; the design's 556 rounded to the whole rem the line editor set) |
| Dialog radius, shadow, surface, backdrop | Aura stock — `--vaadin-radius-l`, `--aura-overlay-shadow`, `--aura-overlay-surface-opacity`, `--aura-overlay-backdrop-filter` | 15, `shadow-m`, 0.85 |
| Header / footer padding | `--vaadin-padding-l` | 16 |
| Footer gap | `--vaadin-gap-s` | 8 |
| Header title | `--aura-font-size-l`, `--aura-font-weight-semibold` | 16, 600 |
| Content inline / block padding | `--vaadin-padding-l` / `--em-card-padding` | 16 / 20 |
| Grid column gap | `--em-section-gap` | 40 |
| Grid row gap | `--em-card-padding` | 20 |
| Eyebrow | `.section-label` — `--aura-font-size-xs`, `--aura-font-weight-semibold`, `--vaadin-text-color-secondary`, uppercase | 12, 600 |
| Rule (`.form-rule`) | `1px solid var(--vaadin-border-color-secondary)`, no margin | — |
| Field label gap, height, radius, border, shadow | Aura stock — `--vaadin-gap-xs`, `--vaadin-radius-m`, `--vaadin-input-field-border-width`, `--aura-shadow-xs` | 4, 34, 9, 1 |
| Field label / value | `--aura-font-size-m` + medium / regular | 14, 500 / 400 |
| Date ↔ time sub-field gap | Aura stock `DateTimePicker` | design 8 — **unverified** against Aura's rendered gap |
| Checkbox / radio label | `--aura-font-size-m`, `--aura-font-weight-medium` | 14, 500 |
| Radio option gap | Aura stock | design 12 (horizontal) — n/a vertical |
| Totals row gap | `--em-card-padding` | 20 |
| Totals row rule | `1px solid var(--vaadin-border-color-secondary)` | — |
| Totals text | see [The totals block](#the-totals-block) | — |

**No new `--em-*` property.** Every value on the frame resolved to a token or to an
already-settled off-scale one (20, 40).

**Every `--lumo-*` name in the design's reference code is a translation, not a token.**
The frame emits `--lumo-border-radius-m, 9px`, `--lumo-border-radius-l, 15px`,
`--lumo-border-radius-s, 5px`, `--lumo-font-size-m` and `--lumo-font-size-l`, and
`--aura-border-color-secondary`, which Aura does not define either (it is
`--vaadin-border-color-secondary`). Copy any of them and it renders at its fallback and
never tracks the theme again (F-062). `Shades/Surface 4` and `Accent colors/Accent
neutral` are the kit's names for `--aura-overlay-surface` and `--aura-neutral-*`, which
the stock dialog and primary button already use.

## API

Unchanged by this spec:

```java
TravelEditorDialog(TravelDto existing,                       // null to add
        UnaryOperator<TravelDto> costPreview,
        IntFunction<List<String>> foreignCountriesForYear,
        Consumer<TravelDto> onSave)
```

`TravelFormModel` keeps its two booleans; the radio group binds to them through one
eligibility value in the form layer, so `TravelDto` and the service are untouched.

## States

The frame draws **only** the resting, populated state. Everything below that is not "as
drawn" comes from the theme or from an ADR, and is marked as such.

| State | Behaviour |
|---|---|
| **default** | as drawn, with `Full daily allowance` selected. `Add travel info` opens empty with the totals hidden — a state the design does not draw |
| **hover** | Aura stock on fields, buttons, checkboxes and radios. On buttons this is a `::before` overlay at `opacity: 0.03`, **invisible in the host's computed style** — do not "verify" it by reading `background-color` |
| **active** | Aura stock; the button hover overlay is suppressed via `:not([active])` |
| **focus** | Aura's accent focus ring, not overridden. Tab order: error summary → fields in grid order (date then time inside each picker) → radio group → checkbox → Cancel → Save. Eyebrows, rules and totals are not focusable |
| **disabled** | **n/a for Save by design** — never a disabled button (ADR-0020). No field is ever disabled either: the eligibility cascade *corrects* the other control rather than locking it (issue #93) |
| **error** | Two layers. Per-field: Aura's red border plus the field's own `Error message` slot, which the frame draws and hides; the pickers carry the i18n messages for incomplete / bad / out-of-range input (issues #85, #140). Form-level: the summary above the form lists every binder failure and the dialog stays open. A `DomainRuleException` renders in the same summary; any other exception propagates to `UiErrorHandler` and must **not** be caught into it (issue #86) |
| **clearing an override** | code origin (ADR-0024 decision 6): a save that moves an overridden count opens a confirm naming the kind, the change and the discarded reason; *Keep editing* abandons the save |

**Contrast is unverified here.** A survey does not boot the app; the only measured pair
that carries over is the primary button's in [`button.md`](button.md). The totals' new
primary-on-overlay pairing is [`visual-verification`](../../../.claude/skills/figma-visual-verification)'s
to confirm.

## Code example

```java
new TravelEditorDialog(null, service::previewTravel,
        service::foreignDestinations, travels::insertLast).open();
```

## Divergence

The code implements the previous, undrawn revision: a flat form with the code's own field
order, two checkboxes for eligibility, and an accent preview box with the total on top.
Owned by the travel-editor redesign issue.

| # | Design / decided | Code today |
|---|---|---|
| 1 | two sections with `DESTINATIONS` / `EXPENSES` eyebrows and two rules | no sections, no rules |
| 2 | country col 1 beside destinations col 2 | country colspan 2, destinations colspan 2 below |
| 3 | charge-to-customer opens the Expenses section | last field in the form |
| 4 | one three-option radio group `Daily allowance`, vertical | two checkboxes with a cascade |
| 5 | totals as transparent rows, rules between, `Trip total` last | `.travel-preview` accent box, `Trip total: €…` heading first |
| 6 | totals text per `totals-card.md` classes | `.travel-preview-amount` 16/600, lines as plain `Span`s, notes `.muted-xs` |
| 7 | header `Edit travel info` / `Add travel info` | `Edit trip` / `Insert travel info` |
| 8 | footer primary `Save` | `Save trip` |
| 9 | dialog `35rem` | `32rem` |
| 10 | column / row spacing 40 / 20 | FormLayout defaults |
| 11 | `City` | `Destinations` — **settled, app**, see above |
| 12 | `<vaadin-text-field>` for km and parking | `BigDecimalField` — **settled, app**, the line editor's decimal contract (ADR-0023) |
| 13 | Title Case labels throughout | sentence case — **open**, see below |

*13 — label case is open, not settled.* `Edit Travel Info`, `Destination Country`, `Travel
Purpose`, `Kilometre Allowance (km)`, `Parking Fees (€)` and `Trip Total` add to the
line-editor frame's evidence for Title Case; `Free lunch provided`, `Trip not eligible for
daily allowance` and both checkbox labels are sentence case on the same frame. The app keeps
sentence case and the convention stays with its own ticket — see
[`../foundations/typography.md`](../foundations/typography.md) § *Label case is undecided*.

## Cross-references

[`editor-dialog.md`](editor-dialog.md) — the scaffold whose rules bind this dialog ·
[`line-editor-dialog.md`](line-editor-dialog.md) — the sibling whose grid and gaps this reuses ·
[`totals-card.md`](totals-card.md) — the totals text ·
[`travel-card.md`](travel-card.md) — the row this dialog edits, and the accent decision this supersedes ·
[`error-summary.md`](error-summary.md) · [`button.md`](button.md) ·
[`../foundations/typography.md`](../foundations/typography.md) § *Weight 700 has no Aura token* ·
ADR-0006, ADR-0020, ADR-0022, ADR-0023, ADR-0024, ADR-0025 ·
F-062, F-069 · issues #85, #86, #93, #133, #140

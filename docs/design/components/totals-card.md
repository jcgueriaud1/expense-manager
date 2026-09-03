# Totals card

**Category:** composite (CSS)
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `styles.css` — `.totals-*`, `.detail-actions`; `ReportDetailView`
**Design:** node `116:4444` › `totals-box` `116:4987`; the action bar is
`actions-section` `116:5004`

## Overview

The money summary at the foot of a report: net, VAT, the tax-free allowance subtotals,
then the grand total. A read-only figure block — never editable, never interactive.

The same box as [`expense-item-card.md`](expense-item-card.md) — same radius, padding,
surface and shadow — deliberately, so the report reads as one stack of cards. It differs
in holding figures rather than rows, in its tighter internal rhythm, and in sitting
outside any section wrapper: it has no `.section-label` above it and the design draws none.

## Anatomy

| Part | Class |
|---|---|
| Card | `.totals-card` |
| A subtotal row | `.totals-row` |
| Its label | `.totals-row-label` |
| Its value | `.totals-row-value` |
| The final row | `.totals-total-row` |
| The grand figure | `.totals-grand` |
| The action bar below | `.detail-actions` |

**A rule sits between every row, not only above the total.** The design draws two `Line`
nodes in a three-row box. Implemented the same way as the section card's: a
`border-block-start` plus `padding-block-start` on every row but the first, over the card's
own row gap — which here is `--vaadin-gap-m` (12), not the section card's 20.

`.detail-actions` sits **outside** the card: `--em-card-padding` block padding,
right-aligned, and **no top rule** — the design draws none.

## Tokens used

| Property | Token | Value |
|---|---|---|
| Radius | `--em-card-radius` | 12 |
| Padding | `--em-card-padding` | 20 |
| Row gap | `--vaadin-gap-m` | 12 |
| Background | `--aura-surface-color` | — |
| Shadow | `--aura-shadow-xs` | — |
| Row rule | `1px solid var(--vaadin-border-color-secondary)` | — |
| Subtotal label | `--aura-font-size-s`, `--vaadin-text-color-secondary` | 13 |
| Subtotal value | `--aura-font-size-m`, `--aura-font-weight-medium`, `--vaadin-text-color` | 14, 500 |
| Total label | `--aura-font-size-m`, `--aura-font-weight-semibold` | 14, 600 |
| Grand total | `--aura-font-size-l`, `--aura-font-weight-semibold` | 16, 600 |
| Action bar padding | `--em-card-padding` | 20 |

**The card has no border.** The design gives `totals-box` a surface, a radius and a shadow
and nothing else — matching the plain `expense-item-card`, not the `--travel` one.

**Do not set a height.** The design's 141px with `justify-between` is Figma's fixed frame,
not a specification; the box grows with however many allowance rows are visible.

### Two figures moved, and one label did not

The design puts the **value** of each subtotal row at `--aura-font-size-m` in the primary
colour, where the app renders the whole row — label and value alike — at
`--aura-font-size-s` secondary. Only the label is secondary. The figure is the content.

The grand total comes **down**, from `--aura-font-size-xl` (18) to `--aura-font-size-l`
(16). The design draws it at 16 in weight 800; the size is taken and the weight is not —
Aura's scale stops at semibold 600, which
[`../foundations/typography.md`](../foundations/typography.md) settled.

The label stays **"Total to reimburse"** against the design's "Total". With five possible
rows above it, the longer label names which figure it is; "Total" alone reads as the total
of the column, which it is not (the allowances are not in Net or VAT).

## The three allowance rows the design omits

The design's box has exactly three rows — Net, VAT, Total — although its own mock data
includes a €962 per-diem line. The app renders three more, each hidden when its amount is
zero: **Per diem allowance**, **Kilometre allowance**, **Meal allowance**.

**They are kept.** The tax-free split is real, statutory information on a Finnish expense
report, and folding it silently into the total loses it from the screen entirely. Because
each row hides at zero, a report with no trip renders exactly the design's three rows —
so this is not a divergence the user sees on the frame's own content.

Settled in the app's favour, and the omission is reported to the designer.

## API

CSS-only. Composed in `ReportDetailView`; every figure is bound to a computed `Signal`, so
the card recomputes live as lines and trips change.

## States

| State | Behaviour |
|---|---|
| default | surface fill, labels secondary, figures primary, grand total emphasised |
| hover | n/a |
| active | n/a |
| focus | n/a |
| disabled | n/a — always read-only |
| error | n/a |

Every state is n/a: this is a pure display component, and saying so is the point of the
row. The buttons in `.detail-actions` below it have their own — see
[`button.md`](button.md).

## Code example

```java
var card = new Div();
card.addClassName("totals-card");
card.setWidthFull();
card.add(row("Net", netDisplay), row("VAT", vatDisplay));
grand.addClassNames("totals-total-row", "totals-grand");
```

## Divergence

| | Design / decided | Code today |
|---|---|---|
| Radius / padding | `--em-card-radius` 12 / `--em-card-padding` 20 | `radius-l` 15 / `padding-l` 16 |
| Border | none | `1px solid var(--vaadin-border-color)` |
| Shadow | `--aura-shadow-xs` | none |
| Rules | between every row | only above the total |
| Row rhythm | `--vaadin-gap-m` 12 | `0.25rem` block padding |
| Subtotal value | `--aura-font-size-m`, primary | `--aura-font-size-s`, secondary |
| Grand total | `--aura-font-size-l` 16 | `--aura-font-size-xl` 18 |
| Action bar | right-aligned, no rule | full-width, top rule, `padding-m` |

The `0.25rem` block padding on `.totals-row` was previously recorded here as a deliberate
raw value — "a 4px block rhythm inside a figure list, tighter than any padding token". The
design specifies 12px with a rule, so the row is superseded rather than still justified.

**Owner:** the report-detail redesign issue this survey files.

## Cross-references

[`expense-item-card.md`](expense-item-card.md) — the same box, holding rows ·
[`expense-line-card.md`](expense-line-card.md) ·
[`button.md`](button.md) — what `.detail-actions` holds ·
[`../foundations/typography.md`](../foundations/typography.md) — the weight-700 decision ·
ADR-0010 (EUR, BigDecimal scale 2), ADR-0025

# Totals card

**Category:** composite (CSS)
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `styles.css` — `.totals-*`, `.detail-actions`; `ReportDetailView`
**Design:** node `116:4444` › `totals-box`

## Overview

The money summary at the foot of a report: net, VAT, per-category subtotals, then the
grand total. A read-only figure block — never editable, never interactive.

## Anatomy

| Part | Class |
|---|---|
| Card | `.totals-card` |
| A subtotal row | `.totals-row` |
| The final row | `.totals-total-row` |
| The grand figure | `.totals-grand` |
| The action bar below | `.detail-actions` |

`.detail-actions` sits outside the card: a top rule plus `--vaadin-padding-m`, holding
Save / Submit / Delete.

## Tokens used

| Property | Token |
|---|---|
| Padding | `--vaadin-padding-l` (16) |
| Border | `1px solid var(--vaadin-border-color)` |
| Radius | `--vaadin-radius-l` (15) |
| Background | `--aura-surface-color` |
| Subtotal row | `--aura-font-size-s`, `--vaadin-text-color-secondary`, `0.25rem` block padding |
| Final row rule | `--vaadin-border-color` + `--vaadin-gap-s`, semibold |
| Grand total | `--aura-font-size-xl` (18), semibold |
| Action bar rule | `--vaadin-border-color-secondary` + `--vaadin-padding-m` |

`0.25rem` on `.totals-row` is a deliberate raw value: a 4px *block* rhythm inside a
figure list, tighter than any padding token, and it tracks the font size because it is in
`rem`. Not an off-scale design value — a typographic detail.


## API

CSS-only. Composed in `ReportDetailView`.

## States

| State | Behaviour |
|---|---|
| default | surface background, rows in secondary colour, grand total emphasised |
| hover | n/a |
| active | n/a |
| focus | n/a |
| disabled | n/a — always read-only |
| error | n/a |

Every state is n/a: this is a pure display component, and saying so is the point of the
row.

## Code example

```java
var totals = new Div();
totals.addClassName("totals-card");
totals.add(row("Net", net), row("VAT", vat));
grand.addClassNames("totals-total-row", "totals-grand");
```

## Divergence

| | Design | Code |
|---|---|---|
| Radius | 12 px | `--vaadin-radius-l` — 15 px |
| Padding | 20 px | `--vaadin-padding-l` — 16 px |

Tokens for the design's values exist and are defined — `--em-card-radius`,
`--em-card-padding` — and are deliberately unreferenced until per-view work consumes
them.

**Owner:** the per-view issue for this component's view. Not a bug to fix in passing: the
foundations settled the values, and switching each consumer is per-view work with its own
visual verification.

## Cross-references

[`expense-line-card.md`](expense-line-card.md) ·
[`button.md`](button.md) — what `.detail-actions` holds ·
ADR-0010 (EUR, BigDecimal scale 2)

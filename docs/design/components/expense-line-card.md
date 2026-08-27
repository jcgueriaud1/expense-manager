# Expense line card

**Category:** composite (CSS)
**Status:** settled, pending per-view revision
**Source:** `styles.css` — `.line-card`, `.line-*`, `.category-dot`, `.clickable`; `ReportDetailView`, `LineEditorDialog`

## Overview

One expense line on the report detail: a category dot, a name, an amount, and optionally
a receipt link. The base card for everything in a report's line list.

[`travel-card.md`](travel-card.md) extends it for trips and generated allowance lines —
same box, accent-tinted. Do not copy `.line-card` for a trip; add `.travel-card`.

**Not** for a report in a list — that is [`report-card.md`](report-card.md), which is a
link.

## Anatomy

| Part | Class |
|---|---|
| Card | `.line-card` |
| Category swatch | `.category-dot` — 0.625rem circle |
| Line name | `.line-name` |
| Amount | `.line-amount` |
| Receipt row | `.line-receipt` |
| Read-only "Line total" row | `.line-total-row`, `.line-total-label`, `.line-total-value` |
| Clickable affordance | `.clickable` — cursor only |

The line-total row is the *editor's* read-only unit price × quantity summary (ADR-0023).
It deliberately borrows the totals bar's look rather than a field's, because it is a
derived figure and not an input.

## Tokens used

| Property | Token |
|---|---|
| Padding | `--vaadin-padding-l` (16) |
| Border | `1px solid var(--vaadin-border-color)` |
| Radius | `--vaadin-radius-l` (15) |
| Background | `--aura-surface-color` |
| Line name | `--aura-font-size-s`, semibold |
| Amount | `--aura-font-size-m`, semibold |
| Total label | `--aura-font-size-s`, `--vaadin-text-color-secondary` |
| Total value | `--aura-font-size-m`, semibold |
| Total rule | `--vaadin-border-color-secondary` + `--vaadin-padding-xs` |
| Swatch colour | `--category-color`, set per line; falls back to `--vaadin-border-color` |
| Cursor | `--vaadin-clickable-cursor, pointer` |

The swatch colour comes from `ReportViewSupport.categoryColor`, which hashes the expense
type name onto the six saturated palette hues so a type reads the same colour throughout
a report. Colour is decoration only — the type name always renders as text (ADR-0020).

**Pending per-view revision:** design draws 12px radius / 20px padding; currently 15/16.

## API

CSS-only. Composed in `ReportDetailView`; the total row is built by `LineEditorDialog`.

## States

| State | Behaviour |
|---|---|
| default | surface background, 1px border |
| hover | none on the card itself; actions inside it are tertiary buttons |
| active | n/a |
| focus | n/a on the card — focus lives on the buttons inside |
| disabled | n/a. A non-editable line simply renders without its action buttons (DRAFT/REJECTED-only editing) |
| error | n/a — line validation surfaces in [`error-summary.md`](error-summary.md) inside the editor dialog |

## Code example

```java
var card = new Div(dot, name, amount);
card.addClassName("line-card");
dot.addClassName("category-dot");
dot.getStyle().set("--category-color", ReportViewSupport.categoryColor(typeName));
```

Setting a custom property through `getStyle()` is legitimate here — it is passing a
*value*, not styling around a layout API.

## Cross-references

[`travel-card.md`](travel-card.md) ·
[`report-card.md`](report-card.md) ·
[`totals-card.md`](totals-card.md) ·
ADR-0023 (quantity), ADR-0020 (never colour alone)

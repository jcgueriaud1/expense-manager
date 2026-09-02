# Travel card

**Category:** composite (CSS) — extends [`expense-line-card.md`](expense-line-card.md)
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `styles.css` — `.travel-*`; `ReportDetailView`, `TravelEditorDialog`
**Design:** node `116:4444` › `116:4935` (the `--travel` card) with the trip row
`116:4936` and its nested allowance rows `116:4945`, `233:7760`

## Overview

A trip and the allowance lines it generates. Two additions to an ordinary
[`expense-line-card.md`](expense-line-card.md) row, and nothing else:

1. **The trip row carries no amount.** A trip is not itself a cost; its money is in the
   rows below it.
2. **Its generated rows are indented 40px and carry no glyph.** That indent is the
   design's whole expression of "this line belongs to the trip above" — there is no
   nesting, no sub-card and no second surface.

Both live in the `--travel` variant of
[`expense-item-card.md`](expense-item-card.md), which the design distinguishes with a
border and a lifted shadow.

**The name of this file is historical.** A trip has not been a card since this frame; it is
a row. The name is kept because the role — a trip and its allowances — has not changed.

## Anatomy

| Part | Class | Notes |
|---|---|---|
| The section card | `.expense-item-card--travel` | border + `--aura-shadow-s` |
| Trip row | `.expense-row` + `.travel-row` | the base row; no right-hand amount group |
| Trip glyph | `.expense-row-icon` | `LucideIcon.PLANE`, 20px |
| Trip purpose | `.expense-row-name` | the trip's purpose, else "Trip" |
| Where | `.muted-xs` | `destinations, country` |
| When | `.expense-row-detail` | the date range — **primary** colour, see below |
| Generated row | `.expense-row` + `.travel-line-row` | `padding-inline-start: 40px`, no glyph |
| Line label + badge | `.travel-line-heading` | wraps |
| Suppressed row | `.travel-line-removed` | a kind dropped by a zero override (#132) |
| Per-diem preview | `.travel-preview`, `.travel-preview-amount` | inside `TravelEditorDialog` |

The trip's date range and its allowance rows' attachment filenames both render at
`--vaadin-text-color`, not the secondary colour — the design's decision, recorded in
[`expense-line-card.md`](expense-line-card.md) § *The filename is content*.

## Tokens used

Everything is inherited from [`expense-line-card.md`](expense-line-card.md). Only these
are this component's own:

| Property | Token | Value |
|---|---|---|
| Generated-row indent | `--em-section-gap` | 40 |
| Card border | `1px solid var(--vaadin-border-color)` | — |
| Card shadow | `--aura-shadow-s` | — |
| "Overridden" badge | `SMALL` + `WARNING` | — |
| "Removed" badge | `SMALL` + class `aura-accent-neutral` | grey |
| Preview padding / radius / gap | `--vaadin-padding-m` / `--vaadin-radius-m` / `--vaadin-gap-xs` | 12 / 9 / 4 |
| Preview amount | `--aura-font-size-l`, semibold | 16 |
| Preview surface | `--aura-accent-surface`, `--aura-accent-border-color` | — |

**The 40px indent reuses `--em-section-gap`, and that deserves a word.** The property is
named for the gap between sections, and this is a horizontal indent — but it is the same
40px design value, and
[`../foundations/spacing.md`](../foundations/spacing.md) already records `expense-left`
among the property's occurrences. Minting a second property for the same number is exactly
the parallel scale `--em-*` is bounded against.

### The accent tint is gone

`.travel-card` painted the trip `--aura-accent-surface` with an accent border, so a trip
read as the report's headline. **The design achieves the same distinction on the card
instead**, with a border and a stronger shadow on the plain surface, and paints no accent
anywhere on this frame. The tint is withdrawn: same intent, the design's means
(ADR-0025).

`.travel-card-icon`'s `--aura-accent-color` goes with it — the trip glyph inherits the
row's text colour like every other glyph. `.travel-preview` **keeps** its accent surface:
it is inside `TravelEditorDialog`, which the design does not draw, so it is out of this
frame's authority.

## API

CSS-only. Composed in `ReportDetailView`; the preview is built by `TravelEditorDialog`.

## States

| State | Behaviour |
|---|---|
| default | the base row's, plus the indent on generated rows |
| hover | **none** — the row is not clickable; see [`expense-line-card.md`](expense-line-card.md) § States |
| active | n/a |
| focus | n/a on the row — on the `⋮` trigger, and on an attachment chip |
| disabled | a generated line is read-only apart from its receipt and its override. On a read-only report it renders **no** `⋮` menu, rather than a disabled one |
| **overridden** | an `Overridden` badge beside the label, the reason given, and the statutory baseline in place of the composed comment (ADR-0024). The amount and the `qty × unit` breakdown are already the effective ones |
| **removed** | `.travel-line-removed` — the label at secondary colour and regular weight, a grey `Removed` badge, the reason, and **no amount**. Its only action is `Reset to calculated` (issue #132) |
| error | n/a — surfaces in the dialog's [`error-summary.md`](error-summary.md) |

### The removed row lost its dashed border, and needed a new expression

`.travel-line-removed` drew a dashed edge on a transparent background, which said "this is
a placeholder, not a line that counts". A row has no edge to dash, so that signal is gone
with the box.

What replaces it is three **textual** signals, which is stronger than the border was and
satisfies ADR-0020 outright: the grey `Removed` badge, the "Removed from the report.
Reason: …" line, and the absence of any amount in the right-hand column. It contributes
nothing to the per-diem subtotal or the report total, and there is nothing to attach to it.

This is **app-origin styling** — the design draws no removed state — so it is a judgement
this spec is recording rather than reading. It is the row most worth looking at in visual
verification: if a muted label in a list of rows does not read as "not on the report", it
needs a real treatment and the choice comes back open.

### Responsive

Both rules exist because of ADR-0020's touch-target and small-screen floor:

- `.travel-line-heading` **wraps**, so the `Overridden` badge drops below an unbroken
  label rather than folding it into a three-line column. `.expense-row-name` stays
  `white-space: nowrap` inside it.
- Below **34rem** `.travel-line-row` wraps, and the 40px indent is dropped — at that width
  it costs more than the nesting it signals is worth.

The old `.travel-line-actions` wrapping rule goes with the inline buttons: a `⋮` trigger
is one 21px control and has nothing to wrap.

## Code example

```java
var row = new HorizontalLayout(left, right, actions);
row.addClassNames("expense-row", "travel-line-row");   // or "travel-row" for the trip
```

## Divergence

| | Design / decided | Code today |
|---|---|---|
| The box | a row in the `--travel` section card | `.line-card` + `.travel-card`, its own box |
| Trip distinction | the card's border + `--aura-shadow-s` | `--aura-accent-surface` + accent border |
| Trip glyph colour | inherits the row's text colour | `--aura-accent-color` |
| Generated lines | indented rows in the same card | separate `.line-card`s in a `.travel-group` |
| Nesting signal | 40px `padding-inline-start` | a wrapper div, no indent |
| Actions | `RowActionMenu` | inline `Receipt` / `Override` / `Reset` / `Edit` / trash buttons |
| Removed state | muted label + badge + reason, no border | dashed border, transparent background |

**Owner:** the report-detail redesign issue this survey files.

## A design defect worth naming

Every generated row on the frame carries the same `net €79.68 · VAT €20.32 (25.5 %)`
string, **including the per-diem row** — a 25.5% VAT split on a statutory tax-free
allowance, beside an amount (€962.00) that matches neither figure. It is mock text
repeated down the column, not a specification.

A tax-free allowance row must show **no** VAT split. The app already distinguishes them
(`GeneratedLineView.isTaxFreeAllowance`), and that behaviour stands; only the VAT-bearing
generated line — parking — carries the net/VAT sub-line. Reported to the designer with the
frame's other defects.

## Cross-references

[`expense-line-card.md`](expense-line-card.md) — the base row ·
[`expense-item-card.md`](expense-item-card.md) — the `--travel` card ·
[`badge.md`](badge.md) — the `Overridden` and `Removed` badges ·
[`row-action-menu.md`](row-action-menu.md) ·
[`editor-dialog.md`](editor-dialog.md) — `TravelEditorDialog` and the preview ·
ADR-0024 (quantity override), ADR-0025, ADR-0020, issue #132

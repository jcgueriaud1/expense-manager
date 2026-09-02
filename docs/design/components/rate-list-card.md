# Rate list card

**Category:** composite (CSS)
**Origin:** design
**Implementation:** unaudited
**Code:** `.rate-list-card` and the `.rate-row` family in
`styles.css`, composed by `AllowanceRatesView` (#169)
**Design:** node `156:5697` › `expense-item-card`, rows `156:5698` / `156:5708` /
`156:5718`, dividers `156:5707` / `156:5717`

## Overview

**One** card holding several read-only labelled values, each row separated by a dotted
rule, each carrying its own overflow menu. On the allowance-rates view it holds the three
per-year rates: domestic per diem, kilometre allowance, meal allowance.

Reach for it when a small fixed set of settings belongs together and each is edited
individually. **Not** for a list that grows — that is a `Grid`, as the foreign per-diems
on the same frame are.

**Not** [`expense-line-card.md`](expense-line-card.md), despite the design node being
named `expense-item-card`: that one is *one card per line* with no internal dividers, and
its rows are report content rather than configuration. The design reuses the same Figma
component for both; the app should not reuse the same CSS class, because the two diverge
on the thing that matters — how many rows a card holds.

## Anatomy

| Part | Class | Design node |
|---|---|---|
| Card | `.rate-list-card` | `156:5697` |
| One row | `.rate-row` | `156:5698` |
| Row icon | `.rate-row-icon` — a `LucideIcon` at `SIZE_M` | `156:5700` |
| Row title | `.rate-row-title` | `156:5702` |
| Value group (right) | `.rate-row-values` | `156:5731` |
| One value | `.rate-row-value` — a regular label + a semibold amount | `156:5705` |
| Separator dot between two values | `.rate-row-dot` — 8px circle | `156:5734` |
| Row actions | [`row-action-menu.md`](row-action-menu.md) | `178:2029` |
| Divider | `.rate-row + .rate-row` top border | `156:5707` |

The domestic row carries **two** values in one row — "Full day (over 10h) €54.00" and
"Partial day (over 6h) €25.00" — separated by the dot. The kilometre and meal rows carry
one, right-aligned. Label regular, amount semibold, in the same `.rate-row-value`.

## Tokens used

| Property | Token | Design value |
|---|---|---|
| Card background | `--aura-surface-color` | kit `shades/surface-4` |
| Card radius | `--em-card-radius` (12) | 12 |
| Card padding | `--em-card-padding` (20) | 20 |
| Row gap | `--em-card-padding` (20) | 20 |
| Card shadow | `--aura-shadow-xs` | `0 1px 4px 0` — hand-drawn, one step off the design's own Shadow XS (`-2px` spread) |
| Divider | `1px dotted var(--vaadin-border-color-secondary)` | a dotted 1px rule, drawn as a raster `Line` layer |
| Icon | `--em-icon-size-m` (20) | 20 |
| Icon gap | `--em-card-padding` (20) | 20 |
| Row title | `--aura-font-size-l` (16), `--aura-font-weight-semibold` (600) | Inter **Bold** 15 |
| Value label | `--aura-font-size-l` (16), `--aura-font-weight-regular` | Inter Regular 15 |
| Value amount | `--aura-font-size-l` (16), `--aura-font-weight-semibold` (600) | Inter Bold 15 |
| Text colour | `--vaadin-text-color` | bound |
| Separator dot | `--vaadin-border-color`, 8px | 8px |

**15px and Inter are both already-settled divergences, not new ones.** The family follows
`--aura-font-family` (Instrument Sans) because the *variable* wins over the drawn text
(ADR-0025 decision 4), and 15px takes `--aura-font-size-l` = 16 — both recorded in
[`../foundations/typography.md`](../foundations/typography.md). The Bold→600 step is the
same settled row. Rediscovering them here would be the drift this folder exists to stop.

The 8px separator dot is a literal, not a token: it is one occurrence and it is a
*size*, so `--vaadin-gap-s` would be a coincidence of number rather than a shared meaning.
It is also **not** `.category-dot`, which is 0.625rem and carries a per-line hue.

## API

CSS-only. Composed in `AllowanceRatesView`.

## States

| State | Behaviour |
|---|---|
| default | surface fill, `--aura-shadow-xs`, dotted rules between rows |
| hover | **none** on the card or the row — the design draws none, and the row is not itself actionable. The `⋮` inside it has its own hover |
| active | n/a — nothing in the card is pressable except the `⋮` |
| focus | n/a on the card and the row; focus lives on the `⋮` button |
| disabled | n/a. A row the user may not edit renders without its `⋮`, the same way `expense-line-card` drops its action buttons |
| error | n/a — rate validation surfaces in [`error-summary.md`](error-summary.md) inside the editor dialog |

## Code example

```java
var card = new Div();
card.addClassName("rate-list-card");
card.add(rateRow(LucideIcon.CAR_TAXI_FRONT, "Domestic per Diem",
        List.of(value("Full day (over 10h) ", "€54.00"),
                value("Partial day (over 6h) ", "€25.00")),
        () -> openDomesticEditor(year, domestic)));
```

## Row icons — implemented as drawn, and one is wrong

The design draws `car-taxi-front` on **Domestic per Diem**, `bed` on **Kilometre
Allowance**, and `utensils` on **Meal Allowance**. Only the third matches its row; the
first two are the expense-category glyphs that come with the reused
`expense-item-card` component — a bed for a per-kilometre mileage rate says something
false.

**Decision: implement as drawn** (ADR-0025 decision 1, the design wins on visual choices),
and **report the mismatch to the designer** alongside the other measured defects in #160.
If the glyphs were the component's defaults rather than a choice, the frame should be
corrected and this row re-surveyed — it is a cheap change on both sides while nothing
depends on it.

The three glyphs are new to the sprite; see
[`../foundations/iconography.md`](../foundations/iconography.md).

## Cross-references

[`expense-line-card.md`](expense-line-card.md) — the sibling this is confused with ·
[`row-action-menu.md`](row-action-menu.md) ·
[`../foundations/typography.md`](../foundations/typography.md) — the 15px and Inter rows ·
ADR-0025 (the design as contract), ADR-0020 (never colour alone)

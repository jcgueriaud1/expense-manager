# Expense item card

**Category:** composite (CSS)
**Origin:** design
**Implementation:** none — the spec is ahead of the code; this survey's delta creates it
**Code:** — nothing implements it yet
**Design:** node `116:4444` › `expense-item-card` — `116:4935` (travel variant) and
`116:4957` (expenses variant); the section wrapper is `116:4931` / `116:4953`

## Overview

**One card per section, holding every row in it.** The report detail has two of them —
Travel Info and Expenses — and each is a single surface whose rows are separated by
internal rules.

This is the design's re-cut of the line list, and it is the opposite of what the app
builds today: [`expense-line-card.md`](expense-line-card.md) described *one card per
line*, stacked with a gap between. That reading was correct against the older frame and
is now wrong. A row is no longer a card; it is a row inside this one.

**Not** [`report-list-section.md`](report-list-section.md), which is a collapsible
`Details` on the report list. This section does not collapse and has no count.

**Not** [`totals-card.md`](totals-card.md) either, although the two are deliberately the
same box — same radius, padding, surface and shadow. The totals card carries figures and
no rows, and it sits outside any section wrapper.

## Anatomy

| Part | Element |
|---|---|
| Section wrapper | a `Div`, column, `--em-section-gap` from its neighbours, `--em-card-padding` between its heading row and the card |
| Section heading row | `HorizontalLayout`, `JustifyContentMode.BETWEEN` |
| Section label | `.section-label` — "TRAVEL INFO", "EXPENSES" |
| Add action | tertiary `Button` with `LucideIcon.PLUS`, label "Add" |
| Card | `.expense-item-card` |
| Card variant | `.expense-item-card--travel` — adds the border and lifts the shadow |
| One row | `.expense-row` — see [`expense-line-card.md`](expense-line-card.md) |
| Internal rule | a `border-block-start` on every row **but the first** — not a separate element |

### The rule is 20 above and 20 below, and the arithmetic is easy to get wrong

The design draws the separator as its own `Line` node with the card's 20px gap on *both*
sides, so two rows sit 40px apart with the rule centred between them. Measured on
`116:4935`: row 1 ends at y=87, the rule is at y=107, row 2 starts at y=127.

Do **not** implement that as a `border-block-end` on the row, which gives 20px total and
reads half as tight as the design. Implement it as:

- the card at `padding: var(--em-card-padding)` and `gap: var(--em-card-padding)`;
- every row after the first at `border-block-start: 1px solid …` plus
  `padding-block-start: var(--em-card-padding)`.

The gap contributes the 20 above the rule, the row's own padding the 20 below, and the
card's padding gives the first and last rows their 20px outer inset — with no separator
element in the DOM.

## Tokens used

| Property | Token | Value |
|---|---|---|
| Radius | `--em-card-radius` | 12 |
| Padding | `--em-card-padding` | 20 |
| Row gap | `--em-card-padding` | 20 |
| Background | `--aura-surface-color` | — |
| Shadow, default | `--aura-shadow-xs` | — |
| Shadow, `--travel` | `--aura-shadow-s` | — |
| Border, `--travel` | `1px solid var(--vaadin-border-color)` | — |
| Internal rule | `1px solid var(--vaadin-border-color-secondary)` | — |
| Section gap | `--em-section-gap` | 40 |
| Section label | `.section-label` — `--aura-font-size-xs`, semibold, uppercase, secondary | 12 |

Two of those are **app-side inferences, not design values**, and both are marked so
because the design gives nothing to read:

- **The internal rule's colour.** The design's `Line` is a rasterised 1px PNG, so it
  carries no fill, no variable and no colour. `--vaadin-border-color-secondary` is the
  app's established token for a rule *inside* a card (the totals card and the action bar
  both use it), against `--vaadin-border-color` for a card's own edge.
- **The `--travel` border colour.** The design draws a raw `rgba(10, 11, 13, 0.5)` bound
  to no variable — 50% of `--aura-neutral-light`, which is far heavier than any border in
  the theme. The *mechanism* is the design's and is adopted; the literal is not. See
  [Divergence](#divergence).

## The two variants exist to separate a trip from an expense

The design distinguishes the Travel Info card from the Expenses card by **giving it a
border and a stronger shadow**, on the same surface fill. The Expenses card has no border
at all.

That replaces the accent tint the app uses today, which
[`travel-card.md`](travel-card.md) documented as "a subtle accent-tinted surface so a
trip reads as the report's headline". Same intent, different means, and the design's means
wins under [ADR-0025](../../adr/0025-figma-design-source-of-truth.md).

## API

CSS-only. Composed in `ReportDetailView`.

## States

| State | Behaviour |
|---|---|
| default | surface fill, radius 12, `--aura-shadow-xs`; the `--travel` variant adds a 1px border and `--aura-shadow-s` |
| hover | **none.** The card is a container, and the design draws no hover on it. The rows inside it are not clickable either — see [`expense-line-card.md`](expense-line-card.md) § States — so the only hover on this component is the row's `⋮` trigger, which belongs to [`row-action-menu.md`](row-action-menu.md) |
| active | n/a — nothing here is pressable |
| focus | n/a on the card. Focus lives on the `⋮` trigger and, in the section heading, on the Add button |
| disabled | n/a. A read-only report renders its rows **without** a `⋮` menu and the section **without** its Add button, rather than as disabled controls — the rule the whole view follows |
| error | n/a — a section has no validation state. Line validation surfaces in [`error-summary.md`](error-summary.md) inside the editor dialog |

**Empty:** a section with no rows renders no card. The Expenses section additionally shows
its "No expenses yet — add your first." prompt, and only while the report is editable
(ADR-0020: never invite an action the report cannot offer). The design draws neither case.

## Code example

```java
var card = new Div();
card.addClassName("expense-item-card");          // + "expense-item-card--travel"
card.setWidthFull();

var label = new Span("Expenses");
label.addClassName("section-label");
var add = new Button("Add", LucideIcon.PLUS.create());
add.addThemeVariants(ButtonVariant.TERTIARY);
add.getElement().setAttribute("aria-label", "Add expense");

var heading = new HorizontalLayout(label, add);
heading.setWidthFull();
heading.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
heading.setAlignItems(FlexComponent.Alignment.CENTER);
```

The `aria-label` is not optional. The design labels both Add buttons simply "Add", so the
page carries two controls with the same accessible name; the label names which section it
adds to. The visible text stays "Add" as drawn.

## Divergence

The design's own values, where they are not being implemented as drawn:

| | Design | Implemented | Why |
|---|---|---|---|
| `--travel` border | `rgba(10, 11, 13, 0.5)` — a raw literal, no variable | `--vaadin-border-color` | A 50%-opacity near-black edge is heavier than anything else in the theme, and an unbound literal cannot track the colour scheme. Reported to the designer |
| Card shadow | `0 1px 4px 0` / `0 2px 5px 0` | `--aura-shadow-xs` / `--aura-shadow-s` | Same offsets and blur as the file's own named Shadow XS / Shadow S effects, with the spread zeroed. The named effect is the design-system decision; the drawn copy lost its spread. See [`../foundations/elevation.md`](../foundations/elevation.md) |

Both are **settled**, and neither is drift: they are the design contradicting itself
between a variable and a drawn value, which the variable wins.

## Cross-references

[`expense-line-card.md`](expense-line-card.md) — one row inside this card ·
[`travel-card.md`](travel-card.md) — the trip row and its nested allowance rows ·
[`totals-card.md`](totals-card.md) — the same box, holding figures ·
[`row-action-menu.md`](row-action-menu.md) — every row's actions ·
[`report-list-section.md`](report-list-section.md) — the collapsible sibling, not this ·
[`../foundations/elevation.md`](../foundations/elevation.md) ·
[`../foundations/spacing.md`](../foundations/spacing.md) — the 20/40 off-scale values ·
ADR-0025 (the design as contract), ADR-0020 (never invite an unavailable action)

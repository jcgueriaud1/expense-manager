# Report card

**Category:** composite (CSS on a `RouterLink`)
**Origin:** design
**Implementation:** unaudited
**Code:** `styles.css` — `.report-card*`; `MyReportsView#reportCard`
**Design:** `106:1146` `report-item` (`State=Default` / `State=Closed`), instanced in
frame `116:2499` "My Expenses" at `88:12941`, `106:1205` and `99:653`

## Overview

One row in the report list: a whole report summarised as a navigable card — title,
status, its trips, when it was created, and its total. It **is** the link — the classes
sit on a `RouterLink`, not on a wrapper — so the entire card is one tab stop and one
click target.

The card has two variants, and which one a report gets is the same predicate that sorts
it into a [section](report-list-section.md): `--actionable` for a report the owner can
still act on (draft or rejected), plain for one that is closed. Actionable lifts to an
elevated surface with a shadow; closed sits flat with a border and a dimmed title, so
"needs you" is visible without reading a word.

**Not** for an expense line inside a report — that is
[`expense-line-card.md`](expense-line-card.md), which is not a link.

## Anatomy

| Part | Class | Content |
|---|---|---|
| Card / link | `.report-card` (+ `.report-card--actionable`) | |
| Title row | `.report-card-title-row` | title, then status badge |
| Title | `.report-card-title` | the report's `additionalInformation`; truncates with an ellipsis |
| Status | [`badge.md`](badge.md) | Submitted / Approved / Rejected |
| Trip list | `.report-card-trips` | one row per trip, rules between |
| Trip row | `.report-card-trip` | plane icon + route on the left, dates on the right |
| Footer | `.report-card-footer` | meta on the left, total on the right |
| Meta | `.report-card-meta` | "Created on …", and for a rejected report a second entry |
| Total | `.report-card-total` | |

**One row is one trip, not one leg.** `Travel.destinations` is a single free-text string
the user types, so the design's `Turku → Helsinki → Copenhagen` is one trip with the
arrows the user wrote — the app neither parses nor composes the route. A card with three
rows has three trips; a card with one row showing three places has one. Nothing here
needs a leg model, and inventing one would misread the design.

The dates on a trip row are the trip's own `departureAt`–`returnAt`, formatted
`25 Aug 2026 – 25 Aug 2026` (en dash, spaced). A single-day trip repeats the date rather
than collapsing to one — the design draws it that way and a collapsed range reads as
missing data.

The rejected card's meta carries a **second entry** separated by a 6px dot:
`Rejected by <actor> on <date>`, taken from the latest `REJECTED` status change. Only a
rejected report gets it; the dot and the second entry are absent otherwise.

## Tokens used

| Property | Token |
|---|---|
| Padding | `--em-card-padding` (20) |
| Radius | `--em-card-radius` (12) |
| Column gap | `--vaadin-gap-s` (8) |
| Background, both variants | `--aura-surface-color` |
| Shadow, actionable | `--aura-shadow-xs` |
| Border, closed | `1px solid var(--vaadin-border-color-secondary)` |
| Title | `--em-font-size-title` (24), `--aura-font-weight-semibold` |
| Title colour, actionable | `--vaadin-text-color` |
| Title colour, closed | `--vaadin-text-color-secondary` |
| Title row padding-bottom | `--vaadin-padding-s` (8) |
| Trip row padding | `--vaadin-padding-s` (8) |
| Trip row radius | `--vaadin-radius-s` (5) |
| Trip row icon gap | `--vaadin-gap-s` (8) |
| Trip rule | `1px solid var(--vaadin-border-color-secondary)` |
| Route text | `--aura-font-size-m` (14), `--vaadin-text-color` |
| Trip dates | `--aura-font-size-xs` (12), `--vaadin-text-color` |
| Footer padding-top | `--vaadin-padding-s` (8) |
| Meta | `--aura-font-size-xs` (12), `--vaadin-text-color-secondary` |
| Total | `--em-font-size-total` (20), `--aura-font-weight-semibold`, `--vaadin-text-color` |
| Trip icon | `VaadinIcon.AIRPLANE` at 16px |

A `RouterLink` is not a Vaadin layout, so this component's `display: flex`,
`flex-direction` and `gap` legitimately live in CSS rather than in Java — the exception
that [`../../theming-layouts.md`](../../theming-layouts.md) allows.

The trip dates use the **primary** text colour, not secondary, even at 12px. That is the
design's choice and it is deliberate enough to be drawn consistently across all four
cards, so it is not treated as an oversight.

`VaadinIcon.AIRPLANE` is already the app's travel glyph (`ReportDetailView`), so the
design's `lucide/plane` maps onto existing precedent rather than a new icon set. The
design carries no `lumo:*` annotation here, so `LumoIcon` is not the prescribed set on
this component.

### Kit variables that must not be copied

The design's reference code names four properties Aura does not define. Each ships a
fallback, so a literal translation freezes at that literal and never follows the colour
scheme again (F-062):

| In the design | Freezes at | Use instead |
|---|---|---|
| `--shades/surface-4` (actionable), `--shades/surface-3` (closed) | `rgba(255,255,255,0.85)` | `--aura-surface-color` |
| `--aura-border-color-secondary` | `rgba(3,3,3,0.08)` | `--vaadin-border-color-secondary` |
| `--text-colors/header-text` | `#0b0b0b` | `--vaadin-text-color` |
| `--lumo-font-family`, `--lumo-font-size-s` (badge) | Instrument Sans, 13px | Aura's own; see [`badge.md`](badge.md) |

The two surface names differ only in level, which is how the kit expresses elevated vs
recessed. Aura expresses the same thing with `--aura-surface-level`, so the variants are
distinguished by shadow and border here rather than by two background colours.

## API

CSS-only. Composed in `MyReportsView`.

## States

| State | Behaviour |
|---|---|
| default | `--aura-surface-color`, 1px border, no shadow, title in secondary colour |
| hover | border tints to `--aura-accent-color`, `transition: border-color 0.12s ease` |
| active | browser default for a link |
| focus | browser/Aura focus ring on the `RouterLink` — the whole card, since the card is the link |
| disabled | n/a — a non-navigable report is not rendered as a card |
| error | n/a — a card renders a report's status, it does not carry validation |

**The hover row was an open question and #162 closed it.** It used to read
`background: var(--aura-surface-color)` — which is what *both* variants already sit on, so
an actionable card, the majority of what a user sees, changed not at all while the pointer
cursor promised it would. The design draws no hover state, so there was nothing to copy and
the redesign had to pick one.

Decided: **the border tints to `--aura-accent-color`.** It is visible on both variants
(the actionable card carries the same 1px border as the closed one, transparent, so the
tint costs no reflow and the box model never shifts); it is distinct from the shadow that
distinguishes the two variants, so hovering an actionable card cannot be misread as a
change of state; and it follows the colour scheme, which a literal would not. The two
alternatives were a shadow lift — rejected because a closed card gaining a shadow reads as
becoming actionable — and a translate, rejected as motion for its own sake on a list that
can hold dozens of cards.

Rendered contrast of the closed card's dimmed title against `--aura-surface-color`,
measured in the running app (#162): **6.21:1 light, 8.35:1 dark**, at 24px/600. It clears
AA for large text (3:1) and normal text (4.5:1) alike, so the dimming is safe as drawn. The
card's other secondary text measures the same, and the trip dates — primary colour at 12px,
the design's deliberate choice — measure 19.16:1 light.

## Divergence

Recorded against the current `MyReportsView#reportCard`, for the report-list redesign to
close:

| What | Now | Design |
|---|---|---|
| Trips | absent | one row per trip, with route and date range |
| Title size | `--aura-font-size-m` (14) | `--em-font-size-title` (24) |
| Padding / radius | `--vaadin-padding-l` (16) / `--vaadin-radius-l` (15) | `--em-card-padding` (20) / `--em-card-radius` (12) |
| Total size | unset | `--em-font-size-total` (20) |
| Footer date | `reportDate` | `createdAt`, labelled "Created on" |
| Rejected meta | absent | "Rejected by <actor> on <date>" |
| Closed title | full text colour | `--vaadin-text-color-secondary` |
| Footer rule | `border-top` on the footer | no rule; the rules are between trip rows |

The footer date is the one that changes meaning rather than looks: `reportDate` is
user-entered and is also the sort and filter key, while the design's label says
"Created on". Both are carried on the summary DTO from #147 so the footer can render
`createdAt` while sorting stays on `reportDate`.

## Code example

```java
var card = new RouterLink("", ReportDetailView.class, new RouteParam("id", id));
card.addClassNames("report-card", "clickable");
if (needsAttention) card.addClassName("report-card--actionable");
```

## Cross-references

[`expense-line-card.md`](expense-line-card.md) ·
[`badge.md`](badge.md) ·
[`report-list-section.md`](report-list-section.md) — the group it sits in ·
[`metric-card.md`](metric-card.md) ·
[`empty-state.md`](empty-state.md) — what renders when the list is empty ·
[`../foundations/spacing.md`](../foundations/spacing.md)

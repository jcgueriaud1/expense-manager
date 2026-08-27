# Report card

**Category:** composite (CSS on a `RouterLink`)
**Status:** settled, pending per-view revision
**Source:** `styles.css` — `.report-card*`; `MyReportsView`

## Overview

One row in the report list: a whole report summarised as a navigable card. It **is** the
link — the classes sit on a `RouterLink`, not on a wrapper — so the entire card is one
tab stop and one click target.

Use `.report-card--actionable` for a report that needs the owner's attention; it lifts to
an elevated surface with a shadow so "needs you" is visible without reading.

**Not** for an expense line inside a report — that is
[`expense-line-card.md`](expense-line-card.md), which is not a link.

## Anatomy

| Part | Class |
|---|---|
| Card / link | `.report-card` (+ `.report-card--actionable`) |
| Title | `.report-card-title` — truncates with an ellipsis |
| Footer strip | `.report-card-footer` — separated by a top rule |
| Total | `.report-card-total` |
| Status | [`badge.md`](badge.md) |

A `RouterLink` is not a Vaadin layout, so this component's `display: flex`,
`flex-direction` and `gap` legitimately live in CSS rather than in Java — the exception
that [`../../theming-layouts.md`](../../theming-layouts.md) allows.

## Tokens used

| Property | Token |
|---|---|
| Padding | `--vaadin-padding-l` (16) |
| Column gap | `--vaadin-gap-s` (8) |
| Border | `1px solid var(--vaadin-border-color)` |
| Radius | `--vaadin-radius-l` (15) |
| Background, rest | `--vaadin-background-container` |
| Background, actionable / hover | `--aura-surface-color` |
| Shadow, actionable | `--aura-shadow-xs` |
| Footer rule | `--vaadin-border-color-secondary` |
| Title | `--aura-font-size-m`, `--aura-font-weight-semibold` |
| Total | `--aura-font-size-l`, `--aura-font-weight-semibold` |
| Text | `--vaadin-text-color` |

**Pending per-view revision:** the design draws this card at 12px radius and 20px
padding. It currently renders `--vaadin-radius-l` (15) and `--vaadin-padding-l` (16).
Switching to `--em-card-radius` / `--em-card-padding` belongs to the report-list issue.

## API

CSS-only. Composed in `MyReportsView`.

## States

| State | Behaviour |
|---|---|
| default | container background, 1px border |
| hover | background lifts to `--aura-surface-color`, `transition: background 0.12s ease` |
| active | browser default for a link |
| focus | browser/Aura focus ring on the `RouterLink` — the whole card, since the card is the link |
| disabled | n/a — a non-navigable report is not rendered as a card |
| error | n/a |

## Code example

```java
var card = new RouterLink("", ReportDetailView.class, new RouteParam("id", id));
card.addClassNames("report-card", "clickable");
if (needsAttention) card.addClassName("report-card--actionable");
```

## Cross-references

[`expense-line-card.md`](expense-line-card.md) ·
[`badge.md`](badge.md) ·
[`empty-state.md`](empty-state.md) — what renders when the list is empty ·
[`../foundations/spacing.md`](../foundations/spacing.md)

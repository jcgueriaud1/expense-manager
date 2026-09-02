# Metric card

**Category:** composite (CSS on a `Div`/`VerticalLayout`)
**Origin:** design
**Implementation:** unaudited
**Code:** `MetricCard.java`; `styles.css` — `.metrics-row`, `.metric-card*`
**Design:** `88:12918` `metrics-row` > `88:12919` / `88:12923` / `88:12927` `metric-card`

## Overview

One at-a-glance aggregate above a list: a caption, one large figure, and a one-line
breakdown under it. Three sit side by side in a `metrics-row` so the row reads as a
single band of context before the user reaches the filters.

It is a **summary of the list below it**, never a control — nothing in it is clickable
and it has no hover or focus affordance. If a figure should filter the list, that is a
different component and needs a design.

**Not** for a single report's money: a report's own net/VAT/total breakdown is
[`totals-card.md`](totals-card.md), which is a labelled table inside one report rather
than an aggregate across many.

The same component serves **My Expenses** (`88:12918`) and **Approvals**
(`327:11681`) — the two frames are identical but for the copy, so build it parameterised
by caption/figure/sub-line rather than hard-coding this view's three.

## Anatomy

| Part | Class | Content |
|---|---|---|
| Row | `.metrics-row` | three cards, equal width, `flex: 1 0 0` with `min-width: 0` |
| Card | `.metric-card` | column |
| Caption | `.metric-card-label` | "Needs you", "In flight", "Reimbursed 2026" |
| Figure | `.metric-card-value` | `2`, `1`, `€100.00` — a count *or* an amount |
| Sub-line | `.metric-card-sub` | "€1234.00 · 1 rejected", "waiting 36 days", "1 approved" |
| Emphasis in sub-line | `.metric-card-sub-alert` | the "1 rejected" fragment only |

The figure is a count on two cards and an amount on the third, so the caption is what
tells the user which — do not append a unit to the figure to disambiguate.

The sub-line is **partially coloured**: only the rejected fragment is red, the rest is
secondary text. That is one `Span` inside another, not two siblings with a separator, so
the "·" stays in the secondary colour.

## Tokens used

| Property | Token |
|---|---|
| Row gap | `--vaadin-gap-l` (16) |
| Padding | `--em-card-padding` (20) |
| Column gap | `--vaadin-gap-m` (12) |
| Radius | `--em-card-radius` (12) |
| Background | `--aura-surface-color` |
| Border | `1px solid var(--vaadin-border-color-secondary)` |
| Caption | `--aura-font-size-xs` (12), `--aura-font-weight-medium`, `--vaadin-text-color-secondary` |
| Figure | `--em-font-size-metric` (28), `--aura-font-weight-semibold`, `--vaadin-text-color` |
| Sub-line | `--aura-font-size-xs` (12), `--vaadin-text-color-secondary` |
| Sub-line alert | `--aura-red-text` |

The design draws the caption at `#64748b` and the figure at `#0f172a`; both are the
kit's literals for what Aura computes as `--vaadin-text-color-secondary` and
`--vaadin-text-color`. Use the tokens — the literals do not follow the colour scheme.

The design's reference code names `--shades/surface-3` for the background,
`--aura-border-color-secondary` for the border, and `--text-colors/header-text` for the
figure. **Aura defines none of the three.** Each ships a fallback, so a literal
translation renders `rgba(255,255,255,0.85)` / `rgba(3,3,3,0.08)` / `#0b0b0b` frozen for
good and never tracks the theme again (F-062). The border one is the trap worth naming:
`--aura-border-color-secondary` is one prefix away from the real
`--vaadin-border-color-secondary`.

## API

None yet. When it is built it should take `(caption, figure, subLine)` with the alert
fragment optional, so the Approvals view reuses it — see *Overview*.

## States

| State | Behaviour |
|---|---|
| default | the only visual state |
| hover | **n/a** — not interactive, and must not appear to be |
| active | n/a — not interactive |
| focus | n/a — not a tab stop; it holds no control and no link |
| disabled | n/a — a metric has no enabled/disabled axis |
| error | n/a — a metric that cannot be computed shows a zero, not an error (see *Empty*) |

### Empty

A user with no reports gets **zeroes, not blanks**: `0`, `€0.00`, and a sub-line that
still renders. The card is never hidden and never shows a dash — a missing card reads
as a broken page, and the zero is the honest answer. This is asserted in the service
tests for #147.

## Copy

Exactly as drawn, because each line is doing specific work:

| Card | Caption | Figure | Sub-line |
|---|---|---|---|
| 1 | `Needs you` | count of drafts + rejected | `€<total> · <n> rejected` |
| 2 | `In flight` | count of submitted | `waiting <n> days` |
| 3 | `Reimbursed <year>` | total € approved this year | `<n> approved` |

The year in caption 3 is **part of the data**, not a literal — the service returns it
(#147) so the caption and the figure can never disagree.

"Needs you" counts **drafts and rejected together**, with rejected also called out in
the sub-line — it is a breakdown of the figure, not an addition to it. The design's own
sample data contradicts this (its NEEDS YOUR ATTENTION section holds a Submitted and an
Approved card while the Rejected one sits under CLOSED); the metric's copy, the header
banner's "2 items need you attention", and the app's existing
`ReportStatus.isEditable()` grouping all agree against it, so the sample data is the
defect. Confirmed for #147; raised with the designer in the survey's follow-up issue.

## Code example

```java
var card = new VerticalLayout(
        span("Needs you", "metric-card-label"),
        span(String.valueOf(metrics.needsYouCount()), "metric-card-value"),
        subLine(metrics));
card.addClassName("metric-card");
card.setPadding(false);
card.setSpacing("var(--vaadin-gap-m)");
```

## Cross-references

[`totals-card.md`](totals-card.md) — one report's money, not many ·
[`report-card.md`](report-card.md) — the list the metrics summarise ·
[`report-list-section.md`](report-list-section.md) ·
[`../foundations/typography.md`](../foundations/typography.md) — why 28px has a property

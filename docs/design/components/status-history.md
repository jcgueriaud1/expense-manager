# Status history

**Category:** composite (CSS)
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `styles.css` — `.status-history*`; `ReportDetailView`
**Design:** node `116:4444` › `status-history-box`

## Overview

The audit trail of a report's transitions, at the foot of the detail view. Chronological,
read-only, one entry per transition.

Distinct from [`status-callout.md`](status-callout.md): the callout explains the *current*
state prominently at the top; the history lists *every* state quietly at the bottom.

## Anatomy

| Part | Class |
|---|---|
| Section | `.status-history` — separated by a top rule |
| Section heading | `.status-history-heading` |
| One entry | `.status-history-entry` |
| Transition label | `.status-history-label` |
| Comment | `.status-history-comment` — italic |

## Tokens used

| Property | Token |
|---|---|
| Section top rule | `--vaadin-border-color-secondary` + `--vaadin-padding-m` |
| Entry gap | `--vaadin-gap-s` (8) |
| Heading | `--aura-font-size-s`, semibold |
| Entry padding | `--vaadin-padding-s` (8) |
| Entry radius | `--vaadin-radius-m` (9) |
| Entry background | `--aura-surface-color` |
| Entry border | `1px solid var(--vaadin-border-color)` |
| Label | semibold |
| Comment | `--aura-font-size-s`, italic, `--vaadin-gap-xs` top margin |

Note the deliberate step down from the cards: `--vaadin-radius-m` (9) and
`--vaadin-padding-s` (8) rather than the cards' `l`/`l`. An entry is subordinate to a
card, and the scale is what expresses that.

## API

CSS-only. Composed in `ReportDetailView`.

## States

| State | Behaviour |
|---|---|
| default | surface entry, 1px border |
| hover | n/a — not interactive |
| active | n/a |
| focus | n/a |
| disabled | n/a |
| error | n/a — a rejection entry is ordinary content, styled no differently |

## Code example

```java
var history = new Div();
history.addClassName("status-history");
var entry = new Div(label, comment);
entry.addClassName("status-history-entry");
label.addClassName("status-history-label");
comment.addClassName("status-history-comment");
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

[`status-callout.md`](status-callout.md) ·
[`../foundations/elevation.md`](../foundations/elevation.md) ·
ADR-0006 (the aggregate that owns the trail)

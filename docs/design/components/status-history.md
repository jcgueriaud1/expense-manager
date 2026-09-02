# Status history

**Category:** composite (CSS)
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `styles.css` — `.status-history*`; `ReportDetailView`
**Design:** node `116:4444` › `status-history-section` `116:4999` — **hidden on the
frame**, and unfinished; see [What the frame does not settle](#what-the-frame-does-not-settle)

## Overview

The audit trail of a report's transitions, at the foot of the detail view. Chronological,
read-only, one entry per transition. Hidden until the first transition is recorded, so a
fresh draft never shows it.

Distinct from [`status-callout.md`](status-callout.md): the callout explains the *current*
state prominently at the top; the history lists *every* state quietly at the bottom.

## Anatomy

| Part | Class |
|---|---|
| Section | `.status-history` |
| Section heading | `.section-label` — "STATUS HISTORY" |
| The box | `.status-history-box` |
| One entry | `.status-history-entry` |
| Transition label | `.status-history-label` |
| Actor and time | `.muted-xs` |
| Comment | `.status-history-comment` — italic |

**One box holding every entry**, its entries separated by rules — the same shape as
[`expense-item-card.md`](expense-item-card.md), and the same change: the app builds one
bordered box *per entry*, which the design does not.

The section's heading is `.section-label`, the same uppercase 12px role the Travel Info and
Expenses headings use. It is **not** a bespoke `.status-history-heading` at
`--aura-font-size-s`, which is what the app renders today — the design draws all three
section headings identically, so they share the class.

## Tokens used

| Property | Token | Value |
|---|---|---|
| Section gap | `--vaadin-gap-s` | 8 |
| Heading | `.section-label` — `--aura-font-size-xs`, semibold, uppercase, secondary | 12 |
| Box radius | `--em-card-radius` | 12 |
| Box padding | `--em-card-padding` | 20 |
| Box background | `--aura-surface-color` | — |
| Box shadow | `--aura-shadow-xs` | — |
| Entry gap | `--vaadin-gap-s` | 8 |
| Entry rule | `1px solid var(--vaadin-border-color-secondary)` | — |
| Entry label | `--aura-font-size-m`, `--aura-font-weight-semibold` | 14, 600 |
| Actor and time | `.muted-xs` | 12 |
| Comment | `--aura-font-size-s`, italic, `--vaadin-gap-xs` top margin | 13 |

**No top rule on the section.** The app separates it with a
`--vaadin-border-color-secondary` rule plus `--vaadin-padding-m`; the design uses the
40px `--em-section-gap` that separates every other section, and nothing else.

## What the frame does not settle

The design's node is **hidden**, and what it draws is incomplete in three ways that a
reader would otherwise take literally:

- **No fill and no border on the box** — only a `0 1px 2px` drop shadow, which against the
  page background is invisible.
- **Vertical padding only.** `py-[20px]` with no horizontal padding, so both text lines
  span the full 900px column, wider than any card on the frame.
- **A 10px entry gap** with no rule between entries, in a box drawn with a single entry —
  so nothing shows how two entries separate.

The surface, the horizontal padding, the shadow token and the entry rules in the table
above are therefore **app-side inference**, taken from
[`expense-item-card.md`](expense-item-card.md) on the grounds that a box on this page
should look like the other boxes on this page. They are marked here so the next reader
knows which rows the design actually specified — the radius, the block padding, the
heading, the type sizes and the section gap — and which this survey supplied.

This is the section to re-survey first if the designer finishes the frame.

## API

CSS-only. Composed in `ReportDetailView`.

## States

| State | Behaviour |
|---|---|
| default | one surface box, entries separated by rules |
| hover | n/a — not interactive |
| active | n/a |
| focus | n/a |
| disabled | n/a |
| error | n/a — a rejection entry is ordinary content, styled no differently. Its *reason* is what the entry's comment carries, and the callout repeats it at the top |

**Empty:** the whole section is hidden when the history is empty, rather than rendering a
box with a "no history" message. A fresh draft has no transitions and needs no explanation
of that.

## Code example

```java
var box = new Div();
box.addClassName("status-history-box");
var heading = new Span("Status history");
heading.addClassName("section-label");        // uppercased in CSS, never in the string
```

The label is uppercased by `text-transform`, not in the Java string — a screen reader
spells out an all-caps string.

## Divergence

| | Design / decided | Code today |
|---|---|---|
| The box | one box for all entries | one bordered box per entry |
| Radius / padding | `--em-card-radius` 12 / `--em-card-padding` 20 | `radius-m` 9 / `padding-s` 8 per entry |
| Heading | `.section-label`, uppercase 12 | `.status-history-heading`, 13 sentence case |
| Section separator | `--em-section-gap` only | top rule + `padding-m` |
| Entry label | `--aura-font-size-m` 14 | inherited body size |
| Entry separation | a rule | the gap between boxes |

The previous revision recorded the 9/8 geometry as a deliberate step down from the cards —
"an entry is subordinate to a card, and the scale is what expresses that". That reasoning
described one-box-per-entry, which the design has replaced; subordination is now expressed
by the rule between rows, as everywhere else on the frame.

**Owner:** the report-detail redesign issue this survey files.

## Cross-references

[`status-callout.md`](status-callout.md) — the same state, explained at the top ·
[`expense-item-card.md`](expense-item-card.md) — the box this borrows ·
[`../foundations/elevation.md`](../foundations/elevation.md) ·
ADR-0006 (the aggregate that owns the trail), ADR-0025

# Report list section

**Category:** composite (CSS on a `Details`, or a `VerticalLayout` + header row)
**Origin:** design
**Implementation:** unaudited
**Code:** `ReportListSection.java` (a `Details`); `styles.css` — `.section-label`,
`.report-list-section*`; composed in `MyReportsView#section`
**Design:** `88:12937` `section-attention` and `88:12943` `section-submitted-closed`,
each with a `section-header` child

## Overview

One titled, **collapsible** group of report cards in the list: a disclosure chevron, an
uppercase label, a right-aligned count, and a stack of [`report-card`](report-card.md)s
under it. The list is always exactly two of these — the split is by whether the report
is the owner's to act on, not by status.

Use it only for grouping a list of cards. A titled group of *fields* inside a form is a
`FormLayout` section and carries no chevron and no count.

**The count belongs to the section, not to the page.** The current view puts a single
report count next to the page title; the design moves it into each section header, where
it says how many are in *that* group. Both at once reads as a contradiction.

## Anatomy

| Part | Class | Content |
|---|---|---|
| Section | `.report-list-section` | column: header, then card stack |
| Header | `.report-list-section-header` | chevron + label on the left, count on the right |
| Chevron | — | 24px, `VaadinIcon.CHEVRON_UP` when expanded |
| Label | `.section-label` | `NEEDS YOUR ATTENTION` / `CLOSED`, uppercase |
| Count | `.report-list-section-count` | `2 reports` / `1 report` — singular below two |
| Card stack | `.report-list-section-cards` | column of report cards |

The label is uppercased **in CSS**, not in the string — the accessible name should read
as a sentence, and a screen reader announcing "N-E-E-D-S" is a real outcome of
uppercase literals.

## Tokens used

| Property | Token |
|---|---|
| Section gap (header → cards) | `--em-card-padding` (20) |
| Card stack gap | `--em-card-padding` (20) |
| Chevron → label gap | `--vaadin-gap-s` (8) |
| Gap between the two sections | `--em-section-gap` (40) |
| Label | `--aura-font-size-xs` (12), `--aura-font-weight-semibold`, `--vaadin-text-color-secondary`, `text-transform: uppercase`, `0.05em` tracking |
| Count | `--aura-font-size-xs` (12), `--vaadin-text-color-secondary` |

`--em-card-padding` is the settled property for the design's recurring 20px, and
`foundations/spacing.md` scopes it to *padding and gap* alike, so a 20px gap uses it
rather than earning a second property with the same value.

The design draws the section gap at 10px between chevron and label and 20px elsewhere;
10px is settled globally as `--vaadin-gap-s` (8px, −2px).

## API

None — composed in the view. If it becomes a `Details`, the summary slot takes the
header row and `setOpened(true)` is the default for both sections.

## States

| State | Behaviour |
|---|---|
| default | expanded, chevron up |
| hover | header shows a pointer and the label lifts to `--vaadin-text-color`; the card stack is unaffected |
| active | browser default for the disclosure control |
| focus | Aura focus ring on the header — the header is the control, so the ring wraps chevron, label and count |
| disabled | n/a — a section is never disabled; an empty group is omitted entirely |
| error | n/a — a section carries no validation |

### Collapsed

The chevron rotates to point down, the card stack is removed from the accessible tree,
and **the count stays visible** — it is the only thing left saying what is inside, so
hiding it with the cards defeats the collapse.

Collapse state is per-session and not persisted. Both sections start expanded.

### Empty

A group with no reports is **not rendered** — no header, no zero count. When *both* are
empty the view shows [`empty-state.md`](empty-state.md); when a filter combination
empties them, the view shows its no-results hint instead. That is the existing
behaviour and the design does not change it.

## Divergence

Recorded against the current `MyReportsView#section`, for the report-list redesign to
close:

| What | Now | Design |
|---|---|---|
| Disclosure | none — a plain `Span` label | chevron, collapsible |
| Count | one total beside the page title | per section, right-aligned in the header |
| Second label | `Submitted & closed` | `CLOSED` |
| Stack gap | `--vaadin-gap-m` (12) | `--em-card-padding` (20) |

The design's own frame renders the label as `CLOSEd`. That is a typo in the drawing, not
a casing rule — the label is uppercased in CSS from `Closed`.

## Code example

```java
var header = new HorizontalLayout(disclosure, count);
header.addClassName("report-list-section-header");
header.setJustifyContentMode(JustifyContentMode.BETWEEN);

var section = new VerticalLayout(header, cards);
section.addClassName("report-list-section");
section.setPadding(false);
section.setSpacing("var(--em-card-padding)");
```

## Cross-references

[`report-card.md`](report-card.md) — what the stack holds ·
[`metric-card.md`](metric-card.md) — the band above the filters ·
[`empty-state.md`](empty-state.md) — when both sections are empty ·
[`../foundations/spacing.md`](../foundations/spacing.md) — the 20px and 10px decisions

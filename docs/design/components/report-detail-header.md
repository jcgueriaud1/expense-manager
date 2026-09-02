# Report detail header

**Category:** composite (CSS)
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `styles.css` — `.report-detail-eyebrow`, `.report-detail-title`;
`ReportDetailView.headerRow()`
**Design:** node `116:4444` › `report-header` `116:4918`, with `title-pill-row`
`116:4922` and the `Title Text` instance `241:10575` (component `241:10557`)

## Overview

The top of a report detail: a back link, the report's name, and its status pill. It
identifies the record and offers the one way out.

**Not a page heading.** The reference views' page titles are static view names at
`--em-font-size-title` (40); this is record data at 24, and the distinction is load-bearing
— see [The title is a record title](#the-title-is-a-record-title).

**Not** [`app-shell.md`](app-shell.md), which is the coral bar above it and owns the 900px
column this header sits in.

## Anatomy

| Part | Element |
|---|---|
| Row | `HorizontalLayout`, `Alignment.CENTER`, `--em-section-gap` between the back link and the title group |
| Back link | tertiary icon `Button`, `LucideIcon.ARROW_LEFT` at `--em-icon-size-l` |
| Title group | `HorizontalLayout`, `--vaadin-gap-l`, the title expanded |
| Eyebrow | `.report-detail-eyebrow` — "Report #12" / "New report" |
| Title | `.report-detail-title` |
| Status pill | [`badge.md`](badge.md), rebuilt on each load |

The back link goes to the owner's list, or to the approval queue in review mode. The design
draws it as a bare 24px glyph with no button box, which is what a `TERTIARY` `Button`
renders under Aura — it stays a `Button` rather than becoming an `Anchor` so it keeps an
accessible name and a focus ring.

## Tokens used

| Property | Token | Value |
|---|---|---|
| Title | `--em-font-size-detail-title`, `--aura-font-weight-semibold` | 24, 600 |
| Eyebrow | `--aura-font-size-xs`, `--vaadin-text-color-secondary` | 12 |
| Back glyph | `--em-icon-size-l` | 24 |
| Back → title gap | `--em-section-gap` | 40 |
| Title → pill gap | `--vaadin-gap-l` | 16 |

`--em-font-size-detail-title` is **new**, minted by this survey. Its justification and the
alternative that was rejected are in
[`../foundations/typography.md`](../foundations/typography.md) § *A record title is not a
page heading*.

The title's weight is 600, not the 700 the design draws — Aura's scale has no 700, settled
in typography for every heading on every frame.

## The title is a record title

The design draws this title at **24px** while the reference frames draw their page headings
at 40, and `--em-font-size-title` was re-decided to 40 for exactly that role. Two sizes for
what looks like one role is the contradiction typography has hit twice before, so it needs
deciding rather than assuming.

**The frame's own geometry decides it.** The `Title Text` node is 769×34 for a 44-character
title — one line at 24px with a 1.4 line height. At 40px the same string wraps to two
lines in a 900px column, and to three on a narrower one. The design laid this out as a
single line of record data, not as a page heading.

That is the same argument typography already accepted for `report-card` — "a report card
title is not a page heading" — so this is consistent with the folder's precedent rather
than a new exception. 24px is also the display ramp's middle step (20 / 24 / 28), which the
ramp had no property for until now.

## The pencil is not implemented, and neither is the title it edits

The design's title is a **report title**: "Future Product Days Conference in Copenhagen",
with a tertiary icon-only pencil beside it to edit in place. The frame proves it is a field
of its own, because the same frame renders a completely different long paragraph in the
"Additional Info" text area below.

`ExpenseReport` has no such field — only `additionalInformation`. **Decided out of scope**
in this survey: no `title` column, no pencil, and the header keeps rendering the report's
`additionalInformation` as its title, truncated.

The consequence is worth stating plainly, because it is a known infidelity and not an
oversight: the same text renders twice on the screen, once as the header title and again in
the text area. That is what the app does today; this survey did not introduce it and did
not fix it.

Two things follow:

- The pencil, and `lumo:edit` behind it, are **not** mapped. The `Title-edit glyphs` row in
  [`../foundations/iconography.md`](../foundations/iconography.md) stays **open** — it was
  conditional on "if that UI is ever built", and it is not being built.
- The eyebrow — "Report #12" / "New report" — is **kept** although the design draws no such
  line. The id is the only handle a user has on a report when talking about it, and nothing
  else on the screen carries it. Settled in the app's favour and reported to the designer.

## API

Built by `ReportDetailView.headerRow()`; the pill is `ReportViewSupport.statusBadge`.

## States

| State | Behaviour |
|---|---|
| default | back glyph, eyebrow above the title, status pill at the right |
| hover | on the back link only — Aura's own tertiary-button hover. **Unverified:** Aura expresses button hover as a `::before` overlay at low opacity, which a computed-style check cannot see |
| active | on the back link only — Aura's own pressed state |
| focus | Aura's focus ring on the back link. It is the first tab stop in the view |
| disabled | n/a. The back link is always available, on every status and in review mode; nothing here is ever disabled |
| error | n/a — the header carries no input. Form validation surfaces in [`error-summary.md`](error-summary.md) below it |

The status pill's own states are [`badge.md`](badge.md)'s; it is static text and has none.

## Code example

```java
var back = new Button(LucideIcon.ARROW_LEFT.create(LucideIcon.SIZE_L),
        event -> navigateBack());
back.addThemeVariants(ButtonVariant.TERTIARY);
back.getElement().setAttribute("aria-label", "Back to reports");

title.addClassName("report-detail-title");
var header = new HorizontalLayout(back, titleColumn, statusBadgeSlot);
header.setSpacing("var(--em-section-gap)");
header.setAlignItems(FlexComponent.Alignment.CENTER);
header.expand(titleColumn);
```

`aria-label` on the back button is required, not optional: an icon-only button with no
accessible name announces as "button" and nothing else.

## Divergence

| | Design / decided | Code today |
|---|---|---|
| Title size | `--em-font-size-detail-title` 24 | `--aura-font-size-l` 16 |
| Back glyph size | `--em-icon-size-l` 24 | the default `--em-icon-size-m` 20 |
| Back → title gap | `--em-section-gap` 40 | the layout's default spacing |
| Title → pill gap | `--vaadin-gap-l` 16 | the layout's default spacing |
| Title content | a `title` field, pencil-edited | `additionalInformation` — **decided out of scope** |
| Eyebrow | not drawn | kept — **settled in the app's favour** |
| Draft pill | a solid accent fill | grey tint — **settled in the app's favour**, see [`badge.md`](badge.md) |

**Owner:** the report-detail redesign issue this survey files, for the four geometry rows.
The title field is not owned by anyone: it is closed, not deferred.

## Cross-references

[`badge.md`](badge.md) — the status pill, and why Draft is grey ·
[`app-shell.md`](app-shell.md) — the bar above and the 900px column ·
[`button.md`](button.md) — the tertiary back link ·
[`../foundations/typography.md`](../foundations/typography.md) — the 24px decision ·
[`../foundations/iconography.md`](../foundations/iconography.md) — the still-open
title-edit row ·
ADR-0025 (the design as contract), ADR-0020 (accessible names)

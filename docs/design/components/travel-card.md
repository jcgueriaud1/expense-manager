# Travel card

**Category:** composite (CSS, extends the expense line card)
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `styles.css` — `.travel-*`; `ReportDetailView`, `TravelEditorDialog`
**Design:** node `116:4444` › the trip card and its generated lines

## Overview

A trip and its generated allowance lines. Structurally an
[`expense-line-card.md`](expense-line-card.md) with an accent-tinted surface, so a trip
reads as the report's headline rather than as one more expense line.

Add `.travel-card` **alongside** `.line-card`; it overrides only background and border.

## Anatomy

| Part | Class |
|---|---|
| Card | `.line-card` + `.travel-card` |
| Trip icon | `.travel-card-icon` — 1.25rem |
| Line label + badge row | `.travel-line-heading` |
| Generated-line row | `.travel-line-row` |
| Line actions (receipt, override, reset) | `.travel-line-actions` |
| A suppressed line | `.travel-line-removed` |
| Per-diem preview (in the dialog) | `.travel-preview`, `.travel-preview-amount` |

## Tokens used

| Property | Token |
|---|---|
| Background | `--aura-accent-surface` |
| Border colour | `--aura-accent-border-color` |
| Icon colour | `--aura-accent-color` |
| Preview padding | `--vaadin-padding-m` (12) |
| Preview radius | `--vaadin-radius-m` (9) |
| Preview gap | `--vaadin-gap-xs` (4) |
| Preview top margin | `--vaadin-gap-m` (12) |
| Preview amount | `--aura-font-size-l`, semibold |
| Removed label | `--vaadin-text-color-secondary`, `--aura-font-weight-regular` |
| "Removed" badge | grey — `SMALL` + class `aura-accent-neutral`, see [`badge.md`](badge.md) |

Everything else is inherited from `.line-card`.

## Responsive rules

Both exist because of ADR-0020's touch-target and small-screen floor, not for looks:

- `.travel-line-actions` and `.travel-line-heading` **wrap**. Several small buttons on one
  row would otherwise squeeze the amount off a phone screen, and the "Overridden" badge
  would fold the label into a three-line column. `.travel-line-heading .line-name` is
  `white-space: nowrap` so the badge drops below an unbroken label.
- Below **34rem**, `.travel-line-row` wraps and `.travel-line-actions` goes full width, so
  the three columns stack instead of pushing the card off-screen.

## API

CSS-only. Composed in `ReportDetailView`; the preview is built by `TravelEditorDialog`.

## States

| State | Behaviour |
|---|---|
| default | accent-tinted surface, accent border |
| hover | none on the card |
| active | n/a |
| focus | n/a on the card — on the action buttons inside |
| disabled | a generated line is read-only apart from attaching a receipt; it renders without edit actions rather than as a disabled control |
| **removed** | `.travel-line-removed` — dashed border, transparent background, label at secondary colour and regular weight, and a grey "Removed" badge. It is a placeholder for something **not** on the report, so it must not read as a line that counts; it still carries its reason and the way back, so it stays legible rather than merely faded (issue #132) |
| error | n/a — surfaces in the dialog's [`error-summary.md`](error-summary.md) |

The badge is grey rather than accent-tinted for the same reason the row is muted: it
labels something that is *not* on the report, and Aura's accent tint is what every
live thing on this screen wears. It asked for grey via `BadgeVariant.CONTRAST` until
2026-09-01 and did not get it — that variant is Lumo-only and silently does nothing
under Aura (F-017), so the badge rendered accent blue. It now scopes the accent to
neutral instead, as [`badge.md`](badge.md) describes.

## Code example

```java
var card = new Div(icon, heading, lines);
card.addClassNames("line-card", "travel-card");
icon.addClassName("travel-card-icon");
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

[`expense-line-card.md`](expense-line-card.md) — the base ·
[`badge.md`](badge.md) — the "Overridden" badge ·
[`editor-dialog.md`](editor-dialog.md) ·
ADR-0024 (quantity override), ADR-0020, issue #132

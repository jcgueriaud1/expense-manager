# Travel card

**Category:** composite (CSS, extends the expense line card)
**Status:** settled, pending per-view revision
**Source:** `styles.css` — `.travel-*`; `ReportDetailView`, `TravelEditorDialog`

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
| **removed** | `.travel-line-removed` — dashed border, transparent background, label at secondary colour and regular weight. It is a placeholder for something **not** on the report, so it must not read as a line that counts; it still carries its reason and the way back, so it stays legible rather than merely faded (issue #132) |
| error | n/a — surfaces in the dialog's [`error-summary.md`](error-summary.md) |

## Code example

```java
var card = new Div(icon, heading, lines);
card.addClassNames("line-card", "travel-card");
icon.addClassName("travel-card-icon");
```

## Cross-references

[`expense-line-card.md`](expense-line-card.md) — the base ·
[`badge.md`](badge.md) — the "Overridden" badge ·
[`editor-dialog.md`](editor-dialog.md) ·
ADR-0024 (quantity override), ADR-0020, issue #132

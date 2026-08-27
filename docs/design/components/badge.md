# Badge

**Category:** themed Vaadin primitive
**Origin:** design
**Implementation:** conforms
**Code:** `ReportViewSupport.statusBadge`; `com.vaadin.flow.component.badge.Badge`
**Design:** node `116:4444` › `Badge` (the Draft pill)

## Overview

Use for a **status** on a report, line or row — a compact label beside a title. Built
through `ReportViewSupport.statusBadge(ReportStatus)` rather than by hand, so the same
status always reads the same way.

**Not** for a count, and **not** for an action — a badge is not clickable. For a
link-like action use a `TERTIARY` [button](button.md).

Badges are one of the two places accent blue survives the theme's neutral-button rule.

## Anatomy

A single pill element carrying text. No parts.

## Tokens used

Stock Aura badge styling — this app overrides nothing. The variant selects the palette
hue (`--aura-green`, `--aura-red`, the accent) and Aura derives fill, border and text
colour from it.

## API

```java
Badge statusBadge(ReportStatus status)   // ReportViewSupport
String statusLabel(ReportStatus status)  // "Draft", "Submitted", …
```

Variant mapping:

| Status | Variants | Renders |
|---|---|---|
| `DRAFT` | `SMALL` | the default badge — accent-tinted, i.e. **blue** |
| `SUBMITTED` | `SMALL, FILLED` | solid neutral, reads as "handed off" |
| `APPROVED` | `SMALL, SUCCESS` | green |
| `REJECTED` | `SMALL, ERROR` | red |

Aura has no accent/primary badge variant — `contrast` is Lumo-only — which is why
`SUBMITTED` uses filled neutral rather than a blue tint.

> The `statusBadge` javadoc calls the `DRAFT` case "the plain default (neutral) badge".
> It renders **blue** under Aura, because the default badge picks up the global accent
> and the theme's neutral scoping applies to buttons only. Verified in the browser in
> both schemes. The javadoc means "no semantic variant", not "grey".

## States

| State | Behaviour |
|---|---|
| default | per the variant table |
| hover | n/a — not interactive |
| active | n/a |
| focus | n/a — not focusable |
| disabled | n/a |
| error | n/a — `ERROR` here is a *semantic* variant, not a validation state |

## Code example

```java
var row = new HorizontalLayout(title, ReportViewSupport.statusBadge(report.status()));
```

## Cross-references

[`button.md`](button.md) ·
[`status-callout.md`](status-callout.md) — the same status as a full-width block ·
[`../foundations/color.md`](../foundations/color.md) ·
ADR-0020 (never colour alone — the label text always renders)

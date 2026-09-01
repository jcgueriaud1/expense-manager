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
colour from it. `DRAFT` selects no hue but re-points the *accent* at `--aura-neutral`,
so the same derivation yields grey.

## API

```java
Badge statusBadge(ReportStatus status)   // ReportViewSupport
String statusLabel(ReportStatus status)  // "Draft", "Submitted", …
```

Variant mapping:

| Status | Variants | Renders |
|---|---|---|
| `DRAFT` | `SMALL` + class `aura-accent-neutral` | **grey** tint |
| `SUBMITTED` | `SMALL` | the default badge — accent-tinted, i.e. **blue** |
| `APPROVED` | `SMALL, SUCCESS` | green |
| `REJECTED` | `SMALL, ERROR` | red |

**All four are tints, never solids — no status takes `FILLED`.** The design draws the
pill one way for every status: a soft fill, a border in the same hue, and saturated
text. Only the hue changes.

`SUBMITTED` is therefore the *default* badge, whose accent tint is the blue the design
draws it in — it needs no variant, and Aura has no accent/primary one to give it.

`DRAFT` wants the one hue Aura's variants don't offer: grey. `contrast` is Lumo-only and
silently does nothing here, so instead of a variant the badge carries Aura's stock
`aura-accent-neutral` class, which scopes `--aura-accent-color` to `--aura-neutral` for
that element; the default badge styling then derives a grey fill, border and text from
it. Same mechanism the theme uses on buttons (F-067), per element because only this
status wants it.

> **Corrected 2026-09-01, verified against the design.** This table previously gave
> `DRAFT` the blue default badge and `SUBMITTED` a `FILLED` (solid) badge described as
> "solid neutral" — two errors. The design's Submitted pill is the blue *tint*, so the
> blue was on the wrong status; and `FILLED` renders solid **blue**, not neutral, since
> the theme's accent-to-neutral scoping is scoped to buttons and never reached the
> badge. Reference: frame `116:2499`, the Submitted pill on the first card.

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

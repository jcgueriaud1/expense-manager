# Status callout

**Category:** composite (CSS)
**Origin:** **unresolved**
**Implementation:** unaudited — origin not established
**Code:** `styles.css` — `.status-callout*`; `ReportDetailView`
**Design:** **unresolved.** No callout was found in frame `116:4444`, but absence in one frame is not proof

## Overview

A full-width block at the top of a report detail explaining the report's current state —
above all a rejection and its reason. Use it when the state needs an **explanation**; use
[`badge.md`](badge.md) when it only needs a label.

One modifier per state; never the base class alone.

## Anatomy

| Part | Class |
|---|---|
| Block | `.status-callout` + one of `--rejected` / `--approved` / `--submitted` |
| Heading | `.status-callout-heading` |
| Reason | `.status-callout-reason` — italic |

## Tokens used

| Property | Token |
|---|---|
| Radius | `--vaadin-radius-l` (15) |
| Padding | `--vaadin-padding-l` (16) |
| Internal gap | `--vaadin-gap-xs` (4) |
| Body text | `--aura-font-size-s`, `--vaadin-text-color` |
| Heading | `--aura-font-weight-semibold` |

Per modifier:

| Modifier | Background | Border | Heading colour |
|---|---|---|---|
| `--rejected` | `--aura-red` @ 8% | `--aura-red` @ 35% | `--aura-red-text` |
| `--approved` | `--aura-green` @ 8% | `--aura-green` @ 30% | `--aura-green-text` |
| `--submitted` | `--aura-accent-surface` | `--aura-accent-border-color` | inherited |

The percentages come from `color-mix(in srgb, …, transparent)` because **Aura has no
opacity ladder** — no `--aura-red-10pct`. This is the reference pattern for tinting any
palette hue.

`--submitted` uses the accent surface rather than a mixed tint, because Aura already
provides an accent-tinted surface and border pair — reach for those before mixing.

## API

CSS-only. Composed in `ReportDetailView`.

## States

| State | Behaviour |
|---|---|
| default | per modifier |
| hover | n/a — not interactive |
| active | n/a |
| focus | n/a |
| disabled | n/a |
| error | the `--rejected` modifier *is* the error presentation; it is a report state, not a validation state. Form validation goes to [`error-summary.md`](error-summary.md) |

## Code example

```java
var callout = new Div(heading, reason);
callout.addClassNames("status-callout", "status-callout--rejected");
heading.addClassName("status-callout-heading");
reason.addClassName("status-callout-reason");
```

## Cross-references

[`badge.md`](badge.md) — the same state, label only ·
[`status-history.md`](status-history.md) — the full trail ·
[`error-summary.md`](error-summary.md) — validation, not report state ·
[`../foundations/color.md`](../foundations/color.md) · ADR-0020

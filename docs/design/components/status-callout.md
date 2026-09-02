# Status callout

**Category:** composite (CSS)
**Origin:** **unresolved**
**Implementation:** unaudited — origin not established
**Code:** `styles.css` — `.status-callout*`; `ReportDetailView`
**Design:** **unresolved.** Frame `116:4444` draws no callout — but it is a `DRAFT`
report, which is the one status the app hides the callout on too, so the frame agrees with
the code and settles nothing. See [Origin](#why-the-origin-is-still-unresolved)

## Why the origin is still unresolved

The report-detail survey re-read frame `116:4444` in full and found no callout node. That
is **weaker evidence than it looks**: the frame's report is a `DRAFT`, and `DRAFT` is
exactly the status on which `updateStatusCallout` hides the callout. So the frame shows
what the app already shows, and says nothing about whether the design specifies a callout
for `REJECTED`, `APPROVED` or `SUBMITTED`.

Resolving this needs a frame drawing one of those three statuses. Until then the origin
stays `unresolved`, treated as `design` per the README, and the values below remain the
app's own — written from the stylesheet in #144 and never checked against a design.

Worth asking the designer for directly: a rejected report is the one state on this screen
that has something to *explain* rather than label, and it is the state the callout exists
for.

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

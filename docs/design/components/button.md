# Button

**Category:** themed Vaadin primitive
**Status:** settled
**Source:** `src/main/resources/META-INF/resources/aura-theme.css` (the accent rule); `com.vaadin.flow.component.button.Button`

## Overview

Use the stock Vaadin `Button` with a `ButtonVariant`. This app does not wrap it.

The design's three roles map onto variants:

| Role | Variant | Renders |
|---|---|---|
| Primary action — "New report", "Submit for approval", a dialog's "Save" | `PRIMARY` | filled, **black** in light / **white** in dark |
| Secondary action — "Cancel", "Save" beside a primary | *(none)* | white outline with a neutral label |
| Link-like action — "Edit", "Add receipt", "Override" | `TERTIARY` | accent **blue** text, no fill |
| Destructive | `+ ERROR` | red |

**Do not** add Aura's `aura-accent-neutral` class to make a primary button black. The
theme already scopes the accent for every non-tertiary button; adding the class puts the
same decision in two places. This supersedes the per-button approach recorded in F-067.

**Do not** use the `LUMO_*` constants, or the `tertiary-inline`, `contrast` or `icon`
variants — all Lumo-only and silently inert under Aura (F-013, F-017). Use plain
`TERTIARY`.

## Anatomy

A single element. The label is a slotted child; icons go in via `setIcon`. Aura's hover
overlay is a `::before` pseudo-element on the host.

## Tokens used

| Part | Token |
|---|---|
| Fill and border accent | `--aura-accent-color-light` / `-dark`, scoped to `--aura-neutral-*` for non-tertiary |
| Tertiary label | `--aura-accent-text-color` |
| Destructive | `--aura-red`, `--aura-red-text` |
| Radius | `--vaadin-radius-m` (9) |
| Height | derived from `--aura-base-size` — 34px |
| Shadow | `--aura-shadow-xs` |
| Label weight | `--aura-font-weight-medium` (500) |

No property is overridden beyond the accent pair. Aura's stock button already supplies
the design's border and shadow.

## API

Stock. The only project rule is variant choice, above.

## States

| State | Behaviour |
|---|---|
| default | as the table in Overview |
| hover | a `::before` overlay at `opacity: 0.03`, inside `@media (any-hover: hover)`, scoped `:not([disabled], [active])`. Invisible in the host's computed style — **all 912 properties are byte-identical to rest** |
| active | Aura's own; the hover overlay is suppressed via `:not([active])` |
| focus | Aura's focus ring, accent-coloured; not overridden |
| disabled | `opacity: 0.5` on the host. `background-color` and `color` are **unchanged**, so a contrast audit reading those two properties measures nothing. Hover does not apply while disabled |
| error | n/a — a button has no validation state; use `ButtonVariant.ERROR` for destructive intent |

**Measured contrast**, primary, on the running app:

| Scheme | Background | Label | Ratio |
|---|---|---|---|
| light | `#0a0b0d` | `#ffffff` | 19.7:1 |
| dark | `#feffff` | `#333333` | 12.6:1 |
| disabled (either) | composited at 50% | — | ~2.1:1 — Aura stock, exempt under WCAG 1.4.3 for inactive controls |

Neither hover nor disabled needs its own rule: both derive from the rendered result
rather than from the accent, so a 3% overlay cannot take 19.7:1 below about 18.9:1.

## Code example

```java
var submit = new Button("Submit for approval", e -> submit());
submit.addThemeVariants(ButtonVariant.PRIMARY);   // renders black

var cancel = new Button("Cancel", e -> close());  // no variant → white outline

var edit = new Button("Edit", e -> edit());
edit.addThemeVariants(ButtonVariant.TERTIARY);    // stays accent blue
```

## Cross-references

[`../foundations/color.md`](../foundations/color.md) ·
[`badge.md`](badge.md) — the other place blue survives ·
[`editor-dialog.md`](editor-dialog.md) — always-enabled Save ·
ADR-0025 · F-013, F-017, F-067

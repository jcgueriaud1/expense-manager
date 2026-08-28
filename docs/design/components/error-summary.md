# Error summary

**Category:** shared Java component
**Origin:** code
**Implementation:** conforms
**Code:** `com.vaadin.expensemanager.base.ui.ErrorSummary`; `styles.css` — `.error-summary*`
**Design:** — never designed. Behaviour adapted from the GOV.UK error-summary pattern (ADR-0020, F-045, F-050)

## Overview

The single top-of-form error summary for **every** form and editor in the app. Use it
wherever a form can fail validation.

**Never disable a submit button to prevent invalid input** (ADR-0020). The submit stays
enabled; pressing it validates and, on failure, this summary appears, takes focus, and
lists what is wrong. A disabled button tells a user nothing about why.

Do not hand-roll a `Div` + `role` + `showErrors` scaffold — that copy-paste is exactly
what this component replaced (F-045).

Behaviour is adapted from the GOV.UK error-summary pattern; the styling is this app's own
Aura.

## Anatomy

| Part | Class |
|---|---|
| Container | `.error-summary` — `role="group"`, `aria-labelledby` → heading, `tabindex="0"` |
| Heading | `.summary-heading` |
| List | `.error-summary ul` |
| A field-linked entry | `.error-summary-link` — a button that reads as an inline link |

## Tokens used

| Property | Token |
|---|---|
| Text | `--aura-red-text` |
| Border | `--aura-red` @ 40% via `color-mix` |
| Background | `--aura-red` @ 8% via `color-mix` |
| Radius | `--vaadin-radius-m` (9) |
| Padding | `--vaadin-padding-m` (12) |
| List indent | `--vaadin-padding-l` (16) |
| Heading | `--aura-font-size-m`, semibold, `--vaadin-gap-s` bottom margin |
| Focus outline | `2px solid var(--aura-red)`, `2px` offset |

`--aura-red-text` is declared once on `.error-summary` and inherited. The heading is an
`H3`, and Aura re-declares `color` on `h1`–`h6` at element level, which beats an inherited
value regardless of specificity — so `.summary-heading` carries `color: inherit`
explicitly. Without it the heading renders `--vaadin-text-color` inside a red callout,
silently. `.error-summary-link` declares the token directly for the same reason (`a` is
Aura's other element-level colour reset). F-072.

## API

```java
void showValidationErrors(BinderValidationStatus<?> status)  // preferred — field-linked
```

Plus two simpler entry points for messages with no binder behind them. Same behaviour in
all three: the summary shows, and focus moves to it.

## States

| State | Behaviour |
|---|---|
| default | not rendered — the summary is absent until a `show*` call |
| hover | on entries only: `.error-summary-link::part(label)` is underlined at rest, so the affordance does not depend on hover |
| active | activating an entry calls `focus()` on the offending field |
| **focus** | the container takes focus on every `show*` call, so a screen reader announces the error count and the summary scrolls into view. `tabindex="0"`, not `-1`, deliberately — see the constructor comment |
| disabled | n/a |
| **error** | this component *is* the error state |

The focus behaviour is the load-bearing part: without it a validation failure can happen
silently off-screen. Vaadin wires the reverse link (each invalid field points at its own
message via `aria-describedby`), so summary → field → message is navigable by keyboard
and AT.

## Code example

```java
var summary = new ErrorSummary();
form.addComponentAsFirst(summary);

save.addClickListener(e -> {          // never disabled
    var status = binder.validate();
    if (status.hasErrors()) { summary.showValidationErrors(status); return; }
    persist();
});
```

## Cross-references

[`editor-dialog.md`](editor-dialog.md) — already wires this in ·
[`status-callout.md`](status-callout.md) — report state, not validation ·
ADR-0020, ADR-0015 (Binder), F-045, F-050

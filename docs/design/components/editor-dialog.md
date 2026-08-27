# Editor dialog

**Category:** shared Java component
**Origin:** code
**Implementation:** conforms
**Code:** `com.vaadin.expensemanager.base.ui.EditorDialog`; `styles.css` — `vaadin-dialog.receipt-preview-dialog`
**Design:** — never designed. Exists to replace a copy-pasted scaffold (F-045)

## Overview

A modal editor with an **always-enabled Save** and a top-of-form error summary, shared by
every editor in the app. Use it for any "edit one thing in a dialog" flow rather than
assembling `Dialog` + summary + `writeBeanIfValid` by hand (F-045).

Build the form with the plain Vaadin API, hand it plus its binder and bean to the dialog,
set the persist action.

## Anatomy

| Part | Source |
|---|---|
| Overlay | Vaadin `Dialog`, reachable as `::part(overlay)` on the host |
| Header title | `setHeaderTitle(title)` |
| Error summary | [`error-summary.md`](error-summary.md), added by the dialog |
| Form | supplied by the caller |
| Save (primary) / Cancel (secondary) | [`button.md`](button.md) |

## Tokens used

Stock Aura overlay styling — nothing overridden. `--aura-shadow-m` via
`--aura-overlay-shadow`, `--aura-overlay-surface-opacity` 0.85.

The one project rule is the **receipt preview** overlay, which is a sizing decision rather
than a colour one:

```css
vaadin-dialog.receipt-preview-dialog::part(overlay) { max-width: min(92vw, 52rem); }
@media (max-width: 450px) { /* full-screen sheet, radius 0 */ }
```

A comfortable floating overlay on a laptop, a full-screen sheet on a phone so an enlarged
receipt is readable edge-to-edge (ADR-0021, ADR-0020). Vaadin 25 renders the overlay
inside the host's shadow root and re-exposes it with `exportparts`, which is why
`::part(overlay)` works directly on the host.

## API

```java
new EditorDialog<>(String title, Component form, Binder<T> binder, T model)
EditorDialog<T> onSave(Runnable action)
```

## States

| State | Behaviour |
|---|---|
| default | closed until `open()` |
| hover / active / focus | stock Aura on the buttons and fields inside |
| disabled | **n/a by design — Save is never disabled.** Validity is communicated by the summary, not by a dead button |
| **error** | three distinct paths, and the distinction matters: a **binder** failure lists messages in the summary and the dialog stays open; a `DomainRuleException` (a user-actionable service guard) shows its message in the same summary and the dialog stays open; **any other exception** is technical and is left to propagate to `UiErrorHandler`, which logs it and shows the generic error dialog rather than leaking a stack trace into the summary (issue #86) |

That third path is the easy one to get wrong: catching everything into the summary turns
an internal fault into what looks like user error.

## Code example

```java
var dialog = new EditorDialog<>("Edit VAT rate", form, binder, model);
dialog.onSave(() -> { service.updateVatRate(id, model.getValue()); refresh(); });
dialog.open();
```

## Cross-references

[`error-summary.md`](error-summary.md) ·
[`button.md`](button.md) ·
ADR-0020, ADR-0021, F-045, F-050, issue #86

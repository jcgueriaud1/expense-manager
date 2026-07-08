# ADR-0015 — Binder for form validation; Signals for dynamic state

**Status:** Accepted

## Context
Vaadin 25 promotes Signals (reactive state) and, separately, `Binder`
(form-to-bean binding + validation). Vaadin 25.2's own examples pair the two:
Binder handles validation and bean read/write, Signals drive reactive field
state. Jakarta Bean Validation integrates automatically through Binder.

## Decision
- **Binder is the primary mechanism for complex forms with validation** — the
  expense line editor, trip/allowance inputs, and user management. Use Binder's
  automatic **Jakarta Bean Validation** integration on the DTOs/records
  (ADR-0003), plus domain guards for invariants (ADR-0006). Prefer
  `readBean`/`writeBean` for explicit write control.
- **Signals where relevant** for dynamic/reactive UI state: selection-driven
  form population (grid → editor), reactive enable/disable and visibility,
  cross-field dynamic validation status (`binder.validationStatusSignal()`),
  and live totals. Signals are additive — not a replacement for Binder.

## Consequences
- Validation stays on the well-documented, automatic Binder path.
- Signals usage is where we most exercise a newer Vaadin API — friction or
  gaps there are prime Vaadin/Docs findings.
- Reactive submit-button enablement and status display follow the documented
  `bindEnabled` / `bindText` + `validationStatusSignal()` pattern.

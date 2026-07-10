# ADR-0020 — The application is accessible and mobile-friendly

**Status:** Accepted

## Context
Vaadin employees record expenses on the devices they have to hand — a laptop at
a desk, but also a phone while travelling, right after paying for a taxi or a
meal. Some users rely on a keyboard and a screen reader. Accessibility and a
usable small-screen layout are therefore product requirements, not later polish;
left unstated they get designed out one view at a time and are expensive to
retrofit. This decision fixes them as a baseline every view inherits, alongside
the base UI shell (ADR-0017) and the Binder/Signals form conventions (ADR-0015).

## Decision
- **Accessibility is a baseline requirement.** Every view targets **WCAG 2.1 AA**
  as the working bar. Concretely and non-exhaustively:
  - Every interactive control is **keyboard-reachable and operable** (no
    mouse-only affordances); focus order follows reading order and focus is never
    trapped or lost after navigation/dialog close.
  - Controls carry **accessible names/labels**; icons that convey meaning are not
    label-only. State changes that matter (validation errors, save/submit
    results, load/empty/error states from ADR-0017) are **announced** to assistive
    tech, not signalled by colour or layout alone.
  - **Colour is never the sole carrier of meaning**, and text/UI contrast meets AA.
  - **No disabled-button gating on forms.** The primary/submit action stays
    enabled; an invalid submit renders a **validation error summary at the top of
    the form**, each entry linking to/focusing its field (the accessible
    error-summary pattern). This **overrides** the ADR-0015 note that submit
    enablement should follow the `bindEnabled` + `validationStatusSignal()`
    pattern — a disabled button gives no reason and is invisible to screen-reader
    users. Signals still drive live derived display (e.g. net/VAT/gross totals).
- **The application is mobile-friendly.** Views are **responsive** and usable on a
  phone-sized viewport (target ~360 px wide and up): layouts reflow rather than
  requiring horizontal scroll, tables/grids degrade gracefully (wide content
  scrolls within its own container, or switches to a card/stacked presentation),
  and touch targets are adequately sized. The Phase 2 report-detail editor
  (variant D — receipt cards + side panel) reflows the side panel below the cards
  on narrow screens rather than crowding them side by side.
- **Prefer the framework's accessible components over hand-rolled markup.** Vaadin
  components ship accessible behaviour; custom compositions must preserve it
  (roles, labels, keyboard handling) rather than reintroduce gaps.

## Consequences
- Every feature issue inherits these as acceptance criteria: keyboard + screen-
  reader operability, AA contrast, always-enabled submit with an error summary,
  and a working small-screen layout. They are verifiable in the ADR-0012 layer-3
  view tests and in manual golden-path checks.
- The form-validation error-summary rule is now the app-wide convention; the
  Phase 2 issues (#22–#25) already follow it.
- Accessibility/responsive regressions are treatable as findings
  (`docs/findings.md`), consistent with findings being a first-class deliverable.

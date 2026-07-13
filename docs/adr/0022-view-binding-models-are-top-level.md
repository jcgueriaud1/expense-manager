# ADR-0022 — View binding models are top-level classes, not inner classes

**Status:** Accepted

## Context
Vaadin views bind their fields to a plain Java bean via `Binder` (ADR-0015). It
is tempting to declare that bean as a `private static` inner class of the view —
it is "only used here". The report detail view (`ReportDetailView`, #24) started
that way with a `ReportFormModel` inner class, and it did not scale:

- The model grew from two fields to the whole editable report (date, additional
  information, **and** the line collection), and the inner class bloated an
  already large view file, hurting readability — one of the review points that
  triggered this ADR.
- An inner binding model cannot be reused by the sub-components that make up the
  view. Once the line editor became its own `CustomField<List<ReportLineModel>>`
  and receipt-card component, the per-line edit model (`ReportLineModel`) had to
  be shared across the view, the field, and the cards — impossible while it was
  private to the view.
- Inner models are awkward to unit-test in isolation and invite the anti-pattern
  of hand-rolled validation in the view instead of `Binder` validators.

## Decision
**A view's `Binder` model — and any per-item edit model it composes — is a
top-level class in the feature's `ui` package, never a `private` inner class of
the view.**

- The binding model is a real, named object (`ReportFormModel`,
  `ReportLineModel`) with getters/setters, living in its own file.
- Validation of those models stays on the `Binder` (field validators / Jakarta
  Bean Validation, ADR-0015). The view does **not** hand-build validation
  message lists; it renders the `Binder`'s `getValidationErrors()` into the
  error summary (ADR-0020). A bound collection is validated by a `Binder`
  `Validator` on its field just like any scalar field.
- These UI models are distinct from the service DTOs (ADR-0003): DTOs cross the
  service boundary and are immutable; binding models are mutable view state.

## Consequences
- View classes stay focused on layout and orchestration; the model's shape is
  discoverable at a glance from its own file.
- Binding models are reusable across a view and its sub-components, and unit-
  testable without instantiating the view.
- Slightly more files. Accepted — the alternative (one growing view file) is what
  this ADR exists to prevent.
- Trivial, genuinely view-private holders are not worth a file; the rule applies
  to `Binder` models, which are neither trivial nor truly private once a view is
  decomposed into components.

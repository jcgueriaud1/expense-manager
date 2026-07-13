# ADR-0022 — Binder form-backing models are top-level classes, not view inner classes

**Status:** Accepted

## Context
A Vaadin `Binder` binds fields to a plain mutable bean with getters/setters
(ADR-0015). The Phase 2.2 report detail view carried that bean as a private
`static` inner class (`ReportDetailView.FormModel`). Phase 2.3 (issue #24) adds a
**second** form — the modal line editor — with its own backing bean, which made
the pattern's costs concrete:

- A view that also declares its form model mixes two concerns (layout/wiring and
  the form's data shape) in one file, and the file grows with every field.
- An inner model can't be exercised on its own, and can't be reused by another
  view or a test without dragging the enclosing view along.
- With two editors in one feature, two nested models bury the data shapes inside
  unrelated UI code.

The question: where do Binder form-backing models live?

## Decision
**Form-backing (Binder) models are their own top-level classes, never view inner
classes** — a repo-wide rule for every view.

- Each model is a package-private top-level class in the same `ui` package as the
  view(s) that bind it (e.g. `ReportFormModel`, `ExpenseLineFormModel`).
- Views keep only layout, wiring, and the `Binder`/validation setup; the mutable
  data shape lives in the model class.
- This applies to Binder backing beans specifically. Small immutable parameter
  objects and DTOs already follow ADR-0003; this ADR is about the mutable beans
  Binder mutates.

## Consequences
- `ReportDetailView.FormModel` is extracted to `ReportFormModel`; the line editor
  gets `ExpenseLineFormModel`. Both are top-level in `report.ui`.
- Slightly more files, each small and single-purpose; views read as
  layout/wiring, and a model can be unit-tested or reused independently.
- Consistency: reviewers apply one rule to every form rather than judging each
  nested model case by case.

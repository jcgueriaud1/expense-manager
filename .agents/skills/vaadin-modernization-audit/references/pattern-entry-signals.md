# Pattern entry (reference example): Signals — reactive UI state

This is the reference example of a **pattern entry** in the capability catalog — the shape every hand-curated pattern entry should take. It calibrates you on the expected reasoning depth, and is the live entry for Signals. The shapes below are drawn from Vaadin's Signal API use-case collection (`github.com/vaadin/use-cases`, `signals/` — ~29 real scenarios).

---

**Learn**: https://vaadin.com/docs/latest/flow/ui-state/building-ui · https://vaadin.com/docs/latest/flow/ui-state/effects-computed

**Capability**: Declare UI state as signals; bound components update automatically, no listeners. Core API:

- **Signals**: `ValueSignal<T>` (writable, local), `ListSignal<T>` / `SharedListSignal<T>` (collections; `Shared*` is multi-user), `.set()` / `.peek()` (read without subscribing) / `.asReadonly()`.
- **Derived & effects**: `signal.map(…)` for computed signals, `Signal.not(…)` and friends for logic, `Signal.effect(owner, () -> …)` for lifecycle-aware side effects (active only while `owner` is attached).
- **Component bindings** (25.0+): `bindText`, `bindVisible`, `bindEnabled`, `bindValue`, `bindThemeVariant`, plus component suppliers (`new Paragraph(() -> …)`).
- **Binder bridge**: Binder exposes `validationStatusSignal()` and per-field `valueSignal()` — signals and Binder compose, they don't compete.
- **Scoping**: a signal in a Spring `@Component` is application-scoped (shared across all users); add `@VaadinSessionScope` for per-session state (current user, preferences, i18n locale).

**Availability gate**: base API 24.8+ (helper-level, behind a feature flag on 24.8/24.9 — cap confidence at medium and note the maturity), component/element bindings 25.0+. Some bindings are still **not in the framework** and need helper methods in current versions — list binding for Grid/ComboBox, browser title, invalid state, component size, Tabs selected-index sync (the use-case repo collects these in a `MissingAPI` class). If a finding depends on one of these, say it currently needs a helper (or awaits the official API) and temper confidence.

**Supersedes — flag these shapes**:

1. **Listener-chain derived state**: a `ValueChangeListener` (or several) whose body only recomputes and sets other components' state (`setText`, `setVisible`, `setEnabled` on siblings). Signal + binding replaces the listeners and the risk of a forgotten update path.
2. **State-sync helper methods**: an `updateView()` / `refreshLabels()`-style method called from multiple event handlers to keep the UI consistent with fields of the view class. The fields become signals; the method dissolves into bindings.
3. **Manual push for shared/background state**: `UI.access()` blocks (or helpers wrapping it) whose purpose is propagating a state change into the UI — background progress, cross-view shared state, broadcaster/listener registries. The reactive replacement is a signal held in an application-scoped (`@Component`) or session-scoped (`@VaadinSessionScope`) bean, or a `SharedListSignal` for collaborative collections (shared chat, task lists — `insertLast`/`clear` instead of broadcast-then-refresh). ⚠ Threading risk: confidence can be high if the shape is clear, but the session prompt MUST start with a human checkpoint confirming the threading assumptions.
4. **Scattered visibility/enabled toggling**: the same `setVisible(condition)` / `setEnabled(condition)` logic duplicated across event handlers → one binding (`bindVisible` / `bindEnabled` / `bindThemeVariant`) on a computed signal.
5. **Polling for state refresh**: `UI.setPollInterval` used to re-read and re-render state that could instead be a bound signal. (Verify polling isn't serving another purpose — e.g. session keep-alive — before flagging.)
6. **Async loading state machine**: a boolean `loading`/`error` flag (or several) set across async callbacks, with `spinner.setVisible` / `errorLabel.setVisible` / `content.setVisible` toggled by hand in each branch → one `ValueSignal<LoadingState>` enum with a `bindVisible(() -> state == …)` per section. Per-item async (a spinner per row) is the same shape with one signal per item.
7. **Breakpoint/resize-driven layout**: a resize listener (or hand-written ResizeObserver `executeJs`) that recomputes layout and toggles components by width → a size signal with computed breakpoint signals (`map(size -> size.width() < SMALL)`) bound with `bindVisible`. (Component-size binding currently needs a helper — see the gate.)
8. **Binder status/value listener mirroring into other components**: a `StatusChangeListener` / `ValueChangeListener` on a Binder whose body only enables the submit button or updates sibling components from validity/field state → `submitButton.bindEnabled(binder.validationStatusSignal()…)` or bind to the field's `valueSignal()`. This does NOT replace Binder — see below.

**Not superseded — never flag**:

- `Binder` field ↔ bean binding and validation. Binder stays; it now *exposes* signals (`validationStatusSignal()`, per-field `valueSignal()`) that you bind to. "Replace Binder with signals" is a false positive — only a listener that mirrors Binder state into *other* components is shape 8.
- One-shot event handling with no derived state (a click handler that saves and shows a notification).
- `UI.access` for one-off actions (showing a notification from a background thread) — only the *state-propagation* use is shape 3.
- Grid/ComboBox data providers — unless surrounding code manually calls `refreshAll` from listeners purely to sync derived state, which is shape 1. (List-to-signal binding for these still needs a helper — see the gate.)

**Finding scope**: one bounded unit (view or component class) per finding, even when the shape repeats across the codebase. On repetition: emit the 2–3 clearest instances, recommend a pilot, note the rest in the report summary. If the exceptions file records an adopted signals pilot, reference it as the established pattern.

**Verification requirement**: behavioral change. Check test coverage of the affected unit; if absent, the session prompt's first step is a browserless-test characterization test of current behavior.

**Confidence guidance**: high — pure derived-state recomputation (shapes 1, 2, 4, 6 with no side effects), or a textbook broadcaster (shape 3 with a clear listener-registry + scheduled-push structure). Medium — listeners mixing state sync with side effects (the refactor must split them first; say so in the prompt), installed version is 24.8/24.9, or the finding depends on a binding that still needs a helper (shape 7, and list/title/invalid bindings). Low — genuinely ambiguous intent. Shape 3 always carries Threading risk regardless of confidence: the session prompt opens with the human checkpoint.

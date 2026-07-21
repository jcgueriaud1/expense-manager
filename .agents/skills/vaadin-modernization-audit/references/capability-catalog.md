# Capability catalog

Two halves, sourced differently. **Components** are authoritative from `get_components_by_version <installed>` — that tool, not this file, decides what exists in the installed version and when it arrived (diff two versions for a delta). **Patterns** are hand-curated here, because no tool enumerates them.

Docs link convention: component pages live at `https://vaadin.com/docs/latest/components/<slug>`; the full index is `https://vaadin.com/docs/latest/components`. When auditing an older version, swap `latest` for the version (e.g. `/docs/v24/`). Prefer `search_vaadin_docs` for the exact Learn link.

## Component hunting list (heuristic, not authority)

The custom→official mappings most worth checking in older codebases. This list tells you *what to grep for*; `get_components_by_version` / `get_component_java_api` confirm the official side exists in the installed version before it becomes a finding. Entries tagged **Preview** are behind a feature flag and may change or be removed in any release — see the Preview rule in `SKILL.md` before flagging one.

- **Card** — styled content container with title/header/media slots — https://vaadin.com/docs/latest/components/card
- **Master-Detail Layout** — responsive split master/detail UI, overlay below breakpoint, router integration — https://vaadin.com/docs/latest/components/master-detail-layout
- **Popover** — rich tooltips/popups anchored to a component — https://vaadin.com/docs/latest/components/popover
- **Dashboard** — static or user-configurable dashboard layouts with widgets — https://vaadin.com/docs/latest/components/dashboard
- **Markdown** — render markdown (incl. streaming AI content) — https://vaadin.com/docs/latest/components/markdown
- **Side Navigation (SideNav)** — app navigation menu with router integration — https://vaadin.com/docs/latest/components/side-nav
- **Multi-Select Combo Box** — combo box with multiple selection — https://vaadin.com/docs/latest/components/multi-select-combo-box
- **Slider** — numeric input via a draggable thumb along a track; two-thumb range + decimal variants (`IntegerSlider` / `DecimalSlider` / `IntegerRangeSlider` / `DecimalRangeSlider`) — https://vaadin.com/docs/latest/components/slider
- **Breadcrumbs** — the user's location path within the app hierarchy, with links back to higher levels — **Preview** (feature flag `com.vaadin.experimental.breadcrumbsComponent`) — https://vaadin.com/docs/latest/components/breadcrumbs
- **Tabs / TabSheet** — TabSheet variant bundling tabs+content — https://vaadin.com/docs/latest/components/tabs
- **Virtual List** — lightweight scrolling list with renderer — https://vaadin.com/docs/latest/components/virtual-list
- **Spreadsheet / Charts / Gantt (Pro)** — note license tier in the finding — https://vaadin.com/docs/latest/components/charts

Also common hand-rolled: badges (Lumo/Aura badge theming), avatars (Avatar/AvatarGroup), confirm dialogs (ConfirmDialog), menu bars, message list/input (chat UIs), upload, login form.

## Pattern entries

No tool enumerates these — this is the curated source. Confirm each pattern's availability/API in the installed version via `search_vaadin_docs` before it becomes a finding.

- **Signals (reactive UI state)** — 24.8+ helpers, 25.0+ element/component bindings — see `pattern-entry-signals.md` (the reference example). Docs: https://vaadin.com/docs/latest/flow/ui-state/building-ui · https://vaadin.com/docs/latest/flow/ui-state/effects-computed
- **Browser APIs (typed server-side wrappers, 25.x)** — Vaadin 25 wraps web-platform capabilities as server-side Java, replacing hand-written `executeJs`/`Page.executeJs` bridges: `Clipboard` and `WebShare` (25.2+), plus geolocation, `History`, fullscreen, screen orientation, and wake lock. Docs: https://vaadin.com/docs/latest/flow/browser-apis — confirm the specific API's `since` via `search_vaadin_docs` before flagging.
  - **Supersedes — flag these**: an `executeJs`/`getElement().executeJs`/`Page.executeJs` call whose script body only invokes one of these web-platform APIs — `navigator.clipboard.*`, `navigator.share`, `navigator.geolocation`, `history.pushState`/`window.history`, `requestFullscreen`, `screen.orientation`, `navigator.wakeLock`. The typed API replaces the JS string, its `$0`/`$1` escaping, and the return-value plumbing.
  - **Not superseded — never flag**: `executeJs` doing genuinely custom DOM work, third-party-library calls, or anything without an official wrapper. `executeJs` is not itself deprecated — only these specific bridges are.
  - ⚠ **Gesture/threading caveat**: clipboard-write and web-share must run inside a user gesture (bound to a click). An `executeJs` fired from a background thread or timer changes behavior if swapped naïvely — treat those as Behavioral risk and say so in the finding.
- **Theming renewal (25)** — the 25 theming system simplification and Aura; affects custom utility-CSS findings — treat custom utility classes as a theming *decision*, not a swap. Docs: https://vaadin.com/docs/latest/upgrading (theming section).
- **Grid + Spring Data lazy loading (24.x)** — simplified lazy-loading setup superseding hand-rolled `CallbackDataProvider` wiring against Spring repositories — https://vaadin.com/docs/latest/components/grid
- **FormLayout responsive columns (24.8+)** — new layout model superseding manual responsive form CSS — https://vaadin.com/docs/latest/components/form-layout

Maintaining this list: release notes and "what's new" posts describe new patterns in prose — structure that into a supersedes / not-superseded entry, following `pattern-entry-signals.md`.

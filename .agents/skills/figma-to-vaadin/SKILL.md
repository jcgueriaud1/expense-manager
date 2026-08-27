---
name: figma-to-vaadin
description: >
  Translate Figma designs into Vaadin Flow (Java) UI code using the Figma MCP and the Vaadin
  docs tools.
  Use this skill whenever the user wants to implement a Figma frame, screen, or component as
  Vaadin Java code — even if they just say "implement this design", "generate Vaadin code from
  Figma", "convert this frame to Java", or paste a Figma URL. Does NOT apply to React, HTML,
  web components, or other frontend frameworks — only Vaadin Flow (Java). Does NOT apply to
  design-only tasks such as editing Figma files or generating Figma components. Does NOT
  configure themes or visual design tokens — that is a separate skill.
compatibility: Requires the Figma MCP server; Vaadin docs tools come from the `vaadin-skills` plugin
---

# Figma to Vaadin Implementation

## Scope

This skill produces Vaadin Flow (Java) code that reproduces the **layout and component
structure** of a Figma design. It does not configure global theme tokens, brand colors, or
typography — that belongs to a separate theme configuration skill.

The main failure mode this skill guards against is jumping straight to code from a guess.
Gather enough context — the design, its annotations, and the real Vaadin API — before writing
anything.

## Project overrides

This is a **local copy**, adapted to this repo. Upstream is silent on all of the following;
here they are hard rules. They override anything else in this skill, and they sit on top of
[`docs/theming-layouts.md`](../../../docs/theming-layouts.md) and
[`CLAUDE.md`](../../../CLAUDE.md), which are the binding authority.

- **No `LumoUtility`, no Tailwind.** `layout-approach` is `vaadin-css`, permanently. Vaadin
  layout Java APIs for structure; scoped, role-named CSS classes for the rest.
- **No `LUMO_*` theme variants.** This app runs Aura, where the `LUMO_*` constants are the
  legacy naming. Use the theme-agnostic ones — `ButtonVariant.PRIMARY`, `TERTIARY`, `ERROR`,
  `SUCCESS`, `WARNING`, `SMALL`, `LARGE`. The `tertiary-inline`, `contrast` and `icon`
  variants are Lumo-only and do nothing under Aura; use plain `TERTIARY` (findings F-013,
  F-017).
- **No `--lumo-*` CSS custom properties.** They are undefined under Aura, so they render
  nothing — silently, with no error. Use `--aura-*` and `--vaadin-*` tokens; look the exact
  names up with `get_theme_css_properties theme=aura` rather than guessing.
- **Aura has no `Npct` opacity scale.** There is no `--aura-red-10pct` equivalent to Lumo's
  `--lumo-error-color-10pct`. Derive a tint with
  `color-mix(in srgb, var(--aura-red) 15%, transparent)`.
- **Custom styling lands in one place:** `src/main/resources/META-INF/resources/styles.css`,
  as scoped, kebab-case, role-named classes (`order-summary-card`, not `blue-background`).
  Not in `getStyle().set(...)`, and not in a new stylesheet per view.
- **Vaadin docs tools come from the `vaadin-skills` plugin**, not a `Vaadin` MCP server entry
  — this repo deliberately has none. `search_vaadin_docs`, `get_full_document`,
  `get_component_java_api` and `get_theme_css_properties` are available under the plugin's
  tool prefix; that a server named `Vaadin` is missing is not a setup failure.

## Workflow

Create TODOs from these steps and follow them in order.

### 1. Fetch design context

`get_design_context` on the given node is the primary source — it has the most detailed
component information; check `data-name` for component type, and note theme/variant hints and
text styles. If the response is truncated (very large or deeply nested frames), fall back to
`get_metadata` for the layer hierarchy, then call `get_design_context` on the specific child
nodes you need.

### 2. Check component annotations

For each component instance, apply these in order: recommended Vaadin component, theme
variants, accessibility requirements, implementation notes, documentation links. Annotations
override guesses from layer names.

If a Figma component still doesn't map clearly to one Vaadin component after checking
annotations, ask: "Should this be a [ComponentA] or [ComponentB]? The Figma shows
[description]." Don't guess.

### 3. Research each component (mandatory)

Never rely on memorized Vaadin knowledge — API surfaces and feature-flag status change between
versions.

- `search_vaadin_docs` to find candidates, record `file_path`
- `get_full_document` for **every** component before implementing — search results are
  previews, not enough on their own
- `get_component_java_api` for the exact Java method signatures — use this whenever you
  need to know which methods a component exposes (slot setters, theme variants, sizing)

If a compile error suggests a method doesn't exist, re-read the component's Java API docs
before guessing at a fix. Don't search local `.m2` jars for source, and don't run anything to
"just try it" — the docs are the authoritative source.

### 4. Resolve project preferences, once

This project has already resolved all four preferences in [`.agent-context`](../../../.agent-context)
at the repo root. **Read that file and use its values — do not ask the user, and do not
re-derive them by auto-detection.** Only ask if a key you need is genuinely absent from it.

```
layout-approach: vaadin-css
architecture: composed-components
sample-data: use-existing-data
verification: verify
```

| Preference | Values | Auto-detect | Otherwise |
|---|---|---|---|
| `layout-approach` | `vaadin-css` **only** | Fixed by project standard — `lumo-utility` and `tailwind` are forbidden here. | Never ask; the value is pinned in `.agent-context`. |
| `architecture` | `single-view` / `composed-components` | No reliable signal | "Should I build this as one view class with private helper methods, or split it into reusable components (e.g. a separate detail/edit form that fires its own save/cancel events)?" |
| `sample-data` | `generate-sample` / `use-existing-data` | Check whether the project already has a repository, service, or entity matching the data shown in the design | "Should I generate small sample data for this view, or is there existing data/service in the project I should wire it to instead?" |
| `verification` | `skip` / `verify` / `verify-and-fix` | No reliable signal | "After implementing, should I skip testing, run visual verification against the Figma design, or run visual verification and automatically apply one round of fixes based on the findings?" |

**Read these two project documents before writing any layout code — they are the binding
authority on layout and styling in this repo, and they override anything in this skill:**

1. [`docs/theming-layouts.md`](../../../docs/theming-layouts.md) — the layout & spacing
   standard: which Vaadin layout Java API covers which need, the `--vaadin-gap-*` /
   `--vaadin-padding-*` token scale, when to fall back to a scoped CSS class, CSS class
   naming, `Scroller` instead of `overflow: auto`.
2. [`CLAUDE.md`](../../../CLAUDE.md) — the Aura-not-Lumo theming rules and the project's
   overall orientation.

Where this skill and those documents disagree, **those documents win**. This skill carries no
bundled layout references; the upstream `references/layouts-*.md` files were deliberately not
copied into this repo (see Provenance).

### 5. Implement

- Use Vaadin components, not generic HTML; prefer the component API over the element/style API
  (e.g. `textField.setReadOnly(true)`, not `.getElement().setAttribute("readonly", "")`)
- Apply theme variants via Java API (`addThemeVariants`)
- Use the layout patterns from `docs/theming-layouts.md` (Vaadin layout Java APIs first;
  scoped, role-named CSS classes with `--vaadin-*` / `--aura-*` tokens for the rest)
- Pick correct heading levels from text styles
- Add accessibility attributes where needed (e.g. `setAriaLabel` on icon-only buttons)

If `architecture: composed-components` — split the view into a container plus reusable
sub-components (e.g. a details/edit form as its own class). Sub-components fire custom
`ComponentEvent`s (e.g. `SaveEvent`, `CancelEvent`) that the container listens for and acts on,
rather than the container reaching into the sub-component's fields directly.

If `sample-data: generate-sample`:
- Define it in a `private` helper method (e.g. `createSampleOrders()`)
- 3–5 items max, or enough to match what the design visually shows (e.g. a scrolling grid) if
  that density is core to the layout
- Realistic values (`"Alice Johnson"`, not `"Item 1"`)
- Add `// Sample data — replace with real service call` comment
- Prefer `List.of(...)` for immutable collections

If `sample-data: use-existing-data`, wire the view to the existing repository/service/entity
instead of inventing new sample data.

### 5b. Update the component spec

If the project keeps per-component design specs, **invoke `figma-component-spec` for
every component this change created or altered**, before Step 6 — in the same change as
the code, because a spec written later is a spec written from memory. That skill owns the
template, the mandatory all-states rule and the staleness audit; do not restate them
here, and do not write the spec by hand instead.

**Done when** every component this change touched has an up-to-date spec.

### 6. Test

This skill's own job — writing code — is done by the end of Step 5. Don't run terminal
commands, open a browser, or take screenshots yourself; what happens next depends on the
`verification` preference resolved in Step 4:

- **`skip`** — stop here.
- **`verify`** — invoke the `figma-visual-verification` skill, passing it the Figma URL (or
  `fileKey`/`nodeId`) used for this view and the route it was implemented at. Present its
  prioritized findings to the user as-is; don't act on them yet.
- **`verify-and-fix`** — invoke `figma-visual-verification` the same way, then apply exactly
  **one** round of fixes addressing its findings, highest severity first. Tell the user what was
  changed and why. Don't loop back into a second verification pass automatically — if the user
  wants to confirm the fixes, that's a new verification run.

`.agent-context` pins `verification: verify` for this project — report only, never auto-fix.

## Universal component patterns

These apply regardless of the styling approach.

```java
// ✅ Component API over element/style API
textField.setReadOnly(true);
button.addThemeVariants(ButtonVariant.TERTIARY);   // not LUMO_TERTIARY — see Project overrides
iconButton.setAriaLabel("Close");
input.setLabel("Label");                      // HasLabel API, not a separate Span

// ✅ Sizing via component API
layout.setSizeFull();
layout.setWidth("600px");

// ❌ Never use the style API for things the component API handles
textField.getElement().setAttribute("readonly", "");
button.getElement().getStyle().set("background", "transparent");
layout.getStyle().set("width", "600px");
avatar.getStyle().set("--vaadin-avatar-size", "48px");
```

## Gotchas

`VerticalLayout` defaults:
- Padding ON — call `setPadding(false)` if not wanted
- Width 100% of parent
- `alignItems` START — children do not stretch horizontally; call `setAlignItems(STRETCH)` or `setWidthFull()` per child to fill the width
- `justifyContentMode` controls the vertical (main) axis

`HorizontalLayout` defaults:
- Padding OFF
- Width shrinks to content — call `setWidthFull()` if it should fill the parent
- `alignItems` STRETCH — children stretch vertically to fill the layout height (a `Button` next to a `TextField` will silently grow)
- `justifyContentMode` controls the horizontal (main) axis
- A layout child's minimum size defaults to its content size; this causes unexpected scrollbars in `Scroller` / `TabSheet`; fix with `component.setMinWidth("0")` or `setMinHeight("0")`

- For purely visual containers prefer `FlexLayout` — it avoids all of the above defaults
- `flex-shrink` is on by default — a fixed-size child shrinks when placed next to a `setWidthFull()` sibling; call `layout.setFlexShrink(component, 0)` to prevent it, or use `layout.setFlexGrow(fullSizeComponent, 1)` instead of `setWidthFull()` to avoid the conflict altogether
- `setWidthFull()` on a child in a content-hugging `HorizontalLayout` expands the layout rather than fitting it; use `setAlignItems(STRETCH)` instead
- A layout child's minimum size defaults to its content size; this causes unexpected scrollbars in `Scroller` / `TabSheet`; fix with `component.setMinWidth("0")` or `setMinHeight("0")`. The same default also applies one level up: a component like `MasterDetailLayout` or `Scroller` placed as the `expand()`ed child of a `VerticalLayout` (or a CSS Grid area) can resist shrinking below its content's natural height even with `setSizeFull()`. If a view overflows the page instead of scrolling internally, add `setMinHeight("0")` to that expanded child itself, not just to a `Scroller` nested further inside it
- `RadioButtonGroup` / `CheckboxGroup` default orientation is theme-dependent: horizontal in Lumo, **vertical in Aura**. If the Figma layer is named/laid out horizontally and the project uses Aura, add `addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL)` / `CheckboxGroupVariant.AURA_HORIZONTAL` — otherwise the group silently renders as a vertical stack
- Feature-flag status changes between versions — don't assume a component needs one from memory; check `search_vaadin_docs("feature flags")` then `get_full_document` on the result
- Never use CSS `margin` to space out a Vaadin layout component from its container — margin sits outside the component's measured box, which breaks `setSizeFull()`/`expand()` height math (a component can measure "correct" while still visually overflowing its parent). Add spacing instead via padding on a wrapping layout, or by targeting the component's own shadow-DOM part with `::part(...)` (e.g. `vaadin-master-detail-layout::part(detail) { padding: ...; }`)
- When writing custom CSS, use real theme CSS custom properties — look them up with the Vaadin MCP (`get_theme_css_properties`) rather than inventing a plausible-sounding variable name with a hardcoded `var(--name, fallback)` fallback. If the name doesn't actually exist, the fallback silently becomes the real value and never tracks the theme (e.g. `var(--vaadin-background-color-secondary, #f9fafb)` — that property doesn't exist; the real one is `--vaadin-background-container`)
- `VerticalLayout`/`HorizontalLayout`/`FlexLayout` already set `box-sizing: border-box` themselves, so padding on them is safe by default. Only plain elements — a custom CSS rule targeting a `Div`, another non-layout component, or a shadow-DOM `::part(...)` — need `box-sizing: border-box` added explicitly when the rule also sets `padding`; without it, padding adds to the element's declared width/height instead of being carved out of it, so a component sized with `setWidth()`/`setSizeFull()` ends up visually larger than intended

## Quick reference: Figma → Vaadin

| Figma | Vaadin |
|---|---|
| Vertical auto layout | `VerticalLayout` |
| Horizontal auto layout | `HorizontalLayout` |
| Free / absolute layout | `FlexLayout` |
| Form / labelled fields | `FormLayout` |
| Master-detail | `MasterDetailLayout` |
| Button | `Button` |
| Text Field | `TextField` |
| Grid / Table | `Grid` |
| Avatar | `Avatar` |
| Card | `Card` (v24.8+) |
| Badge / status label | `Badge` |
| Text layer | `com.vaadin.flow.component.html.Span` |
| Heading 3 | `com.vaadin.flow.component.html.H3` |
---

## Provenance

- **Upstream:** [https://github.com/juuso-vaadin/figma-to-vaadin-skill](https://github.com/juuso-vaadin/figma-to-vaadin-skill)
- **Source path:** `skills/figma-to-vaadin/SKILL.md`
- **Commit:** `3a9289c` (`3a9289c15df9e7a7659f0d92fee204ad1dc65c14`)
- **Copied:** 2026-08-26 — by hand, as a project-owned file. **Not** managed by
  `skills.sh` / `skills-lock.json`; that lock file is CLI-managed against
  `mattpocock/skills` with per-entry hashes, and this skill is locally modified.
- **Locally modified:** yes
  - The three `references/layouts-*.md` files were **not** copied.
    `layouts-lumo-utility.md` and `layouts-tailwind.md` describe approaches
    `docs/theming-layouts.md` forbids outright; `layouts-vaadin-css.md` is a near-duplicate
    of that document, and a near-duplicate is where divergence hides.
  - Step 4's layout-approach mapping now reads `docs/theming-layouts.md` and `CLAUDE.md` as
    the binding authority, and treats the four preferences as already resolved in
    `.agent-context`.
  - Added the **Project overrides** section (no `LumoUtility`, no `LUMO_*` variants, no
    `--lumo-*` properties, no Aura `Npct` scale, one `styles.css`, Vaadin docs tools from
    the `vaadin-skills` plugin).
  - `ButtonVariant.LUMO_TERTIARY` in the universal-patterns example replaced with
    `ButtonVariant.TERTIARY`; the `AvatarVariant.LUMO_LARGE` line dropped (no verifiable
    theme-agnostic equivalent in the 25.2 docs).
  - Step 6 now invokes `figma-visual-verification` (this repo's renamed copy of upstream's
    `vaadin-visual-verification`).
  - `compatibility:` no longer claims a Vaadin MCP server is required.
  - Added **Step 5b — Update the component spec**, delegating to this project's
    `figma-component-spec` skill so a view's change also updates
    `docs/design/components/` rather than leaving that layer to rot. Upstream has no
    component-spec concept at all: it ends at code plus verification, which is why the
    same "is this a card?" question can be answered differently by every view. Kept as a
    pointer rather than a copy of the rules, so the two cannot drift.
- **Not copied at all:** upstream's `figma-to-lumo-theme` — this app is Aura, and
  `CLAUDE.md` forbids `--lumo-*`.

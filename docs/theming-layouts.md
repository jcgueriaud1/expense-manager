# Layouts & spacing — Vaadin APIs + plain CSS

**Project standard. Always follow this.** This app does **not** use LumoUtility classes
or Tailwind. Use Vaadin layout component Java APIs for structure, and plain CSS with
`--vaadin-*` / `--aura-*` custom properties for what the APIs cannot express. This
complements the Aura-not-Lumo rules in [../CLAUDE.md](../CLAUDE.md).

The cardinal rule: **never reach for `getStyle().set(...)` for something a layout Java API
covers** (display/flex, gap, padding, alignment, distribution, flex-grow, size, wrap,
scroll). Those go through the API. Everything the API can't express goes into a scoped,
role-named CSS class in `src/main/resources/META-INF/resources/styles.css`, using tokens —
not hard-coded px.

## Java API first — decision table

| Layout need | Java API |
|---|---|
| Vertical stacking | `VerticalLayout` |
| Horizontal stacking | `HorizontalLayout` |
| Default theme spacing | `layout.setSpacing(true)` — uses `--vaadin-gap` |
| Custom spacing value | `layout.setSpacing("var(--vaadin-gap-l)")` — accepts any CSS value |
| No spacing | `layout.setSpacing(false)` |
| Default theme padding | `layout.setPadding(true)` — uses `--vaadin-padding` |
| Custom padding value | **No API** — `setPadding` is boolean-only (F-058); use a CSS class |
| No padding | `layout.setPadding(false)` |
| Cross-axis alignment | `layout.setAlignItems(...)` / `layout.setDefaultHorizontalComponentAlignment(...)` |
| Main-axis distribution | `layout.setJustifyContentMode(...)` |
| One child fills remaining space | `layout.expand(child)` |
| Per-child alignment override | `layout.setAlignSelf(Alignment.END, child)` |
| Width / height | `component.setWidth("...")`, `component.setHeight("...")` |
| Fill parent | `component.setSizeFull()` |
| Min/max constraints | `component.setMinWidth(...)`, `setMaxWidth(...)` |
| Flex direction (custom) | `flex.setFlexDirection(FlexLayout.FlexDirection.ROW)` |
| Flex wrap | `layout.setWrap(true)` on any `HorizontalLayout`/`VerticalLayout` (ThemableLayout); `flex.setFlexWrap(...)` on `FlexLayout` |
| Scrollable region | `new Scroller(content)` + `setScrollDirection(...)` |
| Responsive form columns | `FormLayout` + `setResponsiveSteps(...)` |
| Resizable split panels | `SplitLayout` |

### Available spacing tokens (`--vaadin-gap-*`, `--vaadin-padding-*`)

`xs`, `s`, `m`, `l`, `xl` — pick the size that matches the design value. There is **no**
unsuffixed `--vaadin-padding` / `--vaadin-gap` custom property to use in your own CSS or
`getStyle()`; those names only exist as the layout components' internal defaults behind
`setPadding(true)` / `setSpacing(true)` (see finding F-030). In custom CSS always use a
sized token, or a fallback: `var(--vaadin-gap-m, 0.75rem)`.

```java
// ✅ Spacing/padding with the layout Java API
HorizontalLayout header = new HorizontalLayout();
header.setSpacing("var(--vaadin-gap-l)");
header.setPadding(true);  // boolean only — there is no setPadding(String), F-058
header.setWidthFull();
header.setAlignItems(FlexComponent.Alignment.CENTER);
header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
header.expand(titleLabel);

// ✅ Per-child flex-grow
VerticalLayout sidebar = new VerticalLayout();
sidebar.expand(contentArea);

// ❌ Don't use the style API for things the layout API covers
layout.getStyle().set("display", "flex");
layout.getStyle().set("gap", "16px");      // → setSpacing("var(--vaadin-gap-m)")
layout.getStyle().set("padding", "16px");  // → setPadding(true), or a CSS class for a custom value
```

## Falling back to CSS

When the layout API can't express a need — decoration (border, radius, background, shadow),
positioning, truncation, 2-D grid, typography/colour on a non-component element — add a
scoped CSS class with `addClassName("descriptive-name")` and write the rule in `styles.css`.
Use `--vaadin-*` / `--aura-*` custom properties rather than hard-coded values.

| Need | CSS |
|---|---|
| Custom gap on a non-Vaadin-layout container | `gap: var(--vaadin-gap-m);` |
| 2-D grid (rows AND columns) | `display: grid; grid-template-columns: ...; gap: var(--vaadin-gap-m);` |
| Aspect ratio | `aspect-ratio: 16 / 9;` |
| Sticky / absolute positioning | `position: sticky; top: 0;` |
| Clip overflow (no scroll) | `overflow: hidden;` |
| Text truncation | `overflow: hidden; text-overflow: ellipsis; white-space: nowrap;` |
| Card decoration | `border: 1px solid var(--vaadin-border-color); border-radius: var(--vaadin-radius-l); background: var(--aura-surface-color);` |
| A dynamic, data-driven colour | set a CSS var on the element (`el.getStyle().set("--x", value)`) and consume it in the class |

**Box-sizing:** raw light-DOM elements (`Div`, `RouterLink`, `Span`) default to
`content-box`, so `width: 100%` + your own `padding`/`border` overflows the parent. A global
`box-sizing: border-box` reset in `styles.css` handles this for all light-DOM elements
(Vaadin components already use border-box; shadow DOM is untouched) — don't re-add padding
compensation by hand.

```java
Div grid = new Div();
grid.addClassName("dashboard-grid");
```
```css
.dashboard-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--vaadin-gap-m);
}
```

## Scroller, not `overflow: auto`

For any region that should scroll, use `Scroller` — not CSS `overflow: auto`. Reserve CSS
`overflow: hidden` for clipping (no scrollbar).

```java
Scroller scroller = new Scroller(content);
scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
scroller.setSizeFull();
```

## CSS class naming

- Kebab-case, role-based: `order-summary-card`, `toolbar-actions`, `status-callout`.
- BEM-ish modifiers for variants: `report-card`, `report-card--actionable`.
- **Not** visual: ❌ `blue-background`, `padding-top-16`.
- Scope loosely to the feature so classes don't collide across views.

## Colour & status

- Never colour alone: any state shown by colour also carries text/label (ADR-0020).
- Prefer the official themed component (e.g. `Badge` + `BadgeVariant`) over hand-styled
  spans (finding F-029). Fall back to palette tokens (`--aura-red`, `--aura-green`, …) and
  `color-mix(...)` only for surfaces the components don't provide.

## Known gap

Arbitrary spacing values that don't map to an `xs`/`s`/`m`/`l`/`xl` token must be hard-coded
for now. If this becomes a frequent need, the whole scale can be tuned globally via
`--aura-base-size` rather than sprinkling magic numbers.

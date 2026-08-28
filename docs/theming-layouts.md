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
| Custom padding value | **No Java API** — `setPadding` is boolean-only. Use `setPadding(false)` + `padding` in the scoped CSS class (F-065) |
| No padding | `layout.setPadding(false)` |
| Cross-axis alignment | `layout.setAlignItems(...)` / `layout.setDefaultHorizontalComponentAlignment(...)` |
| Main-axis distribution | `layout.setJustifyContentMode(...)` |
| One child fills remaining space | `layout.expand(child)` |
| Per-child alignment override | `layout.setAlignSelf(Alignment.END, child)` |
| Width / height | `component.setWidth("...")`, `component.setHeight("...")` |
| Fill parent | `component.setSizeFull()` |
| Min/max constraints | `component.setMinWidth(...)`, `setMaxWidth(...)` |
| Flex direction (custom) | `flex.setFlexDirection(FlexLayout.FlexDirection.ROW)` |
| Flex wrap | `flex.setFlexWrap(FlexLayout.FlexWrap.WRAP)` |
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
// ✅ Custom spacing with the layout Java API; padding has no String overload,
//    so it goes in the scoped CSS class instead (see the asymmetry note below)
HorizontalLayout header = new HorizontalLayout();
header.setSpacing("var(--vaadin-gap-l)");
header.setPadding(false);
header.addClassName("view-header");   // .view-header { padding: var(--vaadin-padding-m); }
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
layout.getStyle().set("padding", "16px");  // → a CSS class: padding: var(--vaadin-padding-m);
```

### The spacing/padding asymmetry (F-065)

`ThemableLayout` does **not** treat the two symmetrically, and it is easy to assume it does:

```java
// Spacing — four ways to set it
void setSpacing(boolean);
void setSpacing(String);            // ✅ any CSS value
void setSpacing(float, Unit);
String getSpacing();

// Padding — one way, and no getter for a value
void setPadding(boolean);           // ❌ there is no setPadding(String)
boolean isPadding();

// Margin — same as padding
void setMargin(boolean);
boolean isMargin();
```

So `setPadding(true)` gives you the theme's default padding and `setPadding(false)` gives you
none; **any other value is a CSS-class job**, exactly like the decoration cases below. The same
holds for margin — and margin on a layout is its own trap: it sits *outside* the component's
measured box, so it breaks `setSizeFull()` / `expand()` height math. A component can measure
"correct" and still visually overflow its parent. Space a layout from its container with padding
on a wrapper, or by targeting the component's own `::part(...)` — never with margin.

This document told you otherwise until 2026-08-26. `setPadding("var(--vaadin-padding-m)")`
appeared here in three places and has never compiled; it went unnoticed because production code
uses `setSpacing(String)` freely and simply never tried the padding form.

## Falling back to CSS

When the layout API can't express a need — decoration (border, radius, background, shadow),
a custom padding value, positioning, truncation, 2-D grid, typography/colour on a
non-component element — add a
scoped CSS class with `addClassName("descriptive-name")` and write the rule in `styles.css`.
Use `--vaadin-*` / `--aura-*` custom properties rather than hard-coded values.

| Need | CSS |
|---|---|
| Custom gap on a non-Vaadin-layout container | `gap: var(--vaadin-gap-m);` |
| Custom padding on any layout (no Java API, F-065) | `padding: var(--vaadin-padding-m);` |
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

## Inherited properties — headings and links don't get yours

Declaring an inherited property on a container does **not** reach every descendant. Aura
re-declares some properties on bare element selectors, and an inherited value loses to any
rule that matches the element itself — **at any specificity, zero included**. Aura's reset
is `:where(h1,h2,h3,h4,h5,h6)`, specificity 0,0,0, and it still beats `.app-header`'s
0,1,0, because the two are not competing on specificity: one matches the element, the
other only inherits to it. Nothing errors (F-072).

What Aura re-declares, derived from `aura.css` in `vaadin-aura-theme-25.2.5.jar`:

| Element | Properties it will not inherit from your container |
|---|---|
| `h1`–`h6` | `color`, `font-weight`, `font-size`, `line-height` |
| `a:any-link` | `color` |
| `b`, `strong`, `th` | `font-weight` |
| `code`, `pre`, `figcaption` | `font-size`, `font-weight`, `line-height` |

**The rule:** when a container declares one of these properties, every descendant in the
table needs its own declaration for it — `color: inherit` to take the container's value,
or the token directly. `Span`, `Div` and Vaadin components inherit correctly and need
nothing. Re-derive the table after a Vaadin upgrade rather than trusting this copy; a
hardcoded list goes stale in exactly the silent way the rule exists to prevent.

### The audit

Two checks. Neither needs Figma, and neither needs to know the right colour — both detect
a contradiction internal to the CSS and the DOM. Which property to test is not a judgement
call either: it is the intersection of *what the theme resets on this element* and *what
project CSS declares on an ancestor of it*, and both sets come from CSS.

**Static.** Parse `aura.css` with a real CSS parser (it ships minified, with `@supports` /
`@scope` / `@media` nesting) and emit an `(element, property)` pair only when the
selector's **subject** — the rightmost compound, pseudo-elements stripped — is that bare
element type. Without the subject rule, `…::part(label)`, `ol li::marker` and
`b,blockquote,pre code,strong` all produce false pairs. Then flag:

> an ancestor declares `P` · the element is in the table for `P` · no project rule matching
> the element declares `P`.

The high-precision signature is a class that overrides *some but not all* of what the
theme resets on its element: both F-072 instances set `font-size`/`font-weight` and not
`color`. Containment is the weak edge — `.app-*` names carry their block (`__`), the
flat-dash majority (`.status-callout-heading`) don't, and the sound alternative is a
dataflow through `addClassName`/`add()` in Java. So this check is precise where BEM holds
and silent elsewhere; treat it as a fast screen, not a proof.

**Runtime.** The rendered DOM settles containment exactly, including dynamic classes:

```js
computed(E)[P] !== computed(inheritanceParent(E))[P] && !projectCssDeclares(E.classList, P)
```

`inheritanceParent` is not `parentElement` — inheritance follows the flattened tree, so
slotted content (`.app-nav__item` is a `vaadin-button`) inherits through its `assignedSlot`
and naive parent-walking reports garbage at every shadow boundary. CDP
`CSS.getMatchedStylesForNode` is worth adding for the *report* rather than the detection:
"loses to `aura.css :where(h1,h2,h3,h4,h5,h6)`" is fixable, "greeting is rgb(0,0,0)" starts
a debugging session.

**Conformance is a separate, slower pass** and the only one with an oracle — and the oracle
is `docs/design/`, never Figma directly. The specs record deliberate divergences from the
design; `app-shell.md` holds one exactly here (the bar's text is pinned, not bound to
`aura-accent-contrast-color`, which flips to black above accent lightness 0.62), so a check
resolving Figma's own variable could assert black-on-coral and call it conformance.

**Do not rely on a contrast gate for this class of bug.** It can invert: black on the
header's `#f16c4e` measures 6.99:1 and the design's white 3.00:1, so axe prefers the defect.

## Known gap

Arbitrary spacing values that don't map to an `xs`/`s`/`m`/`l`/`xl` token must be hard-coded
for now. If this becomes a frequent need, the whole scale can be tuned globally via
`--aura-base-size` rather than sprinkling magic numbers.

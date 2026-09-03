# Vaadin gotchas

Behaviour this project has been bitten by and no config file confesses. Cached here
because looking it up costs a browser probe or a compile error; the one-command
lookups stay in the environment where they cannot go stale.

This file holds **mechanism** — how Vaadin behaves. It is not the design spec.

- Decided values, the token scale and component specs: [`design/`](design/)
- Layout and spacing rules: [`theming-layouts.md`](theming-layouts.md)
- Aura-not-Lumo naming rules: [`../CLAUDE.md`](../CLAUDE.md)

This file holds what none of those cover: behaviour that cost a browser probe or a
compile error to discover.

## Component state styling is invisible on the host element

Aura styles button `:hover` and `[disabled]` in two places that a computed-style check
on the host will not show:

- **hover** — a `::before` pseudo-element overlay at `opacity: 0.03`, inside
  `@media (any-hover: hover)`, scoped `:hover:not([disabled], [active])`.
- **disabled** — `opacity: 0.5` on the host, leaving `background-color` and `color`
  untouched.

So `getComputedStyle(button)` returns **byte-identical values** for hover and rest — all
912 properties — and identical `background-color`/`color` for enabled and disabled. A
contrast audit that reads those two properties concludes "the states are the same" and is
measuring nothing. Read `opacity`, and read `getComputedStyle(el, '::before')`.

The practical consequence for theming: neither state needs its own rule when you change
the accent colour. Both are derived from the rendered result rather than from the accent,
so a filled button at 19.7:1 stays legible on hover by construction — a 3% overlay cannot
move it more than a fraction of a point.

## The navigation chain hands you a Spring proxy, not your view class

Every view in this app annotates its own access at class level (`@RolesAllowed`,
`@PermitAll` — ADR-0008), which makes Spring method security wrap it in a CGLIB proxy. So
`AfterNavigationEvent.getActiveChain().get(0).getClass()` is
`MyReportsView$$SpringCGLIB$$0`, and

```java
myGroup.contains(view)            // Set<Class<?>>.contains — matches nothing, ever
item.view().equals(view)          // likewise
item.view().isAssignableFrom(view)  // ✅ the proxy is a subclass
```

There is no error and no log line; a feature keyed off the view class simply never fires.
The old `@Menu`-driven side nav never met this because `MenuEntry#menuClass()` reports the
*declared* class — so the trap only appears the first time you read the class off a
navigation event. Anything keyed on view identity is exposed: breadcrumbs, analytics,
per-view layout switches. See F-070.

## A ContextMenu's items are outside the browserless component tree

`ContextMenu` attaches its items as a virtual child of its target, and the browserless
tester's `find(...)` / `$(...)` walk does not reach them — open or closed. So a
`RouterLink` inside a nav menu, a `MenuBar` submenu item, or a `Dialog`'s content before
it opens is invisible to a locator, while being perfectly reachable from Java
(`menuBar.getItems().get(0).getSubMenu().getItems()`).

Assert through the component's own API, or move the guarantee into a model you can test
directly — and say out loud which half then rests on visual verification. See F-071.

## FormLayout's row spacing is a half-margin on every child, not a gap

`setRowSpacing("20px")` does not set a grid `row-gap`. The layout's own `#layout` is a
flex container with `row-gap: normal`, and the spacing is applied as
`margin-block: calc(var(--vaadin-form-layout-row-spacing) / 2)` on **every slotted
child** — two neighbours add up to the 20.

So a child whose own class declares `margin: 0` (an `Hr` rule, a `Div` carrying a card
class, a heading `Span`) removes its half and sits **10** from both neighbours, with no
error and a result plausible enough to pass a glance. Measured on the travel editor's
section rules (F-081). Do not reset margins on a `FormLayout` child; the slotted rule
already outranks the UA's `hr { margin-block: 0.5em }`, so "set none" means write nothing.

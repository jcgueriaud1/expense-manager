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

# Radius

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| `--aura-base-radius` | 3 (9px field radius) | 3 | 3 (Aura default) | both agree | **settled** |
| Card radius | 12 | `--vaadin-radius-l` 15 | `--em-card-radius: 12px` | design | **settled** |
| Content-panel radius | 12 | 15 (derived) | `--em-card-radius` — was `--aura-app-layout-radius`, removed with `AppLayout` in #146 | design | **settled** |
| Nav pill radius | 100 | — | the literal, in the shell's own rule: a pill, not a corner, and off the end of the scale | design | **settled** (#146) |

## The scale

At `--aura-base-radius: 3`:

| Token | Value | Used for |
|---|---|---|
| `--vaadin-radius-s` | 5 | technical-detail block |
| `--vaadin-radius-m` | 9 | fields, buttons, error summary, history entry, preview |
| `--vaadin-radius-l` | 15 | cards, callouts (current app value) |
| `--em-card-radius` | 12 | design's card radius (off-scale), and the shell's content card |

## Why the card radius needs its own property

This is the clearest case of the design leaving Aura's derivation. Solving the formulas
backwards:

- a 9px field radius needs `--aura-base-radius: 3` — `round(3 × 2px + 3px) = 9`
- a 12px card radius needs `1.33` — `round(1.5 × 1.33 + 10) = 12`

At `1.33` the field renders 6px. **No single base satisfies both**, so this cannot be
tuned away by picking a better base (F-064). The field radius is the one the kit
components use, so the base stays at 3 and the card takes a property.

`--aura-app-layout-radius` used to be the exception — Aura has a real property for exactly
that value, so the content panel's 12px was overridden directly rather than given an
`--em-*` twin. #146 removed the `AppLayout` the property styles, so the same 12px now
comes from `--em-card-radius` on the shell's own card. A property with nothing left to
style is worse than no property: it looks like a theme decision and changes nothing.

## Note

Setting `--aura-base-radius: 0` does **not** square every corner. To remove all rounding
you must override the base-style radius properties directly.

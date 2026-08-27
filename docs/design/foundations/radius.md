# Radius

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| `--aura-base-radius` | 3 (9px field radius) | 3 | 3 (Aura default) | both agree | **settled** |
| Card radius | 12 | `--vaadin-radius-l` 15 | `--em-card-radius: 12px` | design | **settled** |
| App-layout content radius | 12 | 15 (derived) | `--aura-app-layout-radius: 12px` | design | **settled** |
| Content-panel outer radius (24), pill radii (100) | 24 / 100 | — | deferred to the shell | #146 | **open** |

## The scale

At `--aura-base-radius: 3`:

| Token | Value | Used for |
|---|---|---|
| `--vaadin-radius-s` | 5 | technical-detail block |
| `--vaadin-radius-m` | 9 | fields, buttons, error summary, history entry, preview |
| `--vaadin-radius-l` | 15 | cards, callouts (current app value) |
| `--em-card-radius` | 12 | design's card radius (off-scale) |
| `--aura-app-layout-radius` | 12 | the App Layout content panel |

## Why the card radius needs its own property

This is the clearest case of the design leaving Aura's derivation. Solving the formulas
backwards:

- a 9px field radius needs `--aura-base-radius: 3` — `round(3 × 2px + 3px) = 9`
- a 12px card radius needs `1.33` — `round(1.5 × 1.33 + 10) = 12`

At `1.33` the field renders 6px. **No single base satisfies both**, so this cannot be
tuned away by picking a better base (F-064). The field radius is the one the kit
components use, so the base stays at 3 and the card takes a property.

`--aura-app-layout-radius` is the exception: Aura has a real property for exactly that
value, so it is overridden directly rather than given an `--em-*` twin. It is effective
only while `--aura-app-layout-inset` is non-zero — it is, at `1.5vmin`, and the inset
drops to zero automatically below 800px viewport width.

## Note

Setting `--aura-base-radius: 0` does **not** square every corner. To remove all rounding
you must override the base-style radius properties directly.

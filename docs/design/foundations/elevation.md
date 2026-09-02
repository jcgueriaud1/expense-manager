# Elevation

Surfaces and shadows. The app overrides **nothing** here — the design's shadow colour and
offsets are the Aura kit's own, so every value is stock.

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| Shadow colour and offsets | stock kit values | default | default | both agree | **settled** |
| `--aura-surface-level` | 1 (kit default) | default | default | both agree | **settled** |
| `--aura-surface-opacity` | 0.5 (kit default) | default | default | both agree | **settled** |
| `--aura-overlay-surface-opacity` | not specified | default (0.85) | default | both agree | **settled** |

The design binds `Components/Field background` to `Shades/Surface 4` and its shadow to
`Shades/Shadow color` — both remote Aura kit variables, i.e. defaults under another name.

## Shadows

| Token | Used by |
|---|---|
| `--aura-shadow-xs` | buttons, form inputs, `.report-card--actionable` |
| `--aura-shadow-s` | small overlays, tooltips, cards, primary buttons, checked checkboxes |
| `--aura-shadow-m` | popovers, dialogs, notifications — the default `--aura-overlay-shadow` |

Measured in the design: fields carry `y1 blur4 spread−2`, cards `y2 blur5 spread−1`, both
in `#14141433` — which is `Shades/Shadow color`, the kit default.

## Surfaces

| Token | Meaning |
|---|---|
| `--aura-surface-color` | elevated surface, computed from `--aura-background-color` |
| `--aura-surface-color-solid` | the same, fully opaque |
| `--aura-accent-surface` | surface tinted with the effective accent |
| `--vaadin-background-container` | containers, toolbars, highlighted areas |

In this app: `.line-card`, `.totals-card`, `.status-history-entry` and
`.report-card--actionable` use `--aura-surface-color`; `.travel-card` and
`.travel-preview` use `--aura-accent-surface` so a trip reads as the report's headline;
`.report-card` rests on `--vaadin-background-container` and lifts to
`--aura-surface-color` on hover.

## Rules

- `--aura-surface-level` and `--aura-surface-opacity` only take effect on elements
  carrying `aura-surface` / `aura-surface-solid`, or on the documented component
  selectors. Setting them on an arbitrary `Div` does nothing.
- Surface level is **inherited** by nested surfaces — a card inside a card compounds
  unless the child resets it.

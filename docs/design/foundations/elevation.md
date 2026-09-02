# Elevation

Surfaces and shadows. The app overrides **nothing** here — the design's shadow colour and
offsets are the Aura kit's own, so every value is stock.

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| Shadow colour and offsets | stock kit values | default | default | both agree | **settled** |
| Report-detail cards | a shadow on every card (`116:4935`, `116:4957`, `116:4987`, `116:5001`) | **none** — border only | `--aura-shadow-xs`, and `--aura-shadow-s` on the travel card | design | **settled** |
| Card borders | only the travel card has one | every card has one | follow the design | design | **settled** |
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

### The report-detail cards draw those effects with the spread zeroed

Frame `116:4444` gives its cards `0 1px 4px 0` (expenses, totals) and `0 2px 5px 0`
(travel) — the **same offsets and blur** as the file's own named `Shadow XS` and `Shadow S`
effects, with the spread dropped. A spread of 0 instead of −2 or −1 makes the shadow
fractionally wider; it is not a different elevation.

The named effect is the design-system decision and the drawn copy is a hand-tweak that lost
a value, so the tokens win: `--aura-shadow-xs` on the plain cards and `--aura-shadow-s` on
the travel card. This is the *variable beats the drawn value* rule, and it is the safest
kind of instance — nothing about the intent is in doubt, only two pixels of spread.

The status-history box draws `0 1px 2px`, which matches no named effect at all; it is
inferred to `--aura-shadow-xs` along with the other plain cards. See
[`../components/status-history.md`](../components/status-history.md), which records what
that hidden frame does and does not settle.

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

**The trip's accent surface is withdrawn** by the report-detail redesign: the design
distinguishes a trip with a border and a stronger shadow on the plain surface, and paints
no accent on the frame. `.travel-preview` keeps its accent surface — it is inside a dialog
the design does not draw. See
[`../components/travel-card.md`](../components/travel-card.md).

## Rules

- `--aura-surface-level` and `--aura-surface-opacity` only take effect on elements
  carrying `aura-surface` / `aura-surface-solid`, or on the documented component
  selectors. Setting them on an arbitrary `Div` does nothing.
- Surface level is **inherited** by nested surfaces — a card inside a card compounds
  unless the child resets it.

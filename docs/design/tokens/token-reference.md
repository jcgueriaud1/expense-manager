# Token reference

The master map: every custom property this app relies on, its resolved value, and when to
reach for it. One place, so a per-view change never has to measure the running app.

Which *family* to use (`--aura-*` vs `--vaadin-*`, and the `--lumo-*` ban) is in
[`../../../CLAUDE.md`](../../../CLAUDE.md). Rationale per concern is in
[`../foundations/`](../foundations/).

Resolved on Vaadin **25.2.1** at the three Aura-default inputs this app keeps:
`--aura-base-radius: 3`, `--aura-base-size: 16`, `--aura-base-font-size: 14`.

## Inputs

Unitless numbers Aura derives the scales from. **None is overridden**, so the theme file
does not declare them.

| Property | Value | Notes |
|---|---|---|
| `--aura-base-radius` | 3 | range 0–10; design's 9px field radius confirms it |
| `--aura-base-size` | 16 | range 12–24, prefer multiples of 4; design's 34px button height confirms it |
| `--aura-base-font-size` | 14 | design's `Typography/Font-size M` |
| `--aura-base-line-height` | 1.4 | derivation unverified |
| `--aura-contrast-level` | 1 | range 0–5; raise to strengthen text and border colours |

## Set by this app

The complete list — everything else is Aura stock.

| Property | Value | Why |
|---|---|---|
| `color-scheme` | `light dark` | follow the OS preference; **load-bearing for ThemeSwitcher's "System"** |
| `--aura-app-layout-radius` | `12px` | design's content-panel radius; Aura derives 15 |
| `--em-card-radius` | `12px` | off-scale: between `radius-m` 9 and `radius-l` 15 |
| `--em-card-padding` | `20px` | off-scale: between `padding-l` 16 and `padding-xl` 24 |
| `--em-section-gap` | `40px` | off-scale: beyond `gap-xl` 24 |
| `--em-font-size-title` | `24px` | off-scale: beyond `font-size-xl` 18 |

Plus one rule scoping `--aura-accent-color-light/-dark` to `--aura-neutral-*` on
non-tertiary buttons. All in
`src/main/resources/META-INF/resources/aura-theme.css`.

**`--em-*` is a bounded escape hatch, not a parallel scale.** Adding a fifth is a
decision for this folder, not a per-view convenience. Expect them unreferenced until
per-view work consumes them.

## Radius

| Token | Value | When |
|---|---|---|
| `--vaadin-radius-s` | 5 | small inline blocks — technical detail |
| `--vaadin-radius-m` | 9 | fields, buttons, error summary, history entry, preview |
| `--vaadin-radius-l` | 15 | cards, callouts |
| `--em-card-radius` | 12 | where the design's 12px card radius is required |

## Spacing

`--vaadin-padding-*` and `--vaadin-gap-*` resolve to the same values. **Sized steps
only** — there is no bare `--vaadin-padding` / `--vaadin-gap` (F-030).

| Token | Value | When |
|---|---|---|
| `-xs` | 4 | inside a callout, comment offset |
| `-s` | 8 | card column gap, rule offset, tight groups |
| `-m` | 12 | section padding, preview padding |
| `-l` | 16 | card padding |
| `-xl` | 24 | — |
| `--em-card-padding` | 20 | design's card padding and gap |
| `--em-section-gap` | 40 | design's inter-section gap |

## Typography

| Token | Size | Line height | When |
|---|---|---|---|
| `--em-font-size-title` | 24 | — | page headings |
| `--aura-font-size-xl` | 18 | 26 | grand total, list title |
| `--aura-font-size-l` | 16 | 22 | card totals, amounts, detail title |
| `--aura-font-size-m` | 14 | 20 | body, card titles |
| `--aura-font-size-s` | 13 | 18 | secondary text, callouts |
| `--aura-font-size-xs` | 12 | 16 | eyebrows, section labels |

| Token | Value |
|---|---|
| `--aura-font-family` | `"Instrument Sans", system-ui, ui-sans-serif, sans-serif` |
| `--aura-font-weight-regular` | 400 |
| `--aura-font-weight-medium` | 500 |
| `--aura-font-weight-semibold` | 600 |

## Colour

| Token | Light | Dark | When |
|---|---|---|---|
| `--aura-accent-color` | `#3266e4` | `#3266e4` | selections, focus, tertiary buttons |
| `--aura-accent-text-color` | `#2b59c8` | `#81b9ff` | link text — the only blue text |
| `--aura-neutral-light` / `-dark` | `#0a0b0d` | `#feffff` | the filled primary button |
| `--vaadin-text-color` | `#0a0b0d` | `#feffff` | body text |
| `--vaadin-text-color-secondary` | — | — | `.muted`, eyebrows, totals rows |
| `--vaadin-border-color` | — | — | card borders |
| `--vaadin-border-color-secondary` | — | — | internal rules (card footer, totals) |
| `--vaadin-background-container` | — | — | `.report-card` at rest |
| `--aura-surface-color` | — | — | elevated cards |
| `--aura-accent-surface` / `--aura-accent-border-color` | — | — | trip card, per-diem preview |
| `--aura-red` / `-text`, `--aura-green` / `-text` | — | — | destructive, rejected, approved |
| `--vaadin-user-color-2` | `#b3329d` | `#b3329d` | |

Palette hues available: `--aura-neutral`, `-red`, `-orange`, `-yellow`, `-green`,
`-blue`, `-purple`, each with a `-text` companion. **No opacity ladder** — derive with
`color-mix(in srgb, var(--aura-red) 8%, transparent)`.

## Elevation

| Token | When |
|---|---|
| `--aura-shadow-xs` | buttons, inputs, actionable cards |
| `--aura-shadow-s` | small overlays, cards, primary buttons |
| `--aura-shadow-m` | dialogs, popovers, notifications |

## The formulas

The reference, because they hold whatever the inputs are — they survive a theme change
and go stale only on a Vaadin upgrade.

```
--vaadin-radius-s    = min(0.25lh, round(baseRadius * 1px + 2px, 1px))
--vaadin-radius-m    = round(baseRadius * 2px + 3px, 1px)
--vaadin-radius-l    = round(baseRadius * 1.5px + 10px, 1px)

--vaadin-padding-xs  = --vaadin-gap-xs = round(baseSize * 0.25 * 1px, 1px)
--vaadin-padding-s   = --vaadin-gap-s  = round(baseSize * 0.5  * 1px, 1px)
--vaadin-padding-m   = --vaadin-gap-m  = round(baseSize * 0.75 * 1px, 1px)
--vaadin-padding-l   = --vaadin-gap-l  = round(baseSize * 1    * 1px, 1px)
--vaadin-padding-xl  = --vaadin-gap-xl = round(baseSize * 1.5  * 1px, 1px)

--aura-font-size-m   = round(baseFontSize / 16 * 1rem, 0.0625rem)
--aura-font-size-xs  = clamp(0.625rem, round(font-size-m * 0.85, 0.0625rem), 0.8125rem)
--aura-font-size-s   = round((font-size-m + font-size-xs) / 2, 0.0625rem)
--aura-font-size-l   = round(font-size-m * 1.125, 0.0625rem)
--aura-font-size-xl  = round(font-size-l * 1.125, 0.0625rem)
```

The type ramp is **not** geometric: `xs` is a *clamped* 0.85 of `m`, and `s` is the
**midpoint** of `m` and `xs`. Inferring a constant ratio from one sample gets both wrong.

Line heights (16/18/20/22/26) were measured as **values only** — treat their derivation
from `--aura-base-line-height` as unverified.

## Three traps

- **Aura's docs are wrong about `--aura-font-size-xs`.** They say the default
  "corresponds to `11px`"; the formula and the running app both give **12px** at base 14.
  This is the design's most-used text size, so trusting the docs manufactures an
  off-scale value that does not exist (F-068).
- **The scale cannot express every pair.** A 9px field radius needs `baseRadius = 3`; a
  12px card radius needs `1.33`, at which the field renders 6px. No single base satisfies
  both — the design has left Aura's derivation, and the fix is global, never per-view
  (F-064).
- **Measuring:** `element.style.setProperty()` takes a **CSS** property name, so
  `setProperty('borderTopLeftRadius', …)` fails silently and every token reads back as
  `0px`/`normal`. Use `'border-top-left-radius'`. A whole pass can look successful and be
  entirely zeros.

**Refresh trigger:** a Vaadin minor upgrade. The formulas are Aura internals with no
compatibility promise — re-measure on the running app after every version bump and
correct this file.

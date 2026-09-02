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

Icons have no input of this kind: neither Aura nor the base styles derive an icon scale.
`--vaadin-icon-size` is a single global defaulting to `1lh`, and there is no icon size
ramp — which is why this app mints its own three
([`../foundations/iconography.md`](../foundations/iconography.md)).

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
| `--em-card-radius` | `12px` | off-scale: between `radius-m` 9 and `radius-l` 15 |
| `--em-card-padding` | `20px` | off-scale: between `padding-l` 16 and `padding-xl` 24 |
| `--em-section-gap` | `40px` | off-scale: beyond `gap-xl` 24 |
| `--em-font-size-title` | `40px` | off-scale: beyond `font-size-xl` 18. **Was 24px** — re-decided by the reference-table survey and changed in place by **#169**; see [`../foundations/typography.md`](../foundations/typography.md) |
| `--em-font-size-section` | `30px` | off-scale: beyond `font-size-xl` 18 — in-page section headings. Declared by **#169** |
| `--em-font-size-total` | `20px` | off-scale: beyond `font-size-xl` 18 — the report card's total |
| `--em-font-size-metric` | `28px` | off-scale: beyond `font-size-xl` 18 — the metric card's figure |
| `--em-header-color` | `#f16c4e` | the shell bar's brand coral; bound to no design variable, and no Aura hue is near it |
| `--em-header-text-color` | `#ffffff` | the bar's label colour — pinned, **not** `--aura-accent-contrast-color`, which tracks the accent rather than the bar |
| `--em-icon-size-s` | `16px` | icon beside small text — the design's 16px trip-row glyph |
| `--em-icon-size-m` | `20px` | icon in a button slot or field prefix — the design's bound `Button icon size` |
| `--em-icon-size-l` | `24px` | standalone icon in an app-drawn layout — the design's 24px section chevron |
| `--vaadin-icon-stroke-width` | `2` | **load-bearing.** A base property, not an `--em-*` one, because the framework has a real property for exactly this. Unset it and every icon renders at the SVG default of `1` — visibly thin, silently |

Plus one rule scoping `--aura-accent-color-light/-dark` to `--aura-neutral-*` on
non-tertiary buttons. All in
`src/main/resources/META-INF/resources/aura-theme.css`.

The four icon rows were settled by the iconography survey and **declared by #163's own
PR**, which is the shortest that gap has ever been — the survey and the implementation
happened to be in flight together. `--vaadin-icon-stroke-width` is the one row here that
is not an `--em-*` property: the framework has a real property for exactly that value, so
it is overridden directly rather than given a twin (the same reasoning that once applied
to `--aura-app-layout-radius`).

`--em-font-size-title` changed value in place, which none of the rows above had done
before: the reference-table frame draws 40px where the report-list frame drew 24, and the
40 won. **A changed token is more dangerous than a new one** — a new property renders
unset until it is declared, which is visible, whereas a changed one keeps rendering and
simply moves every consumer. Two consumers move here that were never re-surveyed: the
report card's title and the shell's small-screen greeting, both of which borrowed this
property when it happened to equal their own value. See
[`../foundations/typography.md`](../foundations/typography.md) § *Two heading sizes, one
token*.

**#169 made that change and re-checked both consumers in the browser** rather than
trusting the reasoning above — which is the whole point of a value that moves silently.
Neither was re-surveyed, so neither was *fixed*; both simply now render at 40, and
splitting them off their own properties stays the report-list and shell issues' call.

`--em-font-size-total` and `--em-font-size-metric` were decided by the report-list survey
and declared by **#162**, the first issue to use them — a survey writes no CSS, so the two
sat decided-but-undeclared in between. That gap is the thing to watch when a survey settles
a new property: until it exists, any `var()` on it renders **unset** — invalid, so visibly
wrong rather than silently frozen, which is the safer of the two failures but still a
failure. Do not ship a view that uses a property before it is declared here.

`--aura-app-layout-radius` was here until #146 and is not any more: it styles
`AppLayout`'s content area, and the shell no longer uses `AppLayout`. The design's 12px
content-panel radius is now applied through `--em-card-radius`, which is the same value.

**`--em-*` is a bounded escape hatch, not a parallel scale.** Adding another is a
decision for this folder, not a per-view convenience. The two the shell added are colour,
not scale — the shell's geometry (heights, the 900px column, the 35px overlap) is written
as literals in its own CSS rules, because a custom property for a single use is where a
parallel scale starts. Each is listed in
[`../components/app-shell.md`](../components/app-shell.md).

## Radius

| Token | Value | When |
|---|---|---|
| `--vaadin-radius-s` | 5 | small inline blocks — technical detail |
| `--vaadin-radius-m` | 9 | fields, buttons, error summary, history entry, preview |
| `--vaadin-radius-l` | 15 | cards, callouts |
| `--em-card-radius` | 12 | where the design's 12px card radius is required — including the shell's content card |

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
| `--em-font-size-title` | 40 | — | page headings, report card titles |
| `--em-font-size-section` | 30 | — | in-page section headings |
| `--em-font-size-metric` | 28 | — | metric card figures |
| `--em-font-size-total` | 20 | — | report card totals |
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

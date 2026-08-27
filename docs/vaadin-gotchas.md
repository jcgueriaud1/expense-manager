# Vaadin gotchas

Behaviour this project has been bitten by and no config file confesses. Cached here
because looking it up costs a browser probe or a compile error; the one-command
lookups stay in the environment where they cannot go stale.

Layout and spacing rules live in [`theming-layouts.md`](theming-layouts.md); the
Aura-not-Lumo rules live in [`../CLAUDE.md`](../CLAUDE.md). This file holds what
neither covers.

## The theme record

This app's **theme record**: the values a design-facing run looks up instead of
deciding again. `/figma-theme` writes it, `/figma-survey` reads it, and a difference
recorded here as **settled** is never a per-view question. It is data, refreshed when
the theme changes or the Vaadin version moves; the rationale behind a decision belongs
in an ADR, not here.

All three sections are written. They were settled by the `/figma-theme` run for issue
#144 against Figma file `Irsp3cgi1WX3GiLGpJZECa`, frame `116:4444` (report detail) —
the design's own global variables, read through the binding walk that F-063 describes.
The reasoning lives in [ADR-0025](adr/0025-figma-design-source-of-truth.md).

The headline: **the design is stock Aura.** Its accent, base font size and font family
are each exactly the Aura 25.2 default, so the app's theme file now sets almost nothing
— what it does set is either off-scale (below) or genuinely non-default.

### Decided values

Every property where the design and the app disagreed, plus the places the app keeps its
own value on purpose. A row marked **settled** is closed: a survey that meets that
difference names it as settled and moves on.

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| `color-scheme` | Light + Dark modes | `light dark` | `light dark` | both agree | **settled** |
| `--aura-accent-color-light` | `#3266e4` | `#155dfc` | Aura default (`--aura-blue`) | design | **settled** |
| `--aura-accent-color-dark` | `#3266e4` | `#60A5FA` | Aura default (`--aura-blue`) | design | **settled** |
| `--aura-base-font-size` | 14 | 15 | 14 (Aura default) | design | **settled** |
| `--aura-font-family` | Instrument Sans | Inter, via Google Fonts `@import` | Aura default (Instrument Sans) | design | **settled** |
| `--aura-font-weight-regular` / `-medium` / `-semibold` | 400 / 500 / 600 | 450 / 550 / 650 | Aura defaults | design | **settled** |
| button / input / checkbox / radio / grid-header font weights | not specified | 600 / 500 / 550 / 550 / 600 | unset | design | **settled** |
| `--aura-base-size` | 16 (button height 34, field padding 16) | 16 | 16 (Aura default) | both agree | **settled** |
| `--aura-base-radius` | 3 (field radius 9) | 3 | 3 (Aura default) | both agree | **settled** |
| `--aura-app-layout-radius` | 12 | 15 (derived default) | `12px` | design | **settled** |
| Primary button colour | `accent-neutral` `#0a0b0d` / `#ffffff` | accent blue | neutral, scoped to non-tertiary buttons | design | **settled** |
| Tertiary button colour | drawn neutral | accent blue | accent blue — unchanged | app | **settled** |
| `--vaadin-user-color-2` | `#b3329d` | default | default | both agree | **settled** |
| `--aura-line-height-s` | 20 | 18 (default) | 18 (default) | app | **settled** |
| Background colour | no variable bound | default | default | design does not say | **settled** |
| Shadows | stock kit shadow colour + offsets | default | default | both agree | **settled** |
| Side-nav rules | shell replaced by a top header bar | 3 rules | kept, comment-marked | app, pending #146 | **open** |
| Header gradient, nav widths (220/250), page inset (80) | orange linear gradient | n/a | deferred to the shell | #146 owns it | **open** |

Three values were verified in the browser against the design's own hex rather than
assumed: `--aura-neutral-light` resolves to exactly `#0a0b0d`, `--aura-accent-color` to
exactly `#3266e4`, and `--aura-accent-text-color` to `#2b59c8` light / `#81b9ff` dark.
The design pins the same three. Nothing was colour-matched by eye.

**The black primary button is global, not per-button.** F-067 reached the same
rendered colour (`oklch(0.15 0.0038 248)`) by adding Aura's `aura-accent-neutral` utility
class to individual buttons. The theme now scopes the accent to neutral for every
non-tertiary button, so **per-view code must not add that class** — it is redundant, and
it puts the same decision in two places. Tertiary buttons stay accent-blue, which is what
keeps the design's link-like actions and the blue Draft badge blue.

**The one deletion that is not housekeeping.** Dropping the Inter `@import` is safe
because Aura *bundles* the Instrument Sans webfont — `document.fonts` reports
`Instrument Sans 400 700 loaded` with no `@import` in the file. Had it not, the theme
would have silently fallen back to the system font stack.

### Off-scale values

The design's own values that Aura's derivation cannot produce (F-064). Each is decided
here, once — a view that meets one alone will invent an answer, and the next view will
invent a different one.

| Design value | Where | Nearest tokens | Shape | Decision | Status |
|---|---|---|---|---|---|
| 12 px radius | cards, totals box, status box (6 nodes) | `radius-m` 9, `radius-l` 15 | between | `--em-card-radius: 12px` | **settled** |
| 12 px radius | AppLayout content panel | `radius-l` 15 | between | `--aura-app-layout-radius: 12px` — a real Aura property, exact | **settled** |
| 20 px padding & gap | cards, totals, status, actions (46 occurrences) | `padding`/`gap-l` 16, `-xl` 24 | between | `--em-card-padding: 20px` | **settled** |
| 40 px gap & padding | `content-wrap`, `report-header`, `expense-left` (6) | `gap-xl` 24 | beyond | `--em-section-gap: 40px` | **settled** |
| 24 px font | `report-title` — every page heading | `font-size-xl` 18 | beyond | `--em-font-size-title: 24px` | **settled** |
| 15 px font | expense row titles and amounts (16 nodes) | `font-size-m` 14, `-l` 16 | between | accept `--aura-font-size-l` = 16 px (**+1 px**) | **settled** |
| 10 px gap | intra-row groups (28 content nodes) | `gap-s` 8, `gap-m` 12 | between | accept `--vaadin-gap-s` = 8 px (**−2 px**) | **settled** |
| 24 px radius, 80 px padding, 15 px padding, pill radii, −35 px overlap | app shell only | — | — | deferred to #146 | **open** |

The rule behind the split: a value 3–6 px off the scale *and* recurring gets its own
`--em-*` property, because that gap is visible on every card and heading; a value within
1–2 px takes the nearest token, because a project property that shadows the type scale
for one pixel is where per-view drift starts. Both divergences are named above rather
than hidden.

The four `--em-*` properties are defined in
`src/main/resources/META-INF/resources/aura-theme.css` and are deliberately
**unreferenced** until the per-view issues consume them; `styles.css` keeps its own
classes until each view is revised.

**12 px text is *not* off-scale.** It is exactly `--aura-font-size-xs` at base 14, and
it is the design's most-used size (28 nodes). This is worth stating because F-064
recorded it as a match against the app's *old* base of 15 — the conclusion survives the
move to 14, but not for the reason recorded there. See the docs warning below.

### Resolved token scale

Keeping this here makes a design-to-token comparison free and offline — the
alternative is a running app, since Aura ships these as `calc()`/`round()` expressions
resolved at render time.

**The formulas are the reference.** They hold whatever the theme inputs are, so they
survive a theme change and go stale only on a Vaadin upgrade.

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

Note the type scale is not a geometric ramp: `xs` is a *clamped* 0.85 of `m`, and `s` is
the **midpoint** of `m` and `xs`. Guessing a constant ratio from one sample gets both
wrong.

Resolved at `--aura-base-radius: 3`, `--aura-base-size: 16`, `--aura-base-font-size: 14`
— all three the Aura 25.2 defaults, and since #144 all three the app's own values, so
this table now *is* the app's table:

| Scale | xs | s | m | l | xl |
|---|---|---|---|---|---|
| `--vaadin-radius-*` | — | 5 | **9** | 15 | — |
| `--vaadin-padding-*` / `--vaadin-gap-*` | 4 | 8 | **12** | 16 | 24 |
| `--aura-font-size-*` | 12 | 13 | **14** | 16 | 18 |
| `--aura-line-height-*` | 16 | 18 | **20** | 22 | 26 |

Read from the running app at Vaadin 25.2.1, by applying each token to a probe element
and reading the used value back. Every radius, padding/gap and font-size step now has
its formula, so the earlier "treat the multipliers as unverified" caveat is lifted for
those three scales. The line heights were measured as **values only** — treat their
derivation from `--aura-base-line-height: 1.4` as unverified.

**Probe gotcha:** `element.style.setProperty()` takes a **CSS** property name, so
`setProperty('borderTopLeftRadius', …)` fails silently and every token reads back as
`0px`/`normal`. Use `'border-top-left-radius'`. A whole measurement pass can look
successful and be entirely zeros.

**Two consequences worth knowing before reading a design:**

- **Aura's own docs are wrong about `--aura-font-size-xs`.** They state the default
  "corresponds to `11px`"; the formula and the running app both give **12 px** at base
  14. The measurement wins — and note this is the design's most-used text size, so
  trusting the docs would have manufactured an off-scale value that does not exist.
- **The scale cannot express every pair of values.** A design asking for a 9 px field
  radius and a 12 px card radius needs `baseRadius = 3` for the first (`2·3+3 = 9`)
  and `baseRadius = 1.33` for the second (`1.5·1.33+10 = 12`) — and at 1.33 the field
  becomes 6 px. No single base satisfies both. When that happens the design has left
  Aura's derivation, and the fix is a global decision, never a per-view one — see
  *Off-scale values* above for the ones already taken, and finding F-064.

**Refresh trigger:** a Vaadin minor upgrade. The formulas are Aura internals with no
compatibility promise, so re-measure with a probe element on the running app after every
version bump and correct this table.

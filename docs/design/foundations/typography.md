# Typography

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| `--aura-font-family` | Instrument Sans | Inter, via Google Fonts `@import` | Aura default (Instrument Sans) | design | **settled** |
| `--aura-base-font-size` | 14 | 15 | 14 (Aura default) | design | **settled** |
| `--aura-font-weight-regular` | 400 | 450 | Aura default (400) | design | **settled** |
| `--aura-font-weight-medium` | 500 | 550 | Aura default (500) | design | **settled** |
| `--aura-font-weight-semibold` | 600 | 650 | Aura default (600) | design | **settled** |
| Button / input / checkbox / radio / grid-header weights | not specified | 600 / 500 / 550 / 550 / 600 | unset | design | **settled** |
| `--aura-line-height-s` | 20 | 18 (default) | 18 (default) | app | **settled** |
| Page-heading size | 24 | `font-size-xl` 18 | `--em-font-size-title: 24px` | design | **settled** |
| Expense row title | 15 | — | `--aura-font-size-l` = 16 (**+1px**) | nearest token | **settled** |

The whole font block was deleted rather than ported: 450/550/650 were tuned for Inter's
variable-weight axis and are meaningless against Instrument Sans. The design's own
weights (500 Medium, 600 Semibold) are exactly the Aura defaults.

**No `@import` is needed.** Aura bundles the Instrument Sans webfont —
`document.fonts` reports `Instrument Sans 400 700 loaded` with no import in the theme
file. Had it not, dropping the Inter import would have silently fallen back to the
system stack.

## The scale

At `--aura-base-font-size: 14`, on Vaadin 25.2.1:

| Token | Size | Line height | Used for |
|---|---|---|---|
| `--em-font-size-title` | 24 | — | page headings (off-scale, see below) |
| `--aura-font-size-xl` | 18 | 26 | grand total, list title |
| `--aura-font-size-l` | 16 | 22 | card totals, amounts, detail title, preview amount |
| `--aura-font-size-m` | 14 | 20 | body, card titles, line amounts |
| `--aura-font-size-s` | 13 | 18 | secondary text, line names, callouts, totals rows |
| `--aura-font-size-xs` | 12 | 16 | eyebrows, section labels, technical detail |

Formulas and the derivation trap are in
[`../tokens/token-reference.md`](../tokens/token-reference.md). Two things not to guess:
the ramp is **not** geometric (`xs` is a clamped 0.85 of `m`; `s` is the *midpoint* of
`m` and `xs`), and **Aura's docs are wrong** — they give `xs` as 11px where the app
gives 12px (F-068).

## Text roles

Utility classes in `styles.css`, so a role is named once rather than restated per view:

| Class | Renders |
|---|---|
| `.muted` | secondary colour, `font-size-s` |
| `.muted-xs` | secondary colour, `font-size-xs` |
| `.section-label` | uppercase, `0.05em` tracking, `font-size-xs`, semibold, secondary colour |

## Off-scale

| Design value | Where | Nearest | Decision |
|---|---|---|---|
| 24px | every page heading | `xl` 18 — top of the scale | `--em-font-size-title: 24px` |
| 15px | expense row titles, amounts (16 nodes) | `m` 14 / `l` 16 | accept `l` = 16px (**+1px**) |

24px has no Aura token at all and appears on every page, so it gets a property. 15px is
one pixel from a token, and a project property that shadows the type scale for one pixel
is where per-view drift starts.

## Known design defect

Frame `116:4444` declares `Instrument Sans` and renders **three** families — Instrument
Sans (11 nodes), Inter (44), Public Sans (10). The app follows the *variable*, not the
drawn text (ADR-0025 decision 4): a literal reading would have kept Inter, the very value
the design was replacing. The stray families are the designer's to fix (F-069). Measured
on one frame only.

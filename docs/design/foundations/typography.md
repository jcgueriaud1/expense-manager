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
| Report card total size | 20 | — | `--em-font-size-total: 20px` | design | **settled** |
| Metric figure size | 28 | — | `--em-font-size-metric: 28px` | design | **settled** |
| Metric figure / card title / total weight | 700 (Public Sans Bold) | — | `--aura-font-weight-semibold` = 600 (**−100**) | app | **settled** |

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
| 20px | report card totals (4 nodes, one per card) | `xl` 18 — top of the scale | `--em-font-size-total: 20px` |
| 28px | metric figures (3 nodes, and 3 more on Approvals) | `xl` 18 — top of the scale | `--em-font-size-metric: 28px` |

24px has no Aura token at all and appears on every page, so it gets a property. 15px is
one pixel from a token, and a project property that shadows the type scale for one pixel
is where per-view drift starts.

### The display ramp

20/24/28 form a coherent 4px ramp **above** the text scale, and all three are what the
design uses for its loudest type: a card's total, a page or card heading, a metric
figure. Aura's scale stops at `xl` 18, so none of the three can be derived — 20 is 2px
past the top and 28 is 10px past it.

Both new values took a property rather than the nearest token, which is a deliberate
exception to the "within a pixel or two takes the token" rule that settled 15px:

- **28px** is nowhere near a token, recurs on every metric, and the Approvals frame
  (`327:11681`) reuses the same card — so the alternative is two views inventing 28px
  separately.
- **20px** is only 2px past `xl`, but it is the report card's total sitting directly
  under a 24px title. Flattening it to 18px compresses a deliberate 24/20 pairing into
  24/18 and makes the money read as secondary to the title, which inverts what the card
  is for.

Whether this ramp should be three project properties or three real tokens in the
design's own scale is the designer's call, and is raised as a question rather than
assumed — see the survey follow-up issue.

### Weight 700 has no Aura token

The frame draws its metric figures, card titles and totals in Public Sans **Bold**
(700). Aura's weight scale stops at `--aura-font-weight-semibold` (600) and defines no
bold. 600 is used and the missing 100 accepted, consistent with the settled row above
recording that the design's *specified* weights (500/600) are exactly the Aura
defaults — the 700 comes from the drawn text, not from a variable, and the drawn text on
this frame is already known to be unreliable (see the defect below).

## Known design defect

Frame `116:4444` declares `Instrument Sans` and renders **three** families — Instrument
Sans (11 nodes), Inter (44), Public Sans (10). The app follows the *variable*, not the
drawn text (ADR-0025 decision 4): a literal reading would have kept Inter, the very value
the design was replacing. The stray families are the designer's to fix (F-069).

Confirmed on a **second** frame: `116:2499` "My Expenses" (the report list) draws Public
Sans across its metric cards, report cards and section headers, and Inter on one section
header — the same defect with the same resolution. Two frames now, so it is systematic
rather than a slip on one screen.

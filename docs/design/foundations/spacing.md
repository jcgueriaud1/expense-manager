# Spacing

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| `--aura-base-size` | 16 (34px button height, 16px field padding) | 16 | 16 (Aura default) | both agree | **settled** |
| Card padding & gap | 20 | — | `--em-card-padding: 20px` | design | **settled** |
| Section gap | 40 | — | `--em-section-gap: 40px` | design | **settled** |
| Tight intra-row gap | 10 | — | `--vaadin-gap-s` = 8px (**−2px**) | nearest token | **settled** |
| Page inset | 80 | — | deferred to the shell | #146 | **open** |

Density was left at the Aura default deliberately as well as by agreement: the app
renders full-screen receipt sheets and dialogs on mobile, where a compact density would
shrink touch targets below ADR-0020's floor.

## The scale

At `--aura-base-size: 16` — `--vaadin-padding-*` and `--vaadin-gap-*` resolve to the
same values:

| Token | Value | Used for |
|---|---|---|
| `--vaadin-padding-xs` / `--vaadin-gap-xs` | 4 | callout internal gap, comment offset |
| `--vaadin-padding-s` / `--vaadin-gap-s` | 8 | card column gap, footer rule offset, tight groups |
| `--vaadin-padding-m` / `--vaadin-gap-m` | 12 | section padding, history top padding, preview padding |
| `--vaadin-padding-l` / `--vaadin-gap-l` | 16 | card padding |
| `--vaadin-padding-xl` / `--vaadin-gap-xl` | 24 | — |
| `--em-card-padding` | 20 | design's card padding and card gap (off-scale) |
| `--em-section-gap` | 40 | design's gap between report sections (off-scale) |

There is **no bare `--vaadin-padding` or `--vaadin-gap`** for custom CSS — sized steps
only (F-030).

## Rules

Full standard in [`../../theming-layouts.md`](../../theming-layouts.md). The short form:

- **Structure through the Java layout API**, never `getStyle().set(...)`:
  `setSpacing("var(--vaadin-gap-m)")`, `setPadding("var(--vaadin-padding-l)")`,
  `expand(child)`, `Scroller` for scrolling.
- **Decoration through a scoped, role-named CSS class** in `styles.css`, using tokens.
- A `RouterLink` and other raw elements are **not** Vaadin layouts, so their
  `display: flex` / `gap` legitimately live in CSS — `.report-card` is the worked
  example.
- Light-DOM elements need the `box-sizing: border-box` reset already at the top of
  `styles.css`; without it `width: 100%` plus own padding overflows the parent.

## Off-scale

| Design value | Where | Nearest | Decision |
|---|---|---|---|
| 20px | cards, totals, status box, actions (46 occurrences) | `l` 16 / `xl` 24 | `--em-card-padding: 20px` |
| 40px | `content-wrap`, `report-header`, `expense-left` (6) | `xl` 24 — top of the scale | `--em-section-gap: 40px` |
| 10px | intra-row groups (28 content nodes) | `s` 8 / `m` 12 | accept `s` = 8px (**−2px**) |
| 80px, 15px | page inset, nav links | — | deferred to #146 |

20px is the single most common off-scale value in the design and is 4px from either
neighbour, so it gets a property. 10px is 2px off and cosmetic, so it takes the token.

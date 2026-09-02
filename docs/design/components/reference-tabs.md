# Reference tabs

**Category:** composite
**Origin:** design
**Implementation:** none
**Code:** — (nothing implements it yet)
**Design:** node `156:5401` › `Horizontal tabs`, on frame `156:5396`
"Reference Tables - Allowance Rates"; component definitions `45:580` (`vaadin-tabs`),
`45:549` / `45:550` (`vaadin-tab`, selected and not)

## Overview

The sub-navigation across the three reference-table routes — **VAT Rates**, **Expense
Types**, **Allowance Rates** — drawn as a pill-shaped horizontal tab bar at the top of the
content column, above the page heading.

Use it on those three views and nowhere else. It is **route navigation**, not in-place
content switching: each tab is a link to a different `@Route`, so there is no `TabSheet`
and no content area behind it. A view that wants to switch content it already owns uses
`TabSheet` instead.

**It replaces the shell's "Reference Tables" menu pill.** That was settled in #146 and is
re-decided here: the group's three routes are reached through these tabs, and the shell
pill becomes a plain link into the group. See [Divergence from the shell](#divergence-from-the-shell).

## Anatomy

| Part | Element | Design node |
|---|---|---|
| Bar | `Tabs` (horizontal, the default) | `156:5401` |
| One tab | `Tab` containing a `RouterLink` | `I156:5401;4889:10749`…`10751` |
| Selected tab | the `Tab` whose route matches the current one | `I156:5401;4889:10751` |

**Each tab wraps a real link.** With the shell menu gone these tabs are the only way to
reach two of the three routes, so they must be middle-clickable and must carry the
destination in an `href` — the same reasoning app-shell.md gives for its single-destination
pill. A `Tabs` selection listener that calls `UI.navigate()` is not equivalent and is not
what this spec asks for.

**Entries are filtered by access, not hidden by CSS.** A user who cannot reach
`/vat-rates` must not see a tab for it — the same `AccessAnnotationChecker` pass
`NavGroup` already performs. All three reference views are `@RolesAllowed("ADMIN")`
today, so in practice the bar is all-or-nothing; the filter is what keeps that true when
one of them opens up.

## Tokens used

| Part | Token | Design value |
|---|---|---|
| Bar background | `--vaadin-background-container` | kit `--components/tabs-background`, `rgba(214,217,220,0.5)` |
| Bar radius | `--em-card-radius` (12) | 13 — **accepted**, 1px |
| Bar padding | `--vaadin-padding-xs` (4) | 4 |
| Tab radius | `--vaadin-radius-m` (9) | 9 |
| Tab padding | `--vaadin-padding-s` (8) | 10 horizontal / 7–8 vertical — **accepted**, ≤2px |
| Tab label | `--aura-font-size-m` (14), `--aura-font-weight-medium` (500) | 14 / 500 |
| Unselected label | `--vaadin-text-color-secondary` | bound |
| Selected label | `--vaadin-text-color` | kit `accent-neutral,-text`, `#2d2f30` |
| Selected fill | `--aura-surface-color` | kit `shades/surface-4` |
| Selected border | `1px solid var(--aura-surface-color)` | kit `components/tab-border-active` |
| Selected shadow | `--aura-shadow-s` | `0 2px 5px -1px` — the design's own Shadow S |

**Two of the design's variables do not exist as CSS properties.**
`--components/tabs-background` and `--components/tab-border-active` are Figma variable
*paths* (note the slash), not custom properties, and nothing in `aura.css` defines them.
Copying them across would render the hardcoded fallback forever (F-062). The
translations above are this spec's, and the resolved colours are unverified until
someone measures them.

Bar height 42px and tab height 34px are component heights, not scale steps: 34 is what
Aura already derives for a control from `--aura-base-size`, so only the 42px bar is a
literal, and it is `34 + 2 × --vaadin-padding-xs`.

## API

```java
var tabs = new ReferenceTabs(currentRoute);   // to be built
```

Composed from stock `Tabs` / `Tab` / `RouterLink`. No wrapper exists yet; the delta
creates one, because three views need identical markup and identical access filtering and
copying it three times is how they drift.

## States

| State | Behaviour |
|---|---|
| default | unselected tab: transparent, `--vaadin-text-color-secondary` label |
| hover | **unverified.** Aura's own `vaadin-tab` hover; the design draws no hover state, and the pill sits on a tinted bar where a low-opacity overlay may not read. Measure before shipping |
| active (pressed) | Aura's own; the design draws none |
| **selected** | surface fill, 1px border, `--aura-shadow-s`, `--vaadin-text-color` label, `aria-current="page"` on the link |
| focus | Aura's focus ring on the `RouterLink` inside the tab, not on the `Tab` host — the link is the focusable element. Arrow-key roving focus is `Tabs`' own |
| disabled | n/a — a route the user cannot reach renders no tab at all (ADR-0008), rather than a greyed one |
| error | n/a — a routing failure renders `ErrorView` inside the shell's card, not inside the bar |

**`aria-current="page"` and `Tabs`' own `aria-selected` both apply here** and say different
things. Set the first on the link; `Tabs` sets the second. The bar is a `tablist` by
markup and a navigation by behaviour, which is a known tension — if it reads badly under a
screen reader, prefer plain `Nav` + links over `Tabs` and keep the visual spec. That check
has not been made.

## Code example

```java
var vat = new Tab(new RouterLink("VAT Rates", VatRateView.class));
var types = new Tab(new RouterLink("Expense Types", ExpenseTypeView.class));
var rates = new Tab(new RouterLink("Allowance Rates", AllowanceRatesView.class));

var tabs = new Tabs(vat, types, rates);
tabs.setSelectedTab(rates);
tabs.addClassName("reference-tabs");
```

## Divergence from the shell

[`app-shell.md`](app-shell.md) records **Reference Tables → menu → `/vat-rates`,
`/expense-types`, `/allowance-rates`**, settled in #146. This survey re-decides it: the
tabs win and the menu goes.

| | Before (#146) | Decided here |
|---|---|---|
| Reaching the three routes | a menu opened from the shell pill | these tabs |
| The shell pill | `Button` + `ContextMenu` | a `RouterLink` to the first reference route the user can reach |

That makes `NavGroup.REFERENCE_TABLES` a single-destination group, which the shell already
renders as a plain link — so the shell change is a `linked()` list of one plus `covered()`
entries for the other two, not new machinery. **`app-shell.md` carries a superseding note;
the shell's own issue owns rewriting its Navigation table.**

Two things this hands to that issue rather than deciding:
1. Which route the pill points at when the user can reach only some of the three.
2. Whether the pill stays `aria-current` while the user is on any of the three.

## Cross-references

[`app-shell.md`](app-shell.md) — the navigation this changes ·
[`../foundations/typography.md`](../foundations/typography.md) ·
ADR-0017 (the shell), ADR-0008 (route security), ADR-0025 (the design as contract)

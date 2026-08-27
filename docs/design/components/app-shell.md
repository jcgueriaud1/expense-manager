# App shell

**Category:** shell
**Status:** **open — #146 replaces it**
**Source:** `com.vaadin.expensemanager.base.ui.MainLayout`; `aura-theme.css` — the `vaadin-side-nav` rules

## Overview

The app-wide navigation frame (ADR-0017): an `AppLayout` with a drawer holding the app
name and side navigation, and a navbar holding the drawer toggle, the
[theme switcher](theme-switcher.md) and the user menu.

**This is the one component the design disagrees with structurally**, so treat this file
as a description of *what exists*, not a specification of what to build. #146 owns the
replacement. Do not reconcile the shell against the design in a per-view issue.

## What the design asks for instead

Measured from Figma frame `116:4444`, deferred to #146:

| Element | Design |
|---|---|
| Navigation | a **top header bar**, not a drawer + side nav |
| Header fill | an orange **linear gradient** (`#0000001a` → `#ffffff1a` over a warm base) |
| Nav item | pill-shaped, 100px radius, 15px padding, 10px gap |
| Main nav width | 220 (local variable `Main Nav Width`) |
| Section nav width | 250 (`Section Nav Width`) |
| Content panel | overlaps the header by **−35px**, 12px top radius, 80px page inset |

The gradient is the sharpest divergence: it is a hand-drawn fill bound to no variable,
and Aura has no gradient token. It needs a decision in #146 of the same kind the
off-scale values got.

## Anatomy

| Part | Source |
|---|---|
| Layout | `MainLayout extends AppLayout` |
| Drawer: app name + nav | `addToDrawer(createHeader(), new Scroller(createNavigation()))` |
| Navbar: toggle, user menu, switcher | `addToNavbar(new DrawerToggle(), createUserMenu(...))` |
| Nav items | `vaadin-side-nav-item` |
| Section labels | `vaadin-side-nav::part(label)` |

## Tokens used

The three side-nav rules in `aura-theme.css` are **comment-marked as pending #146**. They
are dead once the shell is replaced, and are kept only so the current nav is not left
unstyled in the meantime — remove them **with** #146, not before.

| Part | Token |
|---|---|
| Non-current item icon | `--aura-accent-text-color` |
| Current item background | `--vaadin-background-container` |
| Current item border | `transparent` |
| Section label | `--aura-font-size-xs`, `--vaadin-text-color`, uppercase, `0.03em` tracking |
| Content panel radius | `--aura-app-layout-radius: 12px` — **already matched to the design** |
| Viewport inset | `--aura-app-layout-inset` — Aura default `1.5vmin`, auto-zero below 800px wide or 600px tall |

`--aura-app-layout-radius` is the one shell value already settled, because Aura has a real
property for it and the design's 12px is unambiguous.

## API

Stock `AppLayout`. Navigation is generated from the routes the current user may see.

## States

| State | Behaviour |
|---|---|
| default | drawer open on desktop, collapsed on mobile |
| hover | stock Aura on nav items |
| active | — |
| focus | stock Aura focus ring |
| **current** | `vaadin-side-nav-item[current]` — container background, transparent border |
| disabled | n/a — a route the user cannot reach is not rendered (ADR-0008) |
| error | n/a — routing failures render `ErrorView` / `NotFoundView` inside the shell |

## Cross-references

[`theme-switcher.md`](theme-switcher.md) — lives in the navbar ·
ADR-0017 (the shell), ADR-0008 (route security) ·
**#146** — the redesign that supersedes this file

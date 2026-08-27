# Theme switcher

**Category:** shared Java component
**Status:** settled
**Source:** `com.vaadin.expensemanager.base.ui.ThemeSwitcher`

## Overview

The navbar control letting a user override the colour scheme: follow the OS ("System"), or
force Light / Dark. One instance, in the app shell. Not for per-view use.

## Anatomy

A `MenuBar` with an `ADJUST` icon trigger (`aria-label="Change colour theme"`) and a
submenu of three items: **System**, **Light**, **Dark**.

## Tokens used

Stock Aura menu-bar styling. Nothing overridden.

The switcher **consumes** the theme rather than styling itself: because Aura resolves every
surface colour through `light-dark()` keyed off `color-scheme`, flipping that one property
re-themes the whole UI live, with no page reload.

## API

```java
new ThemeSwitcher()
public static final String STORAGE_KEY = "expense-manager.color-scheme";
```

## States

| State | Behaviour |
|---|---|
| default | the persisted choice is pre-selected |
| hover / active / focus | stock Aura menu-bar |
| disabled | n/a |
| error | n/a |

Scheme behaviour — verified in the browser:

| Choice | Root `color-scheme` | Resolves to |
|---|---|---|
| Dark | inline `dark` | `dark` |
| Light | inline `light` | `light` |
| **System** | inline **cleared** | `light dark` |

## The dependency that will break it

**`ThemeSwitcher` requires `html { color-scheme: light dark }` in the theme stylesheet.**

"System" works by *clearing* the inline `color-scheme` that `Page.setColorScheme` writes,
which lets the stylesheet's declaration apply again. Delete that one line from
`aura-theme.css` and System silently stops resetting to the OS preference — no error,
nothing visibly broken, just a dead menu item.

That is why the declaration is commented in the theme file, and why the acceptance test is
"System resolves to `light dark`" rather than "the line is present".

The choice is persisted per-browser in `localStorage` under `STORAGE_KEY`, and an inline
script in the page head (`Application#configurePage`) re-applies it before first paint so
a reload never flashes the wrong scheme.

## Code example

```java
addToNavbar(new DrawerToggle(), createUserMenu(currentUserProvider));  // holds the switcher
```

## Cross-references

[`app-shell.md`](app-shell.md) ·
[`../foundations/color.md`](../foundations/color.md) ·
[`../tokens/token-reference.md`](../tokens/token-reference.md) — `color-scheme` is listed as set-by-this-app

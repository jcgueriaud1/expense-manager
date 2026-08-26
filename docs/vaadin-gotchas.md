# Vaadin gotchas

Behaviour this project has been bitten by and no config file confesses. Cached here
because looking it up costs a browser probe or a compile error; the one-command
lookups stay in the environment where they cannot go stale.

Layout and spacing rules live in [`theming-layouts.md`](theming-layouts.md); the
Aura-not-Lumo rules live in [`../CLAUDE.md`](../CLAUDE.md). This file holds what
neither covers.

## The Aura token scale

`figma-survey` compares a design's raw pixel values against this table to decide
whether a difference is a **global theming** question or **view styling**. Keeping it
here makes that comparison free and offline — the alternative is a running app, since
Aura ships these as `calc()`/`round()` expressions resolved at render time.

**The formulas are the reference.** They hold whatever the theme inputs are, so they
survive a theme change and go stale only on a Vaadin upgrade.

```
--vaadin-radius-s   = min(0.25lh, round(baseRadius * 1px + 2px, 1px))
--vaadin-radius-m   = round(baseRadius * 2px + 3px, 1px)
--vaadin-radius-l   = round(baseRadius * 1.5px + 10px, 1px)
--vaadin-gap-m      = --vaadin-padding-m = round(baseSize * 0.75 * 1px, 1px)
--aura-font-size-m  = round(baseFontSize / 16 * 1rem, 0.0625rem)
```

Resolved at `--aura-base-radius: 3`, `--aura-base-size: 16`, `--aura-base-font-size: 14`
(all three are the Aura 25.2 defaults):

| Scale | xs | s | m | l | xl |
|---|---|---|---|---|---|
| `--vaadin-radius-*` | — | 5 | **9** | 15 | — |
| `--vaadin-padding-*` / `--vaadin-gap-*` | 4 | 8 | **12** | 16 | 24 |
| `--aura-font-size-*` | 12 | 13 | **14** | 16 | 18 |

Read directly from a running app: every value in the table, and the formulas for
`radius-s/m/l`, `gap-m`/`padding-m` and `font-size-m`. The remaining `padding`/`gap`
and `font-size` steps were measured as values, not as formulas — treat their
multipliers as unverified.

**Two consequences worth knowing before reading a design:**

- **The font scale moves with the theme.** It is a function of
  `--aura-base-font-size`, which this app's committed theme sets to `15`, not the
  `14` above. Compare a design against the resolved row only after checking which
  base the app is on.
- **The scale cannot express every pair of values.** A design asking for a 9 px field
  radius and a 12 px card radius needs `baseRadius = 3` for the first (`2·3+3 = 9`)
  and `baseRadius = 1.33` for the second (`1.5·1.33+10 = 12`) — and at 1.33 the field
  becomes 6 px. No single base satisfies both. When that happens the design has left
  Aura's derivation, and the fix is a global decision (correct the design, or override
  the base-style radius properties directly), never a per-view one. See finding F-064.

**Refresh trigger:** a Vaadin minor upgrade. The formulas are Aura internals with no
compatibility promise, so re-measure with `getComputedStyle(document.documentElement)`
on the running app after every version bump and correct this table.

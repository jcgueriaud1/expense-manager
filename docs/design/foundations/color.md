# Colour

Aura computes almost every colour from a small number of inputs, and this app overrides
**none** of them. The design asks for the Aura defaults, so the theme file declares no
colour at all — every value below is Aura 25.2 stock, verified against the design's own
hex in the browser.

Naming rules (`--aura-*` vs `--vaadin-*`, and the `--lumo-*` ban) are in
[`../../../CLAUDE.md`](../../../CLAUDE.md).

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| `--aura-accent-color-light` | `#3266e4` | `#155dfc` | Aura default (`--aura-blue`) | design | **settled** |
| `--aura-accent-color-dark` | `#3266e4` | `#60A5FA` | Aura default (`--aura-blue`) | design | **settled** |
| Primary button colour | `accent-neutral` `#0a0b0d` / `#ffffff` | accent blue | neutral, scoped to non-tertiary buttons | design | **settled** |
| Tertiary button colour | drawn neutral | accent blue | accent blue — unchanged | app | **settled** |
| `--vaadin-user-color-2` | `#b3329d` | default | default | both agree | **settled** |
| Background colour | no variable bound | default | default | design does not say | **settled** |
| `--aura-contrast-level` | not specified | default (1) | default | both agree | **settled** |

## Resolved values

Verified by canvas probe on the running app, both schemes:

| Token | Light | Dark |
|---|---|---|
| `--aura-accent-color` | `#3266e4` | `#3266e4` |
| `--aura-accent-text-color` | `#2b59c8` | `#81b9ff` |
| `--aura-neutral-light` / `-dark` | `#0a0b0d` | `#feffff` |
| `--vaadin-text-color` | `#0a0b0d` | `#feffff` |
| `--vaadin-user-color-2` | `#b3329d` | `#b3329d` |

The accent is identical in both schemes — that is the design's choice, not an oversight:
its `Accent color` variable aliases `Accent colors/Blue` in both Light and Dark modes.

## Where each colour is used

| Role | Token | Notes |
|---|---|---|
| Filled primary action | `--aura-neutral-*` via the button rule | black in light, white in dark |
| Link text, tertiary actions | `--aura-accent-text-color` | the only blue text in the UI |
| Status badge (draft) | Aura's default badge | renders accent-tinted, i.e. blue |
| Trip card surface, per-diem preview | `--aura-accent-surface`, `--aura-accent-border-color` | marks a trip as the report's headline |
| Expense-type swatch dot | `--aura-blue/green/orange/purple/red/yellow` | cycled by name hash, `ReportViewSupport.categoryColor` |
| Destructive action | `--aura-red`, `--aura-red-text` | |
| Rejected callout | `--aura-red` at 8% fill, 35% border | via `color-mix` |
| Approved callout | `--aura-green` at 8% fill, 30% border | via `color-mix` |

## Rules

- **Never colour alone.** Every status renders its label as text; colour only reinforces
  it (ADR-0020). `ReportViewSupport.statusBadge` carries the label and *adds* a variant.
- **No opacity scale.** Aura has no `Npct` ladder (unlike Lumo's `--lumo-*-10pct`).
  Derive variants with `color-mix(in srgb, var(--aura-red) 8%, transparent)` — the
  pattern already used by the status callouts.
- **Customise with the `-light`/`-dark` suffixed properties; *apply* with the unsuffixed
  one.** `--aura-accent-color` is a `light-dark()` function of the pair, so setting only
  one half leaves the other scheme on the old value.
- **Contrast floor outranks the design** (ADR-0025 decision 3). The primary button
  measures 19.7:1 in light and 12.6:1 in dark.

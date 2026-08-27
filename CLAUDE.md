# Expense Manager

Spring Boot + Vaadin expense-management app (V1). Server-side Java (Flow, no
React/Hilla), real Postgres from day one, deployed as a Docker container.

Orientation: `docs/plan.md` (phased build plan), `docs/glossary.md` (domain
language), `docs/adr/` (architecture decisions, see `docs/adr/README.md`),
`docs/findings.md` (friction/gaps log — a first-class deliverable),
`docs/design/` (the design spec — decided values, token scale, component specs;
consult it before comparing a design's values to the app's),
`docs/vaadin-gotchas.md` (Vaadin behaviour no config confesses).

## Theming — Aura, never Lumo

This app uses the **Aura** theme (`@StyleSheet(Aura.STYLESHEET)`), not Lumo.
Aura and Lumo are separate, incompatible design systems. **Never use `--lumo-*`
CSS custom properties** — they are undefined under Aura, so `getStyle().set(...,
"var(--lumo-...)")` renders nothing, silently (no error). Style with:

- `--aura-*` for Aura-specific tokens: accent colour (`--aura-accent-color`,
  `--aura-accent-surface`), surfaces (`--aura-surface-color`), typography
  (`--aura-font-size-xs`…`--aura-font-size-xl`), shadows (`--aura-shadow-s/m`),
  palette (`--aura-red`, `--aura-red-text`, …).
- `--vaadin-*` base tokens shared across themes: text (`--vaadin-text-color`,
  `--vaadin-text-color-secondary`), borders (`--vaadin-border-color`,
  `--vaadin-border-color-secondary`), backgrounds (`--vaadin-background-color`,
  `--vaadin-background-container`), spacing (`--vaadin-padding-xs…-xl`,
  `--vaadin-gap-xs…-xl` — **sized only**, there is no bare `--vaadin-padding`/
  `--vaadin-gap` for custom CSS, F-030), radius (`--vaadin-radius-s/m/l`).

Aura has no `Npct` opacity scale (unlike Lumo's `--lumo-*-10pct`); derive
variants with `color-mix(in srgb, var(--aura-red) 15%, transparent)`. Look up
exact token names via the Vaadin docs MCP (`get_theme_css_properties theme=aura`).

For component **theme variants**, use the theme-agnostic `ButtonVariant`
constants — `PRIMARY`, `TERTIARY`, `ERROR`, `SUCCESS`, `WARNING`, `SMALL`,
`LARGE` — **not** the legacy `LUMO_*` ones. The `tertiary-inline`, `contrast`,
and `icon` variants are Lumo-only (no effect under Aura); use plain `TERTIARY`
instead. See findings F-013 and F-017.

## Layouts & spacing

**Vaadin layout Java APIs for structure, plain CSS with tokens for the rest.** Never use
`getStyle().set(...)` for anything a layout API covers — spacing/padding go through
`setSpacing("var(--vaadin-gap-m)")` / `setPadding("var(--vaadin-padding-m)")`, flex-grow
through `expand(child)`, scrolling through `Scroller`. Everything the API can't express
(decoration, positioning, truncation, grid, per-element typography/colour) goes into a
scoped, role-named CSS class in `src/main/resources/META-INF/resources/styles.css` using
`--vaadin-*`/`--aura-*` tokens. Full standard: `docs/theming-layouts.md`.

## Agent skills

### Issue tracker

Issues and PRDs live in GitHub Issues, via the `gh` CLI; external PRs are **not**
a triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary — each role's label string equals its name: `needs-triage`,
`needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See
`docs/agents/triage-labels.md`.

### Design source of truth

The visual design lives in [Figma](https://www.figma.com/design/Irsp3cgi1WX3GiLGpJZECa/Expense-Manager?node-id=88-12278)
— file `Irsp3cgi1WX3GiLGpJZECa`, page `88:12278` ("Visual Design"). `/figma-survey`
resolves a view or component name against that page's frames, so the key belongs here
rather than in an issue body.

`.agent-context` at the repo root records the
four preferences the `figma-to-vaadin` skill resolves — layout approach,
architecture, sample data, verification mode — so a run reads them instead of
asking. Change a value there, not in the skill. `figma-to-vaadin` and
`figma-visual-verification` are project-owned copies of upstream skills, with a
`## Provenance` section at the bottom of each; `figma-survey` and `figma-theme`
are this project's own. All need the project-scoped Figma MCP server, see
`DEVELOPMENT.md`.

**The design spec** lives in `docs/design/` and is the **contract**, not a record of
what was built. One skill writes it, and everything else conforms to it:

- `/figma-survey` **writes the spec.** Scoped `theme`, it reads the design's global
  variables across every mode, puts each divergence to you in one pass, and writes
  `docs/design/foundations/` plus the inputs in `tokens/`. Scoped to a view or a
  component, it writes `docs/design/components/` — one file per component, states
  included. It also produces the **delta** for a ticket. It writes no CSS and no
  Java.
- `/figma-theme` **applies** the global theme: it reads the settled spec, writes only
  what differs from the Aura defaults, proves it in the browser, and writes the
  *resolved values* table back — the one part of the spec only a running app can
  produce. It refuses a spec whose rows are still **open**.
- **Implementation conforms.** Take tokens and states from a component's spec file
  rather than choosing values; a difference is a bug in the code. Never edit a spec
  to match what you just built — that turns the contract into a transcript and hides
  the drift. If a component has no spec, or the design moved, run `/figma-survey`.

Order matters: run the `theme` survey, then `/figma-theme`, before any per-view work.
It decides the font family and base size, which reflow every screen, so spacing
tuned against the old scale is thrown away.

### Domain docs

Single-context. Glossary at `docs/glossary.md` (the `CONTEXT.md` equivalent for
this repo), ADRs in `docs/adr/`. See `docs/agents/domain.md`.

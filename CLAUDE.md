# Expense Manager

Spring Boot + Vaadin expense-management app (V1). Server-side Java (Flow, no
React/Hilla), real Postgres from day one, deployed as a Docker container.

Orientation: `docs/plan.md` (phased build plan), `docs/glossary.md` (domain
language), `docs/adr/` (architecture decisions, see `docs/adr/README.md`),
`docs/findings.md` (friction/gaps log — a first-class deliverable).

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

The visual design lives in Figma, and `.agent-context` at the repo root records the
four preferences the `figma-to-vaadin` skill resolves — layout approach,
architecture, sample data, verification mode — so a run reads them instead of
asking. Change a value there, not in the skill. The Figma-facing skills
(`figma-to-vaadin`, `figma-to-aura-theme`, `figma-visual-verification`) are
project-owned copies with a `## Provenance` section at the bottom of each; they
need the project-scoped Figma MCP server, see `DEVELOPMENT.md`.

### Domain docs

Single-context. Glossary at `docs/glossary.md` (the `CONTEXT.md` equivalent for
this repo), ADRs in `docs/adr/`. See `docs/agents/domain.md`.

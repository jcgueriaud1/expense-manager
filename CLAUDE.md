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
  `--vaadin-background-container`), spacing (`--vaadin-padding`, `--vaadin-gap`),
  radius (`--vaadin-radius-m/l`).

Aura has no `Npct` opacity scale (unlike Lumo's `--lumo-*-10pct`); derive
variants with `color-mix(in srgb, var(--aura-red) 15%, transparent)`. Look up
exact token names via the Vaadin docs MCP (`get_theme_css_properties theme=aura`).

For component **theme variants**, use the theme-agnostic `ButtonVariant`
constants — `PRIMARY`, `TERTIARY`, `ERROR`, `SUCCESS`, `WARNING`, `SMALL`,
`LARGE` — **not** the legacy `LUMO_*` ones. The `tertiary-inline`, `contrast`,
and `icon` variants are Lumo-only (no effect under Aura); use plain `TERTIARY`
instead. See findings F-013 and F-017.

## Agent skills

### Issue tracker

Issues and PRDs live in GitHub Issues, via the `gh` CLI; external PRs are **not**
a triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary — each role's label string equals its name: `needs-triage`,
`needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See
`docs/agents/triage-labels.md`.

### Domain docs

Single-context. Glossary at `docs/glossary.md` (the `CONTEXT.md` equivalent for
this repo), ADRs in `docs/adr/`. See `docs/agents/domain.md`.

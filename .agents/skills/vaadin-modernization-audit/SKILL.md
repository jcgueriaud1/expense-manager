---
name: vaadin-modernization-audit
description: "Audit a Vaadin project for modernization opportunities and emit independent, session-ready suggestions (report only, never edits) — custom components that official Vaadin components now cover, and old patterns (manual UI state sync, UI.access pushes, listener chains, executeJs bridges) that newer capabilities like Signals and the typed Browser APIs simplify. Use when the user wants to audit or modernize a Vaadin codebase, asks 'what should we update', has just upgraded a Vaadin version (e.g. 24→25) and wants to know what's now possible, or asks whether a custom component has an official equivalent."
---

# Vaadin Modernization Audit

Audit a Vaadin project against the capabilities of its **installed** Vaadin version and emit a list of independent, actionable findings. Each finding is a self-contained work unit that can be implemented in its own agent session.

**Prime directive: suggest, never implement.** The output of this skill is a report. Do not modify project code while running the audit, even if a fix looks trivial.

## Inputs and scope

Two audit modes:

- **Full audit** (default, or explicit "audit the codebase"): scan the whole project against the full capability catalog for the installed version.
- **Delta audit** (after a version upgrade): scope the catalog to capabilities introduced *between the previous and current version*. Use this mode when the user mentions a recent upgrade or when invoked from a migration workflow. Delta audits produce shorter, timelier reports — prefer them when applicable.

## Procedure

### 1. Establish versions

Read the effective Vaadin version from the project (`pom.xml` / Gradle build, resolving BOM/property indirection — check the parent and `vaadin.version` property). Cross-check against `get_supported_vaadin_versions` / `get_latest_vaadin_version` so you know where the installed version sits. For a delta audit, also establish the previous version (from the user, git history of the build file, or migration context).

### 2. Fetch the capability catalog

The catalog has two halves, sourced differently:

- **Components** — authoritative and version-pinned from the Vaadin MCP tools, never from memory. `get_components_by_version <installed>` lists every official component in the installed version — this is the source of truth for *what exists*. For a delta audit, diff it against `get_components_by_version <previous>` so the catalog is exactly what the upgrade added.
- **Patterns** (Signals, Browser APIs replacing `executeJs`, Grid lazy-loading, theming renewal) are not components and no tool enumerates them. Use the hand-curated pattern entries in `references/capability-catalog.md`; `references/pattern-entry-signals.md` is the reference example — read it before evaluating any pattern-tier finding so you calibrate on the depth of "supersedes" vs "not superseded" reasoning.

`references/capability-catalog.md` also lists the common hand-rolled widgets worth checking (badge, card, avatar, …) with their official counterparts — a **search heuristic, not an authority**: confirm every match against the tools above. Pull each finding's Learn link from `search_vaadin_docs` or the component's docs page — a finding without a Learn link is incomplete.

### 3. Read the exceptions file

If `.vaadin/modernization.md` exists, read it. It lists deviations the team has already ruled on. Never emit a finding that matches a listed exception. Format: see `references/exceptions-file.md`.

### 4. Inventory the project

Build two inventories by exploring the code (grep/glob — no special tooling needed):

- **Custom UI components**: classes extending `Component`, `Composite`, or composing Vaadin components into reusable widgets, excluding generated code and views themselves.
- **Pattern candidates**: for each pattern entry in the catalog, search for its "supersedes" shapes (the entry tells you what to look for — e.g. `ValueChangeListener`, `UI.access`, update/refresh helper methods, `executeJs` calls to browser web-platform APIs).

### 5. Match with judgment

This is where you earn your keep. For each custom component: read its code, infer its **purpose from context** (what it renders, how it's used at call sites, its name is a hint but not the truth), and compare against the component catalog semantically. A class called `StatusChip` that renders a colored label is a Badge candidate even though no string matches.

For each pattern candidate: read the surrounding code and check it against the pattern entry's supersedes/not-superseded lists. Respect the negative space — the "do not flag" list in each entry exists because false positives destroy trust in this audit.

When intent is genuinely ambiguous, either skip it or emit a low-confidence finding — never guess confidently.

### 6. Emit findings

One concern per finding. Never bundle a component swap with a pattern migration; never emit a codebase-wide architectural finding. Scoping rules:

- **Component findings**: one per custom component (all its usages counted within the single finding).
- **Pattern findings**: one per bounded unit (a view or component class), even if the same shape repeats in fifty views. If it repeats, emit the 2–3 clearest instances as findings, note the repetition in the report summary, and recommend a pilot.
- If the exceptions file records an established pattern (e.g. "signals: adopted, pattern in DashboardView"), reference it in the finding as the pattern to follow.

Use the finding schema in `references/finding-schema.md`. Key fields: what/where/why-now, risk tier, verification requirement, confidence with rationale, and a **session-ready prompt** — except for findings involving threading/locking semantics, which get "human review recommended" instead of a prompt.

### 7. Render the report

Render findings as a self-contained **HTML report** written outside the repo, following `references/html-report.md` — read it before rendering. Core display rules: findings grouped by tier, one card per finding with a badge row (tier / confidence / risk / decision-required / review-only), **before/after code panels as the centrepiece** (real project code, ≤15 lines each), one-sentence Problem and Solution, Wins bullets ≤6 words, collapsed copyable session prompt. End with a **Top recommendation** card, the **Skipped** table, and the next-steps footer. If HTML output isn't feasible in the environment, use the markdown skeleton in `references/finding-schema.md` — same data, plainer rendering.

Tiers:

- **Tier 1 — Component modernization**: custom X where official X exists. Mechanical, high confidence.
- **Tier 2 — Pattern modernization**: architectural simplification (e.g. Signals). Behavioral change, stated confidence, verification required.

### 8. Selection loop

After delivering the report, ask which findings the user wants to act on. Then:

- **Accepted** → hand over the finding's session-ready prompt; remind them it runs in a **fresh session**, one finding per session — never implement in bulk here.
- **Rejected with a load-bearing reason** (a reason a future audit would need in order to not re-suggest it — "the animation is a product requirement", "diverges deliberately") → offer to record it in `.vaadin/modernization.md`. Only offer for durable reasons; skip ephemeral ones ("not right now") and self-evident ones.
- **Tier 2 pattern accepted as a pilot** → after the pilot session succeeds, an `Adopted patterns` entry in the exceptions file lets future audits reference it as the established pattern.

## Confidence calibration

Confidence and risk are **separate axes** — never let risk policy distort a confidence rating. Confidence says how sure you are the finding is real; risk says how carefully it must be executed.

- **High**: pure shape match — custom component with clear single purpose matching an official one; derived-state recomputation with no side effects.
- **Medium**: mixed concerns — listeners that sync state *and* perform side effects (the refactor must split them); custom components with extra behavior beyond the official equivalent (name the delta explicitly).
- **Low**: genuinely ambiguous intent, or the capability is below its maturity gate for the installed version.

Risk classes: **Mechanical** / **Behavioral** / **Threading**. Threading-risk findings (touching `UI.access`, executors, locking, session affinity) still get a session prompt, but the prompt MUST begin with a **human checkpoint**: a short list of threading assumptions the user confirms before the agent session starts (e.g. "confirm the poll runs off the request thread and relies on session affinity"). Never emit a threading-risk prompt without the checkpoint.

Before emitting any **Tier 1 finding**, verify the claimed official component/API actually exists in the installed version — via `get_component_java_api <name> <installed>` (or its presence in `get_components_by_version`), not from memory or the seed list. An audit that hallucinates an official API once is never trusted again.

**Preview components** (e.g. Breadcrumbs) are behind a feature flag and may change or be removed in any release — confirm status via the docs (`search_vaadin_docs` shows the "Preview Feature" admonition and the flag name). A Preview swap is never a mechanical win: cap its confidence at **medium**, mark the finding **Preview** (the report renders a Preview warning badge), and state in the finding that adopting it requires enabling the feature flag and accepting possible breaking changes. Never present a Preview component as a stable, drop-in replacement.

## What this skill must never do

- Implement or edit project code (report only).
- Emit a finding covered by the exceptions file.
- Flag anything on a pattern entry's "not superseded" list (e.g. Binder is not replaced by Signals).
- Bundle multiple concerns into one finding.
- Suggest a capability newer than the installed Vaadin version.

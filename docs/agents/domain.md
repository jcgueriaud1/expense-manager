# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

**This repo is single-context.** Its glossary lives at **`docs/glossary.md`** (not a root `CONTEXT.md`), and its ADRs live in **`docs/adr/`**. Wherever the skills' conventions say `CONTEXT.md`, read `docs/glossary.md` here.

## Before exploring, read these

- **`docs/glossary.md`** — the project's ubiquitous language (the `CONTEXT.md` equivalent for this repo).
- **`docs/adr/`** — read ADRs that touch the area you're about to work in. Start from `docs/adr/README.md` (the index).
- **`docs/plan.md`** — the phased build plan; **`docs/findings.md`** — the living friction/gaps log (a first-class deliverable of this project).

If any of these files don't exist for a given area, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates and extends them lazily when terms or decisions actually get resolved.

## File structure

Single-context layout (this repo):

```
/
├── docs/
│   ├── glossary.md          ← ubiquitous language (CONTEXT.md equivalent)
│   ├── plan.md              ← phased build plan
│   ├── findings.md          ← friction/gaps log
│   └── adr/
│       ├── README.md        ← ADR index
│       ├── 0001-…​.md
│       └── …​
└── src/
```

There is no `CONTEXT-MAP.md`; this is not a multi-context repo. If one is ever added, it would point at per-context glossaries and per-context `src/<context>/docs/adr/` directories.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `docs/glossary.md` — e.g. **Expense Report**, **Expense Line**, **Expense Type**, **VAT Rate**, **Status Change**, **Report Status**. Don't drift to synonyms the glossary avoids (e.g. "category" was retired in favour of **Expense Type**, ADR-0018).

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0019 (whole-aggregate save model) — but worth reopening because…_

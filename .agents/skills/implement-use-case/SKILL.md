---
name: implement-use-case
description: "Implement a GitHub ticket end-to-end (code, tests, verify, commit). Use when asked to implement, build, or work on a use case or issue with a known solution. Not for diagnosing a bug without a fix in hand (diagnosing-bugs), reviewing existing work (code-review), or throwaway exploration (prototype)."
argument-hint: "[ticket number]"
---

Implement the use case described in the given GitHub ticket. These steps are a checklist, not a rigid pipeline: two orderings are load-bearing — pin the acceptance criteria before writing code (Step 1), and have a green suite on a feature branch before committing (Steps 3, 7). Steps 2–4 otherwise interleave; loop them (Step 5) until the criteria are met.

### Step 1: Read the ticket
- Fetch the issue with `gh issue view <number>` and pin down its acceptance criteria before writing any code. The work is done when it satisfies them.

### Step 2: Write the code
- When even slightly unsure about Vaadin API usage, component behavior, theme variables, styling, or best practices, look it up on the Vaadin MCP server before writing. Rely on the docs, not memory, for Vaadin specifics.

### Step 3: Write and run automated tests
- Cover the main flow, alternative flows, business rules, and services with the fast browserless / Vitest / `@SpringBootTest` mechanisms. Prove as much behaviour as possible here — it is far cheaper than driving a browser. Done when the suite proves the ticket's acceptance criteria, not merely when it is green.

### Step 4: Visually verify with Playwright MCP
- **First decide whether a visual check is warranted — it often isn't.** Skip this step when the change adds no new layout, styling, rendering, or component: pure seed/reference data (e.g. a new Flyway-seeded row), config values, or copy tweaks. Such changes render through existing renderers the browserless suite (Step 3) already asserts, so screenshots add no signal — and driving the browser is the costliest part of the loop. There, a green suite plus a direct data check (a SQL query against the DB, or `flyway validate`) *is* the verification; note in the findings that you skipped Playwright and why.
- Otherwise — when the ticket touches layout, spacing, rendering, or a new/changed component — use Playwright MCP for what automated tests can't reach. Follow the [`visual-verification`](../visual-verification/SKILL.md) skill — it covers cheap setup (seed the `local` fixtures instead of clicking through them, deep-link with stable selectors over snapshots) and the visual validation rules. Start the app, navigate every route the ticket touches, screenshot each, confirm it renders correctly, fix what you find, then move on.

### Step 5: Iterate
- Loop on Steps 2–4 until the ticket's acceptance criteria are met, the suite is green, and — where Step 4 applied — every route renders cleanly. Prefer a great result over a fast one.

### Step 6: Log findings
- `docs/findings.md` (a first-class project deliverable) logs friction with the **tooling and platform**, not defects in the app being built. Log two kinds, using its `F-NNN` template: (a) the **agent tooling** — a skill or MCP server that was missing, wrong, or misleading; and (b) **Vaadin itself** — a missing, misleading, or broken API.
- Do **not** log bugs in the application under development (e.g. a wrong VAT rate, a broken flow); those are product issues — fix them or open a ticket, they are not findings.

### Step 7: Commit
- Branch off `main` if you aren't already on a feature branch, then commit the work.

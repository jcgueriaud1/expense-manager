---
name: implement-use-case
description: "Implement a GitHub ticket end-to-end. Use when asked to implement, build, or work on a use case or issue."
argument-hint: "[ticket number]"
---

Implement the use case described in the given GitHub ticket. The steps below run in order — finish each before starting the next.

### Step 1: Read the ticket
- Fetch the issue with `gh issue view <number>` and pin down its acceptance criteria before writing any code. The work is done when it satisfies them.

### Step 2: Write the code
- When even slightly unsure about Vaadin API usage, component behavior, theme variables, styling, or best practices, look it up on the Vaadin MCP server before writing. Rely on the docs, not memory, for Vaadin specifics.

### Step 3: Write and run automated tests
- Cover the main flow, alternative flows, business rules, and services with the fast browserless / Vitest / `@SpringBootTest` mechanisms. Prove as much behaviour as possible here — it is far cheaper than driving a browser. Done when the suite is green.

### Step 4: Visually verify with Playwright MCP
- Automated tests already cover behaviour, so use Playwright MCP for what they can't reach: layout, spacing, rendering, and visual appearance. Follow the [`visual-verification`](../visual-verification/SKILL.md) skill — it covers cheap setup (seed the `local` fixtures instead of clicking through them, deep-link with stable selectors over snapshots) and the visual validation rules. Start the app, navigate every route the ticket touches, screenshot each, confirm it renders correctly, fix what you find, then move on.

### Step 5: Iterate
- Loop on Steps 2–4 until the ticket's acceptance criteria are met, the suite is green, and every route renders cleanly. Prefer a great result over a fast one.

### Step 6: Log findings
- Record any friction or gap hit along the way in `docs/findings.md` (a first-class project deliverable) using its `F-NNN` template.

### Step 7: Commit
- Branch off `main` if you aren't already on a feature branch, then commit the work.

---
name: implement-use-case
description: "Implement a GitHub ticket end-to-end. Use when asked to implement, build, or work on a use case or issue with a known solution. Not for diagnosing a bug without a fix in hand (diagnosing-bugs), reviewing existing work (code-review), or throwaway exploration (prototype)."
argument-hint: "[ticket number]"
---

Implement a GitHub ticket end-to-end. The **acceptance criteria** are the spine:
pin them first, aim every test at them, stop only when they are met.

## Steps

1. **Pin the acceptance criteria.** `gh issue view <number>`; restate what "done"
   means before writing code. Done when you can name each criterion the change
   must satisfy.
2. **Build and prove, in a loop.** Implement, then prove the behaviour in the fast
   test layer (browserless / Vitest / `@SpringBootTest`) — cheaper than a browser,
   so carry as much as possible here. Loop until the suite proves every acceptance
   criterion; a green suite that doesn't is not done.
3. **Visually verify — only when appearance changed.** Browserless tests prove a
   component is present and behaves; they cannot judge spacing, alignment,
   overflow, colour, or responsive layout. So visually verify exactly when the
   change alters something only the eye can catch — new or restyled layout/
   component, a changed theme token, a field added to a form. When it doesn't —
   data, logic, or config rendered through existing, already-styled UI — skip it:
   the green suite plus a direct check (SQL query, `flyway validate`) is the
   verification.

   When you visually verify, invoke the `vaadin-playwright-screenshot` skill to
   capture one screenshot per criterion across every route the ticket touches,
   then the `visual-verdict` skill to score them against the reference.

   **Verify in a separate context** — a delegated agent or sub-session, where the
   harness offers one — briefed with the ticket number, the routes, and a note on
   what changed. Verifying costs an app boot, a temporary capture test, a build
   run and a set of full-page images, none of which the implementation context
   needs; what it needs back is the verdict JSON, the issues and the image paths.
   Where no delegation is available, verify inline and expect to pay that cost.

   **Loop:** if the verdict scores below the 90 threshold, fix what its
   `differences[]`/`suggestions[]` call out in the implementation context — the
   verifier reports, it does not edit code — then re-verify by continuing the
   same verification context rather than opening a fresh one: its app is still
   running and its capture test still written. Repeat until it clears 90 — a
   low-scoring verdict is not done. Open at most the one screenshot you need to
   see a reported issue for yourself. Keep the final passing screenshots and
   verdict; they go on the PR in step 5.
4. **Commit** on a feature branch off `main`.
5. **Open the PR, then attach the visual proof.** After the PR is created, commit
   the passing screenshots into `docs/screenshots/` (name each by its view; if a
   screenshot of the same view exists from a previous issue, overwrite it), then
   post the final `visual-verdict` JSON as a separate `gh pr comment` linking to
   them. Skip only when the change had nothing to visually verify (step 3 was
   skipped).

## While you work

- **Vaadin specifics come from the docs, not memory.** Unsure about an API,
  component, theme token, or styling — look it up on the Vaadin MCP server.
- **Findings log tooling friction only.** In `docs/findings.md` (`F-NNN`): friction
  with the agent tooling (a skill or MCP server that was missing, wrong, or
  misleading) or with Vaadin (a missing, misleading, or broken API). A bug in the
  app you're building (e.g. a wrong VAT rate) is a product ticket, not a finding.

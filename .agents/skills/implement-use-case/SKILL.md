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
   verification. When you visually verify, invoke
   `vaadin-playwright-screenshot@dramafinder` to generate screenshots of every
   route the ticket touches, then invoke the `visual-verdict` skill to validate
   them against the reference. **Loop:** if the verdict scores below the 90
   threshold, fix the issues its `differences[]`/`suggestions[]` call out,
   re-screenshot, and re-run `visual-verdict`. Repeat until it clears 90 — a
   low-scoring verdict is not done. Keep the final passing screenshots and
   verdict; they go on the PR in step 5.
4. **Commit** on a feature branch off `main`.
5. **Open the PR, then post the visual proof as a separate comment.** After the
   PR is created, add the final `visual-verdict` JSON and the passing
   screenshots as their own PR comment (`gh pr comment`) — separate from the PR
   description — so the visual sign-off is reviewable on its own. Skip only when
   the change had nothing to visually verify (step 3 was skipped).

## While you work

- **Vaadin specifics come from the docs, not memory.** Unsure about an API,
  component, theme token, or styling — look it up on the Vaadin MCP server.
- **Findings log tooling friction only.** In `docs/findings.md` (`F-NNN`): friction
  with the agent tooling (a skill or MCP server that was missing, wrong, or
  misleading) or with Vaadin (a missing, misleading, or broken API). A bug in the
  app you're building (e.g. a wrong VAT rate) is a product ticket, not a finding.

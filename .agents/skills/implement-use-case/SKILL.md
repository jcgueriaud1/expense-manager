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
   verification. When you visually verify, follow the
   [`visual-verification`](../visual-verification/SKILL.md) skill and view every
   route the ticket touches.
4. **Conform to the design spec — don't rewrite it.** When the change touches a
   component's appearance, `docs/design/` is the contract: take the tokens and
   states from the component's file rather than choosing values, and treat a
   difference as a bug in the code, not in the spec. Where no file exists for a
   component you are building, or the design has moved, the spec is out of date —
   run [`figma-survey`](../figma-survey/SKILL.md), which owns it. Never hand-write
   or edit a spec file to match what you just built; that turns the contract into a
   transcript and the drift becomes invisible.
5. **Commit** on a feature branch off `main`.

## While you work

- **Vaadin specifics come from the docs, not memory.** Unsure about an API,
  component, theme token, or styling — look it up on the Vaadin MCP server.
- **Findings log tooling friction only.** In `docs/findings.md` (`F-NNN`): friction
  with the agent tooling (a skill or MCP server that was missing, wrong, or
  misleading) or with Vaadin (a missing, misleading, or broken API). A bug in the
  app you're building (e.g. a wrong VAT rate) is a product ticket, not a finding.

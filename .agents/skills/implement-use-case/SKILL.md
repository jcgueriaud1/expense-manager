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
4. **Design spec — conform or author, depending on origin.** When the change
   touches a component's appearance, check `docs/design/components/` and let the
   spec's **Origin** decide which way authority runs.
   - **`design` origin** — the spec is the contract. Take tokens and states from it
     rather than choosing values; a difference is a bug in *your* code. **Never edit
     the spec to match what you built** — that turns the contract into a transcript
     and makes the drift invisible. Where the design has moved, or a component you
     are building has no file, run
     [`figma-survey`](../figma-survey/SKILL.md), which owns design-origin specs.
   - **`code` origin, or a new shared component the design never drew** — an error
     summary, an empty state, a dialog scaffold you are inventing to satisfy an
     ADR — the code is the source, so **you** author the spec, in this change.
     There is no design to survey. Follow the template in
     `docs/design/components/README.md`, and account for all six states with `n/a`
     plus a reason where one cannot occur.
   - Either way, leave **Implementation** for the audit. Asserting `conforms` about
     your own change is asserting something you did not independently check.
5. **Commit** on a feature branch off `main`.

## While you work

- **Vaadin specifics come from the docs, not memory.** Unsure about an API,
  component, theme token, or styling — look it up on the Vaadin MCP server.
- **Findings log tooling friction only.** In `docs/findings.md` (`F-NNN`): friction
  with the agent tooling (a skill or MCP server that was missing, wrong, or
  misleading) or with Vaadin (a missing, misleading, or broken API). A bug in the
  app you're building (e.g. a wrong VAT rate) is a product ticket, not a finding.

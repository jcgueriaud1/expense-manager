# ADR-0012 — Test pyramid layers 1–3, OAuth form-stub

**Status:** Accepted

## Context
"Verification" is a first-class finding area in the brief. Google OAuth cannot
be driven in automated tests, which is a known friction point.

## Decision
Adopt a deliberate pyramid, heavy at the bottom:
1. **Domain unit tests** — pure JUnit, no Spring. State machine
   (submit/approve/reject/resubmit guards) and the allowance calculator.
2. **Service + persistence integration** — `@SpringBootTest` slices on
   **Testcontainers Postgres** with Flyway applied, including a **method-security
   slice** (a `USER` cannot call `approve()`).
3. **Vaadin UI tests** — `browserless-test-spring` (already in the pom):
   server-side view tests (validation fires; wrong-role user doesn't see admin
   actions) without a browser.

**No automatic end-to-end (browser) test in V1.** Manual verification of the
golden path is done ad hoc (Playwright MCP / Vaadin Copilot available).

For dev and test, a **`local`/`test` Spring profile swaps Google OAuth for a
local form-login stub** with the same authorities and user records. Real Google
OAuth runs only in `staging`/`prod` (ADR-0013).

## Consequences
- Fast feedback on the rules most likely to be wrong.
- The OAuth-in-test friction is documented as a finding; automatic E2E is a
  later decision when the stub/mock-OIDC approach is proven.

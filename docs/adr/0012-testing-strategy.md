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

**Form-stub fidelity (Phase 1.3):** the stub is `formLogin` + a JPA-backed
`UserDetailsService` over the same `User` records (login by email + a dev
password from an env var with a safe local default); authorities come from local
roles, so enforcement is identical to prod. A couple of `local`-profile users
(the seeded admin + a plain user) are seeded so there is someone to log in as.
**No mock OIDC server in V1.** The stub deliberately **bypasses the
`OidcUserService`**, so provisioning/claim/gating is *not* exercised by clicking
through `local` — that logic is covered by **direct integration tests** invoking
the custom `OidcUserService` with synthesized claims (claim-by-email,
new-user-create, wrong-domain, unverified-email, disabled-user). Method-security
slices use Spring Security's test authentication, not a real login. The
"provisioning not exercised in manual local use" gap is the accepted cost of not
running a mock OIDC server; revisit with `mock-oauth2-server` if provisioning
regressions slip through.

## Testcontainers harness (Phase 0.9)
- A single abstract `AbstractIntegrationTest` (test scope, in `base/`) holds a
  **singleton** `PostgreSQLContainer`: `static`, started once per JVM run, never
  explicitly stopped (Ryuk reaps it), shared by every integration test class.
- Wiring is Spring Boot 4 **`@ServiceConnection`** on the container — no manual
  `@DynamicPropertySource` datasource plumbing. Flyway migrates the container
  once on boot.
- **`.withReuse(true)`** keeps the container alive between local `./mvnw test`
  runs for fast TDD loops (ignored/disabled in CI).
- **Default state isolation: `@Transactional` rollback per test method.**
  Explicit truncation (or a committed-state setup) is the **documented
  exception**, reserved for tests that require committed state across
  transactions — notably `@Version` / optimistic-lock behaviour (ADR-0011),
  which rollback cannot exercise.

## Consequences
- Fast feedback on the rules most likely to be wrong.
- The OAuth-in-test friction is documented as a finding; automatic E2E is a
  later decision when the stub/mock-OIDC approach is proven.
- One shared container makes the suite fast but means tests must not rely on a
  pristine DB per class; the rollback default enforces this, and the truncation
  exceptions are called out explicitly so the shared-state assumption stays
  honest.

# Findings Log

Living backlog of friction and gaps, per the brief's taxonomy. This is the main
output of the project — every non-trivial work item should leave findings here.

**Areas:** Spec · AI · Vaadin · Tooling/Template · Docs · Verification ·
Deployment/Observability · UX-spec
**Severity:** Low · Medium · High

## Template

```
### F-NNN — <title>
- Date:
- Area:
- Severity:
- Task being attempted:
- Expected vs actual:
- Workaround used:
- Evidence: (prompt / screenshot / code pointer / log / failing test / URL)
- Impact:
- Suggested Vaadin/product improvement:
- Owner / next step:
```

---

### F-001 — Skeleton Java version mismatch (pom 25 vs Dockerfile 21)
- Date: 2026-07-08
- Area: Tooling/Template
- Severity: Medium
- Task being attempted: Reviewing the generated start.vaadin.com skeleton before
  building.
- Expected vs actual: Expected a consistent JDK target. Actual: `pom.xml` sets
  `java.version=25` while `Dockerfile` builds on `eclipse-temurin:21-jdk` and
  runs on `eclipse-temurin:21-jre-alpine`.
- Workaround used: Standardize on Java 25; bump Dockerfile base images (ADR-0014).
- Evidence: `pom.xml:11`, `Dockerfile` (build + runtime stages).
- Impact: "Works locally, breaks in container" class of surprise; silent until
  a Java 25 feature is used.
- Suggested Vaadin/product improvement: start.vaadin.com should keep the
  Dockerfile base image in sync with the pom's `java.version`.
- Owner / next step: Resolved in Phase 0.1 (#4) — Dockerfile build/runtime stages
  bumped to `eclipse-temurin:25-jdk` / `eclipse-temurin:25-jre-alpine` to match the
  pom's Java 25 target.

### F-002 — StreamResource deprecated in Vaadin 25 (streaming API for receipts)
- Date: 2026-07-08
- Area: Docs
- Severity: Low
- Task being attempted: Deciding how to stream receipt bytes to/from the UI.
- Expected vs actual: `StreamResource` is the widely-documented approach but is
  deprecated in Vaadin 25; the current API is `DownloadHandler`/`UploadHandler`
  (`com.vaadin.flow.server.streams`).
- Workaround used: Use the current streaming API; avoid `StreamResource`
  (ADR-0009).
- Evidence: user guidance; Vaadin 25.2 streaming docs.
- Impact: AI agents and older docs/examples will reach for the deprecated class.
- Suggested Vaadin/product improvement: ensure MCP/docs surface the replacement
  prominently on any `StreamResource` reference.
- Owner / next step: confirm exact API usage when implementing receipts.

### F-003 — Finnish VAT rates are statutory and change most years
- Date: 2026-07-09
- Area: Spec
- Severity: Medium
- Task being attempted: Deciding how expense lines capture VAT (Phase 2, ADR-0018).
- Expected vs actual: Assumed a stable reduced rate of 14%; actual 2026 reduced
  rate is **13.5%** (14% → 13.5% on 1 Jan 2026; general 24% → 25.5% on 1 Sept
  2024; several 10% supplies → 14% on 1 Jan 2025). Rates move roughly yearly.
- Workaround used: Model `VatRate`/`ExpenseType` as admin-editable config with an
  `active` flag rather than enums; lines store an FK to the rate they were filed
  under, so past reports never re-compute when the law changes (ADR-0018).
- Evidence: user (authoritative); Verohallinto VAT-rate change history.
- Impact: Hard-coded rates would be a money bug (ADR-0010) and a redeploy-per-law-
  change maintenance burden; per-year versioning would be over-engineering given
  the FK-preserves-history approach.
- Suggested Vaadin/product improvement: n/a (domain/spec finding).
- Owner / next step: **Resolved (2026-07-10, Phase 2.1 #22).** V3 migration seeds
  the 2026 figures **25.5 / 13.5 / 10 / 0** (general 25.5 %; reduced 13.5 %, i.e.
  the 14 % → 13.5 % change of 1 Jan 2026; reduced 10 %; zero-rated), with the six
  expense types mapped to those rates. Values held authoritative per the issue
  author (Finnish tax domain). Rate changes ship as deactivate-old + add-new, never
  an in-place value edit, so filed lines keep their original rate (ADR-0018).

### F-005 — Testcontainers 2.x renamed the Maven modules (`testcontainers-*` prefix)
- Date: 2026-07-09
- Area: Tooling/Template
- Severity: Low
- Task being attempted: Adding the Testcontainers Postgres deps for the
  integration test layer (Phase 0.2, ADR-0004).
- Expected vs actual: The issue and virtually all docs/examples use the 1.x
  coordinates `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`.
  Spring Boot 4.1.0 manages **Testcontainers 2.0.5**, which renamed the modules
  to `testcontainers-postgresql` and `testcontainers-junit-jupiter` (same
  `org.testcontainers` groupId). The 1.x artifactIds resolve to no managed
  version → `'dependencies.dependency.version' ... is missing` at POM read time.
- Workaround used: Use the `testcontainers-*`-prefixed artifactIds; versions come
  from the `testcontainers-bom` already imported by `spring-boot-dependencies`
  (no explicit BOM import needed).
- Evidence: `pom.xml` test deps; `testcontainers-bom-2.0.5.pom`.
- Impact: AI agents and copied snippets will reach for the 1.x names and hit a
  confusing build-model error rather than a normal dependency-resolution failure.
- Suggested Vaadin/product improvement: n/a (Testcontainers/Spring Boot ecosystem).
- Owner / next step: wire the actual Testcontainers harness in Phase 0.9.

### F-006 — Security starter added early activates default Spring Security
- Date: 2026-07-09
- Area: Tooling/Template
- Severity: Low
- Task being attempted: Adding `spring-boot-starter-security` +
  `-oauth2-client` in Phase 0.2 so later phases have their deps ready, before
  the security config exists (Phase 1.4).
- Expected vs actual: Expected the foundation app to boot open. Actual: the mere
  presence of the security starter (plus a Google `ClientRegistration` from the
  `local` env-var defaults) activates Spring Security's default filter chain, so
  every endpoint — including `/actuator/health` — returns `302` to a login page
  until the Phase 1.4 config lands.
- Workaround used: Accepted for this slice — Phase 0.2's acceptance is "boots and
  connects to Postgres", which holds (verified: Hikari connected to the Compose
  Postgres, Tomcat up, no errors). No `SecurityConfig` written here; that is
  Phase 1.4's job, and Actuator health is opened/wired in Phase 0.8.
- Evidence: boot log (`HikariPool-1 ... PgConnection`, `Started Application in
  35.9s`); `curl /actuator/health` → 302.
- Impact: Anyone probing the foundation app before Phase 1 sees a login wall;
  health checks won't pass until 0.8/1.4. Ordering artifact, not a defect.
- Suggested Vaadin/product improvement: n/a.
- Owner / next step: open `/actuator/health` (0.8) and wire
  `VaadinSecurityConfigurer` + route/method security (1.4).

### F-007 — Spring Boot 4 modular auto-config: Flyway silently doesn't run, actuator/test classes relocated
- Date: 2026-07-09
- Area: Tooling/Template
- Severity: Medium
- Task being attempted: Wiring the Flyway baseline, health-probe security, and
  the Testcontainers acceptance test (Phase 0.3, ADR-0005/0012/0013).
- Expected vs actual: Spring Boot 4.0 split the monolithic
  `spring-boot-autoconfigure` into per-technology modules, and the starters no
  longer pull every autoconfig transitively. Two concrete bites:
  1. **Flyway ran nowhere.** `flyway-core` + `flyway-database-postgresql` were on
     the classpath (Phase 0.2), but the JPA starter no longer brings Flyway's
     autoconfiguration, which now lives in a separate `spring-boot-flyway`
     module. The result is *silent*: no `Flyway` bean, no migration, and the
     context still boots because there are no entities for `ddl-auto=validate` to
     check — so "app starts fine" masks "schema was never migrated". Only the
     acceptance test (no `flyway_schema_history`, no `Flyway` bean to autowire)
     surfaced it.
  2. **Classes relocated across new modules.** `EndpointRequest` →
     `org.springframework.boot.security.autoconfigure.actuate.web.servlet`
     (module `spring-boot-security`); `HealthEndpoint` →
     `org.springframework.boot.health.actuate.endpoint` (module
     `spring-boot-health`); `@AutoConfigureMockMvc` moved to module
     `spring-boot-webmvc-test`, which `spring-boot-starter-test` does **not** pull.
- Workaround used: Added `spring-boot-flyway` explicitly. Used the new package
  coordinates for `EndpointRequest`/`HealthEndpoint`. Avoided `@AutoConfigureMockMvc`
  (module absent) by building `MockMvc` by hand from the `WebApplicationContext`
  plus the `springSecurityFilterChain` `FilterChainProxy` (both on the `spring-test`
  classpath) — this exercises the real security filter chain without adding a
  test dependency.
- Evidence: `NoSuchBeanDefinitionException: ... org.flywaydb.core.Flyway`;
  after the fix, boot log `Migrating schema "public" to version "1 - init"` /
  `now at version v1`; `FoundationAcceptanceTest` green (3/3).
- Impact: The most dangerous failure mode is #1 — a missing autoconfig module
  fails open (no error, no migration) rather than closed. Any Boot 4 phase that
  assumes "starter X ⇒ autoconfig X" (like pre-4 muscle memory / copied snippets)
  can ship a broken foundation that still starts. An integration test that
  actually asserts the behaviour is the only reliable guard.
- Suggested Vaadin/product improvement: n/a (Spring Boot 4 ecosystem change);
  worth a note in project onboarding that Boot 4 autoconfig is opt-in per module.
- Owner / next step: none — resolved in Phase 0.3. Watch for the same fail-open
  pattern when later phases add tech that needs its own `spring-boot-*` module.

### F-004 — Inline Grid row editor + ComboBox + Binder + Signals (provisional)
- Date: 2026-07-09
- Area: Vaadin
- Severity: Low (provisional — confirm on implementation)
- Task being attempted: Designing the line editor for the report detail view
  (Phase 2.6, ADR-0015/0019).
- Expected vs actual: Chose an inline Grid row editor (edit in place) with
  `ComboBox` columns for expense type / VAT rate, Binder validation, and Signals
  for live net/VAT/gross totals. This is the fiddliest Vaadin 25 combination
  (Grid editor + editor-component binding + per-row validation + reactive totals)
  and is expected to surface friction.
- Expected vs actual: TBD — to be filled from real implementation experience.
- Workaround used: TBD.
- Evidence: design decision; ADR-0019.
- Impact: TBD; may motivate falling back to a dialog/master-detail editor if the
  inline approach proves too costly.
- Suggested Vaadin/product improvement: TBD from findings during build.
- Owner / next step: capture concrete friction (prompts, code, docs gaps) while
  implementing 2.6.

### F-008 — Auto-menu shell was smooth; UI unit test couldn't reuse the integration base
- Date: 2026-07-09
- Area: Vaadin · Verification
- Severity: Low
- Task being attempted: Phase 0.4 base UI shell (#7) — Aura `MainLayout` with a
  `SideNav` auto-generated from `@Menu` via `MenuConfiguration`, `EmptyState`,
  and global 404 / uncaught-exception views (`HasErrorParameter`).
- Expected vs actual: The core shell was frictionless — `@Layout` + `AppLayout` +
  `MenuConfiguration.getMenuEntries()` produced the security-filterable side nav
  exactly as documented, and `RouteNotFoundError` / `HasErrorParameter` gave the
  two error surfaces with no surprises. The only snag was on the *test* side:
  `SpringBrowserlessTest` (Vaadin's browserless UI tester) must be the test's
  superclass, but so must `AbstractIntegrationTest` (our singleton-Testcontainers
  base) — Java's single inheritance forces one or the other.
- Workaround used: Duplicated the `@ServiceConnection` singleton
  `PostgreSQLContainer` static setup into `NavigationShellUiTest`. Testcontainers
  reuse means it shares the same Docker container, so the cost is a few lines of
  duplicated boilerplate, not a second database.
- Evidence: `NavigationShellUiTest` (3 green UI tests); `MainLayout`,
  `NotFoundView`, `ErrorView` in `base/ui/`.
- Impact: Low, but every future UI unit test faces the same choice. Worth a
  shared composition helper (e.g. a `@TestConfiguration` or a JUnit extension
  that contributes the container) so UI tests and integration tests can share
  the datasource wiring without an inheritance clash.
- Suggested Vaadin/product improvement: `browserless-test-spring` could offer a
  composable (extension-based) entry point rather than a mandatory base class,
  so projects with their own test base aren't forced to choose.
- Owner / next step: revisit when the second UI unit test lands — extract the
  container wiring into a shared JUnit extension if the duplication spreads.

### F-009 — `@Push` websocket threads don't inherit the request correlation id
- Date: 2026-07-09
- Area: Deployment/Observability
- Severity: Low
- Task being attempted: Phase 0.5 structured JSON logging & request correlation
  id (#8) — a lightweight `OncePerRequestFilter` (`RequestCorrelationFilter`)
  that stamps an `X-Request-Id` into the MDC so every log line for one user
  action shares an id, with no Micrometer/OTel tracing stack in V1.
- Expected vs actual: Expected the id to be present on all log lines produced
  while handling a user action. Actual: it is present for ordinary HTTP and
  Vaadin UIDL requests (which pass the servlet filter), but **not** for `@Push`
  messages. Server pushes run on Atmosphere websocket threads that never enter
  the servlet filter chain, so they inherit no MDC and their log lines print an
  empty `[]` correlation slot.
- Workaround used: Accepted and documented, not worked around (per ADR-0013 and
  the issue). Spring Boot 4's built-in structured logging
  (`logging.structured.format.console=ecs`) covers staging/prod with no
  logstash encoder or custom `logback-spring.xml`; `local` stays human-readable
  with `logging.pattern.correlation=[%X{requestId:-}]`.
- Evidence: `RequestCorrelationFilter` javadoc; `application.properties`
  (`logging.pattern.correlation`); `RequestCorrelationFilterTest`.
- Impact: Log lines emitted from push handlers can't be tied back to the
  originating user action by id alone. Low for V1 (single service, push is a
  minor share of logging); grows if push-driven flows expand.
- Suggested Vaadin/product improvement: a documented hook to propagate MDC (or
  a request-scoped context) onto the Atmosphere push thread would close the gap
  without a full tracing dependency.
- Owner / next step: upgrade path is Micrometer Tracing if/when a second service
  or richer correlation is needed; revisit then.

### F-010 — `LoginI18n.createDefault()` returns a null `Header`
- Date: 2026-07-09
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 1.1 (#9) local form-stub login — a public
  `LoginView` hosting a `LoginForm`, customising its title/description via
  `LoginI18n`.
- Expected vs actual: Expected `LoginI18n.createDefault()` to return a fully
  populated i18n object (the "default" name implies every sub-object is present,
  and `getForm()` *is* populated). Actual: `getHeader()` returns `null`, so
  `i18n.getHeader().setTitle(...)` throws `NullPointerException` in the view
  constructor. Because the view is instantiated during navigation, the failure
  surfaced only at runtime as the uncaught-exception `ErrorView` rendered inside
  the shell at `/login` — not at compile time and not in the browserless tests
  (which bypass the form POST and navigate authenticated).
- Workaround used: Construct the header explicitly —
  `var h = new LoginI18n.Header(); h.setTitle(...); i18n.setHeader(h);`.
- Evidence: `security/ui/LoginView.java`; server log
  `NullPointerException ... LoginI18n.getHeader() is null`.
- Impact: Low once known; a five-minute trap. Notable that no test layer caught
  it — the gap is that view *construction* on the real login path isn't
  exercised by the browserless view tests (which authenticate via
  `@WithUserDetails` and never render `LoginView`). Manual/Playwright run caught
  it; worth a smoke test that navigates `/login` anonymously.
- Suggested Vaadin/product improvement: `createDefault()` should populate
  `Header` like it does `Form`, or the getter should never return null for the
  "default" instance.
- Owner / next step: add an anonymous `/login` render smoke test if the login
  view grows more logic.

### F-011 — Form-stub principal must carry production-identical authorities (expected)
- Date: 2026-07-09
- Area: Vaadin/Security
- Severity: Low
- Task being attempted: Phase 1.1 (#9) — wiring the `local`/`test` form-stub
  login so its principal has the same authorities as the future Google OIDC
  path (the friction the issue explicitly flagged as expected).
- Expected vs actual: Anticipated (per the issue) that reconciling the two login
  paths onto one authority model would be fiddly. Actual: it was smooth once a
  shared `AppUserPrincipal` interface (id/email/name/roles) was introduced —
  both `AppUserDetails` (form-stub) and the eventual OIDC principal implement it,
  and `CurrentUserProvider` adapts either into the immutable `CurrentUser` record
  with no per-call DB query. Authorities derive from the *local* stored roles
  (`ROLE_USER`/`ROLE_ADMIN`), so `@RolesAllowed`/`@PreAuthorize` behave
  identically regardless of login mechanism. The only real subtlety: the
  form-stub has no per-user password (there is no password column) — every seeded
  user authenticates against one shared dev password, encoded once, which the
  `RoleHierarchy` bean then complements so an admin storing only `{ADMIN}` gains
  USER access.
- Workaround used: None needed; the `AppUserPrincipal` seam is the design, not a
  workaround.
- Evidence: `security/AppUserPrincipal.java`, `AppUserDetails.java`,
  `LocalUserDetailsService.java`, `CurrentUserProvider.java`,
  `MethodSecurityConfig.java` (RoleHierarchy); `MethodSecurityIntegrationTest`.
- Impact: Positive — the shared-principal seam means Phase 1.2 (Google OAuth)
  only has to build a second `AppUserPrincipal`; the dashboard, header, and
  `CurrentUser` accessor are unchanged.
- Suggested Vaadin/product improvement: none — this is Spring Security working as
  intended once the principal contract is explicit.
- Owner / next step: reuse `AppUserPrincipal` when the `OidcUserService` lands in
  Phase 1.2.

### F-012 — Vaadin route security and Spring method security read `@RolesAllowed` from different switches
- Date: 2026-07-10
- Area: Vaadin/Security
- Severity: Low
- Task being attempted: Phase 1.2 (#10) — wiring the two authorization layers so
  a stand-in privileged operation is guarded by
  `@RolesAllowed("ADMIN")` at both the route (view) and the service-method level
  (the friction the issue explicitly flagged as expected).
- Expected vs actual: Both layers can wear the *same*
  `jakarta.annotation.security.RolesAllowed` annotation, which reads cleanly —
  but they honour it through **independent** mechanisms, and this is a quiet
  trap. Vaadin's `VaadinSecurityConfigurer` navigation access control reads
  `@RolesAllowed` on views out of the box, so the ADMIN-only route was enforced
  immediately (a USER typing `/admin` is rejected; the auto-menu hides the
  entry). Spring **method** security, however, ignores JSR-250 by default:
  `@EnableMethodSecurity` turns on `@PreAuthorize` only. The pre-existing
  `MethodSecurityConfig` used `@EnableMethodSecurity` (bare) and the old
  placeholder test used `@PreAuthorize`, so nothing surfaced the gap — the moment
  the stand-in service switched to the issue-mandated `@RolesAllowed("ADMIN")`,
  a plain USER was **allowed** through and the method-security test failed. The
  method guard, the actual enforcement point, was silently a no-op.
- Workaround used: Set `@EnableMethodSecurity(jsr250Enabled = true)`. Now one
  annotation vocabulary spans both layers: route security for navigation UX,
  method security for real enforcement.
- Evidence: `security/MethodSecurityConfig.java`
  (`@EnableMethodSecurity(jsr250Enabled = true)`),
  `security/StandInPrivilegedService.java` (`@RolesAllowed`),
  `base/ui/AdminToolsView.java` (`@RolesAllowed` route);
  `MethodSecurityIntegrationTest` (USER-rejected / ADMIN-allowed / hierarchy
  passthrough), `AdminToolsViewUiTest` (route + auto-menu filtering). The
  initial red run showed `userMayNotCallAdminOperation` "Expecting code to raise
  a throwable".
- Impact: Two-layer authorization is real and verified. The catch generalises:
  every later phase using `@RolesAllowed` on a service method (approve/reject,
  user management, rate config) depends on `jsr250Enabled` staying on. If someone
  standardises on `@PreAuthorize` instead, JSR-250 can be turned back off — but
  the *route* views would keep working, so a flip is only caught by the method
  slice. That is exactly why the reusable method-security slice matters.
- Suggested Vaadin/product improvement: docs/starter guidance could call out that
  `@RolesAllowed` on a Vaadin view (route-enforced by default) and on a service
  method (needs `jsr250Enabled = true`) travel different paths — an easy source
  of a false sense of defense-in-depth.
- Owner / next step: real admin services in Phases 5/6 annotate their methods the
  same way and point the method-security slice at them, then delete the stand-in.

### F-013 — `--lumo-*` inline styles silently no-op under Aura, and the design-system guardrail that warned about it was deleted
- Date: 2026-07-10
- Area: Vaadin / Docs / Template
- Severity: Medium
- Task being attempted: Prototyping the Phase 2 report-detail line editor (issue
  #3, relates to F-004). Styling the card variants — borders, a selected-card
  highlight, container backgrounds, spacing — with `getStyle().set(...)`.
- Expected vs actual: Expected `getStyle().set("border", "var(--lumo-contrast-10pct)")`
  and friends to render as they always have in Vaadin. Actual: **nothing painted**
  — no border, background, padding, or radius — with no error, no warning, no
  devtools complaint. The inline `style` attribute was present in the DOM, but
  every `var(--lumo-*)` resolved to the empty string. `getComputedStyle(:root)`
  confirmed **all `--lumo-*` tokens are undefined**: this app runs **Aura**
  (Vaadin 25's default, `@StyleSheet(Aura.STYLESHEET)`), which defines
  `--vaadin-*` / `--aura-*` tokens instead. Aura and Lumo are separate,
  incompatible design systems; the Lumo names simply do not exist here.
- Why "Lumo was added" (investigation): **Lumo was never added.** There is no
  Lumo dependency, no Lumo stylesheet import, and `styles.css` is empty — the
  generated start.vaadin.com project has been Aura-only since commit `28ce50a`.
  The `--lumo-*` references are hand-written inline-style *strings*, introduced
  in `ecdba67` ("Phase 0.4 — **Aura** navigation shell & UX-state primitives")
  when `MainLayout` and `EmptyState` were authored. Root cause is **stale
  convention / muscle memory**: Lumo was Vaadin's default theme for ~7 years, so
  `--lumo-*` is the reflexive idiom in old docs, examples, and LLM training data,
  and it got reached for by habit. Aggravating factor: the generated
  `spec/design-system.md` **explicitly forbade it** — *"Do not use `--lumo-*`
  CSS variables … must not be mixed with Aura. Use `--aura-*` … and `--vaadin-*`"*
  — but that spec file was later **removed from the working tree**, so the
  guardrail was no longer in context when the base UI (and later code) was
  written. A real, correct guardrail existed and was dropped.
- Workaround used: Initially, a Lumo→Aura compatibility shim on the prototype
  view root that mapped each `--lumo-*` token to its Aura equivalent via CSS
  custom-property inheritance. That crutch has since been **removed** — every
  `var(--lumo-*)` in the codebase was replaced with its `--aura-*` / `--vaadin-*`
  equivalent (see the fixed mapping below), so no shim is needed.
- Evidence: `Application.java:12` (`@StyleSheet(Aura.STYLESHEET)`, no Lumo);
  empty `src/main/resources/META-INF/resources/styles.css`; `--lumo-*` usages in
  `base/ui/MainLayout.java:63-82` and `base/ui/EmptyState.java:35-44`;
  `getComputedStyle(document.documentElement)` returns `""` for every
  `--lumo-*` probed and real values for `--vaadin-background-container` /
  `--vaadin-border-color` / `--aura-accent-surface` / `--aura-font-size-*`;
  generated `spec/design-system.md` (commit `28ce50a`) line 12 forbidding Lumo;
  the file's later deletion (`git show 28ce50a:spec/design-system.md` exists,
  working tree does not); introducing commit `ecdba67`. Correct token names via
  Vaadin docs MCP `get_theme_css_properties theme=aura vaadin_version=25.2`.
- Impact: The existing base UI already ships these no-op styles — `MainLayout`
  (app-name font size, drawer/navbar spacing) and `EmptyState` (icon colour,
  heading size, secondary text colour) render with Aura defaults, not the
  intended tokens, today. It looks "fine" only because Aura's defaults are
  reasonable, which makes the bug invisible and self-perpetuating: every new
  hand-styled view is one habit away from the same silent no-op, and there is no
  build/lint signal.
- Suggested Vaadin/product improvement: (1) a dev-mode warning when a
  `var(--lumo-*)` is used while Aura is the active theme (undefined-token
  linting) would turn a silent failure into a visible one; (2) start.vaadin.com
  should keep `spec/design-system.md` (or fold its "Aura, not Lumo" rule into
  `CLAUDE.md`) so the guardrail survives; (3) docs/MCP could offer a Lumo→Aura
  token migration table.
- Owner / next step: **Resolved (2026-07-10).** All `var(--lumo-*)` CSS tokens
  removed across the codebase — base UI (`MainLayout`, `EmptyState`,
  `LoginView`) and the Phase 2 prototype variants — mapped to `--aura-*` /
  `--vaadin-*`; the prototype shim was deleted. The "Aura, never Lumo" rule is
  now in `CLAUDE.md`. Mapping applied: `--lumo-space-m`→`--vaadin-padding`,
  `--lumo-border-radius-l`→`--vaadin-radius-l`,
  `--lumo-contrast-{5,10}pct`→`--vaadin-background-container`/`--vaadin-border-color`,
  `--lumo-contrast-{30,50,90}pct`+`--lumo-secondary-text-color`→
  `--vaadin-text-color[-secondary]`, `--lumo-base-color`→`--aura-surface-color`,
  `--lumo-primary-color[-10pct]`→`--aura-accent-color`/`--aura-accent-surface`,
  `--lumo-box-shadow-m`→`--aura-shadow-m`,
  `--lumo-font-size-*`→`--aura-font-size-*`,
  `--lumo-error-*`→`--aura-red`/`--aura-red-text`.
  (`ButtonVariant.LUMO_*` Java enum constants are unaffected — they are the API,
  not CSS tokens.)

### F-014 — Driving OIDC provisioning without a real Google
- Date: 2026-07-10
- Area: Verification
- Severity: Low
- Task being attempted: Phase 1.3 (#11) — integration-testing the domain gate +
  claim/create policy through the real `OidcUserService`, on Testcontainers
  Postgres, without a live Google exchange (the friction the issue explicitly
  flagged as expected; a mock OIDC server is out of scope).
- Expected vs actual: `OidcUserService.loadUser(OidcUserRequest)` needs a real
  token exchange, so it can't be called directly in a test. Expected to need
  some seam to inject synthesized claims. Actual: extracting the policy into a
  separate `UserProvisioningService.provision(OidcUser)` (the thin
  `ProvisioningOidcUserService` adapter calls it after `super.loadUser`) made the
  whole gate/claim/create path drivable with a hand-built `DefaultOidcUser` — no
  HTTP, no mock server. The test synthesizes an `OidcIdToken` with
  `sub`/`email`/`email_verified`/`hd`/`name` claims and calls `provision`
  directly, reusing `AbstractIntegrationTest` so provisioning runs against real
  Flyway-migrated Postgres and the seeded bootstrap admin.
- Workaround used: Public, `@Transactional` `provision(OidcUser)` on a
  profile-agnostic `UserProvisioningService` (instantiable under `test`, wired
  into the filter chain only in `staging`/`prod`) so the slice autowires it
  directly. Covers claim-preserves-ADMIN, new-USER, wrong-domain, unverified,
  disabled, and no-re-sync. Bonus: the split also dodged a CGLIB proxy over
  `OidcUserService`'s `final` setters (see below).
- Evidence: `security/UserProvisioningService.java` (`provision`),
  `security/ProvisioningOidcUserService.java` (adapter),
  `OidcProvisioningIntegrationTest`.
- Impact: Full provisioning coverage with no mock-OIDC dependency, matching the
  issue's "out of scope unless regressions slip through" stance. The gap that
  remains untested is the token exchange itself (`super.loadUser`) and the
  end-to-end redirect/failure-handler wiring — accepted for V1.
- Suggested Vaadin/product improvement: a documented pattern for testing a custom
  `OidcUserService` (drive the post-fetch policy with a synthesized `OidcUser`)
  would save every team from rediscovering this seam.

### F-015 — "Set once" name vs a JPA `updatable=false` column
- Date: 2026-07-10
- Area: Spec
- Severity: Low
- Task being attempted: Phase 1.3 (#11) — claiming the pre-seeded admin row at
  first Google login must populate `sub` **and** replace the placeholder `name`
  ('Expense Admin') with the Google display name (ADR-0007), while `name` is
  still "set once".
- Expected vs actual: Phase 1.1 mapped `User.name` with `@Column(updatable =
  false)` to encode "set once". But claim is an UPDATE of an existing row, and
  Hibernate silently drops `updatable=false` columns from UPDATE statements — so
  the claim's new name would vanish with no error, leaving the placeholder.
- Workaround used: Dropped `updatable=false` from `name` and moved the "set once"
  guarantee into the domain: `User.claim(sub, name)` runs only while `sub` is
  null and provisioning never touches an already-linked (`sub`-present) row, so
  name is written exactly once (at provision or claim) despite the mutable
  column. `email` keeps `updatable=false` (it is the match key, never rewritten).
- Evidence: `user/User.java` (`claim` + `name` column comment),
  `OidcProvisioningIntegrationTest#doesNotResyncNameOrEmailOnSecondLogin`.
- Impact: "Set once" is now a code-level invariant with a guard that fails fast
  on a second claim, rather than a column constraint that silently no-ops. The
  lesson generalises: an immutability rule that has a legitimate one-time
  write-later (seed placeholder → real value) can't be expressed as
  `updatable=false`.
- Suggested Vaadin/product improvement: none — Hibernate behaviour is correct;
  this is a modelling note for the team.

### F-016 — `@Transactional` on an `OidcUserService` subclass ⇒ CGLIB warns on final methods
- Date: 2026-07-10
- Area: Tooling/Template
- Severity: Low
- Task being attempted: Phase 1.3 (#11) — running the provisioning transaction
  inside the custom `OidcUserService`.
- Expected vs actual: The first cut put `@Transactional` directly on a class
  `extends OidcUserService`. It worked, but on a `staging` boot Spring logged
  three `CglibAopProxy` WARNINGs: `OidcUserService`'s `final` setters
  (`setOauth2UserService`, `setClaimTypeConverterFactory`, `setRetrieveUserInfo`)
  "cannot get proxied via CGLIB". Transactional advice forces a CGLIB subclass
  proxy; final methods can't be overridden, so the framework warns. Benign here
  (those setters aren't re-invoked post-construction, and `loadUser` is non-final
  so the advice still applies) — but noisy and fragile.
- Workaround used: Split the transaction out to a plain `UserProvisioningService`
  (`@Transactional`, cleanly proxyable) and left `ProvisioningOidcUserService`
  as a thin, advice-free adapter that isn't proxied at all. Warnings gone; the
  provisioning policy is also now a first-class, directly-testable service.
- Evidence: `security/ProvisioningOidcUserService.java` (adapter),
  `security/UserProvisioningService.java` (transaction); the WARN lines appeared
  in a `staging`-profile boot before the split.
- Impact: Cleaner separation and no proxy noise. Generalises: don't hang Spring
  advice (`@Transactional`, `@Cacheable`, …) on a subclass of a framework class
  that has `final` methods — delegate to a plain collaborator instead.
- Suggested Vaadin/product improvement: none — this is Spring proxying, not
  Vaadin; worth a note in any Vaadin OAuth provisioning example that shows a
  custom `OidcUserService`.

### F-017 — `ButtonVariant.LUMO_*` has theme-agnostic replacements in Vaadin 25 (supersedes the F-013 "those stay" note)
- Date: 2026-07-10
- Area: Vaadin / Docs
- Severity: Low
- Task being attempted: Removing every Lumo reference from the app so only Aura
  is used — including the `ButtonVariant.LUMO_*` enum constants that F-013 and
  `CLAUDE.md` had said should stay as "the correct Java API".
- Expected vs actual: F-013 concluded the `LUMO_*` enum constants were unavoidable
  API (unlike the `--lumo-*` CSS tokens). Actual: the Vaadin 25.2 `ButtonVariant`
  enum now carries **theme-agnostic** constants — `PRIMARY`, `TERTIARY`, `ERROR`,
  `SUCCESS`, `WARNING`, `SMALL`, `LARGE` — alongside the legacy `LUMO_*` (and a
  few `AURA_*`) ones; the current Button docs use the bare names. Each bare
  constant emits the same `theme="…"` attribute as its `LUMO_` twin (verified by
  decompiling the enum), so the swap is behaviour-preserving. Caveat: three
  variants — `tertiary-inline`, `contrast`, `icon` — are **Lumo-only** (per the
  Button styling matrix, "Supported by: Lumo") and have no Aura rendering, so
  those usages were collapsed to plain `TERTIARY`.
- Workaround used: Replaced all `ButtonVariant.LUMO_*` with the theme-agnostic
  constants across `MainLayout` and the report prototypes; `LUMO_TERTIARY_INLINE`
  / `LUMO_CONTRAST` → `TERTIARY`. Also fixed two `var(--lumo-*)` inline styles the
  F-013 sweep missed because they landed later on the Phase 1.3 branch
  (`--lumo-error-text-color` → `--aura-red-text`, `--lumo-secondary-text-color` →
  `--vaadin-text-color-secondary`). Updated `CLAUDE.md` to prescribe the bare
  constants and flag the Lumo-only variants.
- Evidence: `base/ui/MainLayout.java`, `report/prototype/*.java`,
  `security/ui/LoginView.java`; `ButtonVariant` (25.2.1) decompilation showing
  bare + `LUMO_` + `AURA_` constants; Button styling doc variant-support matrix.
- Impact: The codebase is now Lumo-free in functional code (only historical
  finding/NOTES prose still names Lumo, to explain the rationale). Design tokens
  and theme variants are consistently Aura/theme-agnostic.
- Suggested Vaadin/product improvement: mark the `LUMO_*` `ButtonVariant`
  constants `@Deprecated` in favour of the theme-agnostic names, and have the
  styling docs state the bare names are the default so teams don't reach for
  `LUMO_*` out of habit.

### F-018 — `getTextRecursively()` doesn't render grid cells; use `GridTester.getCellText`
- Date: 2026-07-10 (revised 2026-07-10 after re-checking the API)
- Area: Verification
- Severity: Low → **not a product gap** (corrected below)
- Task being attempted: Phase 2.1 (#22) — a layer-3 view test asserting the
  ADMIN-only reference-data settings screens render the Flyway seed (VAT rates
  and expense types) in their `Grid`s, and driving the add/edit/reorder/deactivate
  actions through the grid's per-row action buttons.
- Expected vs actual: Expected `view.getElement().getTextRecursively()` to
  contain the seeded cell values (e.g. "25.5 %", "Restaurant/meals"), as it does
  for plain `H2`/`Paragraph` text. Actual: grid cell content is **absent** from
  the server-side element text — `Grid` rows stream via the data provider and are
  materialised through renderers, not as child elements — so the naive text
  assertion failed even though the grids were correctly populated.
- **Correction (original entry was wrong).** The initial workaround asserted the
  grid's loaded *model* (`$(Grid.class)` → `getGenericDataView().getItems()`) and
  proposed a missing-helper product improvement. Both were mistaken: the
  browserless tier **does** render grid cells, and the helper already exists.
  `test(grid)` / the `findGrid(T.class)` locator return a `GridTester` with
  `getCellText(row, col)` (rendered text, incl. `ComponentRenderer`/value
  providers), `getCellComponent(row, col)`, `size()`, and `getRow(int)`. The
  earlier claim that grid content is "model-only in this tier" was a
  `getTextRecursively()`-by-reflex trap, not a tier limitation.
- Resolution used: Assert rendered cells with
  `findGrid(VatRateDto.class).getCellText(row, col)`. For the per-row **action
  buttons** (Edit / Move / Deactivate) — which live inside a component column and
  so are *not* reachable from a UI-wide `findButton()` — scope the search to the
  cell: `getCellComponent(row, actionsCol)` then `find(Button.class, cell)`
  (helper `rowActionButton(..)` in `AbstractReferenceDataViewUiTest`). Editor
  dialog fields/buttons and the view header's Add button are in the normal tree
  and reachable directly via `findButton()` / `findBigDecimalField()` /
  `findComboBox(..)`.
- Evidence: `reference/ui/VatRateViewUiTest` and `ExpenseTypeViewUiTest`
  (17 green) drive full CRUD + active-options filtering and read every assertion
  from `getCellText` — no Playwright fallback needed for cell content.
- Impact: The real trap is only `getTextRecursively()`; the browserless grid API
  covers rendered cells and cell components fine. Two things that ARE tier-real:
  (1) component-column contents need cell-scoped `find`, not a UI-wide query;
  (2) `getTextRecursively()` still isn't a grid-reading tool — reach for the
  `GridTester` instead.
- Suggested Vaadin/product improvement: none — the helper exists and is
  documented (`flow/testing/browserless/component-testers`). Optional: a doc note
  that component-column children aren't matched by a UI-wide `find()`.
- Owner / next step: none — corrected pattern captured; reuse `getCellText` /
  `rowActionButton` for later grid-backed views (My Reports, approval queue).

### F-019 — HEIC receipts excluded; iPhone default format will bite users
- Date: 2026-07-10
- Area: UX-spec
- Severity: Medium
- Task being attempted: Choosing the allowed receipt content types (Phase 3,
  ADR-0021).
- Expected vs actual: The natural user gesture is "photograph the receipt with my
  phone." iPhones default to **HEIC**, which browsers cannot render inline and is
  awkward to handle server-side, so V1 accepts only JPEG/PNG/PDF (magic-byte
  verified). An iPhone user uploading a straight-from-camera photo will therefore
  be rejected until they change their capture format or convert.
- Workaround used: Exclude HEIC from the allow-list for V1; reject at upload with
  a clear message. Multi-image per line and "receipt required over a threshold"
  are likewise out of scope.
- Evidence: ADR-0021; magic-byte allow-list (JPEG `FF D8 FF` / PNG `89 50 4E 47`
  / PDF `25 50 44 46`).
- Impact: A common, non-obvious rejection for the most natural input path. Server-
  side HEIC→JPEG transcoding (or client-side capture-format guidance) is the
  likely follow-up if real usage hits it.
- Suggested Vaadin/product improvement: an upload component option to transcode or
  clearly flag HEIC would save every mobile-first app this same rejection.
- Owner / next step: revisit if the demo/real usage surfaces HEIC rejections;
  candidate for a transcoding step or a documented "shoot in JPEG" note.

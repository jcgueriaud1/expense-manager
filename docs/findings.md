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
- Owner / next step: verify exact seed values against the Verohallinto decision
  for the target year before the V__ migration ships.

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

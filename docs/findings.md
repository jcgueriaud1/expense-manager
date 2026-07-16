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

### F-004 — Report line editor: inline Grid dropped for variant-C cards + modal dialog
- Date: 2026-07-13 (design 2026-07-09; built in #24)
- Area: Vaadin
- Severity: Low
- Task being attempted: The line editor for the report detail view (Phase 2.3,
  #24; ADR-0015/0019). Originally provisionally an inline Grid row editor with
  `ComboBox` columns, Binder validation, and Signals for live totals.
- Expected vs actual: The inline-Grid combination was **not** built. Prototyping
  four variants (see the retired `report/prototype/NOTES.md`) confirmed the
  provisional worry: the Grid editor collapsed to zero height in flex splits, its
  footer totals were imperative (Signals don't reach Grid footer cells out of the
  box), and per-row editor-component binding was the fiddliest option. Variant C
  (receipt-style cards + a focused modal `Dialog`) was chosen and built instead.
  Building it, the remaining friction was: (a) the expense-type → default-VAT-rate
  pre-fill can't be expressed declaratively in Binder — it needs an imperative
  `ComboBox` value-change listener guarded by `isFromClient()` so
  `binder.readBean` doesn't clobber a loaded rate; (b) making the whole card
  clickable *and* carrying a trash button means both fire on a trash click (DOM
  click bubbling).
- Workaround used: (a) the guarded value-change listener in `LineEditorDialog`;
  (b) the trash button lives *outside* the clickable card body (siblings, not
  nested), so no `stopPropagation` hack is needed. Live totals moved off the Grid
  footer entirely and onto plain `Span`s bound to a `Signal.computed` over a
  `ListSignal` of working lines — which worked cleanly (see F-023).
- Evidence: `report/ui/ReportDetailView`, `report/ui/LineEditorDialog`; the
  deleted `report/prototype/` package; ADR-0019.
- Impact: The cards+dialog editor is lower-density and mobile-friendly, and keeps
  totals off the Grid footer where Signals bind naturally. The type→rate default
  staying imperative is a small, well-contained exception to the Binder-declarative
  ideal.
- Suggested Vaadin/product improvement: a Binder affordance for cross-field
  defaults (set field B's value from field A on user change, without a manual
  `isFromClient` listener) would remove the one imperative seam.
- Owner / next step: none — variant C is the real view; prototype deleted.

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

### F-020 — `@WithUserDetails` invisible to a service that resolves the user via `AuthenticationContext`, once a browserless context has run
- Date: 2026-07-10
- Area: Verification
- Severity: Medium
- Task being attempted: Phase 2.2 (#23) — a layer-2 integration test for
  `ExpenseReportService`, which resolves the report owner through
  `CurrentUserProvider` → Vaadin's `AuthenticationContext`. Authenticated the
  test with `@WithUserDetails("user@vaadin.com")`, as the reference-data view
  tests do.
- Expected vs actual: In isolation the test passed. In the **full suite** it
  failed — first with "No authenticated user in the current security context"
  (`CurrentUserProvider.require()`), then, after a first fix attempt, with
  `AuthenticationCredentialsNotFoundException` from the `@RolesAllowed` method
  interceptor. The authentication set by `@WithUserDetails` was simply not
  visible to the service, but only when a browserless (`SpringBrowserlessTest`)
  context had booted earlier in the same JVM.
- Root cause: Vaadin installs a `VaadinAwareSecurityContextHolderStrategy` **both
  as a bean and as the global `SecurityContextHolder` static**. In a single-app
  process these are the same instance, so everything agrees. In a multi-context
  test JVM, **each** `@SpringBootTest` context installs its *own* strategy
  instance as the global static, last-boot-wins. Meanwhile Spring Security 6
  method security injects *this context's* `SecurityContextHolderStrategy`
  **bean**. So three readers can diverge: `@WithUserDetails`/`TestSecurityContextHolder`
  writes to one instance, the method interceptor reads this-context's bean, and
  `AuthenticationContext` reads the global static — different instances → the
  principal is invisible.
- Workaround used: Authenticate in a JUnit `@BeforeEach` (same lifecycle/thread
  as the body), and **pin the static to this context's bean** before writing:
  `SecurityContextHolder.setContextHolderStrategy(strategyBean)`, then build an
  `AppUserDetails` via `LocalUserDetailsService` and set it through that same
  bean. All three readers then agree, order-independently. (Browserless view
  tests are unaffected: they store the context in the `VaadinSession`, which the
  strategy reads first.)
- Evidence: `report/service/ExpenseReportServiceIntegrationTest` (6 green in the
  full 90-test suite, previously 5 errors + 1 failure only under suite ordering).
- Impact: Any **headless** (`@SpringBootTest`, non-browserless) test that drives
  code resolving the user via `AuthenticationContext`/`SecurityContextHolder`
  must not rely on `@WithUserDetails`/`@WithMockUser` alone — they can write to a
  strategy instance the app never reads once a browserless context has run.
  Production is unaffected (single context; bean == static).
- Suggested Vaadin/product improvement: a small test utility (or doc note) for
  authenticating headless integration tests against Vaadin's session-aware
  strategy — e.g. a `@WithVaadinUser` that writes through the active bean — would
  remove this footgun.
- Owner / next step: reuse the `@BeforeEach` strategy-pinning pattern for future
  owner-scoped service tests (approval queue, export).

### F-021 — Browserless `DatePickerTester.setValue` refuses an invalid (null-on-required) value
- Date: 2026-07-10
- Area: Verification
- Severity: Low
- Task being attempted: Phase 2.2 (#23) — a layer-3 test proving the report form
  keeps Save always enabled and shows a top-of-form error summary when the
  required report date is empty (ADR-0020, no disabled-submit gating).
- Expected vs actual: Expected `findDatePicker().setValue(null)` to model a user
  clearing the field. Actual: the tester threw `IllegalArgumentException: Given
  date is not a valid value` — by design it calls the field's default validator
  and refuses to *set* a value it considers invalid (null on a required picker),
  so the "cleared → validation fails → summary shows" path can't be driven
  through the tester.
- Workaround used: clear the value straight on the component —
  `$(DatePicker.class).first().clear()` — then click Save; the Binder's
  `asRequired` fires and the error summary renders. (Setting a *valid* date is
  fine through `findDatePicker().setValue(date)`.)
- Evidence: `report/ui/ReportDetailViewUiTest.missingRequiredDateShowsErrorSummaryAndPersistsNothing`.
- Impact: To test required-field validation UX at the browserless tier, reach
  past the typed tester to the component's `HasValue.clear()`/`setValue(null)` for
  the deliberately-invalid state; the tester is for valid user input only.
- Suggested Vaadin/product improvement: a tester affordance for "user cleared a
  required field" (e.g. `clear()` on the value testers) would let validation-path
  tests stay on the tester API.
- Owner / next step: none — pattern captured for later required-field forms.

### F-022 — ComboBox won't show a now-inactive historical option unless it's in the item set
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 2.3 (#24) — the line editor offers only *active*
  expense types / VAT rates for new lines (ADR-0018), but editing a historical
  line whose type or rate has since been deactivated must still display it.
- Expected vs actual: Expected setting the `ComboBox` value to the line's stored
  type/rate to render it even if it isn't among `setItems(...)`. Actual: a
  `ComboBox` value that isn't in the item set has no matching option to display —
  the field renders empty, silently dropping the historical classification.
- Workaround used: when opening the editor for an existing line, inject the
  line's type/rate into that ComboBox's item list (labelled "(inactive)") if the
  active list doesn't already contain it, then select that instance —
  `LineEditorDialog.withHistoricalType/withHistoricalRate`. The active-only lists
  still drive *new* lines.
- Evidence: `report/ui/LineEditorDialog`; ADR-0018 (rates preserved by `active`
  flag, never deleted).
- Impact: The active/inactive split needs the editor to reconcile the item set
  per opened line, not just call the "active options" service method once. Easy to
  miss — the failure is a silently blank field, not an error.
- Suggested Vaadin/product improvement: a `ComboBox` mode that renders an
  out-of-set current value (with the item-label generator applied) would remove
  this per-open reconciliation for history-preserving reference data.
- Owner / next step: none — pattern captured for the receipt/approval editors.

### F-023 — Signals (ListSignal + computed + bindText) fit live report totals cleanly
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 2.3 (#24) — live net/VAT/gross totals that update
  as lines are added/edited/removed (ADR-0015), replacing the prototype's
  imperative `refreshTotals()`.
- Expected vs actual: This was the smooth part. A `ListSignal<ExpenseLineDto>` of
  working lines drives both the card list (`VerticalLayout.bindChildren`) and the
  total bar (`Span.bindText(Signal.computed(() -> ...))`). Adding/removing a line
  (list-structure change) and editing one (`entry.set(...)`, per-entry change)
  both re-fire the computed totals with no manual wiring — exactly the reactive
  model ADR-0015 wanted, and the reason totals moved off the Grid footer (F-004).
- Workaround used: none needed. Two API notes worth recording: (1) `get()` may
  only be called inside a reactive context (effect/computed) — use `peek()` in
  click listeners and for the save snapshot, or it throws `IllegalStateException`;
  (2) `bindChildren` manages *all* default-slot children, so the empty-state
  placeholder must be a sibling of the bound container (with `bindVisible`), not
  a child of it.
- Evidence: `report/ui/ReportDetailView` (`lines` ListSignal, `currentTotals`,
  `totalsBar`, `card`).
- Impact: Confirms ADR-0015's Binder-for-validation / Signals-for-dynamic-state
  split works in practice; the get()/peek() context rule is the main thing to
  teach.
- Suggested Vaadin/product improvement: none — the docs cover it; the get()/peek()
  distinction could be more prominent given how easy it is to hit.
- Owner / next step: none — reuse this shape for other live-total surfaces
  (allowance calculator, export preview).

### F-024 — Optimistic-lock read-only guard can't be proven at the service layer inside a `@Transactional` (rollback) test
- Date: 2026-07-13
- Area: Testing
- Severity: Low
- Task being attempted: Phase 2.4 (#25) — a layer-2 service test asserting a
  `SUBMITTED` report rejects a whole-aggregate `update()` with the aggregate's
  editable guard (`IllegalStateException`), passing the version the caller last
  saw.
- Expected vs actual: expected `update(id, dto, submittedVersion)` to reach the
  domain `reconcileLines`/`updateDetails` guard and throw `IllegalStateException`.
  Actual: it threw `ObjectOptimisticLockingFailureException` first. In a
  `@Transactional` (rollback) test every service call joins the *same*
  persistence context; the preceding `submit()` leaves the entity dirty, and the
  next `@Transactional` service method auto-flushes before its query — bumping
  `@Version` in the shared context — so the version the test captured from
  `submit()`'s returned DTO (read *before* that flush) is already stale by the
  time `update()` runs its explicit version check. The service's version guard
  fires before the domain guard is ever reached.
- Workaround used: keep the service-layer test to the *version-independent* path
  (a `SUBMITTED` report rejects `delete()` — no version arg, so it always reaches
  `assertDeletable()`), and prove the edit-once-submitted guard where it belongs:
  the domain unit test (`ExpenseReportTest`, layer 1, no persistence context to
  drift) and the view test (no Save/Add/Delete offered once `SUBMITTED`). The
  genuine optimistic-lock conflict lives in its own committed-state class
  (`ExpenseReportOptimisticLockIntegrationTest`, non-`@Transactional`, ADR-0011).
- Evidence: `report/service/ExpenseReportServiceIntegrationTest`
  (`aSubmittedReportCannotBeDeleted`), `report/domain/ExpenseReportTest`
  (`aSubmittedReportRejectsLineAndDetailEditsAndDelete`).
- Impact: reinforces ADR-0012's layering — version/flush semantics make the
  rollback slice a poor place to assert state-machine guards that sit *behind* a
  version check; push those to layer 1, and use the committed-state exception
  only for the lock behaviour itself.
- Suggested Vaadin/product improvement: none — this is a JPA/`@Transactional`
  test-isolation nuance, not a framework gap.
- Owner / next step: none — pattern captured; apply the same split to the Phase 5
  approve/reject guards.

### F-025 — Buffered receipt bytes live on the Vaadin session heap until save
- Date: 2026-07-13
- Area: Deployment/Observability
- Severity: Low
- Task being attempted: Phase 3.1 (#40) — attaching a receipt on a report at any
  time, including a brand-new never-saved one, with the bytes persisted only on
  the whole-aggregate `create`/`update` (ADR-0021, overriding ADR-0019's
  "save first").
- Expected vs actual: expected (and got) a smooth "attach then save" flow with no
  disabled control. The structural cost, as ADR-0021 called out: between attach
  and save the received `byte[]` is held in the detail view's `pendingReceipts`
  map on the Vaadin session (server heap), not the DB. Bounded by the 10 MB cap ×
  lines with a pending upload per open editor session, but it is real per-user
  heap that a page reload or session timeout silently discards (the Upload
  component keeps no server-side file state across a reload either).
- Workaround used: none needed for V1 — the cap bounds it and a single report has
  few lines. Kept the bytes off the DTOs entirely: `ExpenseLineDto` carries only
  a receipt *summary*, and buffered bytes ride a separate save-only `ReceiptUpload`
  channel keyed by working-line entry, so the heap footprint is visible and local.
- Evidence: `report/ui/ReportDetailView` (`pendingReceipts`,
  `pendingReceiptsByLineIndex`), `report/ui/LineEditorDialog` (`pendingData`),
  `report/service/ReceiptUpload`, ADR-0021 Consequences.
- Impact: at real concurrency (many users each mid-edit with a large pending
  receipt) this is heap pressure to watch; the migration path is a staging store
  (temp file / object storage) for buffered bytes, deferred with ADR-0009's
  object-storage move.
- Suggested Vaadin/product improvement: a first-class "buffer to temp file, not
  heap" `UploadHandler` (there is `TemporaryFileUploadHandler`, but wiring its
  lifecycle to an unsaved-form working copy is manual) would make the safe choice
  the easy one.
- Owner / next step: none — logged per the acceptance criteria; revisit with the
  object-storage decision if heap pressure shows up in staging.

### F-026 — Vaadin `UploadHandler` + `maxFiles(1)` leaves the upload button disabled; legacy `SucceededEvent` doesn't fire
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 3.1 (#40) — an always-enabled receipt upload that
  is replaceable (overwrite), i.e. still usable after one file (ADR-0020: never a
  disabled control).
- Expected vs actual: with `setMaxFiles(1)`, after one successful upload the
  component's built-in file list keeps the file and the upload button goes
  `disabled` (max-files reached) — a gated control, and a second display of the
  filename redundant with our own status row. First fix attempt wired
  `addSucceededListener(e -> clearFileList())` to reset the count; it never fired.
  Under the new `UploadHandler` streaming API the upload lifecycle is delivered
  through the `TransferProgressListener` model, and the legacy `SucceededEvent`
  (and `FinishedEvent`) are not raised — so a handler-based Upload can't rely on
  them for post-upload cleanup.
- Workaround used: call `upload.clearFileList()` at the end of the
  `UploadHandler.inMemory` callback itself (it runs on the UI thread on
  completion). The native list is then always empty, our status row ("📎 filename
  · Remove") is the single source of truth, and the button stays enabled for a
  replacement. Verified live via Playwright (button enabled, no redundant list).
- Evidence: `report/ui/LineEditorDialog` (`uploadRef`, `clearFileList()` inside
  the inMemory callback); Vaadin 25.2 upload docs (progress-listener note); the
  disabled-button screenshot from the first run.
- Impact: a subtle trap — the intuitive `addSucceededListener` compiles and reads
  correctly but is dead code with `UploadHandler`; and `maxFiles(1)` quietly
  fights the "never disable" rule. Cost us one build/restart/verify cycle.
- Suggested Vaadin/product improvement: either fire the legacy succeeded/finished
  events from the `UploadHandler` path too, or deprecate them loudly; and document
  that `maxFiles(1)` disables the add button (with the clear-to-replace pattern) on
  the Upload page.
- Owner / next step: none — pattern captured; reuse the callback-`clearFileList()`
  shape for any future single-file upload.

### F-027 — Vaadin 25 `Dialog` overlay is styled via `vaadin-dialog::part(overlay)`, not `vaadin-dialog-overlay`
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 3.2 (#41) — the receipt preview dialog degrades to
  a full-screen sheet on a phone-sized viewport (ADR-0020), styled from the app's
  global `styles.css`.
- Expected vs actual: Pre-25 guidance (and much of the web) says a `Dialog`'s
  overlay is a top-level `<vaadin-dialog-overlay>` element carrying the dialog's
  class, targetable as `vaadin-dialog-overlay.my-class::part(overlay)`. In Vaadin
  25 that element does not exist at the document top level: `Dialog#addClassName`
  puts the class on the `<vaadin-dialog>` host, and the overlay is rendered
  *inside that host's shadow root* (`<vaadin-dialog-overlay id="overlay"
  exportparts="backdrop, overlay, header, …">`). The old selector matched nothing
  and the media query silently did nothing.
- Workaround used: target the exported part on the host directly —
  `vaadin-dialog.receipt-preview-dialog::part(overlay)`. Confirmed live: the
  overlay fills the 360-px viewport (100vw/100vh, radius 0) under the media query
  and stays a centered `min(92vw, 52rem)` box on desktop.
- Evidence: `resources/META-INF/resources/styles.css`; DOM probe of the dialog
  shadow root (`exportparts` list) via Playwright; `report/ui/ReceiptPreview`
  (`addClassName("receipt-preview-dialog")`).
- Impact: a stale-but-plausible styling recipe that fails silently (no error, no
  visual change) — easy to lose time to. Cost one probe/fix/verify cycle.
- Suggested Vaadin/product improvement: document the `vaadin-dialog::part(...)`
  hooks (overlay/header/content/footer via `exportparts`) on the Dialog styling
  page, and call out the change from the `vaadin-dialog-overlay` top-level element.
- Owner / next step: none — reuse the `::part(overlay)` selector for any future
  responsive dialog.

### F-028 — A clickable component nested in a clickable card fires both listeners (needs explicit stopPropagation)
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: Phase 3.2 (#41) — a receipt thumbnail on a line card that
  enlarges the image on click, where the whole card is itself clickable to open
  the line editor (variant-C detail editor).
- Expected vs actual: Expected clicking the thumbnail button to only open the
  preview. Actual: the DOM `click` bubbled from the button to the card body's
  click listener, so on an editable report the thumbnail *also* opened the line
  editor (the editor won, hiding the preview). Vaadin `ClickNotifier` registers a
  DOM listener per component and relies on native bubbling — there is no
  server-side "stop the parent listener" for a nested `ClickEvent`.
- Workaround used: on the preview affordance, add a client-side listener that
  stops propagation —
  `getElement().executeJs("this.addEventListener('click', e => e.stopPropagation())")`.
  The button's own listener still runs (stopPropagation doesn't cancel the
  element's own handlers), only the ancestor card is spared. Verified live: the
  card thumbnail now opens the preview, and the rest of the card still opens the
  editor. (The existing trash button sidesteps this by living *outside* the
  clickable body — the other valid pattern.)
- Evidence: `report/ui/ReceiptPreview#forReceipt`; `report/ui/ReportDetailView`
  (card body click → `openEditor`); first-run screenshot showing the editor
  opening on a thumbnail click.
- Impact: an easy-to-miss interaction bug that automated view tests (which assert
  presence, not click routing) don't catch — found only in manual Playwright
  verification. Reinforces that nested interactive elements need either isolation
  or explicit propagation control.
- Suggested Vaadin/product improvement: a helper on `ClickNotifier` (e.g.
  `addClickListener(listener).stopPropagation()`) would make this a one-liner
  without hand-written JS.
- Owner / next step: none — pattern captured for future nested-click affordances.

### F-029 — The Lumo `badge` *theme* is unstyled under Aura — use the official Badge component (25.1+)
- Date: 2026-07-13
- Area: Vaadin
- Severity: Low
- Task being attempted: importing the "Expense Reports" Claude Design mockup and
  re-skinning the report list + detail to it (status badges, grouped card list,
  status callouts) while keeping the domain unchanged.
- Expected vs actual: Expected `Span` + `theme="badge primary/success/error"`
  (the standard Vaadin badge recipe) to render a coloured pill. Actual: under the
  Aura theme it renders as **plain, unstyled text** — no background, no radius,
  no colour — because the `badge` style module ships with Lumo, not Aura. No
  error, no console warning; caught only in live Playwright verification (the
  first list screenshot showed "Draft"/"Submitted" as bare text). This is the
  badge-level analogue of F-013/F-017 (Lumo-only *button* variants under Aura).
- Resolution: use the **official `com.vaadin.flow.component.badge.Badge`**
  component (added in Vaadin 25.1, `vaadin-badge-flow`), which *is* styled under
  Aura — not the `theme="badge …"` span, and not a hand-styled span (the initial
  workaround, since replaced). Map with `BadgeVariant`: `SUCCESS` (approved),
  `ERROR` (rejected), `FILLED` (submitted — Aura has no accent/primary variant
  and `CONTRAST` is Lumo-only, so a filled neutral reads as "handed off"), plain
  default (draft), all `SMALL`. The label text always renders, so status is never
  colour-only (ADR-0020). See `report/ui/ReportViewSupport#statusBadge`.
- Evidence: `report/ui/ReportViewSupport`; Badge Java API + styling docs
  (variant support table: `filled/success/warning/error/small/dot` under Aura,
  `contrast` Lumo-only); report UI tests green.
- Impact: another silent Lumo-vs-Aura styling trap. The design mockup's own
  `_ds` tokens (`--aura-text-xs`, `--aura-user-0`, …) are the design project's
  scheme and do **not** match the app's real Aura/`--vaadin-*` tokens either, so
  the whole mockup had to be translated token-by-token, not copied.
- Suggested Vaadin/product improvement: either provide an Aura badge style module
  (so `theme="badge …"` works across themes) or document on the Badge page that
  it is Lumo-only and give the Aura-token recipe.
- Owner / next step: none — reuse `ReportViewSupport#statusBadge` for any future
  status pill.

### F-030 — `--vaadin-padding` / `--vaadin-gap` don't exist (only sized `-xs…-xl` variants)
- Date: 2026-07-13
- Area: Vaadin
- Severity: Medium
- Task being attempted: re-skinning the report list/detail cards to the mockup —
  giving raw `Div`/`RouterLink` cards their own padding and inter-card gaps.
- Expected vs actual: Expected `var(--vaadin-padding)` and `var(--vaadin-gap)` to
  be valid base-style tokens (they read like the obvious names, and pre-existing
  code — `base/ui/MainLayout`, the original totals bar — already used them).
  Actual: the base styles define **only the sized scale** —
  `--vaadin-padding-{xs,s,m,l,xl}` and `--vaadin-gap-{xs,s,m,l,xl}` (plus
  `--vaadin-radius-{s,m,l}`); there is **no unsuffixed `--vaadin-padding` or
  `--vaadin-gap`. An undefined custom property with no fallback resolves to
  nothing, so `padding: var(--vaadin-padding)` silently applies **zero padding** —
  no error, no warning. On Vaadin components it was masked by their own built-in
  padding; on the new hand-built cards it showed as a cramped, unpadded layout
  (spotted by JC against the mockup).
- Workaround used: use the sized tokens — `--vaadin-padding-l` for the roomy card
  interiors, `--vaadin-padding-m` for standard spacing, `--vaadin-gap-m` between
  cards. Fixed every occurrence in `report/ui/*` and the two pre-existing ones in
  `base/ui/MainLayout`. Confirmed via `get_theme_css_properties theme=base`.
- Evidence: `MainLayout` (2), `MyReportsView`, `ReportDetailView`,
  `LineEditorDialog`; base-theme MCP token listing; cramped-card screenshot.
- Impact: a silent no-op that reads like working code and copy-propagates (the
  bad token was already in the template/earlier phases). Always prefer a fallback
  (`var(--token, 1rem)`) or the documented sized token.
- Suggested Vaadin/product improvement: either alias unsuffixed
  `--vaadin-padding`/`--vaadin-gap` to the medium step, or have the dev-mode
  theme linter warn on references to undefined `--vaadin-*` custom properties.
- Owner / next step: none — tokens corrected app-wide.

### F-031 — Theme switcher hand-rolled raw `executeJs`/`localStorage` instead of the idiomatic Flow APIs
- Date: 2026-07-14
- Area: AI
- Severity: Medium
- Task being attempted: the original theme switcher (PR #46) — apply a light/dark/
  system colour scheme live and persist it per-browser.
- Expected vs actual: Expected the first cut to reach for the framework APIs that
  cover exactly this. Actual: it hand-wrote JavaScript strings —
  `getElement().executeJs("document.documentElement.style.colorScheme=$0; …")` to
  flip the scheme and `executeJs("localStorage.setItem/getItem…")` to persist —
  when Flow 25 already ships **`Page.setColorScheme(ColorScheme.Value)`** (since
  25.0; does the same `<html>` `color-scheme` manipulation, plus a `theme`
  attribute) and **`WebStorage`** (typed `setItem`/`removeItem`/`getItem`) for
  localStorage. The raw-JS version worked, so nothing flagged it; it just
  shouldn't have been written that way. Simplified in PR #47.
- Workaround used: rewrote `ThemeSwitcher` on `Page.setColorScheme` + `WebStorage`,
  keying the choice map on `ColorScheme.Value` (`NORMAL`/`LIGHT`/`DARK`). The one
  irreducible bit of JS — the pre-paint bootstrap in `Application#configurePage` —
  legitimately stays: it must run synchronously before first paint and the server
  can't read client `localStorage` before the initial HTML is sent, so no Java API
  can express it.
- Evidence: `base/ui/ThemeSwitcher` (before/after in PRs #46 → #47);
  `Page#setColorScheme` (flow-server 25.2.1 source); `WebStorage` docs.
- Impact: `executeJs` string-slinging is the class of code that bypasses the
  framework, dodges type-checking, and copy-propagates as a bad example. When a
  task is "set a browser/DOM property" or "read/write web storage", check for a
  first-party Flow API (`Page.*`, `WebStorage`, `getThemeList`, `getStyle`) before
  writing JS. Prefer the idiomatic API from the first cut, not as a follow-up.
- Suggested Vaadin/product improvement: cross-link the colour-scheme docs and the
  `Page.setColorScheme`/`WebStorage` APIs from the Aura light/dark page, so the
  idiomatic path is discoverable at the point of need.
- Owner / next step: none — `ThemeSwitcher` now uses the idiomatic APIs.

### F-032 — Allowance rate seed values are provisional (verify against Verohallinto)
- Date: 2026-07-14
- Area: Spec
- Severity: Medium
- Task being attempted: standing up the Phase 4.1/4.4 allowance rate config
  (issue #48) — the per-year reference data the Travel Calculator will cost
  against — seeded from the "Verohallinto 2026 decision".
- Expected vs actual: Expected an authoritative, confirmed 2026 rate table to
  seed from. Actual: the PRD carries **provisional** domestic/km/meal figures
  (full €54 / partial €25 / meal €13.50 / km €0.59, thresholds 10 h / 6 h) and
  no confirmed foreign per-diem table, so the V7 foreign seed is a
  representative ~12-country **starter sample** at plausible amounts, not the
  real decision. The domain memory flags Finnish statutory rates as
  trust-the-user / verify-before-shipping.
- Workaround used: seeded the provisional values, modelled history **by year**
  (not by an `active` flag — the deliberate contrast with ADR-0018) so a
  corrected year is just a new/edited row that never mutates a prior year, and
  gave the admin a runtime settings screen (`AllowanceRatesView`) to fix any
  figure and add a country/year. The caveat is repeated in the V7 migration
  header comment and the entity Javadoc.
- Evidence: `db/migration/V7__allowance_rates.sql` (header + seed),
  `allowance/AllowanceRateService`, `allowance/ui/AllowanceRatesView`;
  [[user-finnish-tax-domain]] memory.
- Impact: until an admin verifies each year's figures against the published
  Verohallinto decision, calculator output (Phase 4.2) is indicative only. The
  by-year model means verification is a data task, not a code change.
- Suggested Vaadin/product improvement: none (domain/data caveat). Product
  next step: confirm the 2026 foreign per-diem table before the calculator
  slice (#49) ships user-visible allowance amounts.
- Owner / next step: admin verifies 2026 figures; the config screen is the fix
  surface.

### F-033 — Browserless `ComboBoxLocator` has no `getValue()`; the selection getter is `getSelected()`
- Date: 2026-07-14
- Area: Verification
- Severity: Low
- Task being attempted: asserting the selected year in the allowance-rate view
  test (`findComboBox(Integer.class)` then read the current value).
- Expected vs actual: Expected the browserless `ComboBoxLocator` to expose a
  `getValue()` mirroring `HasValue#getValue()` (the field locators read as if
  they proxy the component). Actual: it exposes `getSelected()` (and
  `getSuggestions()`/`selectItem(String)`); `getValue()` does not compile. The
  locator is a *tester*, not the component, so its API is its own vocabulary.
- Workaround used: `findComboBox(Integer.class).getSelected()`.
- Evidence: `allowance/ui/AllowanceRatesViewUiTest`;
  `com.vaadin.flow.component.combobox.ComboBoxLocator` (browserless-test-shared
  sources).
- Impact: a small compile-time speed bump; the error message
  ("cannot find symbol getValue()") points the right way once you read the
  locator source.
- Suggested Vaadin/product improvement: either add a `getValue()` alias on the
  value-bearing locators or surface the locator method list in the browserless
  testing docs so the tester vocabulary is discoverable without reading jars.
- Owner / next step: none.

### F-034 — Generated per-diem line is coupled to reference data by name/value, with no first-class marker
- Date: 2026-07-14
- Area: Spec
- Severity: Medium
- Task being attempted: generating the read-only 0 %-VAT per-diem line from a
  Travel (#49). Every `expense_line` row needs a non-null `expense_type_id` and
  `vat_rate_id` (V5), so the generated line must reference a real reference-data
  type + rate — but there is no first-class way to say "this is *the* per-diem
  type / the 0 % rate".
- Expected vs actual: expected a stable, semantic hook (e.g. a `system`/`kind`
  marker on `ExpenseType`/`VatRate`, or a dedicated allowance category). Actual:
  the service resolves the type by the literal name `"Travel allowance"`
  (`findFirstByNameIgnoreCaseAndActiveTrue…`) and the rate by value `0.00`
  (`findFirstByValueAndActiveTrue…`). Both are admin-editable (ADR-0018), so a
  rename or deactivation of that seed row breaks per-diem generation with a
  runtime `IllegalStateException` at save time.
- Workaround used: constant `PER_DIEM_EXPENSE_TYPE = "Travel allowance"` +
  `ZERO_VAT` in `ExpenseReportService`, resolved unfiltered-by-name; a clear
  `IllegalStateException` if the row is missing so the failure is legible.
- Evidence: `report/service/ExpenseReportService` (`perDiemExpenseType()`,
  `zeroVatRate()`), `reference/ExpenseTypeRepository`,
  `reference/VatRateRepository`; seed in `db/migration/V3__reference_data.sql`.
- Impact: a hidden coupling between admin-editable reference data and allowance
  generation. Low blast radius today (the seed exists and tests cover the happy
  path), but an admin action can break a core flow with no compile-time signal.
- Suggested Vaadin/product improvement: none (domain-modelling gap, not a
  framework one). Product next step: add a `system`/`purpose` marker to the
  reference tables (Phase 4.4/5) so allowance lines bind to a role, not a label.
- Owner / next step: revisit when the approval/export slices need to recognise
  allowance lines by kind rather than by expense-type name.

### F-035 — `Signal.computed` throws `MissingSignalUsageException` when a `&&` short-circuits the signal read away
- Date: 2026-07-14
- Area: Vaadin
- Severity: Low
- Task being attempted: binding an empty-state's visibility to "no manual lines
  *and* the report is editable" — `Signal.computed(() -> editable && lines.get().isEmpty())`.
- Expected vs actual: expected the computed to evaluate normally. Actual: when
  `editable` was `false`, Java short-circuited the `&&` and never called
  `lines.get()`, so the effect read *zero* signals and Vaadin threw
  `MissingSignalUsageException: Effect action must read at least one signal
  value.` — surfacing as an `ErrorView` on a read-only report, not a validation
  message.
- Workaround used: read a signal unconditionally first
  (`lines.get().isEmpty() && editableSignal.get()`), and promote the plain
  `editable` boolean to a `ValueSignal<Boolean>` so the computed is both
  side-effect-safe *and* re-runs when editability flips on (re)load (a plain
  field wouldn't trigger recomputation).
- Evidence: `report/ui/ReportDetailView` (`editableSignal`, `linesSection()`);
  failing then passing `ReportDetailViewUiTest.aSubmittedReportShowsTheTripBut…`.
- Impact: an easy trap — a perfectly reasonable boolean guard placed *before* the
  signal read turns a computed into a crash, and only on the branch that
  short-circuits (so it can pass in draft and fail read-only). The runtime
  message is good once you see it, but the failure mode (whole-view error) is
  disproportionate.
- Suggested Vaadin/product improvement: either treat "no signal read this run"
  as a stable/`false` computed rather than an exception, or document the
  short-circuit hazard in the Signals guide with this exact example.
- Owner / next step: none.

### F-036 — Browserless: dialog/overlay text is not under `getCurrentView()`, and `getTextRecursively()` includes hidden elements
- Date: 2026-07-14
- Area: Verification
- Severity: Low
- Task being attempted: asserting the trip dialog's computed per-diem preview
  and that a hidden empty-state is not shown, in `ReportDetailViewUiTest`.
- Expected vs actual: (1) `getCurrentView().getElement().getTextRecursively()`
  did **not** contain the open dialog's text — a `Dialog` overlay attaches to the
  `UI`, not the routed view — so preview/error assertions saw nothing. (2)
  `getTextRecursively()` **does** include `setVisible(false)` elements (visibility
  is a client concern; the server element tree still carries them), so a
  `doesNotContain(...)` assertion can't prove something is hidden.
- Workaround used: assert open-dialog text against
  `UI.getCurrent().getElement().getTextRecursively()`; assert a component is
  hidden via the locator instead (`findSpan().withText(...).exists()` is `false`
  for an invisible span, mirroring how the hidden-button tests use `.exists()`).
- Evidence: `report/ui/ReportDetailViewUiTest`
  (`insertingADomesticTrip…`, `aSubmittedReportShowsTheTrip…`).
- Impact: two non-obvious verification pitfalls; each cost one red run to
  diagnose. The locator's "invisible ⇒ absent" rule is the reliable primitive
  for both existence and visibility assertions.
- Suggested Vaadin/product improvement: note in the browserless docs that
  overlays live under the `UI` (not the view) and that text-tree assertions
  ignore visibility, pointing to the locator `.exists()` idiom for visibility.
- Owner / next step: none.

### F-037 — Slice 2's one-line-per-travel model didn't generalise to four routed outputs
- Date: 2026-07-14
- Area: Spec
- Severity: Medium
- Task being attempted: Phase 4.3 (#50) — extending the Travel Calculator so one
  trip generates up to four read-only lines (per-diem, kilometre, meal, parking)
  instead of the single per-diem line Slice 2 (#49) built.
- Expected vs actual: expected the Slice 2 machinery to extend by adding rules.
  Actual: two of its foundations assumed *one* generated line per travel and had
  to be reshaped. (1) A generated line was identified only by `travel != null`,
  and the aggregate matched existing lines with `Collectors.toMap(getTravel, …)`
  — a one-to-one map that throws on a second line per travel. (2) The totals split
  routed purely on `isGenerated()` (generated ⇒ tax-free per-diem subtotal), but
  parking is *generated **and** VAT-bearing*, so it must land in Net/VAT, not a
  tax-free row — the boolean couldn't express that. `TravelSpec`/`TravelDto` also
  carried flat `perDiem*` fields with no room for the other three outputs.
- Workaround used: added a first-class `GeneratedLineKind` discriminator
  (`PER_DIEM`/`KILOMETRE`/`MEAL`/`PARKING`, with `isTaxFreeAllowance()`) on
  `ExpenseLine`; reconciliation now keys on `(travel, kind)`; totals route by kind
  (`countsInNetVat()` folds parking into Net/VAT, three `allowanceTotal(kind)`
  subtotals for the tax-free ones). `TravelSpec` now carries a
  `List<GeneratedLineSpec>` and `TravelDto` a `TravelAllowances` breakdown record.
- Evidence: `report/domain/GeneratedLineKind`, `ExpenseLine.getGeneratedKind()`,
  `ExpenseReport.regenerateGeneratedLines`/`totals`/`perDiemTotal`+`kilometreTotal`
  +`mealTotal`; `report/service/TravelAllowances`, `ExpenseReportService.toTravelSpec`.
- Impact: a moderate but clean refactor confined to the aggregate + service +
  travel DTOs; the manual-line path was untouched. Directly delivers the "next
  step" F-034 flagged — allowance lines are now recognised by a semantic kind, not
  by expense-type name — so the Phase-5 approval/export slices can group them by
  role. The name-based *reference-data* coupling (F-034) still stands; the four
  types are still resolved by literal name in the service.
- Suggested Vaadin/product improvement: none (domain-modelling shape, not a
  framework gap).
- Owner / next step: when the foreign-trip slice (Slice 4) lands, the same
  `GeneratedLineSpec` list absorbs a foreign per-diem kind with no further reshape.

### F-038 — Receipts on generated lines: a shared persistence context resurrects the cascade-deleted receipt in a rollback test
- Date: 2026-07-14
- Area: Testing
- Severity: Low
- Task being attempted: letting a receipt be attached to a travel-generated line
  (per-diem/kilometre/meal/parking) and verifying that clearing an input (e.g.
  parking fee → 0) orphan-removes the line and its receipt.
- Expected vs actual: in production this is clean — the receipt table's FK is
  {@code ON DELETE CASCADE} (V6) and receipts are not part of the aggregate, so an
  {@code update()} loads the report without the receipt in its session, removes the
  line, and the DB cascades the receipt away. But the layer-2 test is
  {@code @Transactional} (rollback), so {@code create()} and {@code update()} share
  one Hibernate session: the just-created {@code Receipt} lingered, still pointing at
  the now-orphan-removed {@code ExpenseLine}, and the flush threw
  {@code TransientPropertyValueException} ("references an unsaved transient instance").
- Workaround used: {@code entityManager.clear()} between the create and the update
  in the test, modelling the separate-request reality (the same trick
  {@code deletingATripRemovesItsGeneratedLine} already uses). Production code is
  unchanged and correct.
- Evidence: {@code ExpenseReportServiceIntegrationTest.removingAGeneratedLineKindCascadesItsReceipt};
  {@code ExpenseReportService.applyTravelReceipts}; V6 {@code receipt} FK cascade.
- Impact: a test-only artifact of the shared-session pattern, but a sharp one — the
  exception points at "persist the transient instance", which misleads toward a
  production bug that isn't there. The tell is that it only fires when a
  receipt-carrying line is removed within the same transaction it was created in.
- Suggested Vaadin/product improvement: none (JPA session semantics).
- Owner / next step: none.

### F-039 — A year-dependent ComboBox and `Binder.readBean` fight over ordering
- Date: 2026-07-14
- Area: Vaadin/Flow
- Severity: Low
- Task being attempted: adding the destination-country picker to the trip dialog
  (Phase 4.2, #51). The list of countries is *year-dependent* — it must show only
  the countries with a foreign per-diem rate for the **trip's year**, and the year
  comes from the departure `DateTimePicker` in the same form.
- Expected vs actual: expected to just `setItems(...)` once and bind. Actual: a
  `ComboBox`'s `setValue` only preselects when the value is already among its
  items, so an *edited* foreign trip whose stored country must be re-selected needs
  the item list populated **before** `binder.readBean(model)` runs — but the
  departure field's value (which decides the year) isn't set until `readBean` runs.
  Chicken-and-egg: populate-from-field is too late, and `setItems` itself clears the
  current selection.
- Workaround used: drive the initial population from the *model's* departure
  (`model.getDepartureAt()`), not the field, so it works before `readBean`; refresh
  the list on every departure change; and capture/restore the current selection
  around `setItems` so a still-valid country survives a re-populate. Registered the
  refresh before `readBean` (the same ordering the existing preview listeners rely
  on).
- Evidence: `report/ui/TravelEditorDialog` (`refreshCountries`, the pre-`readBean`
  call, the departure listener); `ReportDetailViewUiTest.insertingAForeignTrip...`.
- Impact: a small but non-obvious wrinkle whenever a bound selection field's *items*
  depend on another field in the same bean — easy to ship a dialog that silently
  drops the edited value on open. The tell is "the combo opens empty only when
  editing".
- Suggested Vaadin/product improvement: a `Binder` hook (or `ComboBox` option) to
  supply items lazily from the bean at `readBean` time would remove the ordering
  dance; today the caller must sequence it by hand.
- Owner / next step: none — the pattern is contained in the dialog. A later slice
  that lets the per-diem year differ from the departure year would revisit it.

### F-040 — A `@Transactional` browserless view test with `@WithUserDetails` renders `LoginView` once several browserless classes have run
- Date: 2026-07-15
- Area: Verification
- Severity: Medium
- Task being attempted: Phase 5.1 (#61) — browserless view tests for the admin
  approval queue and the admin-review mode on `ReportDetailView`. The two new
  classes extended the report feature's `@Transactional` `AbstractReportViewUiTest`
  (for its report-seeding helpers) and authenticated with `@WithUserDetails`,
  exactly like the existing `ReportDetailViewUiTest`.
- Expected vs actual: green in isolation and in small combinations, but in the
  **full suite** every navigation in the two new classes resolved to `LoginView`
  (unauthenticated) — including `@PermitAll` targets. Reproducible by running
  ≥4-5 browserless classes together (`ReportDetailViewUiTest`,
  `MyReportsViewUiTest`, `AdminToolsViewUiTest`, + the two new ones): only the
  later-ordered `@Transactional` classes failed, while the non-transactional
  `AdminToolsViewUiTest` and the earlier-ordered transactional classes passed.
- Root cause: the F-020 strategy-divergence, seen from the browserless side. The
  browserless env installs Vaadin's session-aware `SecurityContextHolderStrategy`
  as the global static and reads the request's principal from the `VaadinSession`.
  `@WithUserDetails` writes the context via a `BeforeEachCallback`; once enough
  browserless classes (and the `SecurityContextHolder.setContextHolderStrategy(...)`
  pinning in the layer-2 service tests, F-020) have run in the JVM, the instance
  `@WithUserDetails` writes to and the one the browserless request reads diverge,
  so the session carries no authentication → `LoginView`. `@Transactional` made
  it worse (its listener/thread interplay shifted class ordering into the bad
  window); the non-transactional `AdminToolsViewUiTest` pattern never tripped it.
- Workaround used: gave the approval view tests their own base,
  `AbstractApprovalViewUiTest`, that extends `SpringBrowserlessTest` **directly and
  is not `@Transactional`** (mirroring `AdminToolsViewUiTest`), keeps
  `@WithUserDetails`, seeds reports straight through the repository + aggregate
  (also the only way to reach another owner / a pre-approved state), and cleans
  them up in an `@AfterEach` instead of by rollback. Order-independent and green in
  the full 237-test suite. Attempts to keep the transactional base and authenticate
  imperatively in `@BeforeEach` (pinning the bean, F-020 style) only fixed part of
  it — a `@BeforeEach` runs after the browserless env's `BeforeEachCallback`, so the
  timing is still fragile.
- Evidence: `approval/ui/AbstractApprovalViewUiTest`, `ApprovalQueueViewUiTest`,
  `ApprovalAccessUiTest`; contrast `report/ui/AbstractReportViewUiTest`
  (`@Transactional`, stable only because it orders early).
- Impact: a new browserless view test that both needs DB seeding and drives an
  authenticated flow should extend `SpringBrowserlessTest` directly (non-transactional,
  explicit cleanup), not the transactional report base — otherwise it is a
  suite-ordering time bomb.
- Suggested Vaadin/product improvement: the `@WithVaadinUser` utility floated in
  F-020 (write the auth through the active session-aware strategy) would fix both
  the headless and browserless sides of this footgun.
- Owner / next step: reuse `AbstractApprovalViewUiTest`'s shape for future admin
  view tests (reject/resubmit, export).

### F-041 — Manual/visual verification had no fixtures or economy guidance
- Date: 2026-07-15
- Area: Verification
- Severity: Medium
- Task being attempted: Issue #68 — making skill step 4 (drive the real app with
  the Playwright MCP) cheap to set up and run.
- Expected vs actual: Expected to land directly on the screen under test.
  Actual: the local DB started empty, so verifying the Phase 5.1 approval flow
  (#61) took ~52 Playwright calls — roughly half of them **setup** (clicking
  create → add line → save → submit through the UI just to have a `SUBMITTED`
  report), the rest inflated by full-tree `browser_snapshot` dumps used only to
  find element refs. Nothing documented the fixtures or a cheaper pattern.
- Workaround used: Added `LocalReportSeeder` (`@Profile("local")`, idempotent,
  empty-DB only) that seeds four labelled DRAFT/SUBMITTED(×2)/APPROVED fixtures
  straight through the repository + aggregate — the bypass-the-owner-scoped-service
  technique from `AbstractApprovalViewUiTest`. Documented the fixtures, logins,
  and a Playwright economy pattern (deep-link + stable selectors over snapshots,
  `browser_fill_form`, screenshot only unique visual states) in
  `docs/manual-verification.md`, and pointed `implement-use-case` step 4 at both.
- Evidence: `report/LocalReportSeeder`, `report/LocalReportSeederTest`,
  `docs/manual-verification.md`, `.claude/skills/implement-use-case/SKILL.md`;
  the #61 verification transcript.
- Impact: a login lands directly on the screen under test with zero setup clicks
  — cheaper for both an agent and a human smoke test, and a pleasant local demo.
- Suggested Vaadin/product improvement: a first-class "dev seed" story in the
  starter (profile-guarded, idempotent `ApplicationRunner` convention) so every
  app ships demo fixtures without hand-rolling one.
- Owner / next step: Resolved in #68. Extend the fixture set if a later phase
  (reject/resubmit) needs a `REJECTED` seed.

### F-042 — Running the app from a git worktree collides with the main checkout
- Date: 2026-07-15
- Area: Tooling/Template
- Severity: Low
- Task being attempted: Starting the app from the `issue-68` worktree to visually
  verify the seeder while the main checkout's stack was up.
- Expected vs actual: Expected `./mvnw` to just run. Actual: two collisions — the
  worktree's spring-boot-docker-compose tried to bind its own Postgres to host
  port 5432, already held by the main checkout's `expense-manager-postgres-1`
  ("port is already allocated"); and once that was bypassed, Tomcat failed on
  port 8080, already held by the main dev server.
- Workaround used: Ran against the shared already-running Postgres with
  `-Dspring.docker.compose.enabled=false` and on a free port with
  `-Dserver.port=8081`. The datasource default (`localhost:5432/expense_manager`)
  already points at the running container, so no other wiring was needed.
- Evidence: `application-local.properties` (compose auto-start + datasource
  default); startup logs "Bind for 0.0.0.0:5432 failed" / "Port 8080 was already
  in use".
- Impact: a second checkout/worktree can't naively `./mvnw` alongside the primary
  one; easy to misread as a broken change rather than a port clash.
- Suggested Vaadin/product improvement: none for the product; a worktree note in
  `DEVELOPMENT.md` ("reuse the running DB with compose disabled + a spare port")
  would save the rediscovery.
- Owner / next step: low priority; document the worktree recipe if multi-checkout
  dev becomes common.

### F-043 — Admin review mode silently became editable once Reject moved the report to the (owner-)editable REJECTED state
- Date: 2026-07-15
- Area: Spec
- Severity: Medium
- Task being attempted: Phase 5.2/5.4 (#62) — the admin Reject action on
  `ReportDetailView`'s review mode. `load()` derived interactivity purely from
  status: `editable = dto.status().isEditable()`, then toggled Save/Add-expense/
  Insert-travel/trash and per-card click editing off that flag.
- Expected vs actual: the approve slice (#61) was safe only by accident — the only
  review-reachable state was `SUBMITTED` (not editable), and approve moved it to
  `APPROVED` (also not editable), so review mode was always read-only in practice.
  Reject moves the report to `REJECTED`, which **is** editable (the owner resubmit
  path). After a reject the same view reloaded `REJECTED` and flipped fully
  interactive **for the admin** — Save, Add expense, Insert travel, and the line
  trash all appeared on another user's report. Manually opening `/review/{id}` for
  an already-`REJECTED` report showed the same. Caught in Playwright MCP
  verification, not by the browserless tests (which only asserted the buttons that
  existed pre-fix).
- Root cause: "editable" conflated two questions — *is this status editable?* (a
  domain fact) and *may the current viewer edit it?* (owner path only). Review mode
  is read-only regardless of status, but that constraint lived implicitly in which
  statuses happened to be reachable, not in the code.
- Workaround used: `editable = dto.status().isEditable() && !reviewMode;` — review
  mode is always read-only. Added regression assertions to the successful-reject UI
  test (`Save`/`Add expense` absent after the transition).
- Evidence: `report/ui/ReportDetailView#load`; `approval/ui/ApprovalQueueViewUiTest`
  `rejectingWithAReasonRecordsItAndMovesTheReportToRejected`.
- Impact: a reachable authority gap (an admin editing/saving someone else's report
  through the review view) that stayed latent until the first review-reachable
  state was itself editable. A reminder that "read-only" must be pinned to the
  viewer's role, not inferred from the set of currently-reachable statuses.
- Suggested Vaadin/product improvement: none — app-side. Worth a review-mode
  read-only guard test even for statuses no queue currently routes to.
- Owner / next step: none.

### F-044 — The single-report deep-link in the verification doc used a query param, not the path segment
- Date: 2026-07-15
- Area: Docs
- Severity: Low
- Task being attempted: Phase 5.5 (#63) — visually verifying the owner's edit +
  resubmit path (`REJECTED → SUBMITTED`) on `ReportDetailView` via Playwright MCP,
  deep-linking to the seeded rejected report per `docs/manual-verification.md`.
- Expected vs actual: expected the doc's `/report?reportId=<id>` recipe to open
  that report. Actual: `ReportDetailView` binds its id via `HasUrlParameter<Long>`
  (a path segment), so `?reportId=5` bound no id and silently opened a *fresh
  transient* report — the correct form is `/report/5`.
- Workaround used: corrected the deep-link to `/report/<id>` in the doc.
- Evidence: `report/ui/ReportDetailView` (`implements HasUrlParameter<Long>`);
  `docs/manual-verification.md`.
- Impact: a quiet trap — the wrong URL 200s onto a plausible-looking (but wrong)
  new-report screen rather than erroring, so a verifier can mistake an empty draft
  for the report under test.
- Suggested Vaadin/product improvement: none — doc fix.
- Owner / next step: none.

### F-045 — The always-enabled-Save + error-summary editor scaffold is now hand-copied into a fourth place
- Date: 2026-07-15
- Area: Standards
- Severity: Low
- Task being attempted: Phase 6 write path (#65) — the Users role/access editor on
  `UserManagementView`. The ticket pointed at reusing
  `ReferenceViewSupport.openEditor` "or an analogous local helper".
- Expected vs actual: expected to reuse the shared helper. Actual:
  `ReferenceViewSupport` is package-private in `reference.ui`, so it is unreachable
  from `user.ui`; the same Dialog + `role="alert"` error summary + always-enabled
  Save + `writeBeanIfValid` + `IllegalArgumentException`→summary scaffold had to be
  re-inlined. That makes **four** near-identical copies: `ReferenceViewSupport`,
  `LineEditorDialog`, `TravelEditorDialog`, and now `UserManagementView`.
- Workaround used: inlined a local `openEditor(...)` + `showErrors(...)` in
  `UserManagementView`, matching the established idiom (F-013, ADR-0020) so the
  behaviour and accessibility contract stay identical.
- Evidence: `user/ui/UserManagementView#openEditor`;
  `reference/ui/ReferenceViewSupport#openEditor`;
  `report/ui/LineEditorDialog`; `report/ui/TravelEditorDialog`.
- Impact: low functional risk (the copies agree today), but the error-summary
  contract now lives in four spots — a fix or a11y tweak has to be applied four
  times, and a new editor has no obvious shared home to reach for.
- Suggested Vaadin/product improvement: none for Vaadin — app-side. Promote the
  editor scaffold to a small cross-cutting helper (e.g. `base.ui` or a dedicated
  `EditorDialogs` utility) that any feature package can call, and migrate the four
  copies onto it.
- Owner / next step: **partly resolved (#76)** — the scaffold is now the
  `base.ui.EditorDialog` component (a `Dialog` subclass owning the always-enabled
  Save + `role="alert"` summary + `writeBeanIfValid`), and the reference/allowance
  editors use it. (An earlier take used a static `AdminEditor.openEditor` helper;
  it was replaced by the component after review — a static method wrapping the
  Dialog API was the wrong shape.) The three remaining copies
  (`report/ui/LineEditorDialog`, `report/ui/TravelEditorDialog`,
  `user/ui/UserManagementView`) can now migrate onto `EditorDialog` too — left for
  a follow-up since #76 scoped to reference + allowance.

### F-046 — A `public final` method on a Vaadin route's superclass breaks Spring's CGLIB proxy of the route bean
- Date: 2026-07-16
- Area: Vaadin
- Severity: Low
- Task being attempted: #76 — extracting the shared `ReferenceConfigEditor<T>`
  base for the reference admin screens. `VatRateView`/`ExpenseTypeView` (both
  `@Route @RolesAllowed` beans) now `extends ReferenceConfigEditor<…>`, whose
  grid-reload method I first declared `public final void refresh()`.
- Expected vs actual: expected a plain inherited method. Actual: on view
  instantiation Spring logged `CglibAopProxy: Public final method ... refresh()
  cannot get proxied via CGLIB, consider removing the final marker`. Vaadin's
  Spring route targets are container-managed beans, and the security layer wants a
  CGLIB subclass proxy; a `final` public method on the superclass makes the whole
  bean non-proxyable, so the advice is silently dropped.
- Workaround used: dropped `final` from `refresh()` (kept it `public`,
  overridable). Warning gone.
- Evidence: Spring log line `o.s.aop.framework.CglibAopProxy` during
  `VatRateViewUiTest` context start. (The base was later reworked from a generic
  `ReferenceConfigEditor` into the abstract `reference/ui/ReferenceConfigView`;
  the lesson stands — none of its methods, incl. `refresh()`, are `final`.)
- Impact: low here (no advice actually targets `refresh()`), but a trap: a `final`
  method anywhere on a route's type hierarchy can quietly disable proxy-based
  cross-cutting (method security, `@Transactional`) on that view with only a WARN.
- Suggested Vaadin/product improvement: none code-wise — worth a note in the
  Vaadin+Spring guidance that route/component base classes should avoid `final`
  public methods.
- Owner / next step: none.

### F-047 — Pre-existing aria-label inconsistency across the two reference screens forced extra config surface on the extracted editor
- Date: 2026-07-16
- Area: UX-spec
- Severity: Low
- Task being attempted: #76 — expressing `VatRateView` and `ExpenseTypeView` as
  configs of one `ReferenceConfigEditor<T>` while keeping behaviour (and the exact
  aria-labels the view tests assert) unchanged.
- Expected vs actual: expected one row-subject function per kind to drive all
  three action aria-labels (edit / reorder / toggle). Actual: the two screens had
  drifted — VAT rates label the Edit button `"Edit rate 13.5 %"` while reorder /
  toggle use the same `"rate 13.5 %"` subject; expense types label Edit
  `"Edit expense type Restaurant/meals"` but reorder / toggle use the bare name
  `"Travel allowance"` / `"Publications"`. So the Edit subject and the
  reorder/toggle subject genuinely differ per kind.
- Workaround used: each view spells out its own aria-labels inline in its
  `actions(...)` cell — Edit vs reorder/toggle worded per the existing (drifted)
  convention — preserving every label verbatim rather than normalising them (which
  would change accessible text and break the assertions). (The interim
  config-object take encoded this as two separate label hooks; after the
  parent-class rework the labels are just inline strings in each view.)
- Evidence: `reference/ui/VatRateView#actions` ("Edit rate …" vs "Move rate … up"
  / "Deactivate rate …") vs `reference/ui/ExpenseTypeView#actions` ("Edit expense
  type …" vs bare-name "Move … up" / "Deactivate …").
- Impact: the real lesson is that unspec'd, hand-written aria-label wording drifts
  between sibling screens, and any later extraction must either carry the drift or
  make a deliberate normalisation call.
- Suggested Vaadin/product improvement: none — app-side. A small aria-label
  convention (e.g. always `"<verb> <entity-noun> <identifier>"`) would make the two
  screens' labels uniform.
- Owner / next step: none; consider normalising the labels in a dedicated a11y pass.

### F-048 — `field.setRequiredIndicatorVisible(true)` is redundant next to `Binder…asRequired(…)` and is copied across every editor
- Date: 2026-07-16
- Area: Standards
- Severity: Low
- Task being attempted: #76 review — reworking the reference/allowance editors.
  A reviewer flagged that each editor field does
  `field.setRequiredIndicatorVisible(true);` immediately before binding it with
  `binder.forField(field).asRequired(…)`.
- Expected vs actual: expected the manual call to be doing something. Actual: it
  is a no-op duplicate — `Binder`'s `asRequired(…)` already enables the field's
  visual required indicator when the binding is created. The Vaadin docs state
  this in three places: "calling `binder.asRequired()` on your field automatically
  enables the required indicator"; and "Using `asRequired()` has two effects:
  1. A visual required indicator appears on the field. 2. …". So the explicit
  `setRequiredIndicatorVisible(true)` adds nothing wherever `asRequired` is used.
- Origin: introduced in the very first editor (Phase 2.1 reference-data CRUD,
  commit `6319e5d`, #27) and then copy-pasted as the house pattern into every
  editor since — `git log -S 'setRequiredIndicatorVisible'` shows it spreading
  through #23/#32, #24/#37, #48/#52, #49/#55, #51/#66, #62/#70.
- Workaround used: dropped the redundant call from the in-scope editors
  (`reference/ui/*`, `allowance/ui/*`) while reworking them; behaviour and the
  rendered required indicator are unchanged (verified — the view tests still pass).
- Evidence: Vaadin 25.2 docs `flow/binding-data/components-binder-beans` and
  `building-apps/forms-data/add-form/validation`; the ~14 call sites from
  `grep -rn setRequiredIndicatorVisible src/main/java` at review time.
- Impact: cosmetic-only noise, but it is genuinely misleading — it reads as if the
  indicator needs manual enabling, which invites cargo-culting into fields that do
  NOT use `asRequired` (where it would then be load-bearing and easy to break).
- Suggested Vaadin/product improvement: none for Vaadin. App-side: leave the
  indicator to `asRequired`; only call `setRequiredIndicatorVisible` on fields
  that are required WITHOUT an `asRequired` binding.
- Owner / next step: low priority — sweep the remaining copies in `report/ui`
  (`ReportDetailView`, `LineEditorDialog`, `TravelEditorDialog`) in a follow-up;
  each is a required field already bound with `asRequired`.

### F-049 — "Submit for approval" acted on the last-saved state, silently dropping in-progress edits
- Date: 2026-07-16
- Area: Domain/Service + UI (report detail)
- Severity: Medium
- Task being attempted: Issue #81 — Submit should save the current state and ask
  for confirmation.
- Expected vs actual: Expected the report the user *sees* (with unsaved edits) to
  be what gets submitted. Actual: `onSubmit()` called `service.submit(id, version)`
  against the persisted row only — any working-copy edit (lines, additional info,
  date, buffered receipts) not yet Saved was discarded on submit, and there was no
  confirmation for the one-way lock. Worst case: a report with a line added-but-not-
  Saved failed the "≥1 line" guard because the line lived only in the working copy.
- Workaround used: added `ExpenseReportService.saveAndSubmit(...)` — one
  `@Transactional` that runs the whole-aggregate UPDATE then the `SUBMITTED`
  transition atomically (a failed guard rolls the save back), dispatching first-
  submit vs resubmit on the aggregate's own origin state so a single service method
  serves both UI actions. `onSubmit()` now validates the form, opens a confirmation
  dialog, and on confirm calls the atomic method. The old `submit`/`resubmit`
  service methods are kept (used by test seeders).
- Evidence: `ReportDetailView.onSubmit/confirmSubmit/performSubmit`,
  `ExpenseReportService.saveAndSubmit/applyUpdate`; new tests
  `submittingPersistsA{ReportLevelEdit,LineAdded}…`,
  `saveAndSubmitPersistsTheWorkingEditsThenSubmits`; verified end-to-end in the
  local app (edit-without-save → confirm submit → edit persisted + SUBMITTED).
- Impact: silent data loss on the most consequential owner action; the fix also
  makes the submit atomic and confirmed.
- Suggested Vaadin/product improvement: none — application-level design gap, not a
  framework issue.
- Owner / next step: resolved in this change (issue #81).

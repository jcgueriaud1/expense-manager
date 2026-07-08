# Build Plan — Expense Management V1

Build order for the V1 vertical slice. Phases are sequenced so each one leaves
the app runnable and demoable. Every phase should leave findings in
[findings.md](findings.md). Decisions referenced as ADR-XXXX live in
[adr/](adr/).

**Definition of done (V1, from the brief):** a `vaadin.com` user logs in with
Google, creates a report, adds ≥1 manual line with a receipt image, submits;
an admin reviews and approves/rejects with a comment; the user sees status and
rejection comments and can edit + resubmit — all on real Postgres, deployed as a
Docker container to a preview environment.

Legend: each phase lists **work items**. Drill into any item to spec it in
detail before implementing.

---

## Phase 0 — Foundation (skeleton → real app scaffold)
Goal: a runnable Spring Boot + Vaadin app on real Postgres with the agreed
architecture in place, no features yet.

- **0.1 Fix skeleton defects** — align Dockerfile base images to Java 25
  (ADR-0014, F-001).
- **0.2 Dependencies** — add `spring-boot-starter-security`,
  `spring-boot-starter-oauth2-client`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-validation`, `spring-boot-starter-actuator`, PostgreSQL
  driver, Flyway (`flyway-core` + `flyway-database-postgresql`), Testcontainers
  (`postgresql`, `junit-jupiter`), and Docker Compose support.
- **0.3 Package structure** — create feature packages `base`, `security`,
  `user`, `report`, `approval`, `allowance` (ADR-0002).
- **0.4 Local infra** — Docker Compose Postgres service, auto-started in `local`
  (ADR-0004); one documented command to run the app.
- **0.5 Profiles & config** — `local` / `test` / `staging` / `prod`; env-var
  secrets with safe local defaults (ADR-0013).
- **0.6 Flyway baseline** — `V1__init.sql` (empty/seed scaffolding); confirm
  migrations run on startup (ADR-0005).
- **0.7 Base UI** — `MainLayout` (Aura), navigation shell, empty/loading/error
  patterns from the brief's UX deliverables.
- **0.8 Observability** — Actuator health + liveness/readiness; JSON logging in
  staging/prod; correlation id (ADR-0013).
- **0.9 Testcontainers harness** — base integration test that boots on Postgres.

## Phase 1 — Auth, users & security (UC-007 login; UC-006 groundwork)
Goal: log in and see a role-appropriate dashboard.

- **1.1 User domain** — `User` entity (sub, email, name, roles, enabled),
  repository, DTOs (ADR-0003).
- **1.2 Google OAuth2** — `staging`/`prod` login; domain-gated (`vaadin.com`)
  auto-provisioning on first login (ADR-0007).
- **1.3 OAuth form-stub** — `local`/`test` login with same authorities/records
  (ADR-0012).
- **1.4 Security config** — `VaadinSecurityConfigurer`, route + method security
  scaffolding, `CurrentUser` accessor (ADR-0008).
- **1.5 Seed admin** — migration seeding `jean-christophe@vaadin.com` as ADMIN.
- **1.6 Login view + dashboard** — role-aware landing (UC-007).
- Tests: method-security slice (USER vs ADMIN); provisioning logic.

## Phase 2 — Expense report core (UC-001 create/submit; UC-002 list; UC-005 detail)
Goal: create a report, add/edit/remove lines, see totals, submit.

- **2.1 Report aggregate** — `ExpenseReport` (state machine, `@Version`) owning
  `ExpenseLine`; domain guards (ADR-0006, ADR-0011).
- **2.2 Categories** — Travel, Meal, Accommodation, Office/supplies, Other.
- **2.3 Persistence + migration** — report/line tables (`numeric(19,2)`
  amounts, ADR-0010).
- **2.4 Services + DTOs** — create/list/get/edit-lines/submit; manual mapping;
  transactions.
- **2.5 My Reports view** (UC-002) — own reports + statuses; empty state.
- **2.6 Report detail view** (UC-005/UC-001) — create/edit, add/edit/remove
  lines, live totals (Signals), submit; Binder-validated line editor
  (ADR-0015).
- **2.7 Domain unit tests** — state machine + guards.

## Phase 3 — Receipts (UC-001 receipt image)
Goal: attach and view a receipt on a line.

- **3.1 Receipt storage** — bytea column, size/type validation (ADR-0009).
- **3.2 Upload** — `UploadHandler`, not `StreamResource` (F-002).
- **3.3 View/download** — `DownloadHandler` streaming service method.

## Phase 4 — Finnish allowances (UC-001 trip/allowance)
Goal: trip inputs generate allowance lines; rates are editable config.

- **4.1 Rate config domain + tables** — domestic per-diem, km rate, foreign
  per-diem by country; per-year; seeded from Verohallinto 2026 (ADR-0005).
- **4.2 Allowance calculator** — pure stateless domain service: domestic
  per-diem with free-meal halving; km compensation; foreign country-rate
  lookup; manual override with explanation (ADR-0006).
- **4.3 Trip/travel-calculator UI** — inputs → generated lines; do better than
  ProCountor's foreign-trip default (glossary: Travel Calculator).
- **4.4 Admin rate editing** — settings screen to edit rates per year.
- Note: encoding every exception is explicitly optional — postpone edge cases if
  they threaten the finishable loop.

## Phase 5 — Approval flow (UC-003 approve/reject; UC-002 rejection visibility)
Goal: admin reviews the queue and approves/rejects; user sees outcome & resubmits.

- **5.1 Submitted queue view** — admin list of SUBMITTED reports.
- **5.2 Admin review view** — inspect detail, approve, reject with mandatory
  comment; status history.
- **5.3 Optimistic-lock conflict UX** — reload prompt on `OptimisticLockException`
  (ADR-0011).
- **5.4 User-side status + rejection comments** — visible in report detail.
- **5.5 Edit + resubmit** — `REJECTED → SUBMITTED` (ADR-0006).

## Phase 6 — Admin user management (UC-006)
Goal: admin manages users.

- **6.1 User list view** — all users, roles, enabled state.
- **6.2 Manage roles / revoke** — set roles, flip `enabled` (ADR-0007);
  method-secured.

## Phase 7 — Finance export (UC-004)
Goal: finance exports reports.

- **7.1 Export service** — CSV/Excel of approved reports over a period (format
  TBD when specced).
- **7.2 Export UI + download** — `DownloadHandler`.

## Phase 8 — Deployment & ops
Goal: two Docker environments, safe updates, documented.

- **8.1 Production image** — Java 25 Dockerfile, `./mvnw clean package`.
- **8.2 Hosting target** — **OPEN ITEM** (Docker host / PaaS / k8s) — decide
  before this phase.
- **8.3 Two environments** — auto-updating **preview** + stable
  **staging/prod-like** (ADR-0013).
- **8.4 Migrations & secrets in deploy** — Flyway at startup; env-var secrets.
- **8.5 Health + rolling/blue-green** — readiness-gated cutover, no scheduled
  downtime; don't let this block other progress — document ops findings.
- **8.6 README** — setup, run, deploy, troubleshooting.

---

## Cross-cutting (every phase)
- **Findings** — log to [findings.md](findings.md) as they occur.
- **Tests** — layers 1–3 per ADR-0012; no auto E2E yet.
- **UX states** — empty / loading / error / permission-denied where relevant.

## Open items (decide before the dependent phase)
- Hosting target for preview/staging (Phase 8).
- Allowance edge-case depth (Phase 4) — postpone exceptions if they threaten the
  finishable loop.
- Export format for finance (Phase 7).
- Final root Java package name (currently `com.example`).

## Use-case → phase map
UC-001 submit report → P2/P3/P4 · UC-002 view my reports → P2/P5 ·
UC-003 approve/reject → P5 · UC-004 finance export → P7 ·
UC-005 report detail → P2 · UC-006 admin manage users → P1/P6 ·
UC-007 login & dashboard → P1

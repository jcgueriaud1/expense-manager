# ADR-0013 — 4 profiles, env-var secrets, Actuator + JSON logging

**Status:** Accepted

## Context
The brief demands separate local/staging/prod config, Docker for app + deps,
secrets handling, health checks, and structured logging.

## Decision
- **Profiles** (`application-{profile}.properties` over a base file):
  - `local` (default) — Compose Postgres + OAuth form-stub + seed/test data.
  - `test` — Testcontainers.
  - `staging` — real Google OAuth, external Postgres, no seed data.
  - `prod` — stable, same shape as staging.
- **Secrets** — nothing sensitive in git. Google client id/secret, DB
  credentials, admin-seed identity via **environment variables** (12-factor);
  `local` ships safe defaults.
- **Observability** — Spring Boot **Actuator**: `/actuator/health` with
  `liveness`/`readiness` probes wired into Docker healthchecks and blue-green
  cutover; **structured JSON logging** in staging/prod (human-readable in
  `local`); a correlation/request id in logs.

## Consequences
- The two required deploy environments map onto these profiles: **preview**
  (auto-updates to latest build) and **staging/prod-like** (stable).
- **Open item:** the actual hosting target (Docker host / PaaS / k8s) is decided
  in the plan's deployment phase — it depends on available infra.

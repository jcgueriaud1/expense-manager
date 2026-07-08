# ADR-0004 — PostgreSQL everywhere (Compose + Testcontainers)

**Status:** Accepted

## Context
The brief requires a real relational DB and no mock persistence or demo
shortcuts that hide gaps. The real decision is what tests run against: a
different engine (H2) hides dialect/constraint/JSON/sequence differences —
precisely the gaps we want surfaced.

## Decision
Use **PostgreSQL in every environment**:
- **Local dev:** a Postgres service in Docker Compose, auto-started by Spring
  Boot 4's docker-compose support on `./mvnw`.
- **Tests:** **Testcontainers** Postgres (same engine, Flyway applied).
- **Staging/prod:** external managed Postgres.

**No H2 anywhere.**

## Consequences
- dev = test = prod engine; no dialect drift.
- Docker must be running for local dev and for the integration test layer. This
  requirement is an honest ops finding, not a shortcut.
- First Testcontainers spin-up adds a few seconds to the integration suite.

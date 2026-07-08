# ADR-0005 — Flyway SQL migrations

**Status:** Accepted

## Context
Schema changes must go through migrations. Flyway (SQL-first) vs Liquibase
(XML/YAML/SQL changesets, DB-agnostic). With one engine committed (ADR-0004),
Liquibase's DB-agnostic abstraction buys nothing.

## Decision
Use **Flyway** with plain Postgres SQL migrations (`V1__*.sql`), auto-run by
Spring Boot on startup. Seed data (initial admin, allowance rates) is delivered
via versioned or repeatable migrations.

## Consequences
- Migrations are readable Postgres SQL, exercising the brief's "schema changes
  via migrations" directly.
- Migrations run at container startup — a real, observable deployment concern
  (ordering, failure, rollback) that will generate ops findings.
- Allowance rates are seeded by migration but **editable at runtime** by an
  admin (see ADR and the allowance feature in the plan); annual rate additions
  are a human task, not hard-coded.

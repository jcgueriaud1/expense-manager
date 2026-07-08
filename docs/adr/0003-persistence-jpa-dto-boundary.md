# ADR-0003 — Spring Data JPA + DTO/record UI boundary

**Status:** Accepted

## Context
The brief mandates a real relational DB with migrations from day one.
Candidates: Spring Data JPA (Hibernate), Spring Data JDBC, jOOQ. JPA is the
mainstream default with the richest docs and the best AI-agent familiarity. Its
known weak spot — lazy-loading / detached entities rendered in long-lived UI
components — is itself a representative Vaadin pain point worth surfacing.

## Decision
Use **Spring Data JPA / Hibernate**. The **UI layer consumes DTOs / Java records
returned by services, never live JPA entities.** Mapping entity ↔ DTO is done
**manually in the service layer** (no MapStruct) to keep moving parts minimal.

## Consequences
- Long-lived Vaadin components hold immutable read models, sidestepping
  lazy-init and detached-entity exceptions.
- Services own the transaction boundary and all mapping; entities never escape
  the service layer.
- Manual mapping is more boilerplate; if it becomes painful, reconsider
  MapStruct in a new ADR (and log the friction as a finding).
- Lazy-loading friction we *do* hit is logged as a Vaadin finding, not silently
  worked around.

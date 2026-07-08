# ADR-0008 — Route + method security; invariants in the domain

**Status:** Accepted

## Context
Vaadin route security (`@RolesAllowed`/`@PermitAll` via
`VaadinSecurityConfigurer`) controls which views a user can navigate to. It is
**not** a boundary on the domain operations themselves.

## Decision
Enforce authorization at **two layers**:
- **Route level** — annotations on views for correct navigation UX (admin views
  are `@RolesAllowed("ADMIN")`, authenticated views `@PermitAll`, login public).
- **Service method level** — `@PreAuthorize` / `@RolesAllowed` on service methods
  (`approve()`, `reject()`, user-management) as the real enforcement point
  (defense in depth).

**Business invariants** (state validity) stay in the domain (ADR-0006) and know
nothing about roles.

## Consequences
- Domain rules test without Spring; authorization tests via a security slice
  (a `USER` calling `approve()` is rejected).
- Any awkwardness wiring Vaadin route security to Spring method security is a
  documentable Vaadin finding.

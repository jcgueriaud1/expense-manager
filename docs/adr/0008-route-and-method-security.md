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

**admin ⊇ user** is expressed with a Spring **`RoleHierarchy`** bean
(`ROLE_ADMIN > ROLE_USER`), not by granting both roles at every provisioning/seed
site. An admin stores only `{ADMIN}`; the hierarchy grants USER access at
runtime, so `@RolesAllowed("USER")` views/methods admit admins automatically.
Stored roles and effective authorities therefore differ by design (admin's
effective set is `{ADMIN, USER}`).

**Business invariants** (state validity) stay in the domain (ADR-0006) and know
nothing about roles.

## CurrentUser accessor (Phase 1.4)
- `CurrentUser` returns an **immutable record** `CurrentUser(id, email, name,
  Set<Role> roles)` — never the live JPA `User` (ADR-0003).
- Both login paths build a principal implementing a common `AppUserPrincipal`
  interface that **carries the local user id/email/name/roles**; `CurrentUser`
  adapts the principal to the record with **no per-call DB query**.
- **Consequence — revocation timing:** because authorities are captured into the
  principal at login, an admin changing a user's `enabled`/roles takes effect
  **only at the user's next login / session expiry**, not immediately. Accepted
  for V1 (matches ADR-0007's "refused *at login*"). Immediate revocation
  (per-request `enabled` check or session-registry invalidation on revoke) is
  deferred to Phase 6 if wanted; logged as a known limitation.

## Consequences
- Domain rules test without Spring; authorization tests via a security slice
  (a `USER` calling `approve()` is rejected).
- Any awkwardness wiring Vaadin route security to Spring method security is a
  documentable Vaadin finding.

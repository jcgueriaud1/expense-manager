# ADR-0017 — Base UI shell: auto-menu navigation + shared UX-state primitives

**Status:** Accepted

## Context
Phase 0.7 stands up `MainLayout` (Aura) and the empty/loading/error patterns the
brief lists as UX deliverables, before any feature exists. Two conventions set
here are inherited by every view, so they are fixed up front.

## Decision
- **Navigation is auto-generated.** Views annotate themselves with `@Menu`
  (title/icon/order); `MainLayout` builds its `SideNav` from
  `MenuConfiguration`, which is automatically filtered by the user's access
  (ADR-0008). Features self-register their nav entry — no hand-maintained menu
  list. Role-aware filtering therefore comes for free.
- **Shared UX-state primitives live in `base/`, built now but thin:**
  - an `EmptyState` component,
  - a global error view via `HasErrorParameter` (404 + an uncaught-exception
    view),
  - a documented loading convention.
  These are skeletons; real polish lands with the first feature that renders
  each state.
- **A throwaway `@Route("")` home view** makes the shell demoable in Phase 0 and
  is replaced by the role-aware dashboard in Phase 1 (UC-007, plan 1.6).

### Phase 1 shell additions (UC-007)
- The dashboard is a **single adaptive `DashboardView @Route("") @PermitAll`**
  that renders role-conditional *content* (not separate user/admin routes);
  auto-menu already handles per-role *navigation*.
- Phase 1 content is **thin: greet the user by name + show their role.** No
  placeholder cards for unbuilt features — later phases accrete their own
  sections (P2 recent reports, P5 pending-approvals for admins, …).
- **Current-user identity + logout live in the `MainLayout` header** (logout via
  Vaadin `AuthenticationContext.logout()`), so they appear on every
  authenticated view, not just the dashboard.

## Consequences
- Adding a feature view automatically adds its nav entry, security-filtered — no
  MainLayout edit required.
- Features reuse the base primitives instead of reinventing empty/error/loading,
  keeping UX states consistent per the brief.
- The home view is disposable scaffolding; deleting/replacing it in Phase 1 is
  expected, not churn.

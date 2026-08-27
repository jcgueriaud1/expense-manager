# ADR-0017 — Base UI shell: auto-menu navigation + shared UX-state primitives

**Status:** Accepted, with the navigation decision **superseded by #146**

## Context
Phase 0.7 stands up `MainLayout` (Aura) and the empty/loading/error patterns the
brief lists as UX deliverables, before any feature exists. Two conventions set
here are inherited by every view, so they are fixed up front.

> **Superseded, 2026-08-27 (#146).** The first decision below — auto-generated
> navigation — no longer holds. The Figma design collapses eight views into three
> editorial groups (My Expenses, Admin Tasks, Reference Tables), a grouping no per-view
> annotation can express, so `@Menu` was removed from all eight views and the navigation
> is hand-authored in `NavGroup`. Access filtering did not survive that for free and is
> now explicit, reading the same `@RolesAllowed` / `@PermitAll` the router enforces
> through `AccessAnnotationChecker` — so ADR-0008 is unaffected. `AppLayout`, the
> `DrawerToggle` and the `SideNav` went with it; the shell is a top bar over a content
> card. Everything else here — the UX-state primitives, the adaptive dashboard, identity
> and logout living in the shell rather than per view — still stands, though identity and
> logout now sit behind the avatar. See
> [`../design/components/app-shell.md`](../design/components/app-shell.md).

## Decision
- **Navigation is auto-generated.** *(Superseded by #146 — see the note above.)* Views annotate themselves with `@Menu`
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
- ~~Adding a feature view automatically adds its nav entry, security-filtered — no
  MainLayout edit required.~~ Since #146, a new view must be added to `NavGroup` to be
  reachable from the navigation. That is the cost of an editorial grouping, and it is
  paid deliberately: the alternative was a nav the design does not describe.
- Features reuse the base primitives instead of reinventing empty/error/loading,
  keeping UX states consistent per the brief.
- The home view is disposable scaffolding; deleting/replacing it in Phase 1 is
  expected, not churn.

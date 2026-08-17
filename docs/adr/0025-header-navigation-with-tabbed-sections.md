# ADR-0025 — Header navigation: standalone destinations plus tabbed sections

**Status:** Accepted

## Context
ADR-0017 put navigation in an `AppLayout` drawer built from
`MenuConfiguration.getMenuEntries()`, so every `@Menu`-annotated view
self-registered its own, access-filtered nav entry. That property is worth
keeping. The shell it produced is not: by Phase 6 the drawer listed eight
entries across three hand-maintained sections, and six of the eight were
administrative screens a normal user never sees. The list grew with the app while
the everyday user's actual choices stayed at two.

A wireframe review settled on a header shell instead — logo, a centred menu, an
account avatar — with a handful of destinations rather than a list of screens:
Dashboard, My reports, Reference tables, Admin. That
is a flat-out contradiction of ADR-0017 on three counts, which is why this ADR
exists rather than a quiet refactor:

- it names the component ("`MainLayout` builds its `SideNav` from
  `MenuConfiguration`"),
- it promises "no hand-maintained menu list" and "no MainLayout edit required",
- it leans on the auto-menu for role-aware navigation ("comes for free").

A fixed three-item menu is, on its face, a hand-maintained list. The question
this ADR had to answer was whether the auto-registration property dies with the
drawer.

## Decision

**1. The shell is a header: logo · centred menu · account avatar.** No drawer.
`AppLayout` stays as the container even though its drawer is empty — the
component detects an empty drawer and collapses it, and the layout still supplies
what a hand-rolled header would have to reimplement: the measured navbar height
published as the content's top offset, the coarse-pointer bottom-bar re-slotting,
and Aura's app chrome, which is keyed to `vaadin-app-layout` selectors.

**2. A screen's place in the menu is declared by the screen, not listed in the
shell.** A `@Menu` view that names no layout is its own destination (Dashboard, My
reports). A `@Menu` view that names a *section layout* in its `@Route` becomes a
tab of that section — `ReferenceLayout` for the rate and classification tables,
`AdminLayout` for approvals, review history and user management. `MainLayout`
reads `@Route.layout()` to sort them, so the ADR-0017 property survives exactly
where the app grows: a new screen registers its own navigation by annotating
itself, and no edit to the shell is needed. The one hand-maintained thing left is
`MainLayout.SECTIONS` — a *name* per section, not a list of screens.

Sections are shells, not routes: both extend `TabbedSectionLayout`, which builds
the heading, the tab row and the content slot once.

**3. Role gating stays free.** `getMenuEntries()` is already access-filtered, so a
plain user's administrative set comes back empty and the Admin item is never
built. There is no `hasRole` check in the shell.

**4. Sectioned screens keep their flat routes.** `/approvals`, `/users`,
`/vat-rates`, … are unchanged; each opts into its section with
`@Route(layout = AdminLayout.class)` or `@Route(layout = ReferenceLayout.class)`,
and a section layout nests inside the shell via
`@ParentLayout(MainLayout.class)`. Every admin screen therefore stays
individually addressable and browser-back moves between them. The selected tab is
derived from the location after navigation, never from the click, so a deep link
lights the right tab.

**5. The screen title lives in the content, not the header.** The header holds
navigation only. Views render their own heading — a section layout renders one for
whichever of its screens is showing, so those screens don't each need theirs.

**6. Identity and sign-out stay in the header** (ADR-0017's one navigation clause
that survives intact), now behind the avatar's menu together with the
colour-scheme choice.

## Consequences
- Adding a screen to an existing section: annotate it `@Menu` and point its
  `@Route` at that section's layout. It appears as a tab, access-filtered, with no
  shell edit — as before.
- Adding a **standalone** destination is just `@Menu` with no layout — which means
  the everyday menu *can* grow by accident, the one place this design is looser
  than a hardcoded list. A new section needs one line in `SECTIONS`.
- Section membership is invisible at the `@Menu` annotation: the grouping lives in
  `@Route(layout = ...)`, one line above it. Worth knowing when reading a view.
- ADR-0017's non-navigation clauses are untouched: the shared `EmptyState`, the
  `HasErrorParameter` error view, the loading convention, and the single adaptive
  `DashboardView @Route("") @PermitAll`.
- `NavigationShellUiTest` now asserts the header menu and the admin sub-tabs
  instead of `SideNav` sections, including that a plain user gets no Admin item
  and that a deep link selects the right tab.

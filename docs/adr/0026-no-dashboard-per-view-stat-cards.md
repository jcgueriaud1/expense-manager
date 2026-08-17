# ADR-0026 — No dashboard: each screen carries its own numbers

**Status:** Accepted

## Context
ADR-0017 put an adaptive `DashboardView` at `@Route("")` and ADR-0025 kept it as
one of the header destinations. When we came to fill it in, the candidate content
turned out to be metrics that each belong to a screen that already exists:
"reports needing you" belongs to My reports, "queue depth" to Approvals, "rates
missing for next year" to Reference tables.

Two things made the dedicated page the worse option.

**It would need its own queries.** Every view already loads the rows its metrics
summarise — `MyReportsView` has the owner's reports in memory, `ApprovalQueueView`
the submitted ones, `UserManagementView` the users. A dashboard shares none of
that and would have to re-aggregate across everything.

**It can only ever point elsewhere.** A dashboard number is a fact you read,
memorise, and then go find. A number on the screen that lists the same rows can
*be* the control that narrows them.

For the plain user the page was also close to empty: they have one screen, so a
dashboard was a landing page whose only job was to link to the one place they
were going anyway.

## Decision

**1. There is no dashboard.** `DashboardView` is deleted. `@Route("")` is
`MyReportsView`, which also keeps `/reports` as a `@RouteAlias` so existing links
and the verification docs still resolve.

**2. Each screen carries the few numbers it can answer from data it already
holds**, rendered as a row of `StatCard`s above the content. First set:

| Screen | Cards |
|---|---|
| My reports | Needs you · In flight (with waiting time) · Reimbursed this year |
| Approvals | Awaiting review (count + €) · Oldest waiting · Distinct submitters |

**3. A stat card reads; it does not act.** No navigation, no filtering — the
filter row below it is the control. Making the cards filter the list was tried and
dropped: two ways to narrow the same list, one of them invisible until you notice
a card is pressed, is worse than one obvious way. What still makes the in-view
number better than a dashboard tile is that the number and the rows it counts are
on the same screen, so a disagreement between them is visible.

**4. Cards are the official `Card` component** (`StatCard extends Card`), so
padding, radius and slot spacing come from its base styles as
`--vaadin-card-*` tokens, with Aura supplying the surface colour. Ours is only
"big number", the row's equal columns, and the surface level (4, opaque — white in
light mode, the lightest lifted surface in dark).

**5. A menu of one is not a menu.** With no dashboard, a plain user can reach
exactly one screen, which is also where `""` lands — so `MainLayout` renders no
navigation at all when fewer than two destinations are accessible. Admins still
get My reports · Reference tables · Admin. This needs no role check: the shell
counts the access-filtered destinations.

## Consequences
- The greeting ("Welcome, …") and the role sentence that `DashboardView` carried
  are gone. The user's name lives in the account menu; the app no longer tells you
  your role, which nothing depended on.
- ADR-0017's "single adaptive `DashboardView @Route("")`" clause is retired, as is
  the plan's UC-007. ADR-0025's three-destination menu becomes two-or-more.
- `ReportSummaryDto` gained `submittedAt`, folded from the status history the
  aggregate already walks — no migration, no extra query. It is what lets the list
  say how long something has been waiting.
- Metrics that need data the list does not carry — missing receipts, median time
  to decision, reference-data health — are deliberately **not** built yet. Each
  costs one projection field or one query, and should be added to the screen that
  owns it rather than to a new page.
- A stat card must always be computed from the same list the view renders, never
  from a second source: the number and the rows are on screen together, so any
  disagreement is visible — which is the point, and also the constraint.

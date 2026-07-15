# Manual / visual verification

How to smoke-test the app by hand — or drive it with the Playwright MCP —
**without clicking through any setup**. Two levers make this cheap: a local data
seeder that lands you directly on the screen under test, and an interaction
pattern that keeps a Playwright run small (issue #68).

## Lever 1 — Seeded report fixtures

Running the app in the **`local`** profile on an **empty database** seeds four
labelled expense reports via
[`LocalReportSeeder`](../src/main/java/com/vaadin/expensemanager/report/LocalReportSeeder.java)
(mirroring `LocalUserSeeder`). Each report has one €100.00 line and an
`[seed] …` `additionalInformation` label so it is unmistakable in the UI:

| Fixture label                                      | Owner                         | Status      | Exercises                          |
|----------------------------------------------------|-------------------------------|-------------|------------------------------------|
| `[seed] DRAFT — plain user, edit path`             | `user@vaadin.com`             | `DRAFT`     | owner edit / submit path           |
| `[seed] SUBMITTED — plain user, review + approve`  | `user@vaadin.com`             | `SUBMITTED` | approval queue → review → approve  |
| `[seed] SUBMITTED — admin-owned, cross-owner queue`| `jean-christophe@vaadin.com`  | `SUBMITTED` | cross-owner queue visibility       |
| `[seed] APPROVED — plain user, owner-sees-approved`| `user@vaadin.com`             | `APPROVED`  | owner opens an approved report     |

**Guarantees:**

- **`local`-only.** The seeder is `@Profile("local")`, so it never runs in
  `staging`/`prod` (real user data, ADR-0007) nor in `test` (the browserless
  suites seed and assert their own fixtures).
- **Idempotent.** It only seeds when the report table is empty, so it survives
  restarts and the persistent Compose volume — it never duplicates or clobbers
  reports you created by hand.
- Because it seeds only an empty DB, to regenerate the fixtures on a dirty local
  DB, clear the reports first:
  `docker exec expense-manager-postgres-1 psql -U expense -d expense_manager -c "TRUNCATE expense_report RESTART IDENTITY CASCADE;"`
  then restart the app.

### Logging in

Local uses the form-stub (ADR-0012); the shared dev password is `expense`.

| Role  | Email                        |
|-------|------------------------------|
| User  | `user@vaadin.com`            |
| Admin | `jean-christophe@vaadin.com` |

Deep-link straight to the screen you want — an unauthenticated hit redirects to
`/login`, and after signing in you land back on it:

- `/reports` — the owner's report list (log in as the user).
- `/approvals` — the admin approval queue (log in as the admin).
- `/report?reportId=<id>` — a single report.

## Lever 2 — Cheap Playwright interaction pattern

The dominant cost of a Playwright run is **large `browser_snapshot`
accessibility-tree dumps** and **redundant setup clicks**, not the assertions.
Keep runs small:

- **Seed, don't click.** Ensure the fixtures above exist before driving the UI;
  never click through create → line → submit → approve just to reach a state.
- **Deep-link with stable selectors.** Navigate directly to the route, and drive
  elements by button text / `aria-label` / stable `name` attributes rather than
  `browser_snapshot` → find-ref → click loops. For the login form,
  `input[name="username"]` / `input[name="password"]` + pressing **Enter**
  submits without hunting for the shadow-DOM submit button.
- **Batch form entry** with `browser_fill_form` (one call, many fields) instead
  of many `browser_type` calls.
- **Screenshot only unique visual states** — layout, colour, status badges,
  callouts. Don't re-drive flows the browserless tests (pyramid layer 3) already
  cover behaviourally; those assertions belong there, not in a browser run.
- **Scope any snapshot you do need** (`depth`, `filename`) rather than dumping
  the full tree into context.

Target: the kind of flow that once took ~52 calls (half of it UI setup) drops to
~12 with far fewer tokens.

An agent driving this should follow the `visual-verification` skill
(`.claude/skills/visual-verification`), which the `implement-use-case` skill's
step 4 delegates to.

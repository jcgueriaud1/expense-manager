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
| `[seed] REJECTED — plain user, edit + resubmit`    | `user@vaadin.com`             | `REJECTED`  | owner edits + resubmits (Phase 5.5)|

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
- `/report/<id>` — a single report (a path segment, e.g. `/report/5`; the
  bare `/report` opens a fresh transient report).

### The shell moved in #146 — three things a run depends on

The drawer, the drawer toggle and the side nav are gone; the shell is a coral top bar
over a content card ([`design/components/app-shell.md`](design/components/app-shell.md)).
Three habits break:

- **Signing out is a menu item, not a button.** It lives behind the avatar, so
  `findButton("Sign out")` and any selector looking for a `vaadin-button` finds nothing.
  Click the avatar first — `vaadin-menu-bar-button[first-visible]`, since the MenuBar
  also renders a hidden overflow button and a bare `vaadin-menu-bar-button` selector is
  ambiguous — then `vaadin-menu-bar-item:has-text("Sign out")`. This is the step that
  matters most: switching between the user and the admin is how most runs begin.
- **The colour-scheme switcher moved with it.** Avatar → **Colour theme** → System /
  Light / Dark, two levels of `vaadin-menu-bar-item`. To skip the clicks when you only
  need the pixels, set `document.documentElement.style.colorScheme` directly and clear
  `localStorage['expense-manager.color-scheme']`.
- **Navigating between sections goes through the nav pills.** `My Expenses` is a link;
  `Admin Tasks` and `Reference Tables` are buttons that open a menu of links
  (`vaadin-button:has-text("Admin Tasks")`, then the item). Deep-linking is still
  cheaper — prefer it.

The current group is readable without a screenshot, which makes it a cheap assertion:

```js
[...document.querySelectorAll('.app-nav__item')]
    .map(e => e.textContent.trim() + '=' + e.getAttribute('aria-current'))
```

### Editing CSS while the app is running

`./mvnw` serves the theme from `target/classes`, not from `src`, so an edit to
`META-INF/resources/styles.css` changes nothing until the resource is copied — and the
page reloads looking exactly as before, which reads as "my rule is wrong" rather than
"my rule is not there". Copy it and reload:

```bash
cp src/main/resources/META-INF/resources/*.css target/classes/META-INF/resources/
```

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
  of many `browser_type` calls — but **leave ComboBoxes out of the batch**: a
  `type: "combobox"` field fails with "Element is not a `<select>` element" and
  takes the whole batch down with it (F-054). Drive each ComboBox with two clicks
  instead: its `input` (`#input-vaadin-combo-box-<n>`), then
  `vaadin-combo-box-item:has-text("…")` in the overlay.
- **A `DateTimePicker` needs its own event pair, and fails silently without it**
  (F-055). Neither `browser_fill_form` on the inner date/time inputs nor setting
  `picker.value` commits to the server — the dialog *looks* filled and only the
  error summary says otherwise. From `browser_evaluate`, set `.value` on the
  `vaadin-date-time-picker` and dispatch **both**
  `new CustomEvent('value-changed', {detail: {value}, bubbles: true, composed: true})`
  **and** a bubbling `change`. (A plain `vaadin-text-field` / `vaadin-text-area`
  does commit from an `input` + `change` pair on its inner `input`/`textarea`.)
- **A `vaadin-integer-field` needs the same host-element pair.** `input` + `change`
  on its inner `input` does *not* commit — worse than silent: the server keeps the
  field's pre-filled default, so an override typed as `1` saves as the calculated
  `2` and everything downstream looks merely surprising rather than broken. Set
  `.value` on the `vaadin-integer-field` itself and dispatch `value-changed` +
  `change`, as for the picker.
- **Find fields by their `<label>` text, not by `label=` or the overlay.** A V25
  field carries no `label` attribute (the label is a child `<label>` element), and
  dialog content lives in the **light DOM** — `closest('vaadin-dialog-overlay')`
  matches nothing, so `document.querySelector('vaadin-text-area')` in an open dialog
  happily returns the *view's* field behind it. (An override reason typed that way
  lands in the report's "Additional information".) Select with
  `[...document.querySelectorAll(tag)].find(el => el.querySelector('label')
  .textContent.includes('…'))`.
- **Trips are not seeded** (F-056), so anything Phase 4 — per-diem, kilometre, meal,
  parking, generated-line receipts, Quantity Override — still costs a pass through
  `TravelEditorDialog`. Put every trip a run needs on **one** report so the login,
  navigation and save costs are paid once.
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

---
name: figma-visual-verification
description: >
  Visually verify a Vaadin Flow view against the Figma design it was implemented from, using
  the Playwright MCP server to render the running app and a Figma screenshot as the reference.
  Use this after implementing or changing a Vaadin view from a Figma design, when the user asks
  to "verify against the design", "check it matches Figma", "visually verify the view", or as a
  follow-up step after figma-to-vaadin. Produces a prioritized, actionable list of visual
  discrepancies — it does not fix them. Does NOT apply to backend/business-logic testing or
  general UI test suites — this is visual, design-fidelity verification only.
compatibility: Requires the Playwright MCP server, Figma MCP, and a runnable instance of the target app
---

# Figma Visual Verification

## Purpose

Compare a live, rendered Vaadin view against the Figma design it was built from, and produce a
prioritized list of concrete visual differences — not a pass/fail, and not a vague "looks good."

## Inputs

Needed from the caller (usually `figma-to-vaadin`, right after it finishes a view):

- The Figma URL, or `fileKey` + `nodeId`, the view was implemented from
- The route/URL of the implemented view in the app

Only the Figma node is genuinely unknowable here — ask for it rather than reusing a stale
screenshot. How to start the app, how to log in, and which routes exist are all fixed for this
repo; see **Reaching the app** below, and don't ask the user for them.

## Reaching the app

Every route in this app requires authentication, and setting up the state a design frame shows
costs far more in clicks than it does in seeding. Both are already solved — the full detail is
in [`docs/manual-verification.md`](../../../docs/manual-verification.md); this is the short
version.

### 1. Start it

`./mvnw` from the repo root is the single documented command. It boots the **`local`** profile
by default and Spring Boot's Docker Compose support auto-starts PostgreSQL — Docker must be
running. Wait for the `Started Application in ...` log line before loading any page. The app
serves on `http://localhost:8080` (`PORT` overrides).

### 2. Log in — never screenshot before this

`local` uses the form-stub login (ADR-0012). The shared dev password is **`expense`**.

| Role  | Email                        | Use for                             |
|-------|------------------------------|-------------------------------------|
| User  | `user@vaadin.com`            | own reports, edit/submit paths      |
| Admin | `jean-christophe@vaadin.com` | the approval queue, cross-owner views |

Navigate straight to the target route first: an unauthenticated hit redirects to `/login`, and
after signing in you land back on the route you asked for. Drive the form with
`input[name="username"]` / `input[name="password"]` and press **Enter** — that submits without
hunting for the shadow-DOM submit button.

### 3. Seed, don't click

On an **empty** database the `local` profile seeds four labelled expense reports via
`LocalReportSeeder`, each carrying an `[seed] …` label so it is unmistakable in a screenshot:
a DRAFT and a REJECTED report for the owner-edit path, two SUBMITTED reports (one plain-user,
one admin-owned) for the approval queue, and an APPROVED one. Use them — never click through
create → add line → submit just to reach a state worth photographing.

The seeder is `local`-only and idempotent (it seeds only when the report table is empty). To
regenerate on a dirty local DB, truncate the reports and restart — the command is in
`docs/manual-verification.md`.

Trips are **not** seeded (finding F-056), so any Phase 4 frame — per-diem, kilometre, meal,
parking, generated-line receipts, Quantity Override — still costs a pass through
`TravelEditorDialog`. Put every trip a run needs on **one** report.

### 4. Deep-link routes

- `/reports` — the owner's report list (log in as the user)
- `/approvals` — the admin approval queue (log in as the admin)
- `/report/<id>` — one report, e.g. `/report/5`; bare `/report` opens a fresh transient report

### 5. Driving fields, if a frame needs a populated form

`browser_fill_form` batches many fields in one call, but three components need care — each
fails *silently*, leaving a form that looks filled and a screenshot that lies:

- **ComboBox** — leave it out of the batch entirely; a `type: "combobox"` field takes the whole
  call down with "Element is not a `<select>` element" (F-054). Click its `input`
  (`#input-vaadin-combo-box-<n>`), then `vaadin-combo-box-item:has-text("…")` in the overlay.
- **DateTimePicker** (F-055) and **IntegerField** — neither commits from an `input`/`change`
  pair on the inner input. Set `.value` on the *host* element and dispatch both
  `new CustomEvent('value-changed', {detail: {value}, bubbles: true, composed: true})` and a
  bubbling `change`.
- **Finding a field** — a V25 field has no `label` attribute, and dialog content lives in the
  **light DOM**, so `document.querySelector('vaadin-text-area')` inside an open dialog can
  return the *view's* field behind it. Select by the child `<label>`'s text:
  `[...document.querySelectorAll(tag)].find(el => el.querySelector('label').textContent.includes('…'))`.


## Workflow

### 1. Ensure the app is running — and that you are logged in

Follow **Reaching the app** below. Upstream this skill has no authentication step at all;
every route in this app is behind a login, so skipping it screenshots `LoginView` and the
whole comparison is against the wrong page.

### 2. Capture the reference

`get_screenshot` on the Figma node the view was implemented from. This is the ground truth —
re-fetch it even if a screenshot was already seen during implementation, in case the design
changed since.

### 3. Capture the implementation

Using the Playwright MCP server (check the exact tool names available in your session — common
ones are `browser_navigate`, `browser_resize`, `browser_take_screenshot`, `browser_snapshot`
for the accessibility tree, and `browser_console_messages`):

- Navigate to the view's URL
- Resize the browser to match the Figma frame's dimensions where practical, otherwise a
  standard desktop size (1440×900)
- Take a full screenshot of the view
- Capture the browser console too — layout bugs and JS errors both matter, and the console
  surfaces things a screenshot alone won't (e.g. a component that silently failed to render)
- If the view has distinct states the Figma design also shows (e.g. a selected grid row
  revealing a detail panel), reproduce and capture each of those states

### 4. Compare

Go element by element, not just "does it look similar at a glance":

- **Layout** — spacing, alignment, sizing, padding, including nested-layout issues like doubled
  or collapsed padding, overlapping or overflowing elements
- **Viewport & scroll** — does the contents fit in the browser viewport? Does the view fill the full width? Are there nested scrollable areas? Confirm precisely with `browser_evaluate` — compare `document.documentElement.scrollHeight`/`scrollWidth` against `window.innerHeight`/`innerWidth`; any mismatch means real overflow, even if a screenshot alone looks fine (a screenshot only shows what's currently in view — content pushed below the fold by overflow, like footer action buttons, can look simply "missing" rather than "present but unreachable")
- **Typography** — heading levels, font size/weight, line height
- **Color & contrast** — text readable against its background, status colors (badges, errors)
  match intent, colors correct for the active color scheme if the app supports both light and
  dark
- **Component fidelity** — does the rendered component match Figma: orientation variant, color variant, theme variant, missing or
  extra elements
- **Console errors** — JS errors, failed component registrations, or Vaadin dev-mode warnings
  that indicate something didn't render as intended

### 5. Report findings

Produce a prioritized, actionable list — not a narrative. For each finding, give:

- **Severity** — `blocker` (broken or missing functionality/component), `high` (visibly wrong
  in a way a user would notice immediately, e.g. wrong orientation or color), `low` (minor
  spacing/polish difference)
- **Location** — which view/component
- **Expected vs. actual** — what Figma shows vs. what rendered
- **Suggested fix** — a concrete code-level suggestion where possible (e.g. "add
  `RadioGroupVariant.AURA_HORIZONTAL`"), not just "fix the spacing"

Sort by severity, blockers first. If there are no findings, say so explicitly rather than
omitting the report.

## Boundaries

- This skill only observes and reports — it does not edit code. Applying fixes is a separate
  step (see `figma-to-vaadin`'s `verification: verify-and-fix` option, which applies exactly
  one round of fixes from this report and does not automatically loop back into a second
  verification pass).
- Don't rely on a single full-page screenshot alone — contrast and spacing issues are often
  only visible up close; take additional close-up screenshots of the specific regions where you
  suspect or find issues.

---

## Provenance

- **Upstream:** [https://github.com/juuso-vaadin/figma-to-vaadin-skill](https://github.com/juuso-vaadin/figma-to-vaadin-skill)
- **Source path:** `skills/vaadin-visual-verification/SKILL.md`
- **Commit:** `3a9289c` (`3a9289c15df9e7a7659f0d92fee204ad1dc65c14`)
- **Copied:** 2026-08-26 — by hand, as a project-owned file. **Not** managed by
  `skills.sh` / `skills-lock.json`.
- **Locally modified:** yes
  - **Renamed** `vaadin-visual-verification` → `figma-visual-verification`, in both the
    directory name and the frontmatter `name`. Everything in this repo is Vaadin, so
    `vaadin-` carries no information; `figma-` names the distinguishing feature — Figma's
    `get_screenshot` as the ground truth. It also keeps this skill from being a coin-flip
    against the repo's existing `visual-verification` skill during the trial period.
  - Added the **Reaching the app** section. Upstream has **no authentication handling at all**;
    unmodified, it would screenshot `LoginView` for every route in this app. Derived from
    [`docs/manual-verification.md`](../../../docs/manual-verification.md).
  - Workflow step 1 and the **Inputs** list now point at that section instead of asking the
    user how to start the app or which route to use.

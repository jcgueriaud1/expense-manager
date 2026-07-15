---
name: visual-verification
description: Visually verify an implemented use case using Playwright MCP. Use after implementing UI changes.
---

# Visual Verification

Use Playwright MCP to visually validate the implemented use case.

Unless the use case specifies a particular resolution, use **1920x1080** as the browser resolution.

## Set up state cheaply — seed, don't click

The dominant cost of a Playwright run is **setup clicks** and **large
`browser_snapshot` accessibility-tree dumps**, not the assertions. Remove both:

- **Seed, don't click.** Running the app in the `local` profile on an empty DB
  seeds labelled DRAFT/SUBMITTED/APPROVED report fixtures via `LocalReportSeeder`,
  so you log in and land directly on the screen under test — never click through
  create → add line → save → submit just to reach a state. The fixtures, the
  logins (form-stub, dev password `expense`), and the deep-link routes are in
  [`docs/manual-verification.md`](../../../docs/manual-verification.md).
- **Deep-link with stable selectors.** Navigate straight to the route and drive
  elements by button text / `aria-label` / stable `name` attributes rather than
  `browser_snapshot` → find-ref → click loops. For the login form,
  `input[name="username"]` / `input[name="password"]` + **Enter** submits without
  hunting for the shadow-DOM submit button.
- **Batch form entry** with `browser_fill_form` (one call, many fields) instead
  of many `browser_type` calls.
- **Don't re-verify behaviour** the browserless tests (pyramid layer 3) already
  cover — those assertions belong there. Screenshot only the **unique visual
  states**.
- **Scope any snapshot you do need** (`depth`, `filename`) rather than dumping the
  full tree into context.

## Steps

All the steps listed here must be done and all details are important. The goal is to be thorough instead of quick.

1. Ensure the application is running, with the seeded fixtures present (see above)
2. Navigate to every route defined in the use case's UI/Routes section
3. Perform each step from the use case's main flow
4. Take screenshots of key interaction points
5. Validate the visual appearance according to the validation rules below
6. Record results -- note any visual issues in the per-use-case checklist below

## Validating Visual Appearance

The most important part is to verify what the user sees, i.e. a screenshot.
DOM, CSS rules etc can be used as helpers but the screenshot is what really matters.

1. Layout matches expectations (spacing, alignment, sizing)
2. Spacing & padding are consistent -- content has appropriate breathing room, no cramped or excessively spaced areas. Verify that nested layouts (e.g., AppLayout > VerticalLayout > card) don't double-up padding or collapse it.
Compare padding between similar views (e.g., all admin views should have the same content padding).
3. Typography is readable and consistent
4. Interactive elements are clearly identifiable
5. Responsive behaviour works at common breakpoints (mobile, tablet, desktop)
6. Text contrast and readability
  - All text is clearly readable against its background (titles, labels, values, badges)
  - Colored text (warning/error values, status badges) has sufficient contrast
  - Elements that inherit from a different color scheme (e.g., dark sidebar vs light content) render correctly -- CSS custom properties like `var(--vaadin-background-color)` may resolve differently depending on the inherited color scheme
  - No backgrounds swallow their content text

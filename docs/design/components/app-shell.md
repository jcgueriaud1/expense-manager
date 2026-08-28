# App shell

**Category:** shell
**Origin:** design
**Implementation:** unaudited
**Code:** `com.vaadin.expensemanager.base.ui.MainLayout`, `AppHeader`, `NavGroup`,
`HeaderState`, `HasHeaderState`; `styles.css` — the `.app-shell*` / `.app-header*` /
`.app-nav*` classes; `aura-theme.css` — `--em-header-color`, `--em-header-text-color`
**Design:** node `116:3876` › `Header` (five states), `116:2276` › `Main Link`,
composed in frame `116:4444`

## Overview

The app-wide navigation frame (ADR-0017): a full-width coral bar carrying the logo, three
navigation links and an avatar, over a rounded card whose top corners tuck 35px under the
bar and hold a centred 900px content column.

**This file describes what the design asks for.** It replaced a description of the old
`AppLayout` drawer shell when #146 built the redesign; the drawer, the `DrawerToggle` and
the `SideNav` are gone, and so is `--aura-app-layout-radius`, which only ever styled
`AppLayout`'s content area.

Use it for the frame around a route, and nothing else. A view that wants a heading, a
toolbar or a hero of its own builds one inside the content column — the only thing a view
may ask of the shell is *which of the five header states it gets*.

## Anatomy

| Part | Element | Design node |
|---|---|---|
| Shell root | `MainLayout extends Div implements RouterLayout` — `.app-shell` | — |
| The bar | `AppHeader extends Header` — `.app-header` | `116:3876` |
| Bar's inner row, max 1400px | `.app-header__row` | `116:2296` "Top" |
| Logo + wordmark, linking to `/` | `RouterLink` — `.app-header__logo` | `116:2299` |
| Navigation | `Nav` — `.app-nav` | `116:2304` "Main meny" |
| One group | `RouterLink` or `Button` + `ContextMenu` — `.app-nav__item` | `116:2276` |
| Avatar and its menu | `MenuBar` + `Avatar` — `.app-header__account` | `23:27` |
| Greeting hero (HOME only) | `.app-header__hero` | `116:2310` "CTA" |
| Content card | `.app-shell__card` | `116:4446` "View Frame" |
| Centred column | `Main` — `.app-shell__content` | `116:4917` "content-wrap" |

**One pill, two element types.** A group with a single destination renders as a
`RouterLink` — a real link, middle-clickable. A group with several renders as a button
opening a menu of links, because the design draws one pill per group and the app has
eight routes to reach through three of them. They are styled to be indistinguishable.

The design draws no chevron on either, and Aura's default dropdown indicator on the
avatar is suppressed to match. `aria-haspopup` still announces the menu.

## Tokens used

| Part | Token | Design value |
|---|---|---|
| Bar fill | `--em-header-color` + a written-out gradient wash | `#f16c4e`; `rgb(0 0 0 / .1)` → `rgb(255 255 255 / .1)` at 90° |
| Bar text, logo, pills | `--em-header-text-color` | `#ffffff` |
| Bar focus ring | `--vaadin-focus-ring-color`, re-pointed at the text colour | — |
| Bar vertical padding | `--em-card-padding` | 20 |
| Logo gap, nav gap | `--vaadin-gap-s` (8) | 10 — **accepted**, 2px |
| Pill padding | `--vaadin-padding-s` / `--vaadin-padding-l` (8/16) | 10/15 — **accepted**, 1–2px |
| Pill text | `--aura-font-size-l`, `--aura-font-weight-semibold` | 16, 600 |
| Card radius | `--em-card-radius` | 12 |
| Card surface | `--aura-background-color` | — |
| Status tints | `--aura-green` / `--aura-blue` / `--aura-red` | the design binds these |
| Small-screen greeting | `--em-font-size-title` (24) | undesigned |

**Off-scale values, decided here** — each is written as a literal in the `.app-shell*`
rules with the design value in a comment, because each occurs once and a project property
for a single use is where a parallel scale starts:

| Value | Where | Why not a token |
|---|---|---|
| 350px / 120px | bar height, tall / compact | a component height, not a scale step |
| −35px | card's overlap of the bar | ditto |
| 1400px | the bar's inner row | ditto |
| 900px | the content column | the design's 900-in-1800 proportion |
| 80px | content column's top inset | beyond `--vaadin-padding-xl` 24 |
| 60px | hero bottom padding | beyond the scale |
| 50px / 20px | greeting / hero status line | beyond `--em-font-size-title` 24 |
| 100px | pill radius | a pill, not a corner; beyond `--vaadin-radius-l` 15 |
| 441×236 | the illustration | the asset's own geometry |

### The coral, and the contrast it fails

The design paints the bar a coral bound to **no Figma variable**, and Aura has no orange
near that hue — `--aura-orange` is oklch hue 87, a gold, where this is ~37. So it is the
app's own value.

**White on `#f16c4e` measures 3.00:1** — 2.70:1 to 3.64:1 across the gradient wash. That
clears WCAG AA for the 50px greeting (large text, 3:1) and for non-text contrast, and
**fails** the 4.5:1 required of the 16px nav links, the 16px wordmark and the 20px hero
line. The green `APPROVED` tint is 3.50:1 and fails the same way; blue (5.05:1) and red
(4.55:1) pass.

This shipped **as drawn**, on an explicit call in #146, overriding the contrast floor that
[ADR-0025](../../adr/0025-figma-design-source-of-truth.md) decision 3 otherwise puts above
the design. The two alternatives, both measured and both declined, were: darken the coral
to `#bf533c` (white reaches 4.60:1 at the same hue), or keep the coral and take Aura's own
computed contrast colour, a near-black at 5.6:1. **#160** carries the measurements back to
the designer.

**Do not copy this pairing to a new surface.** It is a recorded exception for one bar, not
a licence for white-on-saturated anywhere else.

### The text colour is pinned, not inherited

The Figma kit binds the bar's text to `aura-accent-contrast-color`. That property tracks
the *accent*, not this bar: it reads white only because the accent is blue, and Aura's own
formula flips it to black once the accent's lightness passes 0.62. Bound to the header it
would one day turn the label black on a coral that never moved. `--em-header-text-color`
is pinned instead, and the divergence is recorded here rather than carried silently.

### Reaching the heading costs an explicit `color: inherit`

Pinning the token is half the job; the bar still has to *deliver* it. `.app-header`
declares `color: var(--em-header-text-color)` once and every descendant inherits it —
except the ones Aura re-declares at element level. Aura's reset

```css
:where(h1,h2,h3,h4,h5,h6) { color: var(--vaadin-text-color); }
```

has specificity 0,0,0, which is *lower* than `.app-header`'s, and still wins: the bar's
white arrives at the greeting by **inheritance**, and an inherited value loses to any
declaration matching the element itself at any specificity. So the greeting `H1` carries
`color: inherit` of its own. The logo (`a`, Aura's other element-level colour reset) has
always carried it, and the pills declare the token directly.

**Rule for anything added to this bar:** an `h1`–`h6` or an `a` needs its own
`color: inherit`, or it renders `--vaadin-text-color` on coral. Everything else — `Span`,
`Div`, Vaadin components — inherits correctly. Nothing errors when this is missed and the
text stays legible (black on `#f16c4e` measures 6.99:1, *better* than the design's white),
so neither the build nor a contrast check reports it. F-072.

## API

```java
// A view choosing its header. DEFAULT needs no code.
public class MyReportsView extends VerticalLayout implements HasHeaderState {
    @Override public HeaderState headerState() { return HeaderState.HOME; }
    @Override public String headerMessage() { return "2 items need your attention"; }
}

// A view whose header depends on data it loads later.
findAncestor(MainLayout.class).setHeaderState(HeaderState.REJECTED);
```

`HeaderState` — `HOME` (tall, coral, greeting hero) · `DEFAULT` (compact, coral) ·
`APPROVED` (green) · `IN_PROGRESS` (blue) · `REJECTED` (red). The design folds height and
tint into one property and this enum keeps that shape; splitting them would invent three
combinations nobody drew.

The three status tints are built and unused. The report detail view is redesigned in its
own issue and will ask for them then; they are the same code path as `DEFAULT`, and adding
them later would mean reopening the shell.

## Navigation

Three groups, hand-authored in `NavGroup` — `@Menu` was removed from all eight views with
#146, because the grouping is editorial and no per-view annotation can express it.

| Group | Renders as | Routes behind it |
|---|---|---|
| My Expenses | link | `/reports`, and `/report/<id>` lights it without being an entry |
| Admin Tasks | menu | `/approvals`, `/approval-history`, `/users`, and `/review/<id>` |
| Reference Tables | menu | `/vat-rates`, `/expense-types`, `/allowance-rates` |
| *(none)* | — | `/` — the design gives the dashboard no nav item; the logo links there |

**Current follows the group, not the route.** Sitting on `/report/5` keeps *My Expenses*
current. The pill carries `aria-current="page"`, which is the half a screen reader reads.

`/review/<id>` is `ReportDetailView` behind a second route — the approver's path, where
`/report/<id>` is the owner's. The class cannot tell them apart, so Admin Tasks claims the
alias by reading it off the `@RouteAlias` annotation. The design never drew the approver's
screen, so which group it belongs under is a judgement, taken here and open to the report
detail issue revisiting it.

Access filtering reads each view's own `@RolesAllowed` / `@PermitAll` through
`AccessAnnotationChecker` — the same annotations the router enforces (ADR-0008). A group
with no reachable entry renders nothing, so the plain user's bar carries one link, not
three greyed ones.

## States

| State | Behaviour |
|---|---|
| default | pill transparent on the bar; the bar's own five states are the `HeaderState` enum, not UI states |
| hover | stock Aura on the button-backed pills; **unverified on the link-backed one**, which has no hover rule of its own — the two are meant to be indistinguishable and this is the one place they may not be |
| active (pressed) | stock Aura on the button-backed pills; none on the link |
| **current** | `.app-nav__item--active` — `rgb(0 0 0 / 0.2)` fill, 100px radius, `aria-current="page"` |
| focus | Aura's focus ring, re-pointed at the bar's text colour: the accent blue measures 1.6:1 against the coral, below the 3:1 a focus indicator needs; white reaches 3.0:1 |
| disabled | n/a — a route the user cannot reach is not rendered (ADR-0008) |
| error | n/a — routing failures render `ErrorView` / `NotFoundView` inside the card |

## Small screens

Undesigned (#145 §3), and the shell now does the drawer's old job, so **nothing hides**.
Below 700px the bar's row wraps, putting the logo and avatar on one line and the nav on
its own; the three pills wrap again at 360px rather than being clipped. The illustration
is dropped and the greeting falls to `--em-font-size-title`. The card's overlap shrinks
to 20px.

Verified at 360px: no horizontal overflow from the shell on any route it wraps, in both
colour schemes.

> `/users` **does** overflow at 360px — its own filter row is a non-wrapping
> `HorizontalLayout` of two ComboBoxes. Measured at full bleed it still overflows to
> 536px against a 360px viewport, so it predates this shell and is not caused by the
> 900px column. It belongs to that view's own redesign.

## Cross-references

[`theme-switcher.md`](theme-switcher.md) — moved into the avatar menu ·
[`../foundations/color.md`](../foundations/color.md) ·
[`../tokens/token-reference.md`](../tokens/token-reference.md) ·
ADR-0017 (the shell), ADR-0008 (route security), ADR-0025 (the design as contract) ·
**F-070** (the proxied view class), **F-071** (menu items and the browserless tester) ·
**#160** — the measured design defects, contrast among them · **#145** — the open design
questions behind the small-screen behaviour and the avatar menu

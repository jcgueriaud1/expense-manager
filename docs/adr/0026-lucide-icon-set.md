# ADR-0026 — Lucide is the app's icon set, delivered as one vendored SVG sprite

**Status:** Accepted

## Context

Until #163 every glyph in the app came from `VaadinIcon`, and each one was a
hand-picked substitute for whatever the design drew. Nothing referenced Lucide:
`grep -ri lucide` over the repo returned nothing.

Two substitutions were wrong at once, and only the first is about pictures:

- **Visual language.** Vaadin Icons are filled, heavier glyphs on a 16px grid.
  Lucide is a 24px outline set at 2px stroke. Side by side with the Figma frames the
  weight mismatch reads as a different product, and no amount of per-node token work
  fixes it — it is the wrong set, not the wrong size.
- **Addressing form.** `"vaadin:inbox"` and `"lumo:plus"` are the Lumo *font-icon*
  addressing form, and this app is Aura-only. `EmptyState` took exactly such a
  string, which quietly pinned all five of its callers to a Lumo collection.

Neither `VaadinIcon` nor `LumoIcon` is *broken*, and this ADR does not claim
otherwise. `CLAUDE.md`'s test for a theme reference is whether the theme defines it,
and both pass: they are supported Vaadin 25.2 icon sets on the classpath, and
`LumoIcon` is what the Figma annotations correctly prescribe for the Vaadin
components' own internals (F-062). They are the wrong set for *this design*, which
is a different and narrower charge.

### What the design actually draws

The survey behind #163 resolved this against page `88:12278` rather than assuming
it, and the answer is not what the issue supposed. **The design does not draw every
glyph from Lucide.** It draws exactly three, and they are hand-pasted SVG frames
with no design-system annotation:

| Layer | Occurrences | Where |
|---|---|---|
| `lucide/chevron-up` | 4 | report-list section headers |
| `lucide/plane` | 2 | report-card trip rows |
| `lucide/copy` | 1 | an off-frame symbol, never placed in a frame |

Everything else glyph-shaped in the designed frames belongs to a **Vaadin DS kit
component** and is annotated as Lumo — the primary button's plus is node `36:902`,
annotated `<vaadin-icon icon="lumo:plus">`. And several glyphs the app has today are
*deliberately absent* from the design: the search field's `Prefix` slot is
`hidden="true"`, and the reference grids' Edit/↑/↓ buttons are drawn as a single `⋮`
overflow menu (`178:1926`) opening a **text-only** context menu.

So of the three Lucide glyphs, one (`chevron-up`) is already satisfied by Aura's
native `Details` toggle, one (`copy`) has no corresponding app action, and one
(`plane`) is a live swap. The issue's proposed 21-row mapping table was, on
inspection, mostly a proposal about views the design has not drawn yet.

That is the tension this ADR has to resolve: the mechanism is clearly worth having,
but adopting it now means **choosing glyphs the design has not specified.**

## Decision

**1. Lucide is the app's icon set.** Every glyph the app itself draws comes from
Lucide, via `LucideIcon`. `VaadinIcon`, `LumoIcon`, and the `"vaadin:name"` /
`"lumo:name"` string forms are out of `src/main/java`, and `IconSetGuardTest` keeps
them out.

**2. This governs glyphs the app draws, not glyphs Vaadin's components draw for
themselves.** A combo box's dropdown arrow, a details summary's chevron, a grid's
sort indicator and a field's clear button ship with their component and stay as they
are. Replacing them would mean fighting each component's shadow DOM for a glyph the
design does not draw either — and the design agrees, since its own frames show those
components with their stock internals. The report-list chevron is the worked example:
the design draws `lucide/chevron-up`, and #162 satisfied it with Aura's own toggle
rotated in CSS, which is correct and stays.

**3. Delivery is one vendored SVG sprite**, at
`src/main/resources/META-INF/resources/icons/lucide.svg`, addressed
`new SvgIcon("icons/lucide.svg", "plane")`. One request, no npm dependency, no build
step. Paths are copied byte-identical from
`github.com/lucide-icons/lucide/tree/main/icons` so an upgrade is a diff; the ISC
licence sits beside the sprite as `LICENSE.lucide`, which is the attribution and a
condition of use.

Rejected: `lucide-static` via npm — npm package contents are not served as Vaadin
static resources without extra plumbing, for a set this small. Rejected: one file per
glyph — it *is* the better-supported path (see Consequences) but costs a request per
glyph and puts 15 near-identical files in the tree.

**4. Every `<symbol>` carries its own presentation attributes.** `fill="none"`,
`stroke="currentColor"`, `stroke-width="2"`, and both `stroke-linecap`/`linejoin`
`round`, plus `viewBox="0 0 24 24"`. This is not belt-and-braces; it is load-bearing,
for the reason in Consequences.

**5. New glyphs come from the design, through `LucideIcon`.** When a view gets its
design pass, its glyphs are read off the Figma frame and added here. A glyph nobody
has drawn is a question for the designer, not a pick for whoever is implementing.

**6. The glyphs chosen ahead of the design are recorded below, as inventions.**
Since #163 adopted the mechanism across the whole app rather than only where the
design reaches, most call sites needed a glyph the design has not specified. Those
picks are agent-chosen and are recorded as such, so the next survey knows which rows
to confirm rather than inheriting them as settled.

## The invention ledger

**Design-resolved** — read off page `88:12278`:

| Glyph | Sites | Evidence |
|---|---|---|
| `plane` | `MyReportsView`, `ReportDetailView` ×2 | `lucide/plane`, nodes `I88:12941;91:1810`, `134:1768` |

**Invented** — chosen while implementing #163; the design draws no glyph for any of
these, and several of the *views* are undrawn. Each is plausible, none is specified:

| Glyph | Replaces | Sites | Note |
|---|---|---|---|
| `plus` | `PLUS` | 5 | The design *does* draw a plus on the primary button — but as the Vaadin kit's `lumo:plus` (node `36:902`), not a Lucide node. Deliberate divergence per decision 1: an app-drawn glyph comes from one set. |
| `pencil` | `EDIT` | 5 | `square-pen` if an editor frame later shows the boxed variant. Undrawn: the design replaces these buttons with a `⋮` menu. |
| `arrow-up` / `arrow-down` | `ARROW_UP` / `ARROW_DOWN` | 2 + 2 | Reorder buttons. Same `⋮` redesign pending. |
| `upload` | `UPLOAD` | 2 | Receipt upload; the dialogs are drawn, the button's glyph is not. |
| `trash-2` | `TRASH` | 2 | Lucide's plain `trash` also exists; `trash-2` is the lidded one in common use. |
| `file-text` | `FILE_TEXT_O`, `"vaadin:file-text-o"` | 2 | |
| `arrow-left` | `ARROW_LEFT` | 1 | Back action. |
| `paperclip` | `PAPERCLIP` | 1 | Receipt attachment. |
| `search` | `SEARCH` | 1 | **Contradicted, not merely undrawn:** the design's own search field hides its `Prefix` slot. Kept as-is here and left to that view's design pass. |
| `inbox` | `"vaadin:inbox"` | 1 | Approval-queue empty state. |
| `archive` | `"vaadin:archive"` | 1 | Review-history empty state. |
| `triangle-alert` | `"vaadin:warning"` | 1 | Error view. Lucide renamed this from `alert-triangle`. |
| `map-pin` | `"vaadin:map-marker"` | 1 | Not-found view. |

Thirteen invented glyphs against one resolved. The honest reading of #163 is that it
traded 25 invented Vaadin picks for 13 invented Lucide picks — and bought the
mechanism, the ADR, and a guard that stops the next one being invented silently. The
count is meant to fall as views get designed, not to be defended.

Not vendored, deliberately: `chevron-up`/`chevron-down` (Aura's `Details` provides
it, decision 2) and `copy` (drawn but never placed, and no app action needs it). Also
gone entirely with #146, which landed before this: the eight `@Menu(icon = "vaadin:…")`
strings, `MainLayout.createSideNavItem`, the header's `SIGN_OUT`, and
`ThemeSwitcher`'s `ADJUST`. The issue's mapping rows for those describe a shell that
no longer exists.

## Consequences

**A sprite addressed by `symbol` renders an external `<use>`, and reads nothing off
the file.** This is the sharp edge, and it is not documented. In
`vaadin-icon-mixin`'s `__srcChanged`, a `src` with a `symbol` (or a `#fragment`)
takes a branch that sets `<use href="icons/lucide.svg#plane">` and **never fetches
the file**. The other branch — a plain `src`, one file per glyph — fetches, inlines
the content into the shadow root, and copies `viewBox`, `fill`, `stroke`,
`stroke-width`, `stroke-linecap` and `stroke-linejoin` off the file's root `<svg>`
onto the one it renders. The sprite branch copies none of them.

So attributes on the sprite's root `<svg>` are silently ignored, and a `<symbol>`
without its own `stroke="currentColor"` renders a **black glyph** — which looks
correct in light mode and vanishes in dark. Hence decision 4, and hence
`LucideIconTest` asserting those attributes per symbol rather than trusting them.
`currentColor` does survive the external reference: it resolves against the `<use>`
element's context, which is what makes one sprite serve both colour schemes. Logged
as F-075.

**The 24 grid is a coupling, not a coincidence.** With no `viewBox` read from the
file, the rendered `viewBox` is `0 0 ${size}` from `vaadin-icon`'s `size` property,
which defaults to **24**. Lucide is a 24 grid, so they agree — but only by matching
defaults on both sides, so the test pins it.

**A typo is a blank box, not an error.** `<use>` resolving to nothing renders empty
and logs nothing. That is the whole reason `LucideIcon` is an enum rather than a
string constant: the names are checked once, against the sprite's actual symbol ids.

**`LucideIcon` cannot implement `IconFactory`.** That interface's `create()` returns
the font-icon `Icon`, which is the Lumo type this ADR moves away from. Anything typed
to `IconFactory` therefore cannot take a Lucide glyph.

**Three shared seams widened.** `ReferenceConfigView.iconButton`/`reorderButton` and
`EmptyState`'s constructor now take `AbstractIcon<?>` — the common supertype of every
Vaadin icon, and the type carrying `setSize`. Typing them to `LucideIcon` would have
re-created the coupling this ADR removes, one level up.

**Adding a glyph is three steps, and step one is the point.** Read the name off the
Figma frame; drop the upstream file into the sprite unmodified; add the enum
constant. If there is no Figma frame, the answer is a question to the designer — the
ledger above exists because that step was skipped, knowingly, once.

## Cross-references

[ADR-0025](0025-figma-design-source-of-truth.md) — the design is the source of truth
for visual decisions, which is what makes the ledger above a debt rather than a
choice · [ADR-0017](0017-base-ui-shell-and-ux-states.md) — `EmptyState`, whose
constructor this changes · `docs/theming-layouts.md` § *Icons* — the day-to-day rule ·
`docs/findings.md` F-075 — the sprite/`<use>` behaviour, and Vaadin 25 shipping no
Lucide or Aura-native icon set · issue #163.

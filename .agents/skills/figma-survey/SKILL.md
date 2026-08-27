---
name: figma-survey
description: "Survey a Figma design against what a Vaadin app already builds, decide every divergence with the user, and write the project's design spec. Ends in the spec plus a checkable delta — never in application code or theme CSS."
disable-model-invocation: true
argument-hint: "[theme | view or component name] [figma url]"
---

# Figma survey

A survey reads two things — the design, and whatever the app already builds for it — and
writes two:

- **The design spec**, the durable half: what the design asks for, with a decision
  recorded wherever the design and the app disagree. A later run looks a value up here
  instead of deciding it again, which is the whole point — an agent with no memory of the
  last session invents a plausible value rather than reaching for the settled one, and by
  the fifth view the app reads as several products.
- **The delta**, the transient half: what the design shows that the app does not yet do,
  written checkable so it can become a ticket's acceptance criteria.

It writes no application code and no theme CSS. Applying the spec belongs to someone
else — `figma-theme` for the global theme, implementation for a view — and that
separation is what lets the expensive judgement calls be reviewed before anyone spends a
day on a diff.

Applies to Vaadin Flow (Java) on the Aura theme.

## Scope

`$ARGUMENTS` fixes one of three scopes, and the scope decides which steps run:

| Scope | Target | Writes |
|---|---|---|
| **theme** | the design's global variables, across every mode | `foundations/`, and the inputs in `tokens/` |
| **view** | one frame — a screen or a dialog | `components/` for the components on it, plus the delta |
| **component** | one component, wherever it appears | that component's file in `components/` |

**Do the theme scope first, and once.** It decides the font family and base size, which
reflow every screen; per-view spacing settled against the old scale is work thrown away.
A theme survey skips steps 2 and 4 — there is no single frame to match against code.

## Steps

### 1. Resolve the target and find the spec's home

`$ARGUMENTS` names a scope, or a view or component in whatever words came to hand —
`"Add Expense"`, `LineEditorDialog`, `report detail` — and may include a Figma URL.

Fix the design file first: take `fileKey` and `nodeId` from a URL of the form
`figma.com/design/:fileKey/:name?node-id=1-2`. Absent a URL, ask for one once, then check
whether the project records a design file (its agent instructions are the usual home)
before asking again.

For a **theme** survey pick a frame that exercises the whole theme — an app shell or a
dashboard, showing nav, fields, buttons and cards — because step 3 resolves variables
through the nodes that frame contains, and an unbound variable is invisible.

For a **view** or **component** survey, resolve the target against both ends: the design
via `get_metadata` on the page to list frame names, and the code via step 2's ladder.
Name both resolutions back to the user; ask only when two candidates are genuinely
indistinguishable.

**The spec's home** is the directory the project's agent instructions name. Read what is
already there — it is the baseline this run revises, not a blank page. No spec at all
means nothing has been settled yet, which makes every divergence in step 6 open by
default; say so, because it bounds what this survey can conclude.

**Done when** the scope is fixed, one Figma node id is fixed, the code side is a named
class or an explicit `none`, and the existing spec is either read or its absence noted.

### 2. Find what's already built

*View and component scopes only.* Climb the ladder and stop at the first rung that hits:

1. `@Route` values — the strongest signal, and cheap to enumerate.
2. Class names in the project's view packages.
3. A grep for the design's distinctive strings (labels, section headings).

**Report which rung hit.** "Matched by route" and "matched by a string grep" are different
confidence levels, and the second earns a second look.

**Done when** the rung is named and the existing classes are listed — or `none`, which
makes this a greenfield survey and leaves *Current implementation* empty in step 9.

### 3. Read the design

**Theme scope — resolve variables through bindings, not collections.** A design that
consumes a shared kit has **remote** variables:
`getLocalVariableCollectionsAsync()` returns the file's own collections only — typically
one unrelated collection with one mode — so an agent that trusts it concludes the design
ships a single mode when the kit's collection carries Light and Dark. That failure is
silent and it inverts the most visible decision in the theme. Consuming a kit as a library
is the normal case, not an edge one.

The binding walk: collect `boundVariables` across the frame's subtree, resolve each id with
`getVariableByIdAsync`, resolve its collection with `getVariableCollectionByIdAsync`, and
read `valuesByMode` keyed by the collection's mode names. `get_variable_defs` flattens
aliases to the active mode's value and takes no mode parameter, which is why it cannot
answer this. The walk needs `use_figma`, which mandates loading its own guidance first —
do that, and keep the run read-only, since it is the one Figma tool that can mutate the
file.

Capture, per mode: accent colour, background colour, font family, base font size, user
colours, and field border tint and width. Two modes means `color-scheme: light dark`; one
mode means that scheme alone and leaves the other to derive — keep the hue, move the
lightness, and mark it derived, since it is your value and not the design's. Whatever no
variable binds is inferred from the frame instead: component heights and spacing give the
base size, corner rounding the base radius, card elevation the surface level, a gap at the
viewport edge the layout inset.

**A variable and a drawn value are not equally authoritative.** A value bound to a
variable, or inherited from a kit component, is a design-system decision and is taken
as-is. A value typed by hand is an *intent* to reconcile against the token scale in
step 7. Expect the two to disagree: a frame can declare one font family and render three.

**View and component scope —** `get_design_context` on the **frame**, never the page: a
page node returns "you have nothing selected", which names a precondition you did not
violate, and a page's `get_metadata` exceeds the token cap outright. Fetch the frame's
screenshot once; pass `excludeScreenshot: true` on child-node calls so the mapping keeps
the context.

**Done when** every variable has a value per mode or is explicitly absent and the mode
count is stated (theme scope); or every Figma **component instance** in the frame's
subtree is enumerated (view and component scope). Instances are the mappable unit — they
carry the `Vaadin component:` annotations. Raw text and vector layers carry none and
belong to step 7 as styling detail on their nearest instance.

### 4. Map each instance to a Vaadin component

*View and component scopes only.* One row per instance enumerated in step 3:

| Figma node | Layer | Annotation says | Vaadin | Source | Why |
|---|---|---|---|---|---|

`Source` is the column that earns the table. It takes one of three values, and it is what
lets a reviewer tell evidence from judgement in a single pass:

- **`annotation`** — Figma named the component and it stands. The common case; leave *Why*
  empty.
- **`override`** — Figma named a component and the design contradicts it. Record the
  contradiction, not just the choice. Annotations carry a component name but not its accent
  scoping, so a button annotated `theme="primary"` whose Figma variant is
  `Color=Accent neutral` renders in the accent colour where the design paints it
  near-black. Check the layer name and the rendered fill before trusting a colour.
- **`invented`** — no annotation, or no component fits. Propose the composition (a layout
  plus a scoped CSS class) and say plainly that it is judgement.

An annotation naming `tertiary-inline`, `contrast` or `icon` is naming a variant the theme
does not implement: it is accepted and renders nothing differently. Map it to the nearest
variant that exists, and say in *Why* that the original is inert.

Confirm every distinct component against the real API with `get_component_java_api` — that
is where exact constant and method names live, and where remembered names fail. Record the
Vaadin version you researched against.

**Done when** every enumerated instance has a row, and every row has a `Source`.

### 5. Establish the app's current values

The spec compares the design against what the app renders **now**, so both ends need
values.

Where the spec already carries the resolved token scale and the Vaadin version matches,
use it — that is what it is for. Otherwise read the scale from a running app: apply each
token to a probe element and read the used value back, because these ship as
`calc()`/`round()` expressions resolved at render time and exist as numbers only in a
browser. `element.style.setProperty()` takes a **CSS** property name, so
`setProperty('borderTopLeftRadius', …)` fails silently and every token reads back as
`0px` — a whole measurement pass can look successful and be entirely zeros.

**Capture the formulas, not just the numbers.** The numbers are true only for today's
inputs and go stale the moment step 6 changes one; the formulas hold across a theme change
and go stale only on a framework upgrade. Mark any step whose expression you could not
read as unverified rather than inferring a multiplier from one sample.

Then list every property the app's own theme file sets, so step 6 knows what is a
deliberate app choice rather than a default.

**Done when** each scale has its formulas and its current values, the version they hold
for is noted, and every property the app's theme sets is listed.

### 6. Turn every divergence into a decision

One row per property where the design and the app disagree. Properties that already agree
need no row and no question.

| Property | Design | App | Decided | From | Status |
|---|---|---|---|---|---|

**Neither side is right by default.** The app's value may be a deliberate choice the design
never saw — a larger base font size for readability, a looser density for touch targets —
and the design's may be the brand. So carry a recommendation into every row and put the
whole table to the user in **one** pass, so they confirm judgements rather than answer a
drip of questions.

`Status` is what a later run reads:

- **settled** — decided, whichever side won. A survey meeting this difference again names
  it settled and moves on.
- **open** — deferred. A survey may raise it again, and should.

A divergence resolved in the **app's** favour is settled, not absent. That row is the whole
reason the spec exists: it is what stops the next survey reporting the app's own font as a
mismatch.

Two decisions outrank the design and are not open to a preference:

- **The accessibility floor.** Contrast, touch-target size and focus visibility are
  constraints. A design value that breaks one is a design bug to report; the app keeps the
  accessible value, recorded as a settled divergence rather than carried silently.
- **A value the design contradicts itself on.** Where a variable and the drawn value
  disagree, the variable wins (step 3), and the drawn value is reported as a design defect
  rather than implemented.

**Done when** every property from step 3 is either identical on both sides or carries a
decision and a status the user chose.

### 7. Name the values the derivation cannot produce

Solve the formulas backwards and the design's raw pixel values will disagree with each
other. A 9 px field radius may ask for one base radius and a 12 px card radius in the same
design for another, at which the field renders 6 px. No single input satisfies both: the
design has left the derivation. Expect this wherever a designer drew by hand rather than
placing a kit component.

Two shapes, across radius, spacing and type alike:

- **between** — the value sits between two steps of the scale.
- **beyond** — the value is off the end of it.

Each is a global decision, and taking it here is the point: a view that meets one alone
invents an answer, and the next view invents a different one. Decide each from: correct the
design back to the scale; override the derived property globally; define one extra
project-level custom property and use it everywhere the design uses that value; or accept
the nearest token and its visible divergence.

A useful split: a value several pixels off **and** recurring earns its own property,
because that gap shows on every card and heading; a value within a pixel or two takes the
nearest token, because a project property shadowing the scale for one pixel is where drift
starts. Name both divergences either way — an accepted difference recorded is what stops it
being rediscovered.

Where the framework has a real property for exactly that value, override it directly
rather than inventing a twin.

**Done when** every off-scale design value is listed with where it appears, its nearest
token, the decision taken, and a status from step 6's two.

### 8. Write the design spec

Write into the project's spec directory, following any template its own `README` defines —
where it does, that is binding and overrides what follows.

**Theme scope** — one file per concern, not one long document, because that is how a later
run arrives: it asks "what is the typography?", not "what did the last run decide?".

| File | Content |
|---|---|
| `foundations/color.md` | accent, neutral, palette, semantic colour |
| `foundations/typography.md` | family, base size, weights, the type scale, text roles |
| `foundations/spacing.md` | density, the padding/gap scale |
| `foundations/radius.md` | corner radii |
| `foundations/elevation.md` | shadows, surfaces |
| `foundations/motion.md` | transitions — say so explicitly if the design specifies none |
| `tokens/token-reference.md` | step 5's formulas, the inputs decided in step 6, and every property the app sets |

Every foundation file carries its share of step 6's decisions and step 7's off-scale
values, each row marked **settled** or **open**. A concern with no divergence still gets a
file saying so; absence recorded is what stops the next survey re-deriving it.

`tokens/token-reference.md` has a second owner: the **resolved values** table is refreshed
by whoever applies the theme and measures the result, since only a running app produces it.
Write the inputs and the formulas; label the resolved table as theirs.

**View and component scope** — one file per component in `components/`, named for the
**role** in kebab-case. A component is a role, not a CSS class: a cluster of classes
rendered as one thing gets one file, because a file per selector is a restatement of the
stylesheet and goes stale with every rename. Only components that exist, or that this
survey's delta will create.

Cover: metadata; when to use it *and when to reach for its sibling instead*; anatomy;
tokens used; API; states; the shortest correct example; cross-references. Do not restate a
constructor signature or a class comment — that is duplication which will disagree with the
source within a release. Record intent, precedent and entitlement.

**States are mandatory: default, hover, active, focus, disabled, error.** Write `n/a` with
the reason where one cannot occur; never leave a row blank and never drop the row. A blank
reads as "not checked" and a missing row as "no such state", and both are
indistinguishable from an oversight. This section catches the most, for two reasons: a
design frame shows the resting state, so every other state arrives from framework defaults
unread; and state styling is often invisible to the obvious check — a theme may express
hover as a pseudo-element overlay and disabled as `opacity` on the host, leaving
`background-color` and `color` untouched in both, so an audit reading those two properties
reports the disabled control as identical to the enabled one having measured nothing.

**A survey cannot fill a measured figure.** It has no running app, so a contrast ratio or a
rendered pixel size is not yours to state. Leave the slot marked as unverified for visual
verification to complete. An unverified row said to be unverified costs the next run five
minutes; the same row presented as settled costs it a wrong decision.

**Done when** every file the scope calls for exists, every decision row carries **settled**
or **open**, every component's six states are accounted for, and no measured figure is
asserted without a measurement.

### 9. Assemble the delta and hand it over

The spec says what the design asks for. The delta says what is missing, and it is what
becomes a ticket.

- **Delta** (existing surveys only) — what the design shows that the app does not do, as a
  numbered list, each item checkable, because each becomes an acceptance-criteria line.
- **Domain gaps** — the design assumes a field or concept the domain lacks. A frame showing
  an editable report title, in an app whose report has no title, is not a restyle; buried
  in the delta it gets reviewed and estimated as one. Decide it here, in this
  conversation — a new field, or an explicit "out of scope, use X instead". Hand the
  implementer a decision, not a question.

Write it up under these headings:

| Heading | Content |
|---|---|
| Target | the Figma node, the matched code, and the rung that matched it |
| Current implementation | what the existing class already does — empty on a greenfield survey |
| Delta | numbered, each item checkable |
| Component mapping | the table from step 4 |
| Spec written | the files created or updated, and every row left **open** |
| View styling | decoration local to this view, which becomes a scoped, role-named CSS class using tokens |
| Domain gaps | each with its decision |
| Acceptance criteria | the component chosen per element, each delta item as its own line, and a visual verification pass against the frame reporting no high-severity differences |
| Research notes | framework version, which components' API was read, the Figma node ids, and what could not be verified |

Global theming differences do **not** go in the delta. They were decided in step 6 and
written in step 8, which is what stops the next survey re-litigating the same radius.

Hand the assembled delta to whatever the project records work in, so implementation
proceeds from that record rather than from this conversation.

**Done when** every heading is filled or explicitly marked empty, the spec files are
listed, and the delta has been handed over.

## A hazard worth naming

The design's reference code arrives threaded with the kit's own variable names, and those
may not be the ones the active theme defines. Each ships a plausible hardcoded fallback,
so a value copied across renders at a **frozen** literal that looks right today and
silently stops tracking the theme forever. Nothing errors, and the wrong colour surfaces
months later in the scheme nobody tested.

The rule is not about a prefix: **never reference a custom property the active theme does
not define.** A typo, a token from a newer version, a token an upgrade removed and a
project property written before it existed all fail identically and just as quietly.
Translate every kit variable to its real equivalent, or record the literal it resolves to
and say that is what you did.

## Figma → Vaadin quick reference

Annotations override this table; it covers layers that carry none.

| Figma | Vaadin |
|---|---|
| Vertical auto layout | `VerticalLayout` |
| Horizontal auto layout | `HorizontalLayout` |
| Free / absolute layout | `FlexLayout` |
| Form / labelled fields | `FormLayout` |
| Master-detail | `MasterDetailLayout` |
| Grid / table | `Grid` |
| Text layer | `Span` |
| Heading | `H1`…`H6`, by the design's text style |
| Badge / status label | `Badge` |
| Icon named `lumo:*` | `LumoIcon.*` — a supported icon set, in `com.vaadin.flow.theme.lumo`, not beside `VaadinIcon` |

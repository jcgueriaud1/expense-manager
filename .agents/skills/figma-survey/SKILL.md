---
name: figma-survey
description: "Survey one Figma frame against what a Vaadin app already builds: a component mapping, and the delta from what exists."
disable-model-invocation: true
argument-hint: "[view or component] [figma url]"
---

# Figma survey

A **survey** reads two things — the design frame, and whatever the app already builds
for it — and produces one artifact: a **component mapping** whose every row declares
where its decision came from.

The survey ends at that mapping. Its output is a written summary, not code: the point
is to establish what should be built and why, so the reasoning survives into whatever
record the project keeps work in and can be reviewed before anyone writes Java.
Implementing here would strand the mapping in a conversation nobody else can read, and
would hide the design's expensive judgement calls inside a diff.

Applies to Vaadin Flow (Java) on the Aura theme.

## Steps

### 1. Resolve the target

`$ARGUMENTS` names a view or a component in whatever words came to hand —
`"Add Expense"`, `LineEditorDialog`, `report detail` — and may include a Figma URL.

Fix the design file first: take `fileKey` and `nodeId` from a URL of the form
`figma.com/design/:fileKey/:name?node-id=1-2`. Absent a URL, ask for one once, then
check whether the project records a design file (its agent instructions are the usual
home) before asking again.

Then resolve the target against **both** ends:

- **Design:** `get_metadata` on the page to list frame names, then match.
- **Code:** step 2's ladder.

Name both resolutions back to the user. Ask only when two candidates are genuinely
indistinguishable.

**Done when** one Figma node id is fixed and the code side is either a named class or
an explicit `none`.

### 2. Find what's already built

Climb the ladder and stop at the first rung that hits:

1. `@Route` values — the strongest signal, and cheap to enumerate.
2. Class names in the project's view packages.
3. A grep for the design's distinctive strings (labels, section headings).

**Report which rung hit.** "Matched by route" and "matched by a string grep" are
different confidence levels, and the second earns a second look.

**Done when** the rung is named and the existing class or classes are listed — or
`none`, which makes this a greenfield survey and leaves step 6's *Current
implementation* empty.

### 3. Read the design

`get_design_context` on the **frame**, never the page: a page node returns
"you have nothing selected", which names a precondition you did not violate, and a
page's `get_metadata` exceeds the token cap outright.

Fetch the frame's screenshot once — step 5 judges view-local decoration against it. Pass
`excludeScreenshot: true` on any child-node call, so the mapping keeps the context.

**Done when** every Figma **component instance** in the frame's subtree is enumerated.
Instances are the mappable unit: they carry the `Vaadin component:` annotations. Raw
text and vector layers carry none, and belong to step 5 as styling detail on their
nearest instance.

### 4. Map each instance to a Vaadin component

One row per instance enumerated in step 3:

| Figma node | Layer | Annotation says | Vaadin | Source | Why |
|---|---|---|---|---|---|

`Source` is the column that earns the table. It takes one of three values, and it is
what lets a reviewer tell evidence from judgement in a single pass:

- **`annotation`** — Figma named the component and it stands. The common case; leave
  *Why* empty.
- **`override`** — Figma named a component and the design contradicts it. Record the
  contradiction, not just the choice. Annotations carry a component name but not its
  accent scoping, so a button annotated `theme="primary"` whose Figma variant is
  `Color=Accent neutral` renders in the accent colour where the design paints it
  near-black; that row reads `Button` + `PRIMARY` + `aura-accent-neutral`. Check the
  layer name and the rendered fill before trusting a colour.
- **`invented`** — no annotation, or no component fits. Propose the composition
  (a layout plus a scoped CSS class) and say plainly that it is judgement. A card whose
  content is a list of peer rows with dividers does not fit `Card`'s
  title/subtitle/media slots; a raw SVG icon has no mapping at all.

An annotation naming `tertiary-inline`, `contrast` or `icon` is naming a Lumo-only
variant: Aura accepts it and renders nothing differently. Map it to the nearest variant
that exists, and say in *Why* that the original is inert.

Confirm every distinct component against the real API with
`get_component_java_api` — that is where exact constant and method names live, and
where remembered names fail (`VaadinIcon.RECEIPT` and `BED` do not exist). Reach for
`get_full_document` when a component's *capability* is in question, not routinely.
Record the Vaadin version you researched against.

**Done when** every enumerated instance has a row, and every row has a `Source`.

### 5. Route the styling differences

Read the **theme record** first — the project's record of its decided colours, fonts
and sizes, and of the resolved token values. It is usually a design-spec directory the
project's agent instructions name, split one file per concern; read the concerns this
view actually touches rather than all of them. Where the project also keeps per-component
specs, read the ones for the components on this frame — a component spec is where a
state (hover, disabled, error) that the frame does not show is recorded.

It changes what counts as a finding:

- A difference the record has already **decided** is not a finding. The app is
  conforming to a settled choice, and a survey that re-raises it every time is how a
  record stops being believed. Name it as settled and move on.
- **No record at all** means the theme was never settled. Say so in the survey — it
  bounds what the survey can conclude — and treat every global difference as open.
- **A component on this frame with no spec** is itself a finding, reported once with the
  view's other gaps. It is the gap that lets the same question be answered differently by
  each view, so it belongs in the survey rather than being silently absorbed.

Route what remains to exactly one of two destinations, because a difference affecting
more than one screen has a different owner than one affecting this screen alone:

- **Global theming** — a token or theme-level mismatch. It belongs to the theme record
  and **never** to this view's ticket, so the next survey does not re-litigate the same
  radius. Record the question, and where you proceeded on an assumption, the assumption
  — so a reviewer sees it rather than inheriting it.
- **View styling** — decoration local to this view, which becomes a scoped, role-named
  CSS class using theme tokens.

Judging which needs the frame's screenshot and the theme's resolved token values. Where
the record carries those values, use them; otherwise read `--vaadin-radius-*`,
`--vaadin-padding-*`, `--vaadin-gap-*` and `--aura-font-size-*` from a running app with
`getComputedStyle`, since Aura ships them as `calc()`/`round()` expressions resolved at
render time. A design value that no single theme input can produce is a global question
by construction.

The design's reference code arrives threaded with `--lumo-*` properties, because that
is genuinely how the shared Aura kit names its variables. Each one ships a plausible
hardcoded fallback, so a value copied across renders at a **frozen** literal that looks
right today and silently stops tracking the theme forever. Translate every kit variable
to its `--aura-*` / `--vaadin-*` equivalent, or record the literal value it resolves to
and say that is what you did.

**Done when** every difference is either named as settled or sits under exactly one of
the two headings.

### 6. Surface what the design assumes

Two things the mapping cannot absorb:

- **Delta** (existing surveys only) — what the design shows that the app does not do,
  as a numbered list. Each item becomes an acceptance-criteria line in step 7, so write
  each one checkable.
- **Domain gaps** — the design assumes a field or concept the domain lacks. A frame
  showing an editable report title, in an app whose report has no title, is not a
  restyle; buried in the delta it gets reviewed and estimated as one. Decide it here,
  in the survey conversation — a new field, or an explicit "out of scope, use X
  instead". Hand the implementer a decision, not a question.

**Done when** every delta item is checkable and every domain gap carries its decision.

### 7. Assemble the survey

Write it up under these headings:

| Heading | Content |
|---|---|
| Target | the Figma node, the matched code, and the rung that matched it |
| Current implementation | what the existing class already does — empty on a greenfield survey |
| Delta | numbered, each item checkable |
| Component mapping | the table from step 4 |
| Global theming | token and theme-level mismatches for the theme record, each with any assumption taken; and whether a theme record existed at all |
| View styling | view-local decoration |
| Domain gaps | each with its decision |
| Acceptance criteria | the checklist below |
| Research notes | Vaadin version, which components' Java API was read, the Figma node ids |

The acceptance checklist is what implementation aims at and what a later visual check
grades against: the component chosen per element, each delta item as its own line, and
finally *a visual verification pass against the frame reports no high-severity
differences*.

Hand the assembled survey to whatever the project records work in — an issue, a spec,
a ticket — so implementation proceeds from that record rather than from this
conversation.

**Done when** every heading above is filled or explicitly marked empty, and the survey
has been handed over.

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

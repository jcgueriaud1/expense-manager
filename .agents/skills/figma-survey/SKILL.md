---
name: figma-survey
description: "Survey one Figma frame against what the app already builds, and produce the component mapping a spec needs."
disable-model-invocation: true
argument-hint: "[view or component]"
---

# Figma survey

A **survey** reads two things — the design frame, and whatever the app already builds
for it — and produces one artifact: a **component mapping** whose every row declares
where its decision came from.

The survey ends at the mapping. `/to-spec` publishes it as an issue,
`/implement-use-case` builds from that issue, `/figma-visual-verification` checks the
result against the frame. Writing Java here would strand the mapping in a conversation
nobody else can read, so the survey's output is context, shaped to slot into
`/to-spec`'s sections.

**The design:** file `Irsp3cgi1WX3GiLGpJZECa`, page `88:12278` ("Visual Design").
Authentication and the project-scoped MCP server: [`DEVELOPMENT.md`](../../../DEVELOPMENT.md).

## Steps

### 1. Resolve the target

`$ARGUMENTS` names a view or a component in whatever words came to hand —
`"Add Expense"`, `LineEditorDialog`, `report detail`. Resolve it against **both** ends:

- **Design:** `get_metadata` on the page to list frame names, then match.
- **Code:** step 2's ladder.

Name both resolutions back to the user before going further. Ask only when two
candidates are genuinely indistinguishable.

**Done when** one Figma node id is fixed and the code side is either a named class or
an explicit `none`.

### 2. Find what's already built

Climb the ladder and stop at the first rung that hits:

1. `@Route` values — the strongest signal, and cheap to enumerate.
2. Class names under `src/main/java/**/ui/`.
3. A grep for the design's distinctive strings (labels, section headings).

**Report which rung hit.** "Matched by route" and "matched by a string grep" are
different confidence levels, and the second earns a second look.

**Done when** the rung is named and the existing class or classes are listed — or
`none`, which makes this a greenfield survey and leaves step 6's *Current
implementation* empty.

### 3. Read the design

`get_design_context` on the **frame**, never the page: a page node returns
"you have nothing selected", which names a precondition you did not violate (F-061),
and a page's `get_metadata` exceeds the token cap outright.

Fetch the frame's screenshot once — step 5 needs to see it. Pass
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
  contradiction, not just the choice. The annotations carry a component name but not
  its accent scoping, so a button annotated `theme="primary"` whose Figma variant is
  `Color=Accent neutral` renders blue where the design paints it near-black; the row
  reads `Button` + `PRIMARY` + `aura-accent-neutral`. Check the layer name and the
  rendered fill before trusting a colour.
- **`invented`** — no annotation, or no component fits. Propose the composition
  (a layout plus a scoped CSS class) and say plainly that it is judgement. A card whose
  content is a list of peer rows with dividers does not fit `Card`'s
  title/subtitle/media slots; a raw SVG icon has no mapping at all.

Confirm every distinct component against the real API with
`get_component_java_api` — that is where exact constant and method names live, and
where remembered names fail (`VaadinIcon.RECEIPT` and `BED` do not exist). Reach for
`get_full_document` when a component's *capability* is in question, not routinely.
Record the Vaadin version you researched against.

**Done when** every enumerated instance has a row, and every row has a `Source`.

### 5. Route the styling differences

Every visual difference goes to exactly one of two destinations, because global and
view theming are separate decisions with separate owners:

- **Global theming** — the difference is a token or theme-level mismatch. Compare
  against the scale in [`vaadin-gotchas.md`](../../../docs/vaadin-gotchas.md). It joins
  the standing theming issue and **never** the view's ticket, so the next survey does
  not re-litigate the same radius.
- **View styling** — the difference is decoration local to this view. It becomes a
  scoped CSS class, per [`theming-layouts.md`](../../../docs/theming-layouts.md).

The design's reference code arrives threaded with `--lumo-*` properties, because that
is genuinely how the shared Aura kit names its variables. Each one ships a plausible
hardcoded fallback, so a value copied across renders at a **frozen** literal that looks
right today and silently stops tracking the theme forever. Translate every kit variable
to its `--aura-*` / `--vaadin-*` equivalent, or record the literal value it resolves to
and say that is what you did.

**Done when** every visual difference sits under exactly one of the two headings.

### 6. Surface what the design assumes

Three kinds of thing the mapping cannot absorb:

- **Delta** (existing surveys only) — what the design shows that the app does not do,
  as a numbered list. Each item becomes an acceptance-criteria line in step 7, so write
  each one checkable.
- **Domain gaps** — the design assumes a field or concept the domain lacks. A frame
  showing an editable report title, in an app whose report has no title, is not a
  restyle; buried in the delta it gets reviewed and estimated as one. Decide it here,
  in the survey conversation — a new field, or an explicit "out of scope, use X
  instead". Do not hand it to the implementer undecided.
- **Open global decisions** — check `docs/adr/` for a decision covering the theme and
  the token scale. Absent one, record the question *and* the assumption you proceeded
  under, so a reviewer sees the assumption rather than inheriting it.

**Done when** every gap carries a decision and every open global question carries its
assumption.

### 7. Assemble and hand off

Arrange the survey under `/to-spec`'s own section names, so it publishes without
reshaping:

| `/to-spec` section | Survey content |
|---|---|
| Problem Statement | how this view diverges from the design — *Current implementation* and *Delta* |
| Solution | the mapping table; proposed compositions for `invented` rows |
| Implementation Decisions | the *Why* behind every `override` and `invented` row; **Global theming** vs **View styling**; open global decisions and their assumptions |
| Testing Decisions | the acceptance checklist |
| Out of Scope | what the frame shows that is not this ticket — a navbar belonging to `MainLayout`, a global theming item |
| Further Notes | Vaadin version, which components' Java API was read, the Figma node ids |

The acceptance checklist is what `/implement-use-case` uses as its spine and
`/figma-visual-verification` uses as its pass condition: the component chosen per
element, each delta item as its own line, and finally
`figma-visual-verification against node <id> reports no high findings`.

**Done when** the content sits under those headings and the user is told to run
`/to-spec` next.

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
| Icon named `lumo:*` | `LumoIcon.*` — a supported 25.2 icon set, in `com.vaadin.flow.theme.lumo`, not beside `VaadinIcon` |

## Provenance

- **Replaces:** `figma-to-vaadin`, this repo's project-owned copy of
  [`juuso-vaadin/figma-to-vaadin-skill`](https://github.com/juuso-vaadin/figma-to-vaadin-skill)
  at commit `3a9289c`, now deleted.
- **Why:** that skill's workflow was unconditionally *implement*, with no step asking
  whether the view already existed. Run against this app it produced a second, thinner
  implementation of a view already shipping — and the thinner one looked closer to the
  design because it did less (finding F-066). The per-view work here is reconciliation,
  so the skill produces a mapping and the existing `/to-spec` →
  `/implement-use-case` chain does the rest.
- **Original**, not vendored: absent from `skills-lock.json`, and not managed by
  `skills.sh`.
- **Kept from the deleted skill:** the Figma → Vaadin quick reference, and the
  `--lumo-*` hazard in step 5 — the one project override that is about *reading* Figma
  rather than about writing Vaadin. Its remaining overrides duplicated `CLAUDE.md`, and
  its layout gotchas moved to `docs/vaadin-gotchas.md`.

# View and component scope

The branch of `figma-survey` for `view` and `component`: how to find what the app builds,
read the frame, map it, and write `components/` and the delta. The steps and their order
are in `SKILL.md`; this file supplies steps 2, 5 and 6.

## Pick the frame

Resolve the target against both ends: the design via the page's frame names, and the code
via the ladder below. A `component` survey may span several frames; enumerate the ones the
component appears in and name them.

## Find what's already built

Climb the ladder and stop at the first **rung** that hits:

1. `@Route` values — the strongest signal, and cheap to enumerate.
2. Class names in the project's view packages.
3. A grep for the design's distinctive strings (labels, section headings).

**Report which rung hit.** "Matched by route" and "matched by a string grep" are different
confidence levels, and the second earns a second look.

**Done when** the rung is named and the existing classes are listed — or `none`, which
makes this a greenfield survey and leaves *Current implementation* empty in the hand-over.

## Read the design

`get_design_context` on the **frame**, never the page: a page node returns "you have
nothing selected", which names a precondition you did not violate, and a page's
`get_metadata` exceeds the token cap outright. Fetch the frame's screenshot once; pass
`excludeScreenshot: true` on child-node calls so the mapping keeps the context.

**Done when** every Figma **component instance** in the frame's subtree is enumerated.
Instances are the mappable unit — they carry the `Vaadin component:` annotations. Raw text
and vector layers carry none and belong to the mapping as styling detail on their nearest
instance.

## Map each instance to a Vaadin component

One row per instance enumerated:

| Figma node | Layer | Annotation says | Vaadin | Source | Why |
|---|---|---|---|---|---|

`Source` is the column that earns the table. It takes one of three values, and it is what
lets a reviewer tell evidence from judgement in a single pass:

- **`annotation`** — Figma named the component and it stands. The common case, and a
  fact; leave *Why* empty.
- **`override`** — Figma named a component and the design contradicts it. Record the
  contradiction, not just the choice. Annotations carry a component name but not its
  accent scoping, so a button annotated `theme="primary"` whose Figma variant is
  `Color=Accent neutral` renders in the accent colour where the design paints it
  near-black. Check the layer name and the rendered fill before trusting a colour.
- **`invented`** — no annotation, or no component fits. Propose the composition (a layout
  plus a scoped CSS class) and say plainly that it is judgement.

**Every `override` and `invented` row is a question for the interview**, carried with the
mapping as your recommendation. So is an **accepted infidelity** — a drawn affordance the
chosen component cannot render, such as a stepper on a field whose domain type has no
step — and a **domain gap** — a field or concept the frame assumes and the domain lacks.
A frame showing an editable report title, in an app whose report has no title, is not a
restyle; the interview decides it (a new field, or an explicit "out of scope, use X
instead") so the implementer receives a decision and not a question.

An annotation naming `tertiary-inline`, `contrast` or `icon` is naming a variant the theme
does not implement: it is accepted and renders nothing differently. Map it to the nearest
variant that exists, and say in *Why* that the original is inert.

Confirm every distinct component against the real API with `get_component_java_api` —
that is where exact constant and method names live, and where remembered names fail.
Record the Vaadin version you researched against.

**The design's reference code is a hazard.** It arrives threaded with the kit's own
variable names, each with a plausible hardcoded fallback, and those names may not be ones
the active theme defines. A value copied across renders at a **frozen** literal that looks
right today and silently stops tracking the theme. Translate every kit variable to the
property the active theme actually defines, or record the literal it resolves to and say
that is what you did.

**Done when** every enumerated instance has a row, every row has a `Source`, and every
`override`, `invented`, infidelity and domain gap is on the interview's list.

## Write the spec — one file per component

One file per component in `components/`, named for the **role** in kebab-case. A component
is a role, not a CSS class: a cluster of classes rendered as one thing gets one file,
because a file per selector is a restatement of the stylesheet and goes stale with every
rename. Only components that exist, or that this survey's delta will create.

Cover: metadata; when to use it *and when to reach for its sibling instead*; anatomy;
tokens used; API; states; the shortest correct example; cross-references. Record intent,
precedent and entitlement — a constructor signature or a class comment is readable from
the source and would disagree with it within a release.

**Stamp the origin, and only write the origin you are entitled to.** A spec this skill
writes is **design origin**: the design specifies the component, so the spec is the
contract and code conforms to it. Record the design node it came from. A component that
exists in code but which the design never drew — an error summary, an empty state, a
shared dialog scaffold — is **code origin** and not yours to author: there is nothing in
the design to read, so a spec written here would be invention. Report it as a gap for
whoever owns that component.

**Leave the implementation state to an audit.** Whether the code conforms to what you just
wrote is a comparison, and a survey asserting `conforms` is asserting something it did not
check. Write `none` where nothing implements the spec yet and `unaudited` otherwise.

**States are mandatory: default, hover, active, focus, disabled, error.** Write `n/a` with
the reason where one cannot occur; a blank reads as "not checked" and a missing row as "no
such state", and both are indistinguishable from an oversight. This section catches the
most: a design frame shows the resting state, so every other state arrives from framework
defaults unread, and a theme may express hover as a pseudo-element overlay and disabled as
`opacity` on the host, leaving `background-color` and `color` untouched in both.

**Done when** every component the scope calls for has a file, every one carries an origin
and six states, and every measured figure is marked **unverified**.

## Assemble the delta

The spec says what the design asks for. The delta says what is missing, and it is what a
ticketing pass turns into acceptance criteria — so each item is numbered and checkable.

| Heading | Content |
|---|---|
| Target | the Figma node, the matched code, and the rung that matched it |
| Current implementation | what the existing class already does — empty on a greenfield survey |
| Delta | numbered, each item checkable |
| Component mapping | the mapping table |
| Spec written | the files created or updated, and every row left **open** |
| View styling | decoration local to this view, which becomes a scoped, role-named CSS class using tokens |
| Domain gaps | each with the decision the interview reached |
| Accepted infidelities | each with the reason, so nobody re-opens it as a bug |
| Acceptance criteria | the component chosen per element, each delta item as its own line, and a visual verification pass against the frame reporting no high-severity differences |
| Decisions for a human | every app-wide convention left **open**, each phrased as one question with the options |
| Design defects | every place the frame contradicts itself, its variables, or the accessibility floor |
| Research notes | framework version, which components' API was read, the Figma node ids, and what could not be verified |

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

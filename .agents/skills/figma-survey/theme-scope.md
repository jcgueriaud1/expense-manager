# Theme scope

The branch of `figma-survey` for `theme`: how to read the design's global variables and
how to write `foundations/` and `tokens/`. The steps and their order are in `SKILL.md`;
this file supplies steps 2, 5 and 6.

## Pick the frame

A theme survey has no single frame to match against code. Pick one that **exercises the
whole theme** — an app shell or a dashboard, showing nav, fields, buttons and cards —
because variables are resolved through the nodes that frame contains, and an unbound
variable is invisible.

## Read the design — resolve variables through bindings, not collections

A design that consumes a shared kit has **remote** variables.
`getLocalVariableCollectionsAsync()` returns the file's own collections only — typically
one unrelated collection with one mode — so an agent that trusts it concludes the design
ships a single mode when the kit's collection carries Light and Dark. That failure is
silent and it inverts the most visible decision in the theme. Consuming a kit as a library
is the normal case, not an edge one.

The binding walk: collect `boundVariables` across the frame's subtree, resolve each id
with `getVariableByIdAsync`, resolve its collection with `getVariableCollectionByIdAsync`,
and read `valuesByMode` keyed by the collection's mode names. `get_variable_defs` flattens
aliases to the active mode's value and takes no mode parameter, which is why it cannot
answer this. The walk needs `use_figma`, which mandates loading its own guidance first —
do that, and keep the run read-only, since it is the one Figma tool that can mutate the
file.

**Capture, per mode:** accent colour, background colour, font family, base font size, user
colours, and field border tint and width. Two modes means `color-scheme: light dark`; one
mode means that scheme alone and leaves the other to **derive** — keep the hue, move the
lightness, and mark it `derived`, since it is your value and not the design's. Whatever no
variable binds is inferred from the frame instead: component heights and spacing give the
base size, corner rounding the base radius, card elevation the surface level, a gap at the
viewport edge the layout inset. Every inferred value is a judgement, and goes to the
interview.

**A variable and a drawn value are not equally authoritative.** A value bound to a
variable, or inherited from a kit component, is a design-system decision and is taken
as-is. A value typed by hand is an *intent* to reconcile against the token scale in the
interview. Expect the two to disagree: a frame can declare one font family and render three.

**Done when** every variable has a value per mode or is explicitly absent, the mode count
is stated, and every inferred value is listed as a question.

## Write the spec — one file per concern

One file per concern, not one long document, because that is how a later run arrives: it
asks "what is the typography?", not "what did the last run decide?".

| File | Content |
|---|---|
| `foundations/color.md` | accent, neutral, palette, semantic colour |
| `foundations/typography.md` | family, base size, weights, the type scale, text roles |
| `foundations/spacing.md` | density, the padding/gap scale |
| `foundations/radius.md` | corner radii |
| `foundations/elevation.md` | shadows, surfaces |
| `foundations/motion.md` | transitions — say so explicitly if the design specifies none |
| `tokens/token-reference.md` | the formulas, the inputs the interview decided, and every property the app sets |

Every foundation file carries its share of the interview's decisions and off-scale rows,
each marked **settled** or **open**.

`tokens/token-reference.md` has a second owner: the **resolved values** table is refreshed
by whoever applies the theme and measures the result, since only a running app produces
it. Write the inputs and the formulas; label the resolved table as theirs.

## Hand over

The theme scope produces no delta. The hand-over lists, in this order:

| Heading | Content |
|---|---|
| Inputs decided | each root decision with its `From` and status |
| Off-scale values | each with where it appears, its nearest token, and the decision |
| Spec written | the files created or updated, and every row left **open** |
| For the theme's applier | what only a running app can fill: the resolved-values table, and the formulas if the spec had none |
| Design defects | every place the design contradicts itself or breaks the accessibility floor |
| Research notes | framework version, the Figma node ids, the mode names read, and what could not be verified |

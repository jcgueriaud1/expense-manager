---
name: figma-theme
description: "Settle a Vaadin Aura app's global theme against a Figma design: decide every divergence, write the CSS, and leave the record a per-view survey reads."
disable-model-invocation: true
argument-hint: "[figma url]"
---

# Figma theme

A design and an app each carry a whole theme, and they rarely agree. This skill
reconciles the two: it reads the design's global variables, compares them against what
the app's theme already sets, turns every **divergence** into a decision the user makes,
writes the resulting CSS, and leaves behind **the record** — the values a later run
looks up instead of deciding again.

The record is what makes this worth doing twice. A per-view survey that reads it can
tell a settled choice from a real finding; without one, every view re-raises the same
font mismatch forever.

Unlike a survey, this ends in code: the theme is a single bounded artifact whose
correctness is checkable in a browser, so the expensive part is the decision, not the
CSS.

Applies to Vaadin Flow (Java) on the Aura theme.

## Steps

### 1. Fix both ends

**Design.** `$ARGUMENTS` may carry a Figma URL. Take `fileKey` and `nodeId` from one
of the form `figma.com/design/:fileKey/:name?node-id=1-2`. Absent a URL, ask for one once, then
check whether the project records a design file (its agent instructions are the usual
home) before asking again. Pick a frame that exercises the whole theme — an app shell
or a dashboard, showing nav, fields, buttons and cards — because step 2 resolves
variables through the nodes that frame contains, and an unbound variable is invisible.

**App.** Aura is the precondition: `@StyleSheet(Aura.STYLESHEET)` marks it, and an app
on the classic Lumo theme is out of scope — say so and stop. Find the stylesheet the
app's own theme entry point imports and list every property it sets.

**Record.** Read the project's theme record if it has one; its values are the baseline
this run revises. No record means nothing has been settled yet, which makes every
divergence in step 4 open by default.

**Done when** one Figma frame id is fixed, every property the app's theme sets is
listed, and the record is either read or its absence noted.

### 2. Read the design's global variables, in every mode

Resolve variables through **bindings**, not through the file's collections. A design
that consumes the Aura kit as a library has **remote** variables:
`getLocalVariableCollectionsAsync()` returns the file's own collections only —
typically one unrelated collection with one mode — so an agent that trusts it concludes
the design ships a single mode when the kit's collection carries Light and Dark. That
failure is silent and it inverts the most visible decision in the theme. Consuming the
kit as a library is the normal case, not an edge one.

The binding walk: collect `boundVariables` across the frame's subtree, resolve each id
with `getVariableByIdAsync`, resolve its collection with `getVariableCollectionByIdAsync`,
and read `valuesByMode` keyed by the collection's mode names. Remote collections and
their modes are fully readable this way.

`get_variable_defs` flattens aliases to the active mode's value and takes no mode
parameter, which is why it cannot answer this. The binding walk needs `use_figma`, which
mandates loading its own guidance before the first call; do that, and keep the run
read-only — it is the one Figma tool that can mutate the design file.

Capture, for every mode:

| Design variable | Aura property |
|---|---|
| accent colour | `--aura-accent-color-light` / `--aura-accent-color-dark` |
| background colour | `--aura-background-color-light` / `--aura-background-color-dark` |
| font family | `--aura-font-family` |
| base font size | `--aura-base-font-size` |
| user colours 0–9 | `--vaadin-user-color-0` … `--vaadin-user-color-9` |
| field border tint / border colour | `--vaadin-input-field-border-color`, plus `--vaadin-input-field-border-width`, which it needs beside it |

The kit labels some variables with `lumo-` prefixes (`lumo-font-family`,
`lumo-font-size-m`). Those are the Aura inputs under an older name; a `lumo-` variable
with no Aura counterpart carries nothing.

Two modes means `color-scheme: light dark`; one mode means that scheme alone, and
leaves the other scheme's accent and background to derive — keep the hue and move the
lightness, and mark it derived, since it is your value and not the design's.

Whatever no variable binds is inferred from the frame instead: component heights and
spacing give `--aura-base-size`, corner rounding gives `--aura-base-radius`, card
elevation gives `--aura-surface-level`, a gap between the layout and the viewport edge
gives `--aura-app-layout-inset`. A dark nav against light content is `color-scheme: dark`
with `--aura-content-color-scheme: light`.

**Done when** every variable in the table has a value per mode or is explicitly absent,
the mode count is stated, and every unbound property carries either an inferred value or
an explicit "the design does not say".

### 3. Resolve the app's token scale

Aura derives radius, padding, gap and font-size from three inputs —
`--aura-base-radius`, `--aura-base-size`, `--aura-base-font-size` — through
`calc()`/`round()` expressions resolved at render time. The resulting values exist only
in a browser, so read them from the running app.

`getComputedStyle(document.documentElement).getPropertyValue('--vaadin-radius-m')`
returns the expression with its `var()`s substituted but not evaluated: that is the
formula. For the number, apply the token to a probe element
(`el.style.borderRadius = 'var(--vaadin-radius-m)'`) and read the used value back. Do
both for every `--vaadin-radius-*`, `--vaadin-padding-*`, `--vaadin-gap-*` and
`--aura-font-size-*`.

**The formulas are the reference.** The numbers are true only for today's inputs and go
stale the moment step 6 changes one; the formulas hold across a theme change and go
stale only on a Vaadin upgrade. Where a step's expression cannot be read, mark its
multiplier unverified rather than inferring it from one sample.

Where the record already carries the formulas for this Vaadin version, use them and
skip the measurement.

**Done when** each of the three scales has its formulas, the Vaadin version they hold
for is noted, and every unverified step is marked.

### 4. Turn every divergence into a decision

One row per property where the design and the app disagree. Properties that already
agree need no row and no question.

| Property | Design | App | Winner | Status | Why |
|---|---|---|---|---|---|

Neither side is right by default. The app's value may be a deliberate choice the design
never saw — a larger base font size for readability, a looser density for touch targets
— and the design's value may be the brand. So carry a recommendation into every row and
put the whole table to the user in **one** pass, so they confirm judgements rather than
answer a drip of questions.

`Status` is what a later run reads:

- **settled** — decided, whichever side won. A survey that meets this difference again
  names it as settled and moves on.
- **open** — deferred. A survey may raise it again, and should.

A divergence resolved in the app's favour is **settled**, not absent. That row is the
whole reason the record exists: it is what stops the next survey reporting the app's
own font as a mismatch.

An app with no theme file of its own diverges nowhere — every design value wins, and
every row is settled by construction.

**Done when** every property from step 2 is either identical on both sides or carries a
winner and a status the user chose.

### 5. Name the values the derivation cannot produce

Solve the formulas backwards, and the design's raw pixel values will disagree with each
other. A 9 px field radius asks for `baseRadius = 3` (`2·3+3`); a 12 px card radius in
the same design asks for `baseRadius = 1.33` (`1.5·1.33+10`), and at 1.33 the field
renders 6 px. No single input satisfies both: the design has left Aura's derivation.
Expect this wherever a designer drew by hand rather than placing a kit component.

Two shapes, across radius, padding/gap and font-size alike:

- **between** — the value sits between two steps of the scale (a 12 px radius; a 20 px
  padding).
- **beyond** — the value is off the end of it (a 24 px heading where the type scale
  stops at 18 px).

Each is a global decision, and taking it here is the point: a view that meets one alone
will invent an answer, and the next view will invent a different one. Decide each from:

- correct the design back to the scale;
- override the derived property directly, globally;
- define one extra project-level custom property, and use it everywhere the design uses
  that value;
- accept the nearest token and its visible divergence.

**Done when** every off-scale design value is listed with where it appears, its nearest
token, the decision taken, and a status from step 4's two.

### 6. Write the theme CSS

Set only what differs from the Aura default. Look each default up with
`get_theme_css_properties theme=aura` at the app's Vaadin version — a default recalled
from memory produces a declaration that looks theme-driven while only re-asserting what
was already true, or quietly misses the real default.

Edit the app's existing theme stylesheet in place; a second file competing with the
first is how two sources of truth start. A webfont needs its `@import` at the top of
that file.

Then confirm in the browser: reload the running app and re-read step 3's tokens. The
resolved numbers must equal what the decided inputs predict through the formulas. That
check is cheap, and it is the difference between CSS that was written and CSS that
works.

**Done when** every winner from step 4 and every override from step 5 is in the CSS, no
property is set to its own default, and the re-read tokens match the predicted values.

### 7. Write the record

The record holds the values a later run looks up. It is **data**, refreshed when the
theme changes or the Vaadin version moves — distinct from whatever the project writes
to hold a decision's *rationale*, which is prose written once and left alone.

Three sections, and a survey reads all three:

| Section | Content |
|---|---|
| Decided values | one row per property from step 4: the decided value, which side it came from, and **settled** or **open** |
| Resolved token scale | step 3's formulas, the table they produce at the decided inputs, the Vaadin version, and the refresh trigger — re-measure after a version bump |
| Off-scale values | step 5's list: the value, where it appears, its nearest token, the decision, and its status |

Put it where the project keeps durable notes an agent reads, and make the project's
agent instructions name that path — a record nothing points at is a record no survey
finds. Where a document already carries part of it, extend that document; a second copy
of the token scale is a copy that will disagree with the first.

**Done when** all three sections exist, every row in the first and third carries
**settled** or **open**, and the record sits at a path the project's agent instructions
name.

---
name: figma-theme
description: "Apply a settled design spec to a Vaadin Aura app's global theme: write only what differs from the theme's defaults, prove it in the browser, and refresh the resolved token values the spec records."
disable-model-invocation: true
argument-hint: "[nothing — the spec is the input]"
---

# Figma theme

This skill **applies** the global theme. It does not decide it.

The deciding happened in a theme-scoped `figma-survey`: that read the design's variables,
put every divergence to the user, and wrote the answers into the project's design spec.
This skill reads that spec and turns it into CSS — a single bounded artifact whose
correctness is checkable in a browser.

Splitting the two matters because the expensive part is the decision, not the CSS, and a
decision made while writing the stylesheet is a decision nobody reviewed. If the spec does
not exist, or its rows are still **open**, stop and say so: applying an unsettled spec
just moves the guessing into a file that looks authoritative.

Applies to Vaadin Flow (Java) on the Aura theme. Aura is the precondition —
`@StyleSheet(Aura.STYLESHEET)` marks it, and an app on the classic Lumo theme is out of
scope.

## Steps

### 1. Read the spec, and refuse an unsettled one

Read the design spec at the path the project's agent instructions name: the foundations
files for the decided values and the off-scale decisions, and the token reference for the
inputs and formulas.

Check three things before writing anything:

- **Every row that this run would apply is marked settled.** An **open** row is an
  undecided question; implementing it silently converts a deferral into a fact. List the
  open rows and stop.
- **The spec's framework version matches the app's.** The formulas are internals with no
  compatibility promise, so a spec resolved against another version may be describing a
  scale the app no longer has.
- **The spec is not empty.** No spec means no theme survey has run. Say so and stop —
  the survey is the prerequisite, not an optional first draft.

**Done when** the spec has been read, its version checked against the app's, and every row
in scope confirmed settled — or the run has stopped with the open rows named.

### 2. Fix the app end

Find the stylesheet the app's theme entry point imports, and list every property it
currently sets. That list is what step 3 diffs against: some declarations will be replaced,
some deleted outright, and knowing which is which is the difference between a rewrite and
an edit.

Note anything in the file that is **not** a theme value — component rules, selector
overrides. Those are not this skill's business unless the spec decides them, and a rewrite
that quietly drops them is how a nav ends up unstyled.

**Done when** every property the app's theme sets is listed, and non-token rules in the
file are identified as out of scope or explicitly in it.

### 3. Write the CSS

**Set only what differs from the theme's own default.** Look each default up rather than
recalling it — a default recalled from memory produces a declaration that looks
theme-driven while only re-asserting what was already true, or quietly misses the real
default. A file full of restated defaults hides the handful of properties that actually
differ.

So a value the spec settled *in favour of the framework default* becomes a **deletion**,
not a declaration. Expect the file to shrink.

Edit the app's existing stylesheet in place; a second file competing with the first is how
two sources of truth start. A webfont needs its `@import` at the top of that file — but
check whether the theme already bundles the family before adding one, because an
unnecessary import is a network request for a font that was already there.

Three things to carry across rather than lose:

- **A declaration another component depends on.** Where a Java component's behaviour reads
  a theme property, dropping that property breaks the component silently. Find out before
  deleting; a comment in the file saying why a line exists is a warning, not decoration.
- **Rules for a part of the UI the spec has not reached yet.** Comment-mark them as pending
  their own issue rather than deleting them, so the app is not left unstyled in between.
- **A comment explaining a non-obvious line.** If you cannot restate why it is there, that
  is a reason to keep it and ask.

**Done when** every settled value from the spec is applied, no property is set to its own
default, and nothing was deleted whose purpose you could not name.

### 4. Prove it, then refresh the resolved values

Reload the running app and read the tokens back. **The resolved numbers must equal what the
decided inputs predict through the spec's formulas.** That check is cheap, and it is the
difference between CSS that was written and CSS that works.

Then exercise what the theme cannot prove by inspection:

- **Both colour schemes**, on more than one screen. A theme is a claim about every screen,
  and the scheme nobody looks at is where a frozen literal surfaces.
- **Any live scheme switch the app offers.** Where a control resets to the OS preference,
  that usually works by *clearing* an inline property so the stylesheet's own declaration
  applies again — so the test is that the reset resolves to the stylesheet's value, not
  that the declaration is present in the file.
- **The states the spec records but a static frame cannot show** — hover, disabled, focus.
  Read what the theme actually changes: it may express hover as a pseudo-element overlay
  and disabled as `opacity` on the host, leaving `background-color` and `color` identical
  in both. An audit reading those two properties reports no difference having measured
  nothing. Diff the full computed style between two states when unsure which property
  moves.

**Then write the measurements back.** The spec's *resolved values* table is this skill's to
own, because only a running app produces it: record the numbers at the decided inputs, the
framework version they hold for, and the refresh trigger. Where the spec quotes a measured
figure elsewhere — a contrast ratio in a component file — this run either confirms it or
corrects it.

**Capture the formulas too, whenever the spec lacks them** — a first run, or a framework
upgrade since the last one. A survey never boots the app, so it defers this here and
records the gap rather than guessing; leave the gap unfilled and the next survey cannot
judge an off-scale value at all. `getComputedStyle(document.documentElement)` returns each
token's expression with its `var()`s substituted but not evaluated: that is the formula.
For the number, apply the token to a probe element and read the used value back. Do both
for every step of every scale, and mark any step whose expression you could not read as
unverified rather than inferring a multiplier from one sample.

**The probe trap:** `element.style.setProperty()` takes a **CSS** property name, so
`setProperty('borderTopLeftRadius', …)` fails silently and every token reads back as `0px`
or `normal`. Use `'border-top-left-radius'`. A whole measurement pass can look successful
and be entirely zeros, and the zeros are indistinguishable from a real answer if you are
not expecting them.

Finally, say what this run **staled**. Changing an input moves every derived value, so any
component spec quoting a resolved number is now suspect even though its token names are
still correct. Recommend a component-spec refresh; do not perform it here.

**Done when** the re-read tokens match the predicted values, both schemes and every state
the spec records have been exercised, the resolved-values table is written back, and the
staled figures are named.

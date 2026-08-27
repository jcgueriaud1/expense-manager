---
name: figma-component-spec
description: "Write and maintain the per-component half of a project's design spec: one file per component that exists, grounded in its source rather than in memory."
argument-hint: "[component name, or 'audit' for every component]"
---

# Figma component spec

A design spec's foundations settle what is true globally — the accent, the type scale,
which pixel values the token scale cannot produce. They cannot settle what a *card* is.
That question gets answered per view, and answered differently each time, which is where
drift actually happens: four project-level properties can be defined, recorded and still
consumed inconsistently because nothing said which element is entitled to them.

This skill writes the layer that answers it: **one file per component that exists**,
carrying the things the source cannot state — when to reach for this component and when
for its sibling, which tokens it is entitled to, and which states must hold.

Two ways in, and they differ only in scope:

- **After a change** — spec the components the change created or altered, in the same
  change as the code. Invoke this immediately after building or modifying a component;
  a spec written later is a spec written from memory.
- **Audit** (`$ARGUMENTS` is `audit`, or names no component) — enumerate every component
  in the project, write the specs that are missing, and flag the ones that have gone
  stale.

Unlike a survey, this ends in committed files. Unlike a theme run, it touches no CSS.

## Steps

### 1. Fix the scope and find the spec's home

**Home.** The per-component specs belong in a `components/` directory inside the
project's design-spec folder, which the project's agent instructions name. Read that
folder's own `README.md` first: where it defines a template or an inventory table, those
are binding and override anything below. Where there is no such directory, say so and
stop — creating a component layer with no foundations to sit under is the wrong order,
and settling the global theme comes first.

**Scope.** After a change, the scope is exactly what the change touched — nothing else.
Do not rewrite the spec of a component the change did not alter; a run that rewrites
untouched files produces churn that hides the real diff. On an audit the scope is every
component, and step 2 enumerates them.

**Done when** the `components/` directory is located, its README has been read, and the
list of components in scope is fixed and named back to the user.

### 2. Enumerate what actually exists

Only spec components that exist **today**. A spec for something planned is a spec that
describes nothing, and it is indistinguishable from a spec for something real.

Two sources, and a component is usually visible in one or the other:

- **Shared classes** — a component in the project's shared UI package, with a
  constructor other code calls.
- **Style clusters** — a group of CSS classes sharing a prefix and rendered together
  (`.line-card`, `.line-name`, `.line-amount`, `.line-receipt`), composed by a view.
  These are components without being classes, and they are the ones most often missed.

**A component is a role, not a CSS class.** A cluster of classes rendered as one thing
gets **one** file, named for the role in kebab-case — not one file per class. Splitting
by class produces a spec per selector, which is a restatement of the stylesheet and goes
stale with every rename.

Where one component is a variant of another — the same box with a different surface —
spec the base and give the variant its own file only if a caller chooses between them.
Otherwise record it as a state or a modifier in the base's file.

**Done when** every component in scope maps to exactly one intended file, and any
class or shared component deliberately *not* being specced is named with the reason.

### 3. Read the ground truth

**Read the source before writing a word.** The stylesheet for the classes, the class for
its API and its own comments, the view for how it is composed. Where the component came
from a design frame, read the frame too — but the source is what ships, so the source
wins on anything the two disagree about.

This is the step a run is tempted to skip, because a plausible spec can be written from
the component's name alone. Such a spec is worse than none: it reads as verified, it is
cited by later work, and nothing about it is checkable. Every token in the spec must be
one you have seen the component reference.

**Done when** every component in scope has its stylesheet rules, its API and its
composition read, and the tokens it references are listed from what was read rather than
recalled.

### 4. Write the spec

Follow the project's template where its `components/README.md` defines one. Absent that,
cover in this order:

1. **Metadata** — category, status, source paths.
2. **Overview** — when to use it, and when to reach for its sibling instead. The second
   half is the one that prevents misuse; a spec that only says what a component is for
   never stops the wrong one being chosen.
3. **Anatomy** — the parts, each mapped to its class or element.
4. **Tokens used** — tokens only. A raw pixel value here is either a bug or a decision
   the foundations already recorded; if it is the latter, cite it. If it is neither, stop
   and raise it rather than documenting it as intended.
5. **API** — the few things a caller needs. Skip for a style-only component.
6. **States** — see below.
7. **Code example** — the shortest correct usage, and no longer.
8. **Cross-references** — the sibling components, and the ADR or finding behind any
   choice a reader would otherwise question.

**Do not restate what the source already says.** A copied constructor signature or
javadoc paragraph is duplication that will disagree with the source within a release.
Record intent, precedent and entitlement — the things reading the code cannot give you.

**Done when** every section is present, no section duplicates the source, and every
token listed was read in step 3.

### 5. Account for every state

Cover **default, hover, active, focus, disabled, error**. Write **`n/a` with the reason**
where a state cannot occur; never leave a row blank and never drop the row. A blank reads
as "not checked" and a missing row reads as "no such state" — neither is a claim anyone
can act on, and both are indistinguishable from an oversight.

This section catches more than the rest of the file together, for two reasons:

- **A state the design never drew is a state nobody verified.** Design frames show the
  resting state. Hover, disabled and error arrive from the framework's defaults, unread.
- **State styling is often invisible to the obvious check.** A theme may express hover as
  a pseudo-element overlay and disabled as `opacity` on the host, leaving
  `background-color` and `color` untouched in both. An audit reading those two properties
  then reports the disabled control as identical to the enabled one — and concludes the
  states are fine having measured nothing. Read what the theme actually changes; diff the
  full computed style between two states if you are unsure which property moves.

Where a state's contrast or legibility matters, record the measured figure, not an
assurance that it was checked.

**Done when** all six states carry either a behaviour or an explicit `n/a` with its
reason, and any measured figure is stated as a number.

### 6. Audit for staleness

On an audit run, and for every file touched otherwise, check the spec against the code it
describes:

- a token the stylesheet references that the spec's token table omits;
- a token the spec lists that the stylesheet no longer references;
- a class or part in the stylesheet absent from the anatomy;
- a spec whose source path no longer exists.

Each is a divergence between the spec and what ships, and a spec that has silently
drifted is worse than a missing one because it is still trusted. Fix what is
mechanical; report what needs a decision.

**Done when** every spec in scope has been diffed against its source, and each
divergence is either corrected or reported.

### 7. Report

State plainly: which specs were written, which updated, which components were skipped
and why, and every divergence found in step 6 that was left for a decision. Where the
project's inventory table lists the components, update it.

Name what the run could **not** establish — a state you could not exercise, a token whose
origin you could not trace. An unverified row said to be unverified costs the next run
five minutes; the same row presented as settled costs it a wrong decision.

**Done when** the user has the list of files written, the skips with reasons, the open
divergences, and the limits of what was verified.

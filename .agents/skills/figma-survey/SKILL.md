---
name: figma-survey
description: "Survey a Figma design against what a Vaadin app already builds, interview the user in rounds on every divergence, and write the project's design spec. Ends in the spec plus a delta in the conversation — never in code, theme CSS, or a ticket."
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
  written checkable so each item can become a ticket's acceptance-criteria line.

It writes no application code, no theme CSS, and **no ticket**. Applying the spec belongs
to `figma-theme` (the global theme) or to implementation (a view). Filing the delta belongs
to a separate ticketing pass, run on this conversation once the user has read the delta.
The survey ends in the conversation, and that pause is what lets the expensive judgement
calls be reviewed before a ticket is worded or a diff is started.

Applies to Vaadin Flow (Java) on the Aura theme.

## Two rules that shape every step

**Facts are yours; decisions are the user's.** Anything readable from the design, the code
or the existing spec, you look up. Anything that picks one side over the other, you put to
the user in **rounds**, each question carrying your recommendation — the method is
[`interview.md`](interview.md). The spec is written only once the interview's **frontier**
is empty.

**A survey never boots the app.** Its three inputs — the design, the code, the existing
spec — are all readable without one, and a running app can only show the scale you are
about to replace. A figure only a browser produces (a contrast ratio, a rendered pixel
size, a resolved token) is left marked **unverified** for whoever applies the spec. An
unverified row said to be unverified costs the next run five minutes; the same row
presented as settled costs it a wrong decision.

## Scope

`$ARGUMENTS` fixes one of three scopes. The scope decides which **branch file** supplies
the reading and the writing in steps 2, 5 and 6:

| Scope | Target | Writes | Branch |
|---|---|---|---|
| **theme** | the design's global variables, across every mode | `foundations/`, and the inputs in `tokens/` | [`theme-scope.md`](theme-scope.md) |
| **view** | one frame — a screen or a dialog | `components/` for the components on it, plus the delta | [`view-scope.md`](view-scope.md) |
| **component** | one component, wherever it appears | that component's file in `components/` | [`view-scope.md`](view-scope.md) |

**Do the theme scope first, and once.** It decides the font family and base size, which
reflow every screen; per-view spacing settled against the old scale is work thrown away.

## Steps

### 1. Resolve the target and find the spec's home

`$ARGUMENTS` names a scope, or a view or component in whatever words came to hand —
`"Add Expense"`, `LineEditorDialog`, `report detail` — and may include a Figma URL.

Fix the design file first: take `fileKey` and `nodeId` from a URL of the form
`figma.com/design/:fileKey/:name?node-id=1-2`. Absent a URL, check whether the project
records a design file (its agent instructions are the usual home) before asking for one.

Resolve the target on the design side with `get_metadata` on the page to list frame
names; the branch file says how to pick the frame. Name the resolution back to the user,
and ask only when two candidates are genuinely indistinguishable.

**The spec's home** is the directory the project's agent instructions name. Read what is
already there — it is the baseline this run revises, not a blank page. No spec at all means
nothing has been settled yet, which makes every divergence in step 4 open by default; say
so, because it bounds what this survey can conclude.

**Done when** the scope is fixed, one Figma node id is fixed, and the existing spec is
either read or its absence noted.

### 2. Read both ends

Follow the branch file's reading sections: **theme** reads the design's variables through
their bindings; **view** and **component** find what the code already builds, read the
frame, and map every component instance to a Vaadin component.

Everything in this step is fact-finding. Where a branch file marks a row as judgement — an
`override` or `invented` mapping, a value inferred from a drawing — carry it forward as a
question for step 4 rather than settling it here.

**Done when** the branch file's reading criteria are met and every judgement call it
surfaced is on the list for step 4.

### 3. Establish the app's values — from files

Take the **formulas** from the spec's token reference — `round(baseRadius * 2px + 3px,
1px)` and its siblings. They are what the framework derives each scale with, they are
stable across a theme change, and they go stale only on a framework upgrade, which is why
the spec caches them. Then list every property the app's own theme file sets, so step 4
knows what is a deliberate app choice rather than a default.

**Compute the resolved values; do not measure them.** Given the formulas this is
arithmetic, and it is the only correct way round — step 4 is about to change an input, so
the numbers you need are the ones at the **decided** inputs, not the ones the app renders
today. Scored against the outgoing base, the wrong base can look better: a type scale
whose small step is a *clamped* fraction of the medium step lands a design's most-used
size exactly on a token at one base and between two tokens at another.

**Where the spec has no formulas** — a first run, or a framework upgrade since the last
one — say so and stop short of asserting any resolved number or off-scale verdict. Those
rows stay unresolved for a measurement pass to fill; obtaining formulas belongs to whoever
applies the theme and is already in a browser.

**Done when** the formulas are in hand or their absence is recorded, the resolved values
at the decided inputs are computed rather than measured, the version they hold for is
noted, and every property the app's theme sets is listed.

### 4. Interview: turn every judgement into a decision

Build the **design tree** of everything steps 2 and 3 left to judgement, and work it in
rounds per [`interview.md`](interview.md). Its material, across every scope:

- **Divergences** — one per property where the design and the app disagree. Properties
  that already agree need no row and no question.
- **Off-scale values** — solve the formulas backwards and the design's raw pixel values
  disagree with each other: a 9 px field radius asks for one base radius and a 12 px card
  radius for another, at which the field renders 6 px. Each such value sits **between** two
  steps of the scale or **beyond** its end, and each is a global decision with four
  options: correct the design back to the scale, override the derived property globally,
  define one project-level property used everywhere that value appears, or accept the
  nearest token and record the difference. Recommend a property only for a value several
  pixels off *and* recurring; within a pixel or two, the nearest token — a property
  shadowing the scale for one pixel is where drift starts. Where the framework has a real
  property for exactly that value, override it rather than inventing a twin.
- **Judgement calls the branch file surfaced** — mappings, accepted infidelities, domain
  gaps.

Two constraints are **facts to report, never questions**:

- **The accessibility floor.** Contrast, touch-target size and focus visibility are
  constraints. A design value that breaks one is a design defect; the app keeps the
  accessible value, recorded as a settled divergence.
- **A value the design contradicts itself on.** Where a variable and the drawn value
  disagree, the variable wins, and the drawn value is a design defect to report.

**Done when** the frontier is empty and the user has confirmed the tree: every property is
either identical on both sides or carries a decision with a status — **settled** or
**open** — that the user chose.

### 5. Write the spec

Write into the project's spec directory, following any template its own `README` defines
— where it does, that is binding and overrides the branch file. The branch file says which
files and what each covers.

Every decision row carries **settled** or **open**. A divergence resolved in the **app's**
favour is settled, not absent — that row is what stops the next survey reporting the app's
own font as a mismatch. A concern with no divergence still gets its file or its section
saying so; absence recorded is what stops the next survey re-deriving it. A figure only a
browser can produce stays **unverified**.

**Done when** every file the scope calls for exists, every decision row carries a status,
and no measured figure is asserted without a measurement.

### 6. Hand over in the conversation

Write the hand-over as your final message, under the headings the branch file gives. It is
the input to a ticketing pass, so it must stand without this conversation: a reader lifting
it into a ticket should never need to re-read the frame.

Global theming differences do **not** go in a view's delta. They were decided in step 4 and
written in step 5, which is what stops the next survey re-litigating the same radius.

**The survey stops here.** Name the ticketing pass as the next step and leave it to the
user; the delta is theirs to read before it becomes anyone's work.

**Done when** every heading is filled or explicitly marked empty, the spec files are
listed, and the message ends with the next step named.

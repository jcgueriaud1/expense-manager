# The interview

How a survey puts its decisions to the user. Every judgement the survey meets is decided
here, in rounds, before a line of the spec is written. **Facts are yours; decisions are
the user's.** A fact is anything the design, the code or the existing spec can answer — look
it up, and where a question turns on one you have not yet read, go and read it rather than
asking. A decision is anything that picks one side over another, and it is put to the
user every time, however obvious the answer looks.

## The design tree

Map the decisions as a **design tree**: each decision hangs off the ones it depends on. A
survey's tree has a recognisable shape, and knowing it is what lets you ask in the right
order:

| Tier | Theme scope | View and component scope |
|---|---|---|
| **Root** — the inputs | mode count, font family, base font size, base radius, density, accent | the app-wide conventions the frame touches: label case, a value that may earn a project property, a pattern the app already uses elsewhere |
| **Branch** — the scale | where each off-scale value lands, which depends on the root because the resolved scale moves when an input moves | which token each design value takes, which depends on the root because a convention settled one way removes the question |
| **Leaf** — the particulars | one derived scheme's tint, one semantic colour | per-component values, `override` and `invented` mappings, an accepted infidelity, a domain gap |

A question at one tier is answerable only when the tier above it is settled. That is the
whole reason for rounds.

## Rounds

The **frontier** is every decision whose prerequisites are settled — the questions you can
ask *now* without guessing at answers you have not heard. Ask the whole frontier in one
round; a question whose answer depends on one still open this round belongs to the next.
Then wait. Each round's answers push the frontier outward and unblock the questions that
depended on them; recompute it and ask the next round.

Every question carries what the two sides say and your recommendation:

```
❓ **Q1** — **<property or call>**: design <value, and whether a variable binds it>;
app <value, and whether the theme file sets it deliberately>. <What the choice
affects, and any constraint that narrows it.>

➡️ <your recommended answer, in one line>

---

❓ **Q2** — …
```

**Recommend the faithful option.** The design is the source of truth, so where it and the
app disagree on a value the recommendation is the design's, unless the app's value is a
recorded decision or a constraint (the accessibility floor, a value the design contradicts
itself on — both are facts, reported rather than asked). An **app-wide convention** is the
exception in the other direction: it outranks a single survey, so recommend leaving it
**open** and naming it in the hand-over as its own decision for a human.

An answer can be a deferral. Record it as **open** and move on; the frontier does not
wait on it, though its subtree does.

## Recording an answer

Each answer becomes one row:

| Property | Design | App | Decided | From | Status |
|---|---|---|---|---|---|

`From` is `design`, `app`, or `derived` — a value that is yours and not the design's (a
dark scheme derived from a single-mode design, say) is marked so. `Status` is what a later
run reads:

- **settled** — decided, whichever side won. A survey meeting this difference again names
  it settled and moves on.
- **open** — deferred. A survey may raise it again, and should.

## Done when

The frontier is empty — every branch of the tree visited, nothing left silently assumed —
and the user has confirmed the tree reads as they meant it. Only then is the spec written.

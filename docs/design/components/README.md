# Component specs

One file per component that **exists in this app, or that a survey's delta will
create**. Nothing speculative beyond that: a component nobody has committed to building
does not get a file, and one that is merely *imagined* for a future view does not either.

A file written ahead of the code carries `Implementation: none`, which is exactly what
that value is for — the spec is the contract, so writing it first is the intended order
when the delta that will satisfy it is already filed.

**Written by `/figma-survey`**, scoped to a view or a component. These files are the
**contract**: implementation takes its tokens and states from them, and a difference is a
bug in the code rather than in the file. Never edit one to match what was just built —
that turns the contract into a transcript and makes the drift invisible. Where the design
has moved, or a component has no file, re-run the survey.

A spec records what the source cannot tell you — when to use the component, when not to,
which tokens it is entitled to, and which states must be covered. It does **not**
duplicate the constructor signature or the class javadoc; those are readable from the
source and would go stale here.

## Inventory

| Component | Category | Origin | Implementation |
|---|---|---|---|
| [`button.md`](button.md) | themed primitive | design | conforms |
| [`badge.md`](badge.md) | themed primitive | design | conforms |
| [`report-card.md`](report-card.md) | composite | design | unaudited |
| [`metric-card.md`](metric-card.md) | composite | design | none |
| [`report-list-section.md`](report-list-section.md) | composite | design | unaudited |
| [`expense-line-card.md`](expense-line-card.md) | composite | design | **drifted** |
| [`travel-card.md`](travel-card.md) | composite | design | **drifted** |
| [`totals-card.md`](totals-card.md) | composite | design | **drifted** |
| [`status-callout.md`](status-callout.md) | composite | **unresolved** | unaudited |
| [`status-history.md`](status-history.md) | composite | design | **drifted** |
| [`error-summary.md`](error-summary.md) | shared Java component | code | conforms |
| [`editor-dialog.md`](editor-dialog.md) | shared Java component | code | conforms |
| [`empty-state.md`](empty-state.md) | shared Java component | code | unaudited |
| [`theme-switcher.md`](theme-switcher.md) | shared Java component | code | unaudited |
| [`app-shell.md`](app-shell.md) | shell | design | unaudited — rebuilt in #146 |

## The two axes

They are independent, and collapsing them into one status field hides which problem you
have.

**Origin — where the spec came from, and therefore which side is authoritative:**

| Origin | Meaning | Direction of authority |
|---|---|---|
| `design` | the design specifies this component | **the spec is the contract.** Code conforms to it; a difference is a bug in the code. *Never* edit the spec to match the code |
| `code` | the app invented it; the design never drew it | **the code is the source.** The spec documents it, and updating the spec when the code changes is correct |
| `unresolved` | not yet established | treat as `design` and find out |

The split matters because "never edit a spec to match what you built" is right for a
design-origin component and wrong for a code-origin one. `ErrorSummary` came from ADR-0020
and the GOV.UK pattern, `EmptyState` from ADR-0017, `EditorDialog` from F-045 — there is no
design contract for any of them, so the code is what a spec can be checked against. If a
code-origin component later gets designed, origin flips to `design` and
[ADR-0025](../../adr/0025-figma-design-source-of-truth.md) takes over: the design wins.

**Implementation — whether the code matches, which is *derived, not declared*:**

| Value | Meaning |
|---|---|
| `none` | the spec is ahead of the code; nothing implements it yet |
| `conforms` | no mechanical divergence found |
| `drifted` | the code and the spec disagree; the spec carries a **Divergence** section naming what and who owns closing it |
| `unaudited` | no comparison has been made |

**This field belongs to an audit, not to a human.** A hand-kept conformance field goes
stale silently, which is the failure it exists to catch. `conforms` should be read as "no
mechanical divergence found" and no more — an audit can tell that a token is missing from
a table, not that the right component was chosen for the design's intent.

> **The values above were assessed by hand during issue #144.** No audit tooling exists
> yet, so treat every `conforms` as provisional and every `unaudited` row as literally
> that. One spec still predates any check of its design counterpart: `status-callout` was
> written from the stylesheet alone.
>
> The report *list* frame (`116:2499`) was surveyed for the report-list redesign, which
> resolved `report-card`'s design reference and added `metric-card` and
> `report-list-section`. `metric-card` is `none` on purpose — the spec is ahead of the
> code, which is what that value is for.
>
> `empty-state` dropped from `conforms` to `unaudited` in #163, which changed its
> constructor to take a built icon: the spec was updated by the change that caused the
> drift, so its `conforms` would have been self-asserted. That is what `unaudited` is for.
>
> **One design-origin row needs a survey, not an edit.** `report-list-section`'s *Anatomy*
> names the chevron as `VaadinIcon.CHEVRON_UP`, an API that #163 removed from the app and
> that was never what the design drew — the design's node is `lucide/chevron-up`
> (`134:1768`), and the shipped implementation uses Aura's own `Details` toggle rotated in
> CSS (correct, per ADR-0026 decision 2). The row is wrong against the design *and*
> against the code, so it is `/figma-survey`'s to fix; editing it here would launder a
> design-origin spec into a transcript.

## Who writes these

| Origin | Author |
|---|---|
| `design` | `/figma-survey`, scoped to a view or a component |
| `code` | whoever builds the component — `implement-use-case` |
| either, once implemented | the audit sets `Implementation` and adds or clears **Divergence** |

## Template

Every file follows this order. Omit a section only when it genuinely does not apply, and
say so rather than dropping the heading silently.

```markdown
# <Name>

**Category:** themed Vaadin primitive | composite | shared Java component | shell
**Origin:** design | code | unresolved
**Implementation:** none | conforms | drifted | unaudited      <- set by the audit
**Code:** <path(s) and CSS classes> — or an em dash if nothing implements it yet
**Design:** <node id > layer name> — or an em dash, with why, if never designed

## Overview
When to use it. When *not* to — the sibling it is confused with.

## Anatomy
The parts, and which class or element each maps to.

## Tokens used
Only tokens. A raw px value in this table is a bug or a recorded off-scale decision.

## API
Constructor and the few methods a caller needs. Skip for CSS-only composites.

## States
default · hover · active · focus · disabled · error — say "n/a" where a state
cannot occur, and never leave a row blank.

## Code example
The shortest correct usage.

## Divergence
Only when Implementation is `drifted`: what differs, and who owns closing it.

## Cross-references
Related components, and the ADR or finding behind any non-obvious choice.
```

## Why States is mandatory

It is the section most easily skipped and the one that catches the most. A real example:
issue #144 verified the new black primary button's contrast at rest and reported the
states as checked, when hover had never been tested and the disabled check was reading
`background-color` — a property Aura does not touch for either state. A spec with a
States row forces the question. See
[`../foundations/elevation.md`](../foundations/elevation.md) and the gotcha in
[`../../vaadin-gotchas.md`](../../vaadin-gotchas.md).

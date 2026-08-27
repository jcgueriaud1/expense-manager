# Component specs

One file per component **that exists in this app**. Nothing speculative: if it is not in
`src/main/java` or `styles.css` today, it does not get a file.

**Maintained by `/figma-component-spec`.** Run it in the same change that builds or
alters a component; `audit` mode backfills missing specs and flags ones that have drifted
from their source. `/figma-survey` reads these and reports a component with no spec as a
gap, but never writes them.

A spec records what the source cannot tell you — when to use the component, when not to,
which tokens it is entitled to, and which states must be covered. It does **not**
duplicate the constructor signature or the class javadoc; those are readable from the
source and would go stale here.

## Inventory

| Component | Category | Status |
|---|---|---|
| [`button.md`](button.md) | themed Vaadin primitive | settled |
| [`badge.md`](badge.md) | themed Vaadin primitive | settled |
| [`report-card.md`](report-card.md) | composite | settled, pending per-view revision |
| [`expense-line-card.md`](expense-line-card.md) | composite | settled, pending per-view revision |
| [`travel-card.md`](travel-card.md) | composite | settled, pending per-view revision |
| [`totals-card.md`](totals-card.md) | composite | settled, pending per-view revision |
| [`status-callout.md`](status-callout.md) | composite | settled |
| [`status-history.md`](status-history.md) | composite | settled |
| [`error-summary.md`](error-summary.md) | shared Java component | settled |
| [`editor-dialog.md`](editor-dialog.md) | shared Java component | settled |
| [`empty-state.md`](empty-state.md) | shared Java component | settled |
| [`theme-switcher.md`](theme-switcher.md) | shared Java component | settled |
| [`app-shell.md`](app-shell.md) | shell | **open — #146 replaces it** |

"pending per-view revision" means the component works and is token-correct, but its
spacing has not yet been reconciled against the design's own card padding and radius —
that is each view's own issue.

## Template

Every file follows this order. Omit a section only when it genuinely does not apply, and
say so rather than dropping the heading silently.

```markdown
# <Name>

**Category:** themed Vaadin primitive | composite | shared Java component | shell
**Status:** settled | open
**Source:** <path(s)>

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

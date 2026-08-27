# Motion

**The design specifies no motion.** It is a set of static frames with no prototype
transitions, no bound motion variables, and no documented easing or duration. So there is
nothing to settle from it, and the app keeps its own small, deliberate set.

## Decisions

| Property | Design | App | Decided | From | Status |
|---|---|---|---|---|---|
| Transitions, easing, duration | not specified | one hover transition (below) | keep the app's | design does not say | **settled** |
| Reduced-motion handling | not specified | none needed yet | — | — | **open** |

## What the app animates

| Element | Transition |
|---|---|
| `.report-card` | `background 0.12s ease` on hover |

That is the whole inventory. Aura supplies its own transitions inside components
(buttons, overlays, fields); those are stock and not overridden.

## Rules

- **Colour and background only.** Nothing in this app animates layout, size or position,
  which keeps every transition off the compositor's critical path and avoids reflow.
- **Keep durations at or under ~150ms** for state feedback, matching the existing 0.12s.
  A hover that takes longer reads as lag rather than response.
- **A new transition needs a row here.** Motion is the easiest thing to add per-view and
  the hardest to notice diverging.
- **If motion grows beyond hover feedback, add a `prefers-reduced-motion` block**
  (ADR-0020). It is marked **open** above rather than **settled** because the app has not
  needed it yet, not because it was decided against.

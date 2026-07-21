# Finding schema

The data model for every finding, and the markdown rendering used when the HTML report (see `html-report.md`) isn't feasible. One concern per finding — if you are tempted to write "and also", split it.

Prose discipline applies in both renderings: **Problem** and **Solution** are one sentence each; **Wins** are bullets of ≤6 words naming concrete deltas ("3 listeners deleted", "one update path") — never "cleaner code" or "easier to maintain". The **before/after code excerpts** (real project code, ≤15 lines per side) carry the explanation; if they need a paragraph to be understood, re-trim them.

```markdown
## [T1-003] CustomBadge → official Badge component

**Tier**: 1 (component modernization)
**What**: Replace `com.example.ui.CustomBadge` with the official `Badge` component (available since 24.1; project is on 24.9).
**Where**: `src/main/java/com/example/ui/CustomBadge.java` — 7 usages across 4 views (OrderListView, CustomerView, ...).
**Why now**: Official Badge covers the rendered output (colored label, text, theme variants). CustomBadge predates its availability.

**Before**:
```java
// CustomBadge.java (48 lines) — Span + custom CSS class
public class CustomBadge extends Span {
    public CustomBadge(String text, Status status) {
        setText(text);
        addClassName("custom-badge");
        addClassName("custom-badge--" + status.cssSuffix());
    }
}
```
**After**:
```java
Badge badge = new Badge(text);
badge.addThemeVariants(statusToVariant(status)); // SUCCESS, ERROR, ...
// CustomBadge.java and custom-badge.css deleted
```

**Problem**: 48 lines of custom component + CSS reimplement what the official Badge ships.
**Solution**: Swap all 7 usages to `Badge` with theme variants; delete the class and its stylesheet.
**Wins**:
- 48 lines + CSS file deleted
- official theming for free
- one less component to document
**Delta**: CustomBadge additionally animates on value change — official Badge does not. Decide: drop the animation, or keep CustomBadge (record in exceptions file).
**Risk**: Mechanical swap. Visual-only change surface.
**Verification**: No behavioral logic. Visual check per affected view; browserless-test assertion on rendered element name/theme if coverage is desired.
**Confidence**: High — single-purpose component, direct catalog match. Would drop to medium if the animation turns out to be relied upon in tests or specs.
**Learn**: https://vaadin.com/docs/latest/components/badge

**Session prompt**:
> In this Vaadin 24.9 project, replace the custom component `com.example.ui.CustomBadge` with the official `Badge` component at all 7 usage sites. The custom animation on value change is intentionally dropped (per audit decision). Preserve theme/color mapping: [observed mapping]. Verify each affected view renders and remove the now-unused CustomBadge class.
```

Field notes:

- **ID**: `T<tier>-<seq>` so findings are referenceable in the exceptions file and follow-up sessions.
- **Delta**: only for component findings — what the custom version does that the official one doesn't. If non-empty, the finding is a *decision*, not just a task; say so.
- **Preview**: set when the suggested component is a Preview feature. Record the feature flag and cap confidence at medium; the finding renders a Preview warning badge (feature flag required, API may change or be removed). Never framed as a stable drop-in.
- **Why now**: in delta audits, name the version change that made this possible ("new in 25.0").
- **Verification**: for Tier 2 findings, check whether the affected unit has test coverage. If not, the session prompt's FIRST step must be writing a browserless-test characterization test of current behavior before any refactoring.
- **Learn**: the docs link(s) from the finding's catalog entry, so the reader can study the capability before deciding. Mandatory on every finding.
- **Session prompt**: written for a fresh agent session with zero context from this audit — self-contained, names exact classes/files, states the decided scope. For **Threading-risk** findings, the prompt MUST begin with a `Human checkpoint` block: the threading assumptions the user confirms before starting the session (never omit the prompt, never omit the checkpoint).

## Report skeleton

```markdown
# Modernization audit — <project> (Vaadin <version>[, delta <old>→<new>])

## Summary
<counts per tier, notable repetitions, pilot recommendation if a pattern repeats>

## Tier 1 — Component modernization
<findings>

## Tier 2 — Pattern modernization
<findings>

## Skipped
<candidates covered by exceptions file (IDs only); ambiguous cases deliberately not flagged>

## Next steps
1. Reject → record in .vaadin/modernization.md (offer to write entries)
2. Accept → one fresh session per finding, using its session prompt
```

The Skipped section is deliberate: showing what the audit chose *not* to flag is what makes repeated runs trustworthy.

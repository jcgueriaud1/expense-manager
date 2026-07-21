# Exceptions file: `.vaadin/modernization.md`

Human-owned, checked into the repo. Records team decisions so audits stop re-litigating them. The audit reads it in step 3 and suppresses matching findings; it may append entries **only when the user explicitly rejects a finding and asks to record it**.

Format — one entry per line under two sections:

```markdown
# Modernization decisions

## Keep (intentional deviations — do not flag)
- CustomBadge: keep — value-change animation is a product requirement (audit T1-003, 2026-07)
- OrderPanel: keep — diverges from CRUD component deliberately, custom workflow
- LegacyDateWidget: keep until customer X migration completes (revisit 2027)

## Adopted patterns (reference as established, don't re-propose broadly)
- signals: adopted — pilot pattern in DashboardView (audit T2-001, 2026-07)
```

Matching rules for the audit:

- **Keep entries** match by class/component name (first token before the colon). A matching candidate produces no finding; list its name in the report's Skipped section.
- **Adopted entries** don't suppress findings — they change their framing: subsequent findings for that pattern reference the established pilot ("apply the pattern established in DashboardView") instead of explaining the migration from scratch.
- Entries with a revisit date that has passed are NOT suppressed — emit the finding and note the expired entry.
- Free-form rationale after the dash is for humans; don't parse it, but do read it — it may narrow what "keep" means.

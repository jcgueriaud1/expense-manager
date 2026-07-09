# Findings Log

Living backlog of friction and gaps, per the brief's taxonomy. This is the main
output of the project — every non-trivial work item should leave findings here.

**Areas:** Spec · AI · Vaadin · Tooling/Template · Docs · Verification ·
Deployment/Observability · UX-spec
**Severity:** Low · Medium · High

## Template

```
### F-NNN — <title>
- Date:
- Area:
- Severity:
- Task being attempted:
- Expected vs actual:
- Workaround used:
- Evidence: (prompt / screenshot / code pointer / log / failing test / URL)
- Impact:
- Suggested Vaadin/product improvement:
- Owner / next step:
```

---

### F-001 — Skeleton Java version mismatch (pom 25 vs Dockerfile 21)
- Date: 2026-07-08
- Area: Tooling/Template
- Severity: Medium
- Task being attempted: Reviewing the generated start.vaadin.com skeleton before
  building.
- Expected vs actual: Expected a consistent JDK target. Actual: `pom.xml` sets
  `java.version=25` while `Dockerfile` builds on `eclipse-temurin:21-jdk` and
  runs on `eclipse-temurin:21-jre-alpine`.
- Workaround used: Standardize on Java 25; bump Dockerfile base images (ADR-0014).
- Evidence: `pom.xml:11`, `Dockerfile` (build + runtime stages).
- Impact: "Works locally, breaks in container" class of surprise; silent until
  a Java 25 feature is used.
- Suggested Vaadin/product improvement: start.vaadin.com should keep the
  Dockerfile base image in sync with the pom's `java.version`.
- Owner / next step: Resolved in Phase 0.1 (#4) — Dockerfile build/runtime stages
  bumped to `eclipse-temurin:25-jdk` / `eclipse-temurin:25-jre-alpine` to match the
  pom's Java 25 target.

### F-002 — StreamResource deprecated in Vaadin 25 (streaming API for receipts)
- Date: 2026-07-08
- Area: Docs
- Severity: Low
- Task being attempted: Deciding how to stream receipt bytes to/from the UI.
- Expected vs actual: `StreamResource` is the widely-documented approach but is
  deprecated in Vaadin 25; the current API is `DownloadHandler`/`UploadHandler`
  (`com.vaadin.flow.server.streams`).
- Workaround used: Use the current streaming API; avoid `StreamResource`
  (ADR-0009).
- Evidence: user guidance; Vaadin 25.2 streaming docs.
- Impact: AI agents and older docs/examples will reach for the deprecated class.
- Suggested Vaadin/product improvement: ensure MCP/docs surface the replacement
  prominently on any `StreamResource` reference.
- Owner / next step: confirm exact API usage when implementing receipts.

### F-003 — Finnish VAT rates are statutory and change most years
- Date: 2026-07-09
- Area: Spec
- Severity: Medium
- Task being attempted: Deciding how expense lines capture VAT (Phase 2, ADR-0018).
- Expected vs actual: Assumed a stable reduced rate of 14%; actual 2026 reduced
  rate is **13.5%** (14% → 13.5% on 1 Jan 2026; general 24% → 25.5% on 1 Sept
  2024; several 10% supplies → 14% on 1 Jan 2025). Rates move roughly yearly.
- Workaround used: Model `VatRate`/`ExpenseType` as admin-editable config with an
  `active` flag rather than enums; lines store an FK to the rate they were filed
  under, so past reports never re-compute when the law changes (ADR-0018).
- Evidence: user (authoritative); Verohallinto VAT-rate change history.
- Impact: Hard-coded rates would be a money bug (ADR-0010) and a redeploy-per-law-
  change maintenance burden; per-year versioning would be over-engineering given
  the FK-preserves-history approach.
- Suggested Vaadin/product improvement: n/a (domain/spec finding).
- Owner / next step: verify exact seed values against the Verohallinto decision
  for the target year before the V__ migration ships.

### F-004 — Inline Grid row editor + ComboBox + Binder + Signals (provisional)
- Date: 2026-07-09
- Area: Vaadin
- Severity: Low (provisional — confirm on implementation)
- Task being attempted: Designing the line editor for the report detail view
  (Phase 2.6, ADR-0015/0019).
- Expected vs actual: Chose an inline Grid row editor (edit in place) with
  `ComboBox` columns for expense type / VAT rate, Binder validation, and Signals
  for live net/VAT/gross totals. This is the fiddliest Vaadin 25 combination
  (Grid editor + editor-component binding + per-row validation + reactive totals)
  and is expected to surface friction.
- Expected vs actual: TBD — to be filled from real implementation experience.
- Workaround used: TBD.
- Evidence: design decision; ADR-0019.
- Impact: TBD; may motivate falling back to a dialog/master-detail editor if the
  inline approach proves too costly.
- Suggested Vaadin/product improvement: TBD from findings during build.
- Owner / next step: capture concrete friction (prompts, code, docs gaps) while
  implementing 2.6.

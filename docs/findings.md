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
- Owner / next step: JC — fix in the foundation phase.

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

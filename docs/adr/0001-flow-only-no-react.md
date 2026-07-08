# ADR-0001 — Flow only (server-side Java), no React/Hilla

**Status:** Accepted

## Context
Vaadin 25 offers two UI models: server-side Flow (Java) and opt-in React views
(Hilla endpoints). The project brief prioritizes a small, low-moving-parts
implementation toolset so that "where is Vaadin smooth vs painful" findings are
attributable to one model, not muddied by two. The skeleton was generated for
Flow (no `hilla-spring-boot-starter`).

## Decision
Build the entire UI with **Flow (server-side Java)**. React/Hilla is not added
in V1. It remains available as a later proving-ground extension if a
findings-worthy reason appears (e.g. offline receipt capture).

## Consequences
- Single routing model (`@Route`), single testing stack, no TypeScript build.
- All views are Java classes under each feature's `ui` package.
- If a future need arises, adding React is an explicit, logged decision.

# Empty state

**Category:** shared Java component
**Status:** settled
**Source:** `com.vaadin.expensemanager.base.ui.EmptyState`; `styles.css` — `.no-results`

## Overview

The shared "there is nothing here yet" placeholder, for empty collections, filtered-to-
nothing results, and not-yet-populated views (ADR-0017). Reuse it rather than inventing a
per-view empty layout, so UX states stay consistent.

A thin skeleton on purpose: a centred icon, a heading, an explanatory line. Illustrations
and calls to action land with the first feature that genuinely needs them — not
speculatively.

Two variants, and the difference is worth keeping straight:

- **`EmptyState`** — the collection is empty. Offer the way to create the first item.
- **`.no-results`** — a filter matched nothing. The data exists; the *filter* is the
  problem, so do not offer "create", offer "clear the filter".

## Anatomy

| Part | Source |
|---|---|
| Container | `EmptyState extends VerticalLayout`, centred |
| Icon | constructor arg |
| Heading | constructor arg |
| Description | constructor arg |
| Filter-empty text | `.no-results` |

## Tokens used

| Property | Token |
|---|---|
| `.no-results` colour | `--vaadin-text-color-secondary` |
| `.no-results` padding | `--vaadin-padding-m` (12) |

`EmptyState` itself uses `VerticalLayout`'s own spacing API rather than CSS — structure
through the Java API, per [`../../theming-layouts.md`](../../theming-layouts.md).

## API

```java
new EmptyState(String icon, String heading, String description)
```

## States

| State | Behaviour |
|---|---|
| default | icon, heading, description, centred |
| hover / active / focus | n/a on the container; any action inside is a [button](button.md) |
| disabled | n/a |
| error | n/a — a failure to *load* is not an empty state. That goes to `UiErrorHandler`'s error dialog; rendering it as "nothing here" would tell the user their data is gone |

That last row is the one this component exists to keep honest.

## Code example

```java
if (reports.isEmpty()) {
    add(new EmptyState("vaadin:file-text-o", "No reports yet",
            "Create your first expense report to get started."));
} else if (filtered.isEmpty()) {
    var none = new Span("No reports match these filters.");
    none.addClassName("no-results");
    add(none);
}
```

## Cross-references

[`report-card.md`](report-card.md) — what renders when the list is *not* empty ·
ADR-0017 (shared UX-state primitives)

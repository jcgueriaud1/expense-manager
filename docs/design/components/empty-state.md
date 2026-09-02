# Empty state

**Category:** shared Java component
**Origin:** code
**Implementation:** unaudited
**Code:** `com.vaadin.expensemanager.base.ui.EmptyState`; `styles.css` — `.no-results`
**Design:** — never designed. A shared UX-state primitive (ADR-0017)

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
| Icon | constructor arg — a built [Lucide](../../adr/0026-lucide-icon-set.md) icon, 3em |
| Heading | constructor arg |
| Description | constructor arg |
| Filter-empty text | `.no-results` |

## Tokens used

| Property | Token |
|---|---|
| `.no-results` colour | `--vaadin-text-color-secondary` |
| `.no-results` padding | `--vaadin-padding-m` (12) |
| Icon colour | `--vaadin-text-color-secondary` |
| Icon size | `3em` — off-scale on purpose, see below |

`EmptyState` itself uses `VerticalLayout`'s own spacing API rather than CSS — structure
through the Java API, per [`../../theming-layouts.md`](../../theming-layouts.md).

The icon's `3em` is a raw value rather than a token, and deliberately: it is relative to
the heading beneath it, so the glyph keeps its proportion if the type scale moves. A
`--vaadin-icon-size-*` token would pin it to an absolute instead. This is one of the few
places a caller should *not* size its own icon — the component owns it, because an empty
state's glyph sits in a layout nothing else sizes.

## API

```java
new EmptyState(AbstractIcon<?> icon, String heading, String description)
```

The icon arrives **built**, not named. It was a `"collection:name"` string until #163,
which only the Lumo font-icon sets can be addressed by and so hardcoded the icon set here
for every caller. `AbstractIcon<?>` is the supertype of every Vaadin icon and the one
carrying `setSize`, which this component needs. Pass `null` for no icon.

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
    add(new EmptyState(LucideIcon.FILE_TEXT.create(), "No reports yet",
            "Create your first expense report to get started."));
} else if (filtered.isEmpty()) {
    var none = new Span("No reports match these filters.");
    none.addClassName("no-results");
    add(none);
}
```

## Cross-references

[`report-card.md`](report-card.md) — what renders when the list is *not* empty ·
ADR-0017 (shared UX-state primitives) ·
[ADR-0026](../../adr/0026-lucide-icon-set.md) — the icon set, and why this constructor
takes a component rather than a name

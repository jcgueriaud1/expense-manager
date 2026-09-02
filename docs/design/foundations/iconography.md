# Iconography

## Decisions

| Property | Design | App before | Decided | From | Status |
|---|---|---|---|---|---|
| Icon set | **two, by role** — Lucide for hand-placed glyphs, Lumo inside kit Button icon slots | Lucide only (#163) | **Lucide only** | app | **settled** |
| Delivery | n/a — Figma has no delivery model | one vendored sprite, external `<use>` | keep (ADR-0026) | app | **settled** |
| Button-slot icon size | 20 — bound to `Component sizes/Button/Button icon size` | `1lh`, which computes to ≈20 | `--em-icon-size-m: 20px`, set explicitly | design | **settled** |
| Inline icon size | 16 — trip-row plane (`I106:1205;91:1810`) | `16px` literal at the one call site | `--em-icon-size-s: 16px` | both agree | **settled** |
| Standalone icon size | 24 — section-header chevron (`134:1768`) | Aura's `Details` toggle default — *unmeasured* | `--em-icon-size-l: 24px` | design | **settled** |
| Stroke width | 2 — Lucide's own | 2, but hardcoded per `<symbol>`, which makes the token inert | `--vaadin-icon-stroke-width: 2`, with the attribute **removed** from the symbols | app | **settled** |
| Icon colour | inherits the surrounding text colour | `currentColor`, via each symbol's `stroke` | `currentColor` — never `--vaadin-icon-color` | both agree | **settled** |
| Empty-state glyph size | never designed — the design draws no empty state | `3em` | `3em`, off-scale on purpose | app | **settled** |
| `lucide/ellipsis-vertical` | drawn — grid and card row overflow (`170:7881`, `178:2033`) | absent; the app draws three separate row buttons | add when the `⋮` redesign lands | design | **open** |
| `lucide/copy` | drawn (`341:13938`) but **never placed** | absent | leave out until the design places it | design | **settled** |
| Title-edit glyphs | `lumo:checkmark` (`36:503`) + an edit pencil, both 20 and fill-based | absent — the app has no inline title edit | Lucide `check` / `pencil` **if** that UI is ever built | app | **open** |

Two rows are **open**, and both wait on a view the design has drawn but nobody has
committed to building. Neither is a licence to invent a glyph in the meantime.

## The set

**Lucide, and only Lucide, for every glyph the app draws.** The decision, the sprite, and
the reasoning are [ADR-0026](../../adr/0026-lucide-icon-set.md); this file records what the
*design* says about icons and where the app was told to differ.

### The design uses two sets, and that is the one place this spec overrules it

The design's icon language splits cleanly by role:

| Role | Set | Size | Draws with | Glyphs seen |
|---|---|---|---|---|
| Hand-placed, standalone | **Lucide** | 16 / 24 | `stroke`, 2px | `chevron-up`, `plane`, `ellipsis-vertical`, `copy` |
| Inside a kit Button's icon slot | **Lumo** | 20 | **`fill`** | `lumo:plus` (`36:902`), `lumo:checkmark` (`36:503`), an edit pencil |

[ADR-0025](../../adr/0025-figma-design-source-of-truth.md) makes the design authoritative
on visual decisions, so overruling it needs a reason better than preference. The reason is
that **the split tracks the tooling, not the design.** It correlates exactly with whether a
Vaadin kit component supplied an icon slot: every Lumo glyph sits in a kit Button, whose
Figma component ships with its slot pre-filled from the Lumo library, and every Lucide
glyph sits where no kit component provided one — a section header, a trip row, a grid
overflow. No visual logic separates the two sets; a component boundary does.

Taken literally the split would also be self-defeating. A 20px filled Lumo checkmark
beside a 24px 2px-stroke Lucide chevron in the same view is precisely the two-products
effect ADR-0026 exists to prevent, and it is visible in the frames.

So this is a divergence **resolved in the app's favour and recorded as settled**, which is
the case this spec format exists for: without this row the next survey reports the app's
Lucide plus as a mismatch against `lumo:plus` and proposes reverting #163.

It is still worth **raising with the designer** — if the Lumo glyphs were deliberate, this
decision should be revisited; if they were the kit's defaults, the design file should be
corrected so the next reader is not misled.

## The size scale

Three role sizes, minted as project properties because the design names three and the
framework offers one global:

| Token | Value | Design source | Used for |
|---|---|---|---|
| `--em-icon-size-s` | 16px | `I106:1205;91:1810` (plane, 16×16) | inline beside small text — trip rows |
| `--em-icon-size-m` | 20px | `Component sizes/Button/Button icon size` (a bound variable) | button icon slots, field prefixes |
| `--em-icon-size-l` | 24px | `134:1768` (chevron, 24×24) | a standalone glyph in a layout the app drew |

`3em` stays the empty-state glyph's size and takes no token: it is relative to the heading
beneath it on purpose, so the glyph keeps its proportion if the type scale moves. See
[`../components/empty-state.md`](../components/empty-state.md).

### The 20 was already free, and minting it anyway has a cost

`--vaadin-icon-size` is a **base** style property whose default is `1lh` — one line height
of the icon's own computed font size. At this app's settled inputs a button label is
`--aura-font-size-m` 14px against `--aura-line-height-m` 20px, so an unsized icon in a
button already renders at ≈20px, matching the design's bound variable **by mechanism
rather than by luck**.

Minting `--em-icon-size-m` and setting it explicitly therefore buys consistency and
greppability at a real price: it replaces a *contextual* size with a *fixed* one. An icon
placed in small text would previously have shrunk with it and now will not. That trade was
taken deliberately; it is recorded here so the next reader does not mistake it for an
oversight, and it is the row to revisit first if icons start looking oversized in dense
contexts.

**Do not rebind the global `--vaadin-icon-size` to `--em-icon-size-m`.** Use the properties
at call sites. Rebinding the global would freeze the `1lh` behaviour for every icon in the
app, including in contexts nobody has designed yet, and would do it invisibly.

> Which line height a Vaadin button label actually resolves `1lh` against is **unverified**
> — the ≈20px above is arithmetic on this spec's cached line-height values, and those were
> themselves recorded as measured values whose derivation is unconfirmed
> ([`../tokens/token-reference.md`](../tokens/token-reference.md)). It does not change the
> decision, since the size is now set explicitly either way.

## Colour

An icon takes the **text colour of its parent**, and that is the whole rule. Every symbol
in the sprite strokes in `currentColor`, so tinting an icon means colouring its container,
never the glyph. One sprite serves both colour schemes as a result: `currentColor` resolves
against the `<use>` element's context, so it survives the external reference.

**`--vaadin-icon-color` does nothing to our icons, and will not say so.** The base styles
apply it as `fill: var(--vaadin-icon-color, currentColor)` on the icon host. Lucide glyphs
are `fill="none"` and draw entirely with `stroke`, so a `fill` colour has no visible
effect. Reach for it to tint a Lucide icon and nothing happens, with no error — the same
silent-success shape as [F-075](../../findings.md) and
[F-062](../../findings.md). Colour the parent instead.

## Stroke width

**Set once, globally: `--vaadin-icon-stroke-width: 2`.** The `<symbol>`s must **not** carry
`stroke-width` themselves.

That is a change from how the sprite was first built, and the mechanism is worth stating
because it is invisible from either end. The base styles apply the property to the `<svg>`
`vaadin-icon` renders:

```css
svg {
  @container style(--vaadin-icon-stroke-width) {
    stroke-width: var(--vaadin-icon-stroke-width);
  }
}
```

`stroke-width` is an inherited SVG property, so that value reaches the `<use>`'s referenced
content — **unless the referenced element declares `stroke-width` itself**, because a
presentation attribute on an element beats an inherited value at any specificity. That is
[F-072](../../findings.md)'s mechanism one layer down, and it is what made the token inert
while the symbols hardcoded `stroke-width="2"`.

Routing it through the token makes the knob real: stroke weight becomes tunable in one
place, which is what a design system should offer for a stroke-based icon set.

**The risk it introduces, and why the token is not optional.** In the sprite/`symbol`
branch `vaadin-icon` sets no `stroke-width` on the `<svg>` it renders (it reads nothing off
the sprite — ADR-0026, F-075), and the `@container style()` guard means the rule applies
*only* while the property is set. So if `--vaadin-icon-stroke-width` is ever unset, every
icon in the app falls back to the SVG default of `1` and renders visibly too thin, with
nothing logged. The property is load-bearing, not decorative.

The other five attributes — `fill="none"`, `stroke="currentColor"`, `stroke-linecap`,
`stroke-linejoin`, `viewBox` — **stay on each symbol**. Nothing in the theme supplies them,
and the sprite branch reads none off the file.

## What the design actually draws

Four Lucide glyphs across the whole Visual Design page, all hand-pasted SVG frames with no
design-system annotation:

| Glyph | Node(s) | Placed? |
|---|---|---|
| `chevron-up` | `134:1768`, `134:1773`, +2 | yes — section headers, ×4 |
| `plane` | `I88:12941;91:1810`, `I106:1205;91:1810` | yes — trip rows, ×2 |
| `ellipsis-vertical` | `170:7881` (Type=Grid), `178:2033` (Type=Card) | yes — row overflow |
| `copy` | `341:13938` | **no** — a component with zero instances |

### A methodological trap, for the next survey

`get_metadata` on a page expands **instance** subtrees but stops at component
**definitions**. A `grep lucide` over a page dump therefore under-reports, and it did:
`ellipsis-vertical` lives inside the `Grid Buttons` component set and was invisible to the
page-wide grep that found the other three. ADR-0026 was written on that incomplete count
and says "exactly three".

The page carries **14** component definitions. Each is a blind spot that only
`get_design_context` on the definition itself will open. Resolved so far: `Grid Buttons`
(both variants → `ellipsis-vertical`), `Header` `State=Default` (**no icons** — logo mark,
text links, avatar, confirming #146's iconless shell), `Title Text` (both states → two
20px fill-based Lumo glyphs), `report-item` (→ `plane`, also visible via its instances).
Unresolved: the four other `Header` state variants and the two `Main Link` states — all
expected to be text and illustration, none expected to carry an icon, but **not checked**.

## The invention ledger

Thirteen of the app's fifteen glyphs were chosen without a drawn reference. The ledger
lives in [ADR-0026](../../adr/0026-lucide-icon-set.md) rather than being duplicated here,
because it is a record of a decision taken under time pressure and belongs with that
decision. It shrinks as views are designed.

This survey moves one row: `search` is not merely undrawn but **contradicted** — the
design's own search field hides its `Prefix` slot (`I143:1952;8900:15575`, `hidden="true"`).
The app keeps its search icon for now; dropping it belongs to that view's design pass.

## Unverified

A survey never boots the app, so these are open for visual verification, not conclusions:

- The rendered size of Aura's `Details` summary toggle, against the design's 24px chevron.
- That `--vaadin-icon-stroke-width` does reach the sprite's glyphs once the per-symbol
  attribute is removed. The mechanism above is reasoned from the component's CSS, which is
  source rather than measurement — sound, but not the same as seen.
- The glyph size inside `ellipsis-vertical`'s hit area (`34×36` and `34×21` are the button
  boxes, not the glyph).

## Cross-references

[ADR-0026](../../adr/0026-lucide-icon-set.md) — the set, the sprite, the invention ledger ·
[ADR-0025](../../adr/0025-figma-design-source-of-truth.md) — why overruling the design's
two-set split needed an argument · [`typography.md`](typography.md) — the base font size
and line heights the `1lh` default resolves against ·
[`../tokens/token-reference.md`](../tokens/token-reference.md) — the icon properties in the
master map · [`../components/empty-state.md`](../components/empty-state.md) — the `3em`
exception · [`../../theming-layouts.md`](../../theming-layouts.md) § *Icons* — the
day-to-day rule · `docs/findings.md` F-075 (the sprite/`<use>` behaviour), F-072 (the
inheritance mechanism), F-062 (silent-success styling).

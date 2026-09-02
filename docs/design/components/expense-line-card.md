# Expense line card

**Category:** composite (CSS)
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `styles.css` — `.line-card`, `.line-*`, `.category-dot`; `ReportDetailView`,
`LineEditorDialog`
**Design:** node `116:4444` › `expense-row` — `116:4958` (with attachment),
`116:4968` (multi-unit, two attachments, wrapping subtitle), `116:4978` (plain)

## Overview

**One expense line: a row inside [`expense-item-card.md`](expense-item-card.md).** Its
expense-type glyph, name and subtitle on the left; its amount, optional quantity
breakdown and net/VAT split on the right; its actions behind a `⋮` menu at the far right.

**It is no longer a card, and that is the change this revision carries.** The previous
frame drew each line as its own bordered box, stacked with a gap; the design now draws one
card per *section* with the lines as rows inside it. The name of this file is kept for its
role — one expense line — not for the box it used to be.

The row's radius, padding, surface and shadow are therefore **not its own**: they belong to
the card around it. This spec covers only what a row contributes.

[`travel-card.md`](travel-card.md) is the trip row and the allowance rows nested under it —
same anatomy, two additions. **Not** for a report in a list, which is
[`report-card.md`](report-card.md) and is a link.

## Anatomy

| Part | Class | Notes |
|---|---|---|
| Row | `.expense-row` | `JustifyContentMode.BETWEEN`, `Alignment.CENTER` |
| Left group | `.expense-row-left` | glyph + text column, `--em-card-padding` gap |
| Expense-type glyph | `.expense-row-icon` | 20px Lucide, from the type's own `icon` — see below |
| Text column | `.expense-row-text` | column, `--vaadin-gap-s` |
| Line name | `.expense-row-name` | the expense type's name |
| Subtitle | `.muted-xs` | the line's comment, else `VAT 25.5 %` |
| Attachment list | `.expense-row-attachments` | wraps; `--em-card-padding` between chips |
| One attachment | `.expense-row-attachment` | paperclip + filename |
| Right group | `.expense-row-right` | column, `Alignment.END`, `--vaadin-gap-s` |
| Amount | `.expense-row-amount` | gross |
| Quantity breakdown | `.muted-xs` | only when quantity ≠ 1 (ADR-0023) |
| Net / VAT split | `.muted-xs` | `net €79.68 · VAT €20.32 (25.5 %)` |
| Actions | `RowActionMenu` | see [`row-action-menu.md`](row-action-menu.md) |

### The glyph replaces the colour dot, and comes from the domain

The design gives every row a 20px Lucide glyph for its expense type — `plane`,
`car-taxi-front`, `bed`, `utensils` on this frame. It draws **no colour swatch anywhere**.

`.category-dot` is therefore withdrawn. It was app-invented: `ReportViewSupport`
hashed `String.hashCode()` of the type *name* onto six palette hues, which meant the
colour was stored nowhere and changed silently when an admin renamed a type. It also did
not work — against the nine seeded types, four collide on `--aura-red` (Travel allowance,
Taxi/transport, Accommodation, Other), two on `--aura-green`, and `--aura-purple` is
unreachable. A column of near-identical red dots is the opposite of telling types apart,
and red is the app's rejected/error hue besides.

**The glyph is a persisted attribute of the expense type**, not a mapping in the view: an
`icon` column on `ExpenseType`, chosen by an admin from the Lucide set. That is the
decision taken in this survey and its migration is in the delta. A view-side name→glyph
map was the cheaper option and was rejected for carrying the same rename fragility as the
hash it replaces.

Colour is not restored in any form. The type name always renders as text beside the glyph,
so nothing rests on either (ADR-0020).

## Tokens used

| Property | Token | Value |
|---|---|---|
| Line name | `--aura-font-size-l`, `--aura-font-weight-semibold` | 16, 600 |
| Amount | `--aura-font-size-l`, `--aura-font-weight-semibold` | 16, 600 |
| Subtitle, quantity, net/VAT | `.muted-xs` — `--aura-font-size-xs`, secondary | 12 |
| Attachment filename | `--aura-font-size-xs`, `--vaadin-text-color` | 12 |
| Glyph | `--em-icon-size-m` — `LucideIcon.SIZE_M` | 20 |
| Paperclip | `--em-icon-size-s` — `LucideIcon.SIZE_S` | 16 |
| Glyph → text gap | `--em-card-padding` | 20 |
| Text / amount column gap | `--vaadin-gap-s` | 8 |
| Paperclip → filename gap | `--vaadin-gap-xs` | 4 |
| Between attachment chips | `--em-card-padding` | 20 |
| Row top rule and inset | the card's — see [`expense-item-card.md`](expense-item-card.md) | — |

Three of those are the design's off-scale values taking a nearest token, each settled in
the foundations rather than here: the 15px name and amount take
`--aura-font-size-l` (**+1px**), the 10px column gaps take `--vaadin-gap-s` (**−2px**),
and the 5px paperclip gap takes `--vaadin-gap-xs` (**−1px**). The 20px gaps take
`--em-card-padding`, which is the property the design's 20px already owns — used as a gap
here, which is what [`../foundations/spacing.md`](../foundations/spacing.md) records it
for.

### The filename is content, not metadata

The design puts attachment filenames and trip dates at **`--vaadin-text-color`**, not the
secondary colour every other sub-line uses. That is deliberate and is followed: a receipt's
filename is the thing the user came to check, and the app currently renders it through
`.muted-xs` at secondary. Only the paperclip glyph inherits, so it tracks the same colour.

## API

CSS-only. Composed in `ReportDetailView`; the row's actions come from `RowActionMenu`.

## States

| State | Behaviour |
|---|---|
| default | no fill and no border of its own — the card supplies both. A top rule and 20px inset on every row but the first |
| hover | **none.** The design draws no hover on the row and, per this survey's decision, the row is **not clickable** — the `⋮` menu is the only route to editing. The old whole-row click-to-edit is withdrawn along with `.clickable` |
| active | n/a — the row is not pressable |
| focus | n/a on the row. Focus lives on the `⋮` trigger, and inside the attachment list on each filename, which is activatable |
| disabled | n/a. A read-only report's row renders **no** `⋮` menu at all, rather than a disabled trigger — the rule the whole view follows. Its attachments stay activatable, so a submitted report's receipts remain viewable (ADR-0021) |
| error | n/a — a row has no validation state. Line validation surfaces in [`error-summary.md`](error-summary.md) inside [`editor-dialog.md`](editor-dialog.md) |

**What withdrawing the row click costs.** Editing a line goes from one click on the row to
two — open the menu, choose Edit. That is a real regression and it is the design's call
under ADR-0025, taken deliberately here over the alternative of keeping the click as an
unadvertised shortcut. It is worth watching once it lands: the row is the largest target
on the screen and the menu trigger is 21px wide.

### The attachment chip keeps its preview

The design draws an attachment as a paperclip and a filename, where the app renders
`ReceiptPreview` — an image thumbnail that enlarges in a dialog, or an "open" link for a
PDF (ADR-0021). **Both:** the resting state is the design's chip, and activating it opens
the existing enlarge dialog or streams the PDF. The read affordance ADR-0021 was written
for is intact; only the resting presentation changes.

A buffered (not-yet-saved) attachment shows its chip immediately, from the in-memory bytes
(issue #89) — unchanged by this revision.

## Code example

```java
var row = new HorizontalLayout(left, right, actions);
row.addClassName("expense-row");
row.setWidthFull();
row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
row.setAlignItems(FlexComponent.Alignment.CENTER);

var glyph = expenseType.icon().create();        // ExpenseType.icon, not a view-side map
glyph.addClassName("expense-row-icon");
```

## The editor's line-total row

Retained from this spec's previous revision, because it is still built and still styled —
it is simply not part of an `expense-row`. It is `LineEditorDialog`'s read-only unit
price × quantity summary (ADR-0023): `.line-total-row`, `.line-total-label`,
`.line-total-value`, borrowing the totals card's look rather than a field's because it is a
derived figure and not an input.

| Property | Token |
|---|---|
| Rule and inset | `--vaadin-border-color-secondary` + `--vaadin-padding-xs` |
| Label | `--aura-font-size-s`, `--vaadin-text-color-secondary` |
| Value | `--aura-font-size-m`, semibold |

The design does not draw the dialog, so this block is **code origin** and the code is its
authority. See [`editor-dialog.md`](editor-dialog.md).

## Divergence

The code implements this spec's **previous** revision, which the design has since re-cut.
Every row below was established by reading `ReportDetailView.card()`,
`ReportViewSupport.categoryColor` and `styles.css` against frame `116:4444` — it is a
structural difference, not a token mismatch.

| | Design / decided | Code today |
|---|---|---|
| The box | a row inside one section card | `.line-card` — its own bordered box per line |
| Geometry | the card's; the row has none | `radius-l` 15, `padding-l` 16, own border |
| Separation | 20px, a rule, 20px | a 12px gap between cards |
| Type indicator | 20px Lucide glyph from `ExpenseType.icon` | `.category-dot` — a hashed colour swatch |
| Name / amount size | `--aura-font-size-l` 16 both | `--aura-font-size-s` 13 / `--aura-font-size-m` 14 |
| Subtitle | `.muted-xs` 12 | `.muted` 13 |
| Filenames, trip dates | `--vaadin-text-color` | `.muted-xs`, secondary |
| Actions | `RowActionMenu` | inline tertiary buttons; whole-row click-to-edit |
| Attachment resting state | paperclip + filename chip | `ReceiptPreview` thumbnail |

**Owner:** the report-detail redesign issue this survey files. `ExpenseType.icon` is a
domain change and is the same issue's, not a follow-up.

## Cross-references

[`expense-item-card.md`](expense-item-card.md) — the card this is a row of ·
[`travel-card.md`](travel-card.md) — the trip and allowance rows ·
[`row-action-menu.md`](row-action-menu.md) — the row's actions ·
[`totals-card.md`](totals-card.md) ·
[`report-card.md`](report-card.md) — a report in a list, not a line ·
[`editor-dialog.md`](editor-dialog.md) ·
[`../foundations/iconography.md`](../foundations/iconography.md) — the glyph set and sizes ·
[`../foundations/typography.md`](../foundations/typography.md) — the 15px decision ·
ADR-0023 (quantity), ADR-0021 (receipts), ADR-0020 (never colour or icon alone),
ADR-0025 (the design as contract)

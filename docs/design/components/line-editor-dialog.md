# Line editor dialog

**Category:** composite
**Origin:** design
**Implementation:** drifted — see [Divergence](#divergence)
**Code:** `com.vaadin.expensemanager.report.ui.LineEditorDialog`,
`ExpenseLineFormModel`; `styles.css` — `.line-total-row`, `.line-total-label`,
`.line-total-value`
**Design:** node `358:3267` › `Dialog` — the `Edit Expense` state

## Overview

The focused modal editor for **one manual expense line**: its type, gross unit price,
quantity, VAT rate, comment and receipt, with a live read-only total of unit price ×
quantity (ADR-0023). Opened from the report detail by *Add expense* or by editing a line
card.

**When to reach for a sibling instead.** This dialog never edits a *generated* allowance
line — those figures belong to the `AllowanceCalculator`, not the user:

| Instead of | Use |
|---|---|
| Editing a trip's generated line | re-open the trip editor; a save recomputes its lines |
| Attaching a receipt to a generated line | `TravelLineReceiptDialog` — receipt only, no figures |
| Overriding a generated figure | `GeneratedLineOverrideDialog` |

It is *not* built on the shared [`editor-dialog.md`](editor-dialog.md) scaffold — it needs
a receipt section and a field-derived total, neither of which that API takes — but the
scaffold's **rules still bind it**: an always-enabled Save, a top-of-form error summary,
and the three distinct error paths recorded there.

## Anatomy

| Part | Element | Grid |
|---|---|---|
| Overlay | `Dialog`, `::part(overlay)` | — |
| Header title | `setHeaderTitle(…)` | — |
| Error summary | [`error-summary.md`](error-summary.md) — **undrawn**, code origin (ADR-0020) | above the form |
| Expense type | `ComboBox<ExpenseTypeDto>` | row 1, colspan 2 |
| Unit price | `BigDecimalField` | row 2, col 1 |
| Quantity | `BigDecimalField` | row 2, col 2 |
| Rule | `.line-total-row`'s `border-block-start` | row 3, colspan 2 |
| Total label / value | `.line-total-label` / `.line-total-value` | row 4, colspan 2 |
| VAT rate | `ComboBox<VatRateDto>` | row 5, **col 1 only** |
| Comment | `TextArea` | row 6, colspan 2 |
| Receipt heading + control | `Upload` | row 7, colspan 2 |
| Cancel / Save | [`button.md`](button.md) — secondary, then `PRIMARY` | footer |

The design hides the header's close button (`I358:3267;3701:12281`) and a third footer
button (`I358:3267;3701:11669`). Both stay unbuilt; Escape and Cancel are the exits.

## Layout — a two-column grid, and one deliberate hole

The single most load-bearing thing on this frame, and the thing the code does not do. The
content is a **two-column grid**, not a stack:

```
┌─────────────────────────────────────┐
│ Expense type                        │  colspan 2
├──────────────────┬──────────────────┤
│ Unit price       │ Quantity         │
├──────────────────┴──────────────────┤
│ ───────────────── rule ─────────────│  colspan 2
│ Total                     €450.00   │  colspan 2
├──────────────────┬──────────────────┤
│ VAT rate         │      (empty)     │  ← colspan 1, nothing beside it
├──────────────────┴──────────────────┤
│ Comment                             │  colspan 2, 2 rows
├─────────────────────────────────────┤
│ Receipt (optional) + Upload         │  colspan 2
└─────────────────────────────────────┘
```

**VAT rate is half width with an empty cell to its right.** That is drawn, not an
artefact: `I358:3267;3704:13280;358:3278` is `col-1` at 173px while the row is 386px wide.
Stretching it to full width is the easy mistake, and it breaks the frame's rhythm — the
field pairs visually with the Unit price above it.

Structure goes through the Java API, never `getStyle()`
([`../../theming-layouts.md`](../../theming-layouts.md)):

```java
form.setResponsiveSteps(new ResponsiveStep("0", 1), new ResponsiveStep("24rem", 2));
form.setColumnSpacing("var(--em-section-gap)");   // 40
form.setRowSpacing("var(--em-card-padding)");     // 20
form.setColspan(typeField, 2);
```

The one-column step below 24rem is the app's, not the design's: the design draws two
columns at 418px and no small-screen state, and two 173px fields do not survive a phone.

**Vertical rhythm is uniform 20px**, measured off the frame — every row gap is 20, and the
Total row carries a further 20px of block-end padding, so the clear space between the
total and the VAT rate label is **40**. The rule sits with 20px above and below it.

## Tokens used

| Property | Token | Value |
|---|---|---|
| Dialog width | — | `26rem` (416px; the design's 418 is not expressible in whole rem) |
| Dialog radius, shadow, surface, backdrop | Aura stock — `--vaadin-radius-l`, `--aura-overlay-shadow`, `--aura-overlay-surface-opacity`, `--aura-overlay-backdrop-filter` | 15, `shadow-m`, 0.85 |
| Header / footer padding | `--vaadin-padding-l` | 16 |
| Footer gap | `--vaadin-gap-s` | 8 |
| Header title | `--aura-font-size-l`, `--aura-font-weight-semibold` | 16, 600 |
| Content inline padding | `--vaadin-padding-l` | 16 |
| Content block padding | `--em-card-padding` | 20 |
| Grid column gap | `--em-section-gap` | 40 |
| Grid row gap | `--em-card-padding` | 20 |
| Field label gap, height, radius, border, shadow | Aura stock — `--vaadin-gap-xs`, `--vaadin-radius-m`, `--vaadin-input-field-border-width`, `--aura-shadow-xs` | 4, 34px, 9, 1px |
| Field label / value | `--aura-font-size-m` + medium / regular | 14, 500 / 400 |
| Rule above the total | `1px solid var(--vaadin-border-color-secondary)` | — |
| Rule clearance, above and below | `--em-card-padding` | 20 |
| Total label | `--aura-font-size-m`, `--aura-font-weight-semibold`, `--vaadin-text-color` | 14, 600 |
| Total value | `--aura-font-size-l`, `--aura-font-weight-semibold`, `--vaadin-text-color` | 16, 600 |
| Total row block-end padding | `--em-card-padding` | 20 |
| Receipt heading | `--aura-font-size-m`, `--aura-font-weight-medium` | 14, 500 |
| Receipt heading → control gap | `--vaadin-gap-s` | 8 (design draws 10; settled −2px) |
| Drop-label icon | `LucideIcon.UPLOAD` at `--em-icon-size-m` | 20 |
| Upload drop area | Aura stock — `--vaadin-radius-l`, `--vaadin-border-color-secondary` | 15 |

**The total row's two figures are the design's, and both move.** Label 14/600 primary and
value 16/600 primary, where the code renders 13 secondary and 14/600. This is not a fresh
judgement: it is exactly the pattern [`totals-card.md`](totals-card.md) settled for the
report's grand total — the size is taken and the weight is not. The frame draws the label
at Inter **Bold** (700) and the value at Inter **Extra Bold** (800); Aura's weight scale
stops at semibold 600, and the drawn family contradicts the frame's own
`--lumo-font-family` binding. Both are known, recorded defects (F-069, issue #173), so the
**variable wins over the drawn text** (ADR-0025 decision 4).

**Every `--lumo-*` name in the design's reference code is a translation, not a token.** The
frame emits `--lumo-border-radius-m, 9px`, `--lumo-border-radius-l, 15px`,
`--lumo-font-size-m, 14px` and `--aura-border-color-secondary` — the last of which Aura
does *not* define either (it is `--vaadin-border-color-secondary`). Copy any of them and it
renders at its hardcoded fallback and never tracks the theme again (F-062).

## API

Unchanged by this spec:

```java
LineEditorDialog(List<ExpenseTypeDto> types, List<VatRateDto> rates,
        ExpenseLineDto existing,                       // null to add
        Function<Long, DownloadHandler> savedReceiptSource,
        BiConsumer<ExpenseLineDto, ReceiptUpload> onSave)
```

`onSave` receives the edited line plus the buffered receipt mutation, or `null` where the
receipt was left untouched. Nothing is persisted until the report is saved.

## States

The frame draws **only** the resting, populated, receipt-free state. Everything below that
is not "as drawn" comes from the theme or from an ADR, and is marked as such.

| State | Behaviour |
|---|---|
| **default** | as drawn. `Add expense` opens empty with quantity `1`; the header reads `Add expense` — a state the design does not draw |
| **hover** | Aura stock on fields, buttons and the Upload drop area. On buttons this is a `::before` overlay at `opacity: 0.03` and is **invisible in the host's computed style** — do not "verify" it by reading `background-color` |
| **active** | Aura stock; the button hover overlay is suppressed via `:not([active])` |
| **focus** | Aura's accent focus ring, not overridden. Tab order is header → error summary → fields in grid order → Upload → Cancel → Save |
| **disabled** | **n/a for Save by design** — never a disabled button (ADR-0020); validity is carried by the error summary. Individual fields are never disabled either: a deactivated historical type or rate is *injected* into its ComboBox rather than locking the field (ADR-0018) |
| **error** | Two layers. Per-field: Aura's red border plus the field's own `Error message` slot, which the frame draws and hides. Form-level: the summary above the form, listing every binder failure, dialog stays open. A `DomainRuleException` renders in the same summary; any other exception propagates to `UiErrorHandler` and must **not** be caught into it (issue #86) |

Two more states the design does not draw and the code owns (ADR-0021):

| Receipt state | Behaviour |
|---|---|
| empty | the Upload control alone. **No status line** — the control already says it |
| attached | filename with a paperclip, a tertiary/error `Remove`, and an inline preview |
| rejected | server-side magic-byte rejection surfaces as an error `Notification`, never a disabled control |

**Contrast is unverified here.** A survey does not boot the app, so no ratio in this file
is measured; the primary button's 19.7:1 / 12.6:1 in [`button.md`](button.md) is the only
measured pair that carries over. The total row's new primary-on-surface pairing is
[`visual-verification`](../../../.claude/skills/figma-visual-verification)'s to confirm.

## Code example

```java
new LineEditorDialog(referenceData.activeExpenseTypes(),
        referenceData.activeVatRates(), entry.peek(), service::receiptDownload,
        (dto, receipt) -> {
            if (receipt != null) {
                pendingReceipts.put(entry, receipt);
            }
            entry.set(dto);
        }).open();
```

## Divergence

The code implements a **single-column stack**; the design specifies a two-column grid with
a different total row and two different field types. Structural, not a token mismatch. Owned
by the line-editor redesign issue.

| # | Design | Code today |
|---|---|---|
| 1 | two-column grid, 40/20 gaps, VAT rate half width | `ResponsiveStep("0", 1)` — one column at every width |
| 2 | Comment is a `TextArea`, two rows | `TextField`, single line |
| 3 | Total label 14/600 primary; value 16/600 | label 13 regular secondary; value 14/600 |
| 4 | rule with 20px clearance above and below | `border-top` + `--vaadin-padding-xs` (4) |
| 5 | dialog 418px | `setWidth("28rem")` = 448px |
| 6 | labels `Unit price` (+ helper `Gross, each`) and `Total` | `Unit price (gross, each)`, `Line total` |
| 7 | footer primary reads `Save` | `Save expense` |
| 8 | no status line in the receipt empty state | renders `No receipt attached.` |
| 9 | upload icon in the drop label, plain-text button | icon on the button, stock drop label |
| 10 | Quantity has minus/plus step buttons | none — **accepted**, see below |
| 11 | Title Case labels throughout | sentence case — **open**, see below |

**Two rows are settled against the design, deliberately.**

*10 — the Quantity stepper is not built.* The design annotates
`<vaadin-number-field>` with `lumo:minus` / `lumo:plus` step buttons. `setStepButtonsVisible`
exists only on `NumberField` (Double) and `IntegerField` (int); `BigDecimalField` has no
such API, and ADR-0023 makes quantity `numeric(19,2)` precisely so a line can read
`12.5 km`. Keeping `BigDecimalField` keeps the decimal contract intact, so the stepper is a
recorded, accepted infidelity rather than a gap to close. Should it ever be built, the trap
to know is that `setStep` drives the buttons *and* enables step validation — setting it to
1 for the buttons would reject `12.5`.

*11 — label case is open, not settled.* Every field label on this frame is Title Case
(`Expense Type`, `Unit Price`, `VAT Rate`, `Edit Expense`) where the app is sentence case
throughout. The app keeps sentence case for now and the convention goes to an independent
ticket for a human decision. The frame is not self-consistent evidence either way —
`Receipt (optional)` is sentence case on the same frame, and no other surveyed frame
(`116:2499`, `116:4444`, `156:5396`) establishes Title Case. A later survey should raise
this again rather than treat it as decided.

## Cross-references

[`editor-dialog.md`](editor-dialog.md) — the scaffold whose rules bind this dialog ·
[`error-summary.md`](error-summary.md) ·
[`button.md`](button.md) ·
[`totals-card.md`](totals-card.md) — the precedent for the total row's two figures ·
[`expense-line-card.md`](expense-line-card.md) — the row this dialog edits ·
[`../foundations/typography.md`](../foundations/typography.md) § *Weight 700 has no Aura token* ·
[`../foundations/iconography.md`](../foundations/iconography.md) — Lucide-only, which is why the drawn `lumo:upload` becomes `LucideIcon.UPLOAD` ·
ADR-0015, ADR-0018, ADR-0020, ADR-0021, ADR-0023, ADR-0025, ADR-0026 ·
F-004, F-045, F-062, F-069 · issues #86, #173

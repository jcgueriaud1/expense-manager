# Prototype — Report detail line editor (issue #3, finding F-004)

**Throwaway.** In-memory stub data, no services, no persistence, no tests.
Delete this whole `report/prototype` package once the design question is
answered and the winner is folded into the real `ReportDetailView`.

## The question

The Phase 2 PRD flags the report **line editor** as the friction-prone,
decision-open surface (F-004): the ADR-0015 provisional pick is an inline
editable Grid, but the PRD explicitly says *"fall back to a dialog / master-detail
editor if the inline approach proves too costly."* So: **which line-editing
paradigm should `ReportDetailView` use?**

Three structurally different answers, on one route, switchable via `?variant=`
and the floating bottom bar:

| Variant | Paradigm | Feel |
|---|---|---|
| **A** | Inline editable Grid (row editor) — ComboBox/field cells, Save/Cancel per row, totals in the Grid footer | Dense, spreadsheet-like, edit-in-place. ADR provisional pick. |
| **B** | Master–detail — compact list on the left, one persistent form on the right that binds to the selected line; totals card above the form | Calm, one editor that never moves, good for longer forms (Phase 3 receipts). |
| **C** | Stacked cards + modal dialog — no grid; each line is a receipt-like card with its own net/VAT breakdown, sticky total bar, edit opens a focused `Dialog` | Mobile-friendly, per-line maths on the card, lowest density. |
| **D** | Cards + persistent side panel (C×B hybrid) — C's receipt cards, but selecting a card loads it into B's always-present right-hand form instead of a modal; selected card highlighted | Editor never covers the list, gives Phase 3 receipts a home, keeps the scannable receipt feel. Added at the user's request. |

## How to run

Needs Docker (Postgres via compose) and JDK 25.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
open -a Docker            # if not already running
./mvnw spring-boot:run
```

Then log in (`user@vaadin.com` / `expense`) and open:

- http://localhost:8080/prototype/report-detail?variant=A
- Flip variants with the floating bar (arrows) or **Alt + ← / →**.
- "Reset data" clears the in-memory report. Edits survive variant switches
  (kept in the Vaadin session), but not a server restart.

The route is hidden when `vaadin.productionMode=true`, so a stray merge can't
ship it.

## Verdict — DECIDED (2026-07-10)

> **Winner: variant D — Cards + persistent side panel.**
> Chosen by the user: liked variant C's receipt-style card layout combined with
> variant B's always-present right-hand form panel (not a modal dialog). The
> editor never covers the list, the receipt stays scannable, and the persistent
> panel gives Phase 3 receipt upload an obvious home.
>
> **Not yet built as the real view** — the user asked to stop at the prototype;
> do not create `report/ReportDetailView` yet. When picked up later, rebuild D
> *properly* per the note below.

Once decided: delete the three losing variant classes + `PrototypeSwitcher` +
this package, and rebuild the winner *properly* (Binder validation, domain
guards, optimistic-lock UX, real service) in `report/ReportDetailView`.

> Early steer from the user: liked **C's card layout** + **B's right-hand
> panel** (not a dialog) → that combination is variant **D**.

## F-004 friction log (first-class deliverable — copy into `docs/findings.md`)

Observed while building the three variants against Vaadin 25.2.1 / Aura:

1. **Inline Grid editor — type→VAT default is not declarative.** Pre-filling the
   VAT ComboBox from the chosen expense type can't be expressed through Binder
   alone; it needs a manual `valueChangeListener` on the editor's type field
   that pokes the VAT field (variant A) / form (B, C). The "default but
   overridable" rule is easy to state and fiddly to wire.
2. **Grid footer totals are imperative.** Live net/VAT/gross in the Grid footer
   means holding `Grid.Column` references and re-`setComponent`-ing footer cells
   on every change — no reactive binding. Signals would help but don't reach
   footer cells out of the box.
3. **Grid collapses to zero height in a flex/`HorizontalLayout` split** unless
   you set an explicit height or `setAllRowsVisible(true)` (hit in variant B).
   Master–detail layouts need care here.
4. **Re-parenting gotcha (general, not Vaadin-version-specific):** a shared
   `Grid` field added by a builder method called twice silently moves to the
   last parent and vanishes from the first — a component has one parent. Easy to
   trip when a variant reuses a field-level component.
5. **Dev-toolbar popover console error when a `Dialog` opens** (variant C):
   `NotSupportedError: hidePopover ... at promoteToolbar`. It's the Vaadin
   dev-mode toolbar reacting to the overlay, not app code, and absent in a
   production build — but it is noise during development.
6. **`var(--lumo-*)` inline styles silently no-op under the Aura theme.** This
   app runs Aura (Vaadin 25, `@StyleSheet(Aura.STYLESHEET)`), which defines
   `--vaadin-*` / `--aura-*` design tokens — the classic `--lumo-*` custom
   properties are **undefined**. So any `getStyle().set("border",
   "var(--lumo-contrast-10pct)")`-style code produces *no* border, background,
   padding, or radius, with no error and no warning — it just renders flat.
   Caught here because variant D's selected-card highlight and all card borders
   didn't paint (inline style present in the DOM, but `var()` resolved to
   nothing). Confirmed via `getComputedStyle(:root)`: every `--lumo-*` token is
   an empty string. **Resolved:** all `var(--lumo-*)` tokens across the codebase
   (these variants *and* the base UI — `MainLayout`, `EmptyState`, `LoginView`,
   which had the same latent no-op) were replaced with `--aura-*` / `--vaadin-*`
   equivalents, and the "Aura, never Lumo" rule was added to `CLAUDE.md`. Full
   write-up and mapping in `docs/findings.md` F-013. Correct token names come
   from the Vaadin docs MCP (`get_theme_css_properties theme=aura`).

None of these blocks any variant; all three are fully functional. They are the
cost signal the PRD asked us to capture for the inline approach vs the
alternatives.

# ADR-0010 — EUR-only, BigDecimal scale 2, no Money value object

**Status:** Accepted

## Context
Money bugs are unforgivable in an expense tool, so type discipline is
architectural. Vaadin Oy is Finland-only, and the Verohallinto foreign per-diem
rates are themselves published in EUR — so even foreign trips settle in EUR.
Multi-currency (and the Eurocard import that would introduce it) is explicitly
deferred by the brief.

## Decision
- All monetary amounts are **`BigDecimal`, fixed scale 2**, `RoundingMode.HALF_UP`.
  Never `double`/`float`. Postgres column type `numeric(19,2)`.
- **EUR is an implicit, documented invariant** (EUR everywhere). **No `Money`
  value object in V1.**

## Consequences
- Simple, minimal ceremony; no `@Embeddable` mapping.
- If multi-currency arrives (with Eurocard import), introduce `Money(amount,
  currency)` then — and log how painful that retrofit is as a finding.

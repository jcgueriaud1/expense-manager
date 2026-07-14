package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * The outcome of a foreign per-diem calculation (Phase 4.2/4.3, ADR-0006) — the
 * "do better than ProCountor" payoff: a foreign trip is costed against the
 * destination country's rate, never a silent Finnish default.
 *
 * <p>Unlike the domestic per-diem — full/partial day amounts, free-meal halving —
 * the foreign per-diem is a single flat per-year amount per country (there is one
 * {@link ForeignPerDiemRate#getAmount()} per country), so the result only needs the
 * money, the {@link #dayCount allowance-day count} it was multiplied by, and an
 * explanation. Partial foreign-day fractions, night/return-day rules, and foreign
 * meal-deduction reductions are deliberately out of scope this slice (logged as
 * findings): each qualifying day counts once at the full country rate.
 *
 * <p>The {@link #amount} is EUR at scale 2 (ADR-0010) — {@code 0.00} when the trip
 * is not eligible or too short to earn any allowance day. The {@link #explanation}
 * is written into the generated line's comment so the approval UI can show
 * <em>why</em> the amount is what it is, without recomputing.
 *
 * @param amount      the tax-free foreign per-diem, EUR scale 2 (never negative)
 * @param dayCount    number of allowance days the trip earned at the country rate
 * @param explanation short breakdown for the line comment / approval UI
 */
public record ForeignPerDiemResult(BigDecimal amount, int dayCount, String explanation) {

    /** Whether the trip earned any allowance (drives whether a line is generated). */
    public boolean hasAllowance() {
        return amount.signum() != 0;
    }
}

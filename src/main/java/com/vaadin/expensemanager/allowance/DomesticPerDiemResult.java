package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * The outcome of a domestic per-diem calculation (Phase 4.2, ADR-0006).
 *
 * <p>The {@link #amount} is the tax-free daily allowance the trip earns, EUR at
 * scale 2 (ADR-0010) — {@code 0.00} when the trip is too short or not eligible.
 * The {@link #explanation} is a short human-readable breakdown of how that
 * amount was reached (full/partial days, free-meal halving); it is written into
 * the generated line's comment so the Phase-5 approval UI can show <em>why</em>
 * the allowance is what it is, without recomputing.
 *
 * @param amount      the tax-free per-diem, EUR scale 2 (never negative)
 * @param fullDays    number of whole 24-hour periods that earned a full day
 * @param partialDays {@code 1} if the leftover earned a partial day, else {@code 0}
 * @param explanation short breakdown for the line comment / approval UI
 */
public record DomesticPerDiemResult(BigDecimal amount, int fullDays, int partialDays,
        String explanation) {

    /** Whether the trip earned any allowance (drives whether a line is generated). */
    public boolean hasAllowance() {
        return amount.signum() != 0;
    }
}

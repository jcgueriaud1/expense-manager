package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * The outcome of one of the simpler allowance rules — kilometre, meal, or parking
 * (Phase 4.3, ADR-0006). The richer domestic per-diem keeps its own
 * {@link DomesticPerDiemResult} (it also reports full/partial day counts); these
 * three only ever need the money and a one-line explanation.
 *
 * <p>The {@link #amount} is EUR at scale 2 (ADR-0010) — {@code 0.00} when the rule
 * produced nothing (km = 0, meal not requested, parking fee = 0). The
 * {@link #explanation} is a short human-readable breakdown written into the
 * generated line's comment so the Phase-5 approval UI can show <em>why</em> the
 * amount is what it is, without recomputing.
 *
 * @param amount      the allowance, EUR scale 2 (never negative)
 * @param explanation short breakdown for the line comment / approval UI
 */
public record AllowanceAmount(BigDecimal amount, String explanation) {

    /** Whether the rule produced a non-zero amount (drives whether a line is generated). */
    public boolean hasAmount() {
        return amount != null && amount.signum() != 0;
    }
}

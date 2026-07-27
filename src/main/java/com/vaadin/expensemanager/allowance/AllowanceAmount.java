package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * The outcome of one of the <strong>flat</strong> allowance rules — meal or
 * parking (Phase 4.3, ADR-0006). Neither is a multiple, so both generate a
 * quantity-1 line whose unit price <em>is</em> this amount (ADR-0023); the
 * kilometre rule, which genuinely multiplies, has its own
 * {@link KilometreAllowance}, and the richer domestic per-diem its own
 * {@link DomesticPerDiemResult} (it also reports full/partial day counts).
 *
 * <p>The {@link #amount} is EUR at scale 2 (ADR-0010) — {@code 0.00} when the rule
 * produced nothing (meal not requested, parking fee = 0). The
 * {@link #explanation} is a short human-readable breakdown written into the
 * generated line's comment so the Phase-5 approval UI can show <em>why</em> the
 * amount is what it is, without recomputing.
 *
 * @param amount      the flat allowance, EUR scale 2 (never negative) — also the
 *                    generated line's unit price, at quantity 1
 * @param explanation short breakdown for the line comment / approval UI
 */
public record AllowanceAmount(BigDecimal amount, String explanation) {

    /** Whether the rule produced a non-zero amount (drives whether a line is generated). */
    public boolean hasAmount() {
        return amount != null && amount.signum() != 0;
    }
}

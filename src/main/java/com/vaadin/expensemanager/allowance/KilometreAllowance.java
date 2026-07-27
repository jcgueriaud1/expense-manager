package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The outcome of the kilometre-allowance rule (ADR-0023) — the one trip output
 * that is genuinely a <strong>multiple</strong>: {@code kilometres × €/km rate}.
 * Its two factors are carried separately (rather than a pre-multiplied lump like
 * {@link AllowanceAmount}) so the generated line can persist
 * {@code quantity = kilometres} and {@code unit price = ratePerKm} and read like
 * an invoice line — "12.5 × €0.55 = €6.88".
 *
 * <p>The {@link #amount} is <strong>derived</strong> from the two factors, EUR at
 * scale 2 HALF_UP (ADR-0010), so the money can never disagree with the factors
 * that produced it. It is {@code 0.00} when the rule produced nothing (km = 0),
 * in which case no line is generated.
 *
 * @param kilometres  the distance driven, in km (never negative; 0 → nothing earned)
 * @param ratePerKm   the trip-year €/km rate (0 when nothing was earned)
 * @param explanation short breakdown for the line comment / approval UI, or
 *                    {@code null} when nothing was earned
 */
public record KilometreAllowance(BigDecimal kilometres, BigDecimal ratePerKm,
        String explanation) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /** The rule produced nothing — no distance, no rate consulted, no line. */
    static KilometreAllowance none() {
        return new KilometreAllowance(ZERO, ZERO, null);
    }

    /** This allowance with its explanation filled in (the calculator formats it). */
    KilometreAllowance withExplanation(String explanation) {
        return new KilometreAllowance(kilometres, ratePerKm, explanation);
    }

    /** The allowance in EUR — {@code kilometres × ratePerKm}, scale 2 HALF_UP. */
    public BigDecimal amount() {
        return kilometres.multiply(ratePerKm).setScale(2, RoundingMode.HALF_UP);
    }

    /** Whether the rule earned money (drives whether a line is generated). */
    public boolean hasAmount() {
        return amount().signum() != 0;
    }
}

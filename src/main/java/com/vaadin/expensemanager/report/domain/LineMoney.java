package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure money maths for an expense line (ADR-0010): all figures are
 * {@code BigDecimal} at scale 2, {@code HALF_UP}, EUR-only.
 *
 * <p>The gross amount is what the user paid; net and VAT are always <em>derived</em>
 * from it and the line's VAT rate, never stored (ADR-0010, ADR-0019):
 * {@code net = gross / (1 + rate)}, {@code vat = gross − net}. Deriving VAT as the
 * remainder (rather than {@code gross · rate/(1+rate)}) keeps {@code net + vat}
 * exactly equal to {@code gross} at scale 2, so a report total summed per line
 * never drifts across mixed rates.
 *
 * <p>Deliberately a stateless function object, not a method on {@link ExpenseLine}:
 * the detail view recomputes live net/VAT/gross for an <em>unsaved</em> line
 * (raw amount + chosen rate, before any entity exists), and the entity computes
 * the same way for a persisted line — one formula, one source of truth, no
 * money-bug-prone duplication. Rate is passed as its percent value (e.g.
 * {@code 25.50}), matching {@code VatRate.getValue()}.
 */
public final class LineMoney {

    private static final int SCALE = 2;

    private LineMoney() {
    }

    /** Zero at the money scale — {@code 0.00}. */
    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }

    /** The gross amount at scale 2 (what the user paid). */
    public static BigDecimal gross(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Net = gross / (1 + rate), where {@code ratePercent} is e.g. {@code 25.50}. */
    public static BigDecimal net(BigDecimal amount, BigDecimal ratePercent) {
        var divisor = BigDecimal.ONE.add(ratePercent.movePointLeft(2));
        return gross(amount).divide(divisor, SCALE, RoundingMode.HALF_UP);
    }

    /** VAT = gross − net (the remainder, so {@code net + vat == gross} exactly). */
    public static BigDecimal vat(BigDecimal amount, BigDecimal ratePercent) {
        return gross(amount).subtract(net(amount, ratePercent));
    }
}

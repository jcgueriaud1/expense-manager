package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The derived money figures of a single gross amount at a VAT rate — never
 * stored (ADR-0010, ADR-0019).
 *
 * <p>The amount a user enters on a line is the <strong>gross</strong> (what they
 * paid); net and VAT are worked <em>backward</em> from it:
 * {@code net = gross / (1 + rate/100)} and {@code vat = gross − net}, both at
 * {@link RoundingMode#HALF_UP} scale 2. Deriving per line and summing the
 * results (rather than applying a blended rate to a grand total) is what keeps a
 * report with mixed VAT rates free of rounding drift — hence this small value
 * type is the single unit both the aggregate and the live-totals UI reduce over.
 *
 * <p>Kept as a pure, DB-free helper on purpose: {@link ExpenseLine} delegates to
 * {@link #of} for its persisted figures, and {@code ReportDetailView} calls the
 * exact same method to recompute live totals from unsaved edits, so the two can
 * never disagree.
 *
 * @param net   the net amount (gross excluding VAT), scale 2
 * @param vat   the VAT amount (gross − net), scale 2
 * @param gross the gross amount as entered, scale 2
 */
public record LineAmounts(BigDecimal net, BigDecimal vat, BigDecimal gross) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Derives net/VAT/gross from a gross amount and a percent VAT rate.
     *
     * @param gross       the gross amount as entered (required; may be negative
     *                    for credits/corrections, but derivation of zero is fine)
     * @param ratePercent the VAT rate as a percent, e.g. {@code 25.50} (required)
     * @throws IllegalArgumentException if either argument is {@code null}
     */
    public static LineAmounts of(BigDecimal gross, BigDecimal ratePercent) {
        if (gross == null || ratePercent == null) {
            throw new IllegalArgumentException("Gross amount and VAT rate are required");
        }
        BigDecimal g = gross.setScale(2, RoundingMode.HALF_UP);
        BigDecimal divisor = BigDecimal.ONE.add(ratePercent.divide(HUNDRED));
        BigDecimal net = g.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal vat = g.subtract(net);
        return new LineAmounts(net, vat, g);
    }

    /**
     * The gross of a unit price × quantity line, HALF_UP scale 2 (ADR-0023).
     *
     * <p>The single place that multiplication happens: {@link ExpenseLine#gross()}
     * and the live-totals UI both call it, so a persisted line and an unsaved edit
     * can never round differently.
     *
     * @param unitPrice the gross unit price, each (required; may be negative)
     * @param quantity  the line quantity (required; the domain enforces {@code > 0})
     * @throws IllegalArgumentException if either argument is {@code null}
     */
    public static BigDecimal grossOf(BigDecimal unitPrice, BigDecimal quantity) {
        if (unitPrice == null || quantity == null) {
            throw new IllegalArgumentException("Unit price and quantity are required");
        }
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Derives net/VAT/gross from a unit price × quantity line (ADR-0023) — the
     * gross is {@link #grossOf} and net/VAT come off <em>that</em>, never off the
     * unit price.
     */
    public static LineAmounts ofLine(BigDecimal unitPrice, BigDecimal quantity,
            BigDecimal ratePercent) {
        return of(grossOf(unitPrice, quantity), ratePercent);
    }

    /** The additive identity — a zero total at scale 2, for empty reductions. */
    public static LineAmounts zero() {
        BigDecimal z = BigDecimal.ZERO.setScale(2);
        return new LineAmounts(z, z, z);
    }

    /** Component-wise sum, for accumulating a report total from its lines. */
    public LineAmounts add(LineAmounts other) {
        return new LineAmounts(net.add(other.net), vat.add(other.vat),
                gross.add(other.gross));
    }
}

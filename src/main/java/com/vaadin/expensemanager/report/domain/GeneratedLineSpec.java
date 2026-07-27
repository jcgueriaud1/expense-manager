package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.VatRate;

/**
 * One resolved generated line a {@link Travel} should own, carried on its
 * {@link TravelSpec} (Phase 4.3, ADR-0019). The service builds these by
 * <strong>recomputing each amount server-side</strong> via the
 * {@code AllowanceCalculator} (the client never sends money) and resolving the
 * line's reference data (its {@link ExpenseType} and {@link VatRate}); the
 * aggregate then reconciles a travel's generated lines against this set, matching
 * an existing line by its {@link #kind}.
 *
 * <p>A generated line carries the same <strong>unit price × quantity</strong>
 * shape as a manual one (ADR-0023): the {@linkplain #gross() gross} is
 * {@code unitPrice × quantity}, never stored. The kilometre and per-diem lines are
 * the real multiples — {@code quantity = kilometres}, {@code unitPrice = €/km rate};
 * {@code quantity = days}, {@code unitPrice = per-day rate} — while the meal and
 * parking lines are flat and use {@link #flat} to carry their computed amount at
 * quantity {@code 1}. Both parts are normalized to money scale here, so the
 * {@link #gross()} a preview shows is exactly the gross the persisted line derives.
 *
 * <p>A spec is only present for a kind that produced something — a trip that
 * earned no per-diem (or no partial day), drove no kilometres, paid no meal
 * allowance, or had no parking fee simply omits that kind, so any prior line of it
 * is removed. The
 * {@link #unitPrice} is therefore always non-zero and the {@link #quantity}
 * strictly positive (the aggregate/{@link ExpenseLine} reject anything else).
 *
 * @param kind        which generated line this is (routes it in the totals)
 * @param expenseType resolved expense type the line is filed under
 * @param vatRate     resolved VAT rate (0 % for the allowances, the parking rate for parking)
 * @param unitPrice   server-computed gross unit price, EUR scale 2 (non-zero)
 * @param quantity    how many units (km for the kilometre line, else {@code 1}), scale 2
 * @param comment     the calculator's explanation, written into the line's comment
 */
public record GeneratedLineSpec(GeneratedLineKind kind, ExpenseType expenseType,
        VatRate vatRate, BigDecimal unitPrice, BigDecimal quantity, String comment) {

    public GeneratedLineSpec {
        unitPrice = scaled(unitPrice);
        quantity = scaled(quantity);
    }

    /**
     * A flat generated line — meal or parking (ADR-0023): the calculator's computed
     * {@code amount} is the unit price and the quantity is {@code 1}, so these keep
     * producing exactly the euros they always did.
     */
    public static GeneratedLineSpec flat(GeneratedLineKind kind, ExpenseType expenseType,
            VatRate vatRate, BigDecimal amount, String comment) {
        return new GeneratedLineSpec(kind, expenseType, vatRate, amount, BigDecimal.ONE,
                comment);
    }

    /** The derived gross — unit price × quantity, HALF_UP scale 2 (ADR-0023). */
    public BigDecimal gross() {
        return LineAmounts.grossOf(unitPrice, quantity);
    }

    /**
     * Whether this line is worth generating: a non-zero unit price at money scale
     * and a positive quantity — exactly what {@link ExpenseLine} accepts. A rule
     * that produced nothing fails this, so its kind is omitted and any prior line
     * of it is removed.
     */
    public boolean isEarned() {
        return unitPrice.signum() != 0 && quantity.signum() > 0;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2)
                : value.setScale(2, RoundingMode.HALF_UP);
    }
}

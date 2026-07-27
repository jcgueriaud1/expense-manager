package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One per-diem line's two factors (ADR-0023): a <strong>day count</strong> and the
 * euro rate <strong>per day</strong>. A per-diem is the textbook multiple — so, like
 * {@link KilometreAllowance}, the factors are carried separately rather than as a
 * pre-multiplied lump, and the generated line persists {@code quantity = days},
 * {@code unit price = perDay} and reads like an invoice line ("2 × €54.00 =
 * €108.00").
 *
 * <p>Three per-diem lines are built from this shape: the domestic full-day and
 * partial-day components ({@link DomesticPerDiemResult}) and the single foreign
 * component ({@linkplain AllowanceCalculator#foreignPerDiem every day at the flat
 * country rate}).
 *
 * <p>The {@link #amount()} is <strong>derived</strong> from the two factors, EUR at
 * scale 2 HALF_UP (ADR-0010), so the money can never disagree with the day count
 * that produced it. <strong>Free-meal halving halves {@link #perDay}</strong>
 * ({@link #halved()}), never the day count — the whole point of the split is that
 * quantity stays an honest number of days.
 *
 * @param days        allowance days at this rate (never negative; 0 → nothing earned)
 * @param perDay      the per-day rate, EUR scale 2 (already halved when a free meal
 *                    applied)
 * @param explanation short breakdown for the line comment / approval UI, or
 *                    {@code null} when nothing was earned
 */
public record PerDiemComponent(int days, BigDecimal perDay, String explanation) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal TWO = new BigDecimal("2");

    public PerDiemComponent {
        days = Math.max(days, 0);
        perDay = perDay == null ? ZERO : perDay.setScale(2, RoundingMode.HALF_UP);
    }

    /** Nothing earned at this rate — no days, no line generated. */
    static PerDiemComponent none() {
        return new PerDiemComponent(0, ZERO, null);
    }

    /** The days a trip earned at {@code perDay}, explanation still to be filled in. */
    static PerDiemComponent of(int days, BigDecimal perDay) {
        return new PerDiemComponent(days, perDay, null);
    }

    /**
     * This component with the <strong>unit price halved</strong> for a free meal
     * (glossary: free-meal halving), rounded HALF_UP scale 2 — the day count is
     * untouched.
     */
    PerDiemComponent halved() {
        return new PerDiemComponent(days, perDay.divide(TWO, 2, RoundingMode.HALF_UP),
                explanation);
    }

    /** This component with its explanation filled in (the calculator formats it). */
    PerDiemComponent withExplanation(String explanation) {
        return new PerDiemComponent(days, perDay, explanation);
    }

    /** The allowance in EUR — {@code days × perDay}, scale 2 HALF_UP. */
    public BigDecimal amount() {
        return perDay.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
    }

    /** The day count as a line quantity (ADR-0023), scale 2 like every quantity. */
    public BigDecimal quantity() {
        return BigDecimal.valueOf(days).setScale(2);
    }

    /** Whether this component earned money (drives whether a line is generated). */
    public boolean isEarned() {
        return days > 0 && perDay.signum() != 0;
    }
}

package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Pure, stateless domain service that turns trip inputs + rate config into an
 * allowance (Phase 4.2, ADR-0006). No Spring, no database — a function of its
 * arguments, so the rules most likely to be wrong get fast layer-1 unit tests
 * (ADR-0012).
 *
 * <p><strong>Domestic per-diem (Finnish, Verohallinto model).</strong> The trip
 * duration between departure and return is split into whole 24-hour periods plus
 * a leftover:
 * <ul>
 *   <li>each full 24 h earns one <em>full</em> day;</li>
 *   <li>a leftover longer than the full-day threshold (default 10 h) earns an
 *       extra full day, longer than the partial-day threshold (default 6 h) a
 *       <em>partial</em> day, and anything shorter earns nothing;</li>
 *   <li>a free meal (lunch) <strong>halves</strong> the whole per-diem
 *       (glossary: free-meal halving);</li>
 *   <li>a trip flagged not eligible earns <strong>no</strong> per-diem.</li>
 * </ul>
 * The thresholds and amounts come from the trip-year {@link DomesticPerDiemDto}
 * rate (Slice 1). A return that is not strictly after departure is rejected —
 * the caller surfaces the message (ADR-0020).
 *
 * <p><strong>The other three trip outputs (Phase 4.3).</strong> A trip may also
 * earn a {@linkplain #kilometreAllowance kilometre allowance} (km × the year's
 * €/km rate), a {@linkplain #mealAllowance meal allowance} (a flat per-year
 * amount when the trip is flagged for it) — both tax-free — and carry
 * {@linkplain #parking parking fees} (a VAT-bearing expense passed through at
 * face value). Each is its own rule so it can be unit-tested in isolation and
 * skipped when it produced nothing (km = 0, meal not requested, fee = 0).
 *
 * <p>Amounts are server-authoritative: this runs on inputs the client sent, and
 * the client never sends money. Kept as a plain instance (not a bean) so a unit
 * test can {@code new} it; the report service holds one instance.
 */
public final class AllowanceCalculator {

    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /**
     * Computes the domestic per-diem for a trip.
     *
     * @param departure   trip departure date-and-time (required)
     * @param returnAt    trip return date-and-time (required, strictly after
     *                    {@code departure})
     * @param notEligible whether the trip is flagged not eligible for a daily
     *                    allowance (earns nothing)
     * @param freeLunch   whether a free meal was provided (halves the per-diem)
     * @param rate        the trip-year domestic per-diem rate (required)
     * @throws IllegalArgumentException if an argument is {@code null} or the
     *                                  return is not after the departure
     */
    public DomesticPerDiemResult domesticPerDiem(LocalDateTime departure,
            LocalDateTime returnAt, boolean notEligible, boolean freeLunch,
            DomesticPerDiemDto rate) {
        if (departure == null || returnAt == null) {
            throw new IllegalArgumentException(
                    "Departure and return date & time are required");
        }
        if (rate == null) {
            throw new IllegalArgumentException("No per-diem rate for the trip year");
        }
        if (!returnAt.isAfter(departure)) {
            throw new IllegalArgumentException(
                    "Return must be after the departure");
        }
        if (notEligible) {
            return new DomesticPerDiemResult(zero(), 0, 0,
                    "Trip not eligible for daily allowance — no per-diem.");
        }

        Duration duration = Duration.between(departure, returnAt);
        long fullDays = duration.toDays();
        long leftoverMinutes = duration.toMinutes() - fullDays * 24 * 60;

        int partialDays = 0;
        int extraFullDays = 0;
        if (leftoverMinutes > minutes(rate.fullDayMinHours())) {
            extraFullDays = 1;
        } else if (leftoverMinutes > minutes(rate.partialDayMinHours())) {
            partialDays = 1;
        }
        long fullCount = fullDays + extraFullDays;

        BigDecimal gross = rate.fullDayAmount().multiply(BigDecimal.valueOf(fullCount))
                .add(rate.partialDayAmount().multiply(BigDecimal.valueOf(partialDays)));

        BigDecimal amount = freeLunch
                ? gross.divide(TWO, 2, RoundingMode.HALF_UP)
                : gross.setScale(2, RoundingMode.HALF_UP);

        String explanation = explain(fullCount, partialDays, rate, gross, amount,
                freeLunch);
        return new DomesticPerDiemResult(amount, (int) fullCount, partialDays,
                explanation);
    }

    /**
     * Computes the tax-free kilometre allowance for a trip: {@code kilometres ×}
     * the year's €/km rate, rounded to cents (ADR-0010). A {@code null},
     * zero, or negative distance earns <strong>nothing</strong> (no line is
     * generated) and the rate is not consulted; any positive distance requires a
     * rate — a missing one is rejected so the caller surfaces it (ADR-0020).
     *
     * @param kilometres the distance driven, in km (may be {@code null}/zero → none)
     * @param rate       the trip-year kilometre rate (required when km &gt; 0)
     * @throws IllegalArgumentException if km &gt; 0 and {@code rate} is {@code null}
     */
    public AllowanceAmount kilometreAllowance(BigDecimal kilometres,
            KilometreRateDto rate) {
        if (kilometres == null || kilometres.signum() <= 0) {
            return new AllowanceAmount(ZERO, null);
        }
        if (rate == null) {
            throw new IllegalArgumentException("No kilometre rate for the trip year");
        }
        BigDecimal amount = kilometres.multiply(rate.amountPerKm())
                .setScale(2, RoundingMode.HALF_UP);
        String explanation = "Kilometre allowance: " + plainKm(kilometres) + " km × €"
                + rate.amountPerKm().toPlainString() + "/km = " + eur(amount);
        return new AllowanceAmount(amount, explanation);
    }

    /**
     * Computes the tax-free meal allowance ({@code ateriakorvaus}) for a trip: the
     * flat per-year amount when the trip is flagged to pay it, else
     * <strong>nothing</strong>. When flagged, a rate must exist — a missing one is
     * rejected so the caller surfaces it (ADR-0020).
     *
     * @param pay  whether the trip pays a meal allowance
     * @param rate the trip-year meal allowance (required when {@code pay})
     * @throws IllegalArgumentException if {@code pay} and {@code rate} is {@code null}
     */
    public AllowanceAmount mealAllowance(boolean pay, MealAllowanceDto rate) {
        if (!pay) {
            return new AllowanceAmount(ZERO, null);
        }
        if (rate == null) {
            throw new IllegalArgumentException("No meal allowance for the trip year");
        }
        BigDecimal amount = rate.amount().setScale(2, RoundingMode.HALF_UP);
        return new AllowanceAmount(amount, "Meal allowance: " + eur(amount));
    }

    /**
     * Passes a trip's parking fee through as a VAT-bearing expense (Phase 4.3):
     * the fee at face value, or <strong>nothing</strong> for a {@code null}, zero,
     * or negative fee (no line is generated). Unlike the two allowances above this
     * carries VAT (the parking expense type's rate) — the caller resolves that; the
     * calculator only decides the amount and whether a line is worth generating.
     *
     * @param fee the parking fee paid, EUR (may be {@code null}/zero → none)
     */
    public AllowanceAmount parking(BigDecimal fee) {
        if (fee == null || fee.signum() <= 0) {
            return new AllowanceAmount(ZERO, null);
        }
        BigDecimal amount = fee.setScale(2, RoundingMode.HALF_UP);
        return new AllowanceAmount(amount, "Parking fees: " + eur(amount));
    }

    private static long minutes(int hours) {
        return hours * 60L;
    }

    /** A kilometre distance without trailing zeros ("120", "12.5"). */
    private static String plainKm(BigDecimal kilometres) {
        return kilometres.stripTrailingZeros().toPlainString();
    }

    private static String explain(long fullCount, int partialDays,
            DomesticPerDiemDto rate, BigDecimal gross, BigDecimal amount,
            boolean freeLunch) {
        if (fullCount == 0 && partialDays == 0) {
            return "Trip too short for a per-diem — no allowance.";
        }
        var parts = new StringBuilder("Domestic per-diem: ");
        boolean first = true;
        if (fullCount > 0) {
            parts.append(fullCount).append(" × full day (")
                    .append(eur(rate.fullDayAmount())).append(')');
            first = false;
        }
        if (partialDays > 0) {
            if (!first) {
                parts.append(" + ");
            }
            parts.append(partialDays).append(" × partial day (")
                    .append(eur(rate.partialDayAmount())).append(')');
        }
        if (freeLunch) {
            parts.append(" = ").append(eur(gross))
                    .append(", halved for free meal → ").append(eur(amount));
        } else {
            parts.append(" = ").append(eur(amount));
        }
        return parts.toString();
    }

    private static String eur(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}

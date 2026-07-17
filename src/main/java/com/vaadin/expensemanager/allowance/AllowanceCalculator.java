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

        AllowanceDays days = allowanceDays(departure, returnAt, rate.fullDayMinHours(),
                rate.partialDayMinHours());
        long fullCount = days.fullDays();
        int partialDays = days.partialDays();

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
     * Computes the foreign per-diem for a trip against the destination country's
     * rate (Phase 4.2/4.3) — {@code country rate × allowance-day count}. Unlike the
     * domestic per-diem there is a single flat amount per country (no full/partial
     * split), so every allowance day the trip earns is valued at that one rate.
     *
     * <p>The allowance-day count is the same duration split the domestic per-diem
     * uses (whole 24-hour periods plus a leftover over the partial-day threshold),
     * but since partial foreign-day fractions are out of scope this slice every such
     * day — full or leftover — counts once at the full country rate. A trip flagged
     * not eligible, or too short to earn any day, earns <strong>nothing</strong>.
     * Free-meal halving is <em>not</em> applied (foreign meal-deduction reductions
     * are out of scope, manual-line territory).
     *
     * <p>A {@code null} rate is rejected — the caller looks the country's rate up for
     * the trip year and must surface a missing one as a clear failure (never a
     * silent Finnish default, ADR-0020).
     *
     * @param departure         trip departure date-and-time (required)
     * @param returnAt          trip return date-and-time (required, after departure)
     * @param notEligible       whether the trip earns no daily allowance
     * @param rate              the destination country's trip-year rate (required)
     * @param fullDayMinHours   the year's full-day threshold (hours) for day counting
     * @param partialDayMinHours the year's partial-day threshold (hours) for day counting
     * @throws IllegalArgumentException if an argument is {@code null} or the return
     *                                  is not after the departure
     */
    public ForeignPerDiemResult foreignPerDiem(LocalDateTime departure,
            LocalDateTime returnAt, boolean notEligible, ForeignPerDiemDto rate,
            int fullDayMinHours, int partialDayMinHours) {
        if (departure == null || returnAt == null) {
            throw new IllegalArgumentException(
                    "Departure and return date & time are required");
        }
        if (rate == null) {
            throw new IllegalArgumentException(
                    "No foreign per-diem rate for the destination country");
        }
        if (!returnAt.isAfter(departure)) {
            throw new IllegalArgumentException(
                    "Return must be after the departure");
        }
        if (notEligible) {
            return new ForeignPerDiemResult(zero(), 0,
                    "Trip not eligible for daily allowance — no per-diem.");
        }

        int dayCount = allowanceDays(departure, returnAt, fullDayMinHours,
                partialDayMinHours).total();
        if (dayCount == 0) {
            return new ForeignPerDiemResult(zero(), 0,
                    "Trip too short for a per-diem — no allowance.");
        }
        BigDecimal amount = rate.amount().multiply(BigDecimal.valueOf(dayCount))
                .setScale(2, RoundingMode.HALF_UP);
        String explanation = "Foreign per-diem (" + rate.country() + "): " + dayCount
                + " × " + eur(rate.amount()) + " = " + eur(amount);
        return new ForeignPerDiemResult(amount, dayCount, explanation);
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
     * flat per-year amount, else <strong>nothing</strong>.
     *
     * <p>A meal allowance is paid <em>only when no per-diem applies</em> (the
     * Finnish rule; issue #93) — so it takes both the pay flag and the trip's
     * eligibility, and pays only when the trip is flagged to pay it
     * <strong>and</strong> is not eligible for a daily allowance. A trip that is
     * eligible for a per-diem earns none regardless of the flag: the two are
     * mutually exclusive, and enforcing that here keeps the money right whatever
     * flag combination reaches the server, not only what the dialog allows. When a
     * meal allowance is actually paid a rate must exist — a missing one is rejected
     * so the caller surfaces it (ADR-0020).
     *
     * @param pay         whether the trip is flagged to pay a meal allowance
     * @param notEligible whether the trip is not eligible for a daily allowance
     *                    (i.e. no per-diem applies — the precondition for a meal
     *                    allowance)
     * @param rate        the trip-year meal allowance (required only when one is paid)
     * @throws IllegalArgumentException if a meal allowance is paid and {@code rate}
     *                                  is {@code null}
     */
    public AllowanceAmount mealAllowance(boolean pay, boolean notEligible,
            MealAllowanceDto rate) {
        if (!pay || !notEligible) {
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

    /**
     * The full/partial allowance days a trip's duration earns against the given
     * thresholds — the shared per-diem day split (domestic and foreign both use it).
     * Whole 24-hour periods each earn a full day; a leftover over the full-day
     * threshold earns an extra full day, over the partial-day threshold a partial
     * day, and anything shorter nothing.
     */
    private static AllowanceDays allowanceDays(LocalDateTime departure,
            LocalDateTime returnAt, int fullDayMinHours, int partialDayMinHours) {
        Duration duration = Duration.between(departure, returnAt);
        long fullDays = duration.toDays();
        long leftoverMinutes = duration.toMinutes() - fullDays * 24 * 60;
        int partialDays = 0;
        int extraFullDays = 0;
        if (leftoverMinutes > minutes(fullDayMinHours)) {
            extraFullDays = 1;
        } else if (leftoverMinutes > minutes(partialDayMinHours)) {
            partialDays = 1;
        }
        return new AllowanceDays(fullDays + extraFullDays, partialDays);
    }

    /**
     * The full and partial allowance days a trip earned. The domestic per-diem
     * values them at the full/partial rate; the foreign per-diem counts every day
     * once at the flat country rate ({@link #total()}).
     */
    private record AllowanceDays(long fullDays, int partialDays) {

        /** Total allowance days — full plus partial (foreign counts each once). */
        int total() {
            return (int) fullDays + partialDays;
        }
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

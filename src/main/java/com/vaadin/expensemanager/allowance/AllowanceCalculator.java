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
 * <p>Amounts are server-authoritative: this runs on inputs the client sent, and
 * the client never sends money. Kept as a plain instance (not a bean) so a unit
 * test can {@code new} it; the report service holds one instance.
 */
public final class AllowanceCalculator {

    private static final BigDecimal TWO = new BigDecimal("2");

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

    private static long minutes(int hours) {
        return hours * 60L;
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

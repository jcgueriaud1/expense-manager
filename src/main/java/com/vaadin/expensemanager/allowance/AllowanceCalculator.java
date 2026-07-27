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
 *   <li>a free meal (lunch) <strong>halves</strong> the per-diem — applied to each
 *       component's per-day rate, never to the day count (glossary: free-meal
 *       halving, ADR-0023);</li>
 *   <li>a trip flagged not eligible earns <strong>no</strong> per-diem.</li>
 * </ul>
 * The result is the two {@link PerDiemComponent}s — full days at the full rate,
 * the leftover at the partial rate — which become the {@code PER_DIEM_FULL} and
 * {@code PER_DIEM_PARTIAL} generated lines (issue #124). The thresholds and amounts
 * come from the trip-year {@link DomesticPerDiemDto} rate (Slice 1). A return that
 * is not strictly after departure is rejected — the caller surfaces the message
 * (ADR-0020).
 *
 * <p><strong>The other three trip outputs (Phase 4.3).</strong> A trip may also
 * earn a {@linkplain #kilometreAllowance kilometre allowance} (km × the year's
 * €/km rate), a {@linkplain #mealAllowance meal allowance} (a flat per-year
 * amount when the trip is flagged for it) — both tax-free — and carry
 * {@linkplain #parking parking fees} (a VAT-bearing expense passed through at
 * face value). Each is its own rule so it can be unit-tested in isolation and
 * skipped when it produced nothing (km = 0, meal not requested, fee = 0). The
 * kilometre rule returns its two <em>factors</em> ({@link KilometreAllowance}) so
 * its generated line can carry {@code km × €/km} rather than a lump (ADR-0023) —
 * as do the per-diem rules ({@link PerDiemComponent}); the two flat rules return a
 * plain {@link AllowanceAmount}.
 *
 * <p>Amounts are server-authoritative: this runs on inputs the client sent, and
 * the client never sends money. Kept as a plain instance (not a bean) so a unit
 * test can {@code new} it; the report service holds one instance.
 */
public final class AllowanceCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /**
     * Computes the domestic per-diem for a trip as its two {@code days × per-day
     * rate} components (ADR-0023): the whole 24-hour periods at the full-day rate
     * and the leftover at the partial-day rate. A free meal halves each component's
     * <em>rate</em>, leaving the day counts honest.
     *
     * @param departure   trip departure date-and-time (required)
     * @param returnAt    trip return date-and-time (required, strictly after
     *                    {@code departure})
     * @param notEligible whether the trip is flagged not eligible for a daily
     *                    allowance (earns nothing)
     * @param freeLunch   whether a free meal was provided (halves the per-day rates)
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
            return DomesticPerDiemResult.none();
        }

        AllowanceDays days = allowanceDays(departure, returnAt, rate.fullDayMinHours(),
                rate.partialDayMinHours());
        return new DomesticPerDiemResult(
                component((int) days.fullDays(), rate.fullDayAmount(), "full day",
                        freeLunch),
                component(days.partialDays(), rate.partialDayAmount(), "partial day",
                        freeLunch));
    }

    /**
     * One domestic per-diem component: the days valued at {@code rate}, halved for a
     * free meal on the <em>unit price</em> (ADR-0023), with its own line explanation.
     * Zero days earn nothing, so no line is generated for that component.
     */
    private static PerDiemComponent component(int days, BigDecimal rate,
            String dayLabel, boolean freeLunch) {
        if (days <= 0) {
            return PerDiemComponent.none();
        }
        PerDiemComponent earned = PerDiemComponent.of(days, rate);
        if (freeLunch) {
            earned = earned.halved();
        }
        return earned.withExplanation(
                explainDomestic(earned, dayLabel, rate, freeLunch));
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
     * silent Finnish default, ADR-0020). The result is a single
     * {@link PerDiemComponent} — {@code days × country rate} — generated as a
     * {@code PER_DIEM_FULL} line, since every foreign day counts at the full rate
     * (ADR-0023).
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
    public PerDiemComponent foreignPerDiem(LocalDateTime departure,
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
            return PerDiemComponent.none();
        }

        int dayCount = allowanceDays(departure, returnAt, fullDayMinHours,
                partialDayMinHours).total();
        if (dayCount == 0) {
            return PerDiemComponent.none();
        }
        var earned = PerDiemComponent.of(dayCount, rate.amount());
        return earned.withExplanation("Foreign per-diem (" + rate.country() + "): "
                + dayCount + " × " + eur(earned.perDay()) + " = " + eur(earned.amount()));
    }

    /**
     * Computes the tax-free kilometre allowance for a trip: {@code kilometres ×}
     * the year's €/km rate, rounded to cents (ADR-0010). Returns the two factors
     * rather than the product (ADR-0023) — the generated line persists the distance
     * as its quantity and the €/km rate as its unit price — with the euros
     * {@linkplain KilometreAllowance#amount() derived} from them.
     *
     * <p>A {@code null}, zero, or negative distance earns <strong>nothing</strong>
     * (no line is generated) and the rate is not consulted; any positive distance
     * requires a rate — a missing one is rejected so the caller surfaces it
     * (ADR-0020).
     *
     * @param kilometres the distance driven, in km (may be {@code null}/zero → none)
     * @param rate       the trip-year kilometre rate (required when km &gt; 0)
     * @throws IllegalArgumentException if km &gt; 0 and {@code rate} is {@code null}
     */
    public KilometreAllowance kilometreAllowance(BigDecimal kilometres,
            KilometreRateDto rate) {
        if (kilometres == null || kilometres.signum() <= 0) {
            return KilometreAllowance.none();
        }
        if (rate == null) {
            throw new IllegalArgumentException("No kilometre rate for the trip year");
        }
        var earned = new KilometreAllowance(kilometres, rate.amountPerKm(), null);
        return earned.withExplanation("Kilometre allowance: " + plainKm(kilometres)
                + " km × €" + rate.amountPerKm().toPlainString() + "/km = "
                + eur(earned.amount()));
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

    /**
     * The line comment for one domestic per-diem component: "Domestic per-diem: 2 ×
     * full day (€54.00) = €108.00", naming the free-meal halving on the per-day rate
     * when it applied — so the approval UI can see why the unit price is half the
     * statutory rate without recomputing.
     */
    private static String explainDomestic(PerDiemComponent earned, String dayLabel,
            BigDecimal rate, boolean freeLunch) {
        var parts = new StringBuilder("Domestic per-diem: ").append(earned.days())
                .append(" × ").append(dayLabel).append(" (");
        if (freeLunch) {
            parts.append(eur(rate)).append(" halved for free meal → ");
        }
        return parts.append(eur(earned.perDay())).append(") = ")
                .append(eur(earned.amount())).toString();
    }

    private static String eur(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

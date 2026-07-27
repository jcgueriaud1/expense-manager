package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit test (pyramid layer 1, ADR-0012): pure JUnit, no Spring/DB, for
 * {@link AllowanceCalculator} — the domestic per-diem rules (Phase 4.2).
 *
 * <p>Uses the seeded 2026 figures (full €54 / partial €25, thresholds 10 h / 6 h)
 * so the numbers line up with the calculator slice the UI drives.
 */
class AllowanceCalculatorTest {

    private final AllowanceCalculator calculator = new AllowanceCalculator();

    // Full €54, partial €25, full over 10 h, partial over 6 h (V7 seed).
    private static final DomesticPerDiemDto RATE = new DomesticPerDiemDto(1L, 2026,
            new BigDecimal("54.00"), new BigDecimal("25.00"), 10, 6);

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 8, 0);

    private DomesticPerDiemResult perDiem(LocalDateTime returnAt, boolean freeLunch) {
        return calculator.domesticPerDiem(START, returnAt, false, freeLunch, RATE);
    }

    @Test
    void subSixHourTripEarnsNothing() {
        // 5 h → no allowance, no line generated.
        var result = perDiem(START.plusHours(5), false);
        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.hasAllowance()).isFalse();
    }

    @Test
    void betweenSixAndTenHoursEarnsThePartialDay() {
        var result = perDiem(START.plusHours(8), false);
        assertThat(result.amount()).isEqualByComparingTo("25.00");
        assertThat(result.fullDays()).isZero();
        assertThat(result.partialDays()).isEqualTo(1);
    }

    @Test
    void justOverSixHoursEarnsPartialButExactlySixDoesNot() {
        assertThat(perDiem(START.plusHours(6), false).amount())
                .isEqualByComparingTo("0.00");
        assertThat(perDiem(START.plusHours(6).plusMinutes(1), false).amount())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void overTenHoursEarnsAFullDay() {
        var result = perDiem(START.plusHours(11), false);
        assertThat(result.amount()).isEqualByComparingTo("54.00");
        assertThat(result.fullDays()).isEqualTo(1);
        assertThat(result.partialDays()).isZero();
    }

    @Test
    void exactlyTenHoursIsStillOnlyPartial() {
        // Strict thresholds: 10 h is not over 10 h, so it stays partial.
        assertThat(perDiem(START.plusHours(10), false).amount())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void exactly24HoursIsOneFullDayWithNoLeftover() {
        var result = perDiem(START.plusHours(24), false);
        assertThat(result.amount()).isEqualByComparingTo("54.00");
        assertThat(result.fullDays()).isEqualTo(1);
        assertThat(result.partialDays()).isZero();
    }

    @Test
    void multiDayFullPlusPartialLeftover() {
        // 24 h + 7 h leftover (> 6 h, ≤ 10 h) → one full + one partial.
        var result = perDiem(START.plusHours(31), false);
        assertThat(result.fullDays()).isEqualTo(1);
        assertThat(result.partialDays()).isEqualTo(1);
        assertThat(result.amount()).isEqualByComparingTo("79.00");
    }

    @Test
    void multiDayLeftoverOverTenHoursEarnsAnotherFullDay() {
        // 24 h + 11 h leftover (> 10 h) → two full days.
        var result = perDiem(START.plusHours(35), false);
        assertThat(result.fullDays()).isEqualTo(2);
        assertThat(result.partialDays()).isZero();
        assertThat(result.amount()).isEqualByComparingTo("108.00");
    }

    @Test
    void freeLunchHalvesThePerDiem() {
        // One full + one partial = €79.00, halved to €39.50.
        var result = perDiem(START.plusHours(31), true);
        assertThat(result.amount()).isEqualByComparingTo("39.50");
        assertThat(result.full().explanation()).contains("halved for free meal");
        assertThat(result.partial().explanation()).contains("halved for free meal");
    }

    @Test
    void notEligibleEarnsNothingRegardlessOfDuration() {
        var result = calculator.domesticPerDiem(START, START.plusHours(48), true,
                false, RATE);
        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.hasAllowance()).isFalse();
        // Neither component is earned, so neither per-diem line is generated.
        assertThat(result.full().isEarned()).isFalse();
        assertThat(result.partial().isEarned()).isFalse();
    }

    @Test
    void returnBeforeDepartureIsRejected() {
        assertThatThrownBy(() -> calculator.domesticPerDiem(START,
                START.minusHours(1), false, false, RATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Return must be after");
    }

    @Test
    void returnEqualToDepartureIsRejected() {
        assertThatThrownBy(() -> calculator.domesticPerDiem(START, START, false,
                false, RATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullInputsAreRejected() {
        assertThatThrownBy(() -> calculator.domesticPerDiem(null, START, false, false,
                RATE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.domesticPerDiem(START, START.plusHours(8),
                false, false, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachComponentExplainsItsOwnDaysTimesRate() {
        // Issue #124: each per-diem line carries its own comment, so the approval UI
        // reads "1 × full day (€54.00) = €54.00" per line rather than one lump.
        var result = perDiem(START.plusHours(31), false);
        assertThat(result.full().explanation())
                .contains("1 × full day", "€54.00").doesNotContain("partial");
        assertThat(result.partial().explanation())
                .contains("1 × partial day", "€25.00").doesNotContain("full");
    }

    // --- Per-diem split into full/partial components (issue #124, ADR-0023) ---

    @Test
    void thePerDiemCarriesFullAndPartialDaysAsSeparateDaysTimesRateComponents() {
        // 2 × 24 h + 7 h leftover → 2 full days + 1 partial day. Each component is a
        // real quantity × unit price, and the euros are their sum: 2×54 + 1×25 = €133.
        var result = calculator.domesticPerDiem(START, START.plusHours(55), false, false,
                RATE);

        assertThat(result.full().days()).isEqualTo(2);
        assertThat(result.full().perDay()).isEqualByComparingTo("54.00");
        assertThat(result.full().quantity()).isEqualByComparingTo("2");
        assertThat(result.full().amount()).isEqualByComparingTo("108.00");
        assertThat(result.partial().days()).isEqualTo(1);
        assertThat(result.partial().perDay()).isEqualByComparingTo("25.00");
        assertThat(result.partial().amount()).isEqualByComparingTo("25.00");
        assertThat(result.amount()).isEqualByComparingTo("133.00");
    }

    @Test
    void aFullDayOnlyTripEarnsNoPartialComponent() {
        // 24 h → one full day, no leftover: the partial component earns nothing, so no
        // partial-day line is generated.
        var result = perDiem(START.plusHours(24), false);

        assertThat(result.full().isEarned()).isTrue();
        assertThat(result.full().days()).isEqualTo(1);
        assertThat(result.partial().isEarned()).isFalse();
        assertThat(result.partial().days()).isZero();
        assertThat(result.partial().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void aPartialOnlyTripEarnsNoFullComponent() {
        // 8 h → partial only; the full component earns nothing.
        var result = perDiem(START.plusHours(8), false);

        assertThat(result.full().isEarned()).isFalse();
        assertThat(result.partial().isEarned()).isTrue();
        assertThat(result.partial().perDay()).isEqualByComparingTo("25.00");
    }

    @Test
    void freeMealHalvesTheUnitPriceAndLeavesTheDayCountsHonest() {
        // ADR-0023: halving belongs on the unit price, never on the quantity — the day
        // counts are the statutory record. 2 full + 1 partial, halved rates.
        var result = calculator.domesticPerDiem(START, START.plusHours(55), false, true,
                RATE);

        assertThat(result.full().days()).isEqualTo(2);
        assertThat(result.full().perDay()).isEqualByComparingTo("27.00");
        assertThat(result.full().amount()).isEqualByComparingTo("54.00");
        assertThat(result.partial().days()).isEqualTo(1);
        assertThat(result.partial().perDay()).isEqualByComparingTo("12.50");
        assertThat(result.amount()).isEqualByComparingTo("66.50");
    }

    @Test
    void aHalvedOddCentRateRoundsHalfUpPerLine() {
        // Each line rounds independently, HALF_UP at scale 2 (ADR-0023's accepted
        // per-line rounding): €53.01 / 2 = €26.505 → €26.51 as the unit price, so two
        // full days come to €53.02 rather than the pre-split €53.01.
        var oddRate = new DomesticPerDiemDto(1L, 2026, new BigDecimal("53.01"),
                new BigDecimal("25.01"), 10, 6);
        var result = calculator.domesticPerDiem(START, START.plusHours(48), false, true,
                oddRate);

        assertThat(result.full().perDay()).isEqualByComparingTo("26.51");
        assertThat(result.full().amount()).isEqualByComparingTo("53.02");
        assertThat(result.amount()).isEqualByComparingTo("53.02");
    }

    @Test
    void aTooShortTripEarnsNeitherComponent() {
        var result = perDiem(START.plusHours(5), false);

        assertThat(result.full().isEarned()).isFalse();
        assertThat(result.partial().isEarned()).isFalse();
        assertThat(result.full().explanation()).isNull();
        assertThat(result.partial().explanation()).isNull();
    }

    // --- Kilometre allowance (Phase 4.3) ---

    // Seeded 2026 rate: €0.550 / km (V7 seed, corrected by V10 — issue #90).
    private static final KilometreRateDto KM_RATE =
            new KilometreRateDto(1L, 2026, new BigDecimal("0.550"));

    @Test
    void kilometreAllowanceIsDistanceTimesRateRoundedToCents() {
        // 120 km × €0.55 = €66.00.
        var result = calculator.kilometreAllowance(new BigDecimal("120"), KM_RATE);
        assertThat(result.amount()).isEqualByComparingTo("66.00");
        assertThat(result.hasAmount()).isTrue();
        assertThat(result.explanation()).contains("120 km", "€0.550/km", "€66.00");
    }

    @Test
    void kilometreAllowanceCarriesTheDistanceAndRateAsSeparateFactors() {
        // ADR-0023: the generated line persists quantity = km, unit price = €/km, so
        // the calculator hands back both factors rather than a pre-multiplied lump.
        var result = calculator.kilometreAllowance(new BigDecimal("120"), KM_RATE);

        assertThat(result.kilometres()).isEqualByComparingTo("120");
        assertThat(result.ratePerKm()).isEqualByComparingTo("0.550");
        // The euros stay the product of exactly those two.
        assertThat(result.amount()).isEqualByComparingTo("66.00");
    }

    @Test
    void fractionalKilometresRoundToTheNearestCent() {
        // 12.5 km × €0.55 = €6.875 → €6.88 (HALF_UP) — the fractional-distance case
        // the km-as-quantity shape exists for (ADR-0023).
        var result = calculator.kilometreAllowance(new BigDecimal("12.5"), KM_RATE);
        assertThat(result.amount()).isEqualByComparingTo("6.88");
        assertThat(result.kilometres()).isEqualByComparingTo("12.5");
        assertThat(result.ratePerKm()).isEqualByComparingTo("0.550");
        assertThat(result.explanation()).contains("12.5 km", "€6.88");
    }

    @Test
    void zeroOrNullKilometresEarnNothingAndNeedNoRate() {
        var none = calculator.kilometreAllowance(BigDecimal.ZERO, null);
        assertThat(none.hasAmount()).isFalse();
        // No distance, no rate consulted — so nothing for the caller to generate.
        assertThat(none.kilometres()).isEqualByComparingTo("0.00");
        assertThat(none.ratePerKm()).isEqualByComparingTo("0.00");
        assertThat(calculator.kilometreAllowance(null, null).amount())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void positiveKilometresWithoutARateAreRejected() {
        assertThatThrownBy(() -> calculator.kilometreAllowance(new BigDecimal("10"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kilometre rate");
    }

    // --- Meal allowance (Phase 4.3) ---

    private static final MealAllowanceDto MEAL_RATE =
            new MealAllowanceDto(1L, 2026, new BigDecimal("13.50"));

    @Test
    void mealAllowanceIsTheFlatRateWhenFlaggedAndNotEligible() {
        var result = calculator.mealAllowance(true, true, MEAL_RATE);
        assertThat(result.amount()).isEqualByComparingTo("13.50");
        assertThat(result.hasAmount()).isTrue();
        assertThat(result.explanation()).contains("€13.50");
    }

    @Test
    void mealAllowanceIsNothingWhenNotFlaggedAndNeedsNoRate() {
        assertThat(calculator.mealAllowance(false, true, null).hasAmount()).isFalse();
    }

    @Test
    void mealAllowanceIsNothingWhenEligibleForAPerDiemAndNeedsNoRate() {
        // Issue #93: a meal allowance and a per-diem are mutually exclusive, so a
        // trip still eligible for a per-diem earns no meal allowance even if flagged
        // to pay one — and the rate is not consulted (none is needed).
        assertThat(calculator.mealAllowance(true, false, null).hasAmount()).isFalse();
    }

    @Test
    void mealAllowanceFlaggedAndNotEligibleWithoutARateIsRejected() {
        assertThatThrownBy(() -> calculator.mealAllowance(true, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meal allowance");
    }

    // --- Foreign per-diem (Phase 4.2/4.3) ---

    // Germany 2026: flat €71.00 per allowance day (V7 seed). Day counting uses the
    // domestic 10 h / 6 h thresholds.
    private static final ForeignPerDiemDto GERMANY =
            new ForeignPerDiemDto(1L, 2026, "Germany", new BigDecimal("71.00"));

    private PerDiemComponent foreign(LocalDateTime returnAt, boolean notEligible) {
        return calculator.foreignPerDiem(START, returnAt, notEligible, GERMANY, 10, 6);
    }

    @Test
    void foreignPerDiemIsCountryRateTimesAllowanceDayCount() {
        // 24 h + 7 h leftover (> 6 h) → 2 allowance days × €71.00 = €142.00.
        var result = foreign(START.plusHours(31), false);
        assertThat(result.amount()).isEqualByComparingTo("142.00");
        assertThat(result.days()).isEqualTo(2);
        assertThat(result.isEarned()).isTrue();
        assertThat(result.explanation()).contains("Germany", "2 ×", "€71.00", "€142.00");
    }

    @Test
    void foreignPerDiemCarriesTheDaysAndCountryRateAsSeparateFactors() {
        // ADR-0023: the foreign per-diem line persists quantity = days, unit price =
        // the country rate — a single full-day-kind line, since every foreign day
        // counts at the full rate.
        var result = foreign(START.plusHours(31), false);

        assertThat(result.days()).isEqualTo(2);
        assertThat(result.quantity()).isEqualByComparingTo("2");
        assertThat(result.perDay()).isEqualByComparingTo("71.00");
        assertThat(result.amount()).isEqualByComparingTo("142.00");
    }

    @Test
    void foreignPerDiemLeftoverOverTenHoursCountsAsAnotherFullDay() {
        // 24 h + 11 h leftover (> 10 h) → 2 allowance days × €71.00 = €142.00.
        var result = foreign(START.plusHours(35), false);
        assertThat(result.days()).isEqualTo(2);
        assertThat(result.amount()).isEqualByComparingTo("142.00");
    }

    @Test
    void foreignPerDiemPartialLeftoverCountsAtTheFullCountryRate() {
        // 8 h (> 6 h, ≤ 10 h) → 1 day at the full country rate (no partial fraction).
        var result = foreign(START.plusHours(8), false);
        assertThat(result.days()).isEqualTo(1);
        assertThat(result.amount()).isEqualByComparingTo("71.00");
    }

    @Test
    void foreignSubSixHourTripEarnsNothing() {
        var result = foreign(START.plusHours(5), false);
        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.isEarned()).isFalse();
        assertThat(result.days()).isZero();
    }

    @Test
    void foreignNotEligibleEarnsNothing() {
        var result = foreign(START.plusHours(48), true);
        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.isEarned()).isFalse();
    }

    @Test
    void foreignFreeMealIsNotDeducted() {
        // Foreign meal-deduction reductions are out of scope: freeLunch has no field
        // here, so a foreign per-diem is never halved (contrast the domestic rule).
        var result = calculator.foreignPerDiem(START, START.plusHours(11), false,
                GERMANY, 10, 6);
        assertThat(result.amount()).isEqualByComparingTo("71.00");
    }

    @Test
    void foreignPerDiemRejectsAMissingRate() {
        assertThatThrownBy(() -> calculator.foreignPerDiem(START, START.plusHours(11),
                false, null, 10, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("foreign per-diem rate");
    }

    @Test
    void foreignPerDiemRejectsReturnBeforeDepartureAndNullDates() {
        assertThatThrownBy(() -> calculator.foreignPerDiem(START, START.minusHours(1),
                false, GERMANY, 10, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Return must be after");
        assertThatThrownBy(() -> calculator.foreignPerDiem(null, START, false, GERMANY,
                10, 6)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Parking (Phase 4.3) ---

    @Test
    void parkingPassesTheFeeThroughAtFaceValue() {
        var result = calculator.parking(new BigDecimal("12.00"));
        assertThat(result.amount()).isEqualByComparingTo("12.00");
        assertThat(result.hasAmount()).isTrue();
        assertThat(result.explanation()).contains("€12.00");
    }

    @Test
    void zeroOrNullParkingFeeEarnsNothing() {
        assertThat(calculator.parking(BigDecimal.ZERO).hasAmount()).isFalse();
        assertThat(calculator.parking(null).amount()).isEqualByComparingTo("0.00");
    }
}

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
        assertThat(result.explanation()).contains("halved for free meal");
    }

    @Test
    void notEligibleEarnsNothingRegardlessOfDuration() {
        var result = calculator.domesticPerDiem(START, START.plusHours(48), true,
                false, RATE);
        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.hasAllowance()).isFalse();
        assertThat(result.explanation()).contains("not eligible");
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
    void explanationDescribesTheBreakdown() {
        var result = perDiem(START.plusHours(31), false);
        assertThat(result.explanation())
                .contains("1 × full day", "1 × partial day", "€79.00");
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
    void fractionalKilometresRoundToTheNearestCent() {
        // 12.5 km × €0.55 = €6.875 → €6.88 (HALF_UP).
        var result = calculator.kilometreAllowance(new BigDecimal("12.5"), KM_RATE);
        assertThat(result.amount()).isEqualByComparingTo("6.88");
        assertThat(result.explanation()).contains("12.5 km");
    }

    @Test
    void zeroOrNullKilometresEarnNothingAndNeedNoRate() {
        assertThat(calculator.kilometreAllowance(BigDecimal.ZERO, null).hasAmount())
                .isFalse();
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

    private ForeignPerDiemResult foreign(LocalDateTime returnAt, boolean notEligible) {
        return calculator.foreignPerDiem(START, returnAt, notEligible, GERMANY, 10, 6);
    }

    @Test
    void foreignPerDiemIsCountryRateTimesAllowanceDayCount() {
        // 24 h + 7 h leftover (> 6 h) → 2 allowance days × €71.00 = €142.00.
        var result = foreign(START.plusHours(31), false);
        assertThat(result.amount()).isEqualByComparingTo("142.00");
        assertThat(result.dayCount()).isEqualTo(2);
        assertThat(result.hasAllowance()).isTrue();
        assertThat(result.explanation()).contains("Germany", "2 ×", "€71.00", "€142.00");
    }

    @Test
    void foreignPerDiemLeftoverOverTenHoursCountsAsAnotherFullDay() {
        // 24 h + 11 h leftover (> 10 h) → 2 allowance days × €71.00 = €142.00.
        var result = foreign(START.plusHours(35), false);
        assertThat(result.dayCount()).isEqualTo(2);
        assertThat(result.amount()).isEqualByComparingTo("142.00");
    }

    @Test
    void foreignPerDiemPartialLeftoverCountsAtTheFullCountryRate() {
        // 8 h (> 6 h, ≤ 10 h) → 1 day at the full country rate (no partial fraction).
        var result = foreign(START.plusHours(8), false);
        assertThat(result.dayCount()).isEqualTo(1);
        assertThat(result.amount()).isEqualByComparingTo("71.00");
    }

    @Test
    void foreignSubSixHourTripEarnsNothing() {
        var result = foreign(START.plusHours(5), false);
        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.hasAllowance()).isFalse();
        assertThat(result.explanation()).contains("too short");
    }

    @Test
    void foreignNotEligibleEarnsNothing() {
        var result = foreign(START.plusHours(48), true);
        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.hasAllowance()).isFalse();
        assertThat(result.explanation()).contains("not eligible");
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

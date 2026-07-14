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
}

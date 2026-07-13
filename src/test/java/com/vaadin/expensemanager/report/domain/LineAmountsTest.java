package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit test (pyramid layer 1, ADR-0012): pure JUnit, no Spring/DB, for the
 * money derivation in {@link LineAmounts} (ADR-0010).
 *
 * <p>The entered amount is the gross; net/VAT are worked backward at HALF_UP
 * scale 2. Negatives (credits/corrections) flow through unchanged.
 */
class LineAmountsTest {

    @Test
    void derivesNetAndVatBackwardFromGross() {
        var amounts = LineAmounts.of(new BigDecimal("100.00"), new BigDecimal("25.50"));

        // 100 / 1.255 = 79.68 (HALF_UP); VAT = 100 - 79.68.
        assertThat(amounts.gross()).isEqualByComparingTo("100.00");
        assertThat(amounts.net()).isEqualByComparingTo("79.68");
        assertThat(amounts.vat()).isEqualByComparingTo("20.32");
        // net + VAT reconstructs the gross exactly.
        assertThat(amounts.net().add(amounts.vat())).isEqualByComparingTo("100.00");
    }

    @Test
    void zeroRateGivesZeroVat() {
        var amounts = LineAmounts.of(new BigDecimal("100.00"), new BigDecimal("0.00"));

        assertThat(amounts.net()).isEqualByComparingTo("100.00");
        assertThat(amounts.vat()).isEqualByComparingTo("0.00");
    }

    @Test
    void negativeGrossProducesNegativeNetAndVat() {
        var amounts = LineAmounts.of(new BigDecimal("-100.00"), new BigDecimal("25.50"));

        assertThat(amounts.gross()).isEqualByComparingTo("-100.00");
        assertThat(amounts.net()).isEqualByComparingTo("-79.68");
        assertThat(amounts.vat()).isEqualByComparingTo("-20.32");
    }

    @Test
    void addSumsPerLineToAvoidDriftAcrossMixedRates() {
        var line255 = LineAmounts.of(new BigDecimal("100.00"), new BigDecimal("25.50"));
        var line135 = LineAmounts.of(new BigDecimal("50.00"), new BigDecimal("13.50"));

        var total = LineAmounts.zero().add(line255).add(line135);

        assertThat(total.gross()).isEqualByComparingTo("150.00");
        assertThat(total.net()).isEqualByComparingTo("123.73");   // 79.68 + 44.05
        assertThat(total.vat()).isEqualByComparingTo("26.27");     // 20.32 + 5.95
    }

    @Test
    void requiresBothArguments() {
        assertThatThrownBy(() -> LineAmounts.of(null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LineAmounts.of(BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

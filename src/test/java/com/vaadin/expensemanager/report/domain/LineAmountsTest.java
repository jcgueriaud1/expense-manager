package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit test (pyramid layer 1, ADR-0012): pure JUnit, no Spring/DB, for the
 * money derivation in {@link LineAmounts} (ADR-0010).
 *
 * <p>The entered amount is the gross unit price and the gross is
 * {@code unit × quantity} (ADR-0023); net/VAT are worked backward from that gross
 * at HALF_UP scale 2. Negatives (credits/corrections) flow through unchanged.
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

    @Test
    void grossOfMultipliesUnitPriceByQuantityHalfUp() {
        // 3 × 12.50 = 37.50 exactly; 3 × 12.335 rounds HALF_UP to 37.01.
        assertThat(LineAmounts.grossOf(new BigDecimal("12.50"), new BigDecimal("3")))
                .isEqualByComparingTo("37.50");
        assertThat(LineAmounts.grossOf(new BigDecimal("12.335"), new BigDecimal("3")))
                .isEqualByComparingTo("37.01");
    }

    @Test
    void grossOfQuantityOneIsTheUnitPriceItself() {
        // The invisible-until-used guarantee (ADR-0023): quantity 1 changes nothing.
        assertThat(LineAmounts.grossOf(new BigDecimal("100.00"), BigDecimal.ONE))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void grossOfCarriesANegativeUnitPriceThrough() {
        // Credits ride a negative unit price, never a negative quantity.
        assertThat(LineAmounts.grossOf(new BigDecimal("-30.00"), new BigDecimal("2")))
                .isEqualByComparingTo("-60.00");
    }

    @Test
    void grossOfAcceptsAFractionalQuantity() {
        assertThat(LineAmounts.grossOf(new BigDecimal("0.53"), new BigDecimal("12.50")))
                .isEqualByComparingTo("6.63");   // 6.625 → HALF_UP
    }

    @Test
    void ofLineDerivesNetAndVatFromTheMultipliedGross() {
        var amounts = LineAmounts.ofLine(new BigDecimal("100.00"), new BigDecimal("3"),
                new BigDecimal("25.50"));

        // Net/VAT come off the gross (300.00), not off the unit price.
        assertThat(amounts.gross()).isEqualByComparingTo("300.00");
        assertThat(amounts.net()).isEqualByComparingTo("239.04");
        assertThat(amounts.vat()).isEqualByComparingTo("60.96");
        assertThat(amounts.net().add(amounts.vat())).isEqualByComparingTo("300.00");
    }

    @Test
    void grossOfRequiresBothArguments() {
        assertThatThrownBy(() -> LineAmounts.grossOf(null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LineAmounts.grossOf(BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * Immutable read model of a {@link KilometreRate} for the UI and the calculator
 * (ADR-0003).
 *
 * @param id          persistent id
 * @param year        the year this rate applies to
 * @param amountPerKm compensation per kilometre (EUR/km)
 */
public record KilometreRateDto(Long id, int year, BigDecimal amountPerKm) {
}

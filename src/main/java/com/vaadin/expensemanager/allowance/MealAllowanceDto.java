package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * Immutable read model of a {@link MealAllowanceRate} for the UI and the
 * calculator (ADR-0003).
 *
 * @param id     persistent id
 * @param year   the year this rate applies to
 * @param amount meal allowance amount (EUR)
 */
public record MealAllowanceDto(Long id, int year, BigDecimal amount) {
}

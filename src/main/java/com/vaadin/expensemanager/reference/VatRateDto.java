package com.vaadin.expensemanager.reference;

import java.math.BigDecimal;

/**
 * Immutable read model of a {@link VatRate} for the UI (ADR-0003).
 *
 * @param id           persistent id
 * @param value        the rate as a percent, e.g. {@code 25.50}
 * @param displayOrder position in the admin/selection ordering (ascending)
 * @param active       whether the rate is offered to new lines
 */
public record VatRateDto(Long id, BigDecimal value, int displayOrder, boolean active) {
}

package com.vaadin.expensemanager.reference;

import java.math.BigDecimal;

/**
 * Immutable read model of an {@link ExpenseType} for the UI (ADR-0003).
 *
 * <p>Carries the default rate's id (for editing) and its value (for display) so
 * the UI never dereferences a JPA association.
 *
 * @param id                  persistent id
 * @param name                display name
 * @param displayOrder        position in the admin/selection ordering (ascending)
 * @param active              whether the type is offered to new lines
 * @param defaultVatRateId    id of the required default VAT rate
 * @param defaultVatRateValue the default VAT rate's percent value, for display
 */
public record ExpenseTypeDto(Long id, String name, int displayOrder, boolean active,
        Long defaultVatRateId, BigDecimal defaultVatRateValue) {
}

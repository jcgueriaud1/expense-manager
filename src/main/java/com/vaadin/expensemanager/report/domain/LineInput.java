package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;

import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.VatRate;

/**
 * A single line's desired state, fed into {@link ExpenseReport#replaceLines} for
 * whole-aggregate reconciliation (ADR-0019).
 *
 * <p>The service resolves the DTO's reference ids to managed {@link ExpenseType}
 * / {@link VatRate} entities and hands the aggregate this carrier; the aggregate
 * owns the insert/update/orphan-remove decision. A {@code null} {@link #id}
 * marks a new line to insert; a non-null one matches an existing line to update
 * (any existing line absent from the input list is orphan-removed).
 *
 * @param id          the existing line's id, or {@code null} for a new line
 * @param expenseType resolved expense type (may be a deactivated one, for a
 *                    historical line that keeps its filed classification)
 * @param amount      gross amount the user paid (required, non-zero)
 * @param vatRate     resolved VAT rate (may be deactivated, kept for history)
 * @param comment     optional free-text note
 */
public record LineInput(Long id, ExpenseType expenseType, BigDecimal amount,
        VatRate vatRate, String comment) {
}

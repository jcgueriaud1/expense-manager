package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;

/**
 * Immutable working copy of one {@link com.vaadin.expensemanager.report.domain.ExpenseLine
 * ExpenseLine} for the detail view (ADR-0003, ADR-0019).
 *
 * <p>Carries the full {@link ExpenseTypeDto} / {@link VatRateDto} the line is
 * filed against — not just their ids — so the editor's ComboBoxes can show the
 * selected value even when it is a <strong>now-deactivated</strong> type or rate
 * that no longer appears in the active-options list (a historical line keeps its
 * filed classification, ADR-0018). Net/VAT/gross are derived by the view via
 * {@code LineMoney}, so they are not carried here.
 *
 * @param id          persistent line id, or {@code null} for a not-yet-saved line
 * @param expenseType the line's expense type (may be inactive, for history)
 * @param amount      gross amount the user paid (required, non-zero on save)
 * @param vatRate     the line's VAT rate (may be inactive, for history)
 * @param comment     optional free-text note, may be {@code null}
 */
public record ExpenseLineDto(Long id, ExpenseTypeDto expenseType, BigDecimal amount,
        VatRateDto vatRate, String comment) {
}

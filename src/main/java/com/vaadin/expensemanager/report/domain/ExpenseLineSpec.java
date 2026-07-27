package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;

import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.VatRate;

/**
 * A resolved line instruction for whole-aggregate reconciliation (ADR-0019).
 *
 * <p>The service turns each incoming {@code ExpenseLineDto} into one of these by
 * resolving the reference-data <em>ids</em> to managed {@link ExpenseType} /
 * {@link VatRate} entities (looked up unfiltered, so a now-inactive historical
 * rate still resolves), then hands the list to
 * {@link ExpenseReport#reconcileLines}. The aggregate matches on {@link #id}:
 * non-null → update the existing line, {@code null} → insert a new one, and any
 * existing line whose id is absent from the list is orphan-removed.
 *
 * <p>Keeping the reference lookups in the service (not the domain) is what lets
 * the aggregate stay free of repositories while still owning the reconciliation
 * invariant (ADR-0006, ADR-0003).
 *
 * @param id          the existing line id to update, or {@code null} to insert
 * @param expenseType the resolved expense type (required)
 * @param amount      the gross <em>unit price</em>, each (required, non-zero;
 *                    negatives allowed for credits — ADR-0023)
 * @param quantity    the line quantity (required, strictly {@code > 0}); the line
 *                    gross is {@code amount × quantity}
 * @param vatRate     the resolved VAT rate (required)
 * @param comment     optional free-text note
 */
public record ExpenseLineSpec(Long id, ExpenseType expenseType, BigDecimal amount,
        BigDecimal quantity, VatRate vatRate, String comment) {

    /**
     * A single-unit line spec — quantity {@code 1}, so the unit price <em>is</em>
     * the gross. The shape every caller that predates quantity wants (ADR-0023).
     */
    public static ExpenseLineSpec of(Long id, ExpenseType expenseType,
            BigDecimal amount, VatRate vatRate, String comment) {
        return new ExpenseLineSpec(id, expenseType, amount, BigDecimal.ONE, vatRate,
                comment);
    }
}

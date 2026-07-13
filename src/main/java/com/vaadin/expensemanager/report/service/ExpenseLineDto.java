package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;

/**
 * Immutable working copy of one expense line for the detail view (ADR-0003,
 * ADR-0019).
 *
 * <p>Carries the reference-data <em>ids</em> that drive persistence
 * ({@link #expenseTypeId}, {@link #vatRateId}) plus flattened display fields
 * ({@link #expenseTypeName}, {@link #vatRatePercent}) so the receipt cards render
 * without ever dereferencing a JPA association. Net/VAT are not carried — the UI
 * derives them live from {@link #amount} + {@link #vatRatePercent} via the shared
 * domain helper, so an unsaved edit and a persisted line use the same maths.
 *
 * <p>{@link #id} is the reconciliation key (ADR-0019): {@code null} for a
 * not-yet-persisted line the service will insert, non-null for one it will match
 * and update. A line whose type/rate has since been deactivated still round-trips
 * — the ids resolve unfiltered on save, and the display fields keep the card
 * readable even though the pickers no longer offer that option.
 *
 * @param id             persistent line id, or {@code null} for a new line
 * @param expenseTypeId  chosen expense type id (required to be valid on save)
 * @param expenseTypeName expense type name, for display
 * @param vatRateId      chosen VAT rate id (required to be valid on save)
 * @param vatRatePercent VAT rate as a percent, e.g. {@code 25.50}, for display
 *                       and live derivation
 * @param amount         gross amount as entered (required, non-zero on save)
 * @param comment        optional free-text note, may be {@code null}
 */
public record ExpenseLineDto(Long id, Long expenseTypeId, String expenseTypeName,
        Long vatRateId, BigDecimal vatRatePercent, BigDecimal amount,
        String comment) {

    /** Whether this line has been persisted (drives insert vs update). */
    public boolean isPersisted() {
        return id != null;
    }
}

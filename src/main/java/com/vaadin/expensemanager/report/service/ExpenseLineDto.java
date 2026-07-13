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
 * <p><strong>Receipt summary, never the bytes (ADR-0021).</strong> The trailing
 * fields describe the line's attached receipt — {@link #receiptId},
 * {@link #receiptFilename}, {@link #receiptContentType}, {@link #receiptSizeBytes}
 * — so a card can show "📎 file.jpg" without loading the blob; the {@code byte[]}
 * is never carried here. On the load path all four come from a blob-free
 * projection. Optimistically (an unsaved attach) the filename is set while
 * {@link #receiptId} is still {@code null}; a pending removal clears them — so
 * {@link #hasReceipt()} keys off the filename, the field common to both.
 *
 * <p>{@link #id} is the reconciliation key (ADR-0019): {@code null} for a
 * not-yet-persisted line the service will insert, non-null for one it will match
 * and update. A line whose type/rate has since been deactivated still round-trips
 * — the ids resolve unfiltered on save, and the display fields keep the card
 * readable even though the pickers no longer offer that option.
 *
 * @param id                 persistent line id, or {@code null} for a new line
 * @param expenseTypeId      chosen expense type id (required to be valid on save)
 * @param expenseTypeName    expense type name, for display
 * @param vatRateId          chosen VAT rate id (required to be valid on save)
 * @param vatRatePercent     VAT rate as a percent, e.g. {@code 25.50}, for display
 *                           and live derivation
 * @param amount             gross amount as entered (required, non-zero on save)
 * @param comment            optional free-text note, may be {@code null}
 * @param receiptId          attached receipt's id, or {@code null} if none / unsaved
 * @param receiptFilename    attached receipt's filename, or {@code null} if none
 * @param receiptContentType attached receipt's sniffed MIME type, or {@code null}
 * @param receiptSizeBytes   attached receipt's byte length, or {@code null} if none
 */
public record ExpenseLineDto(Long id, Long expenseTypeId, String expenseTypeName,
        Long vatRateId, BigDecimal vatRatePercent, BigDecimal amount,
        String comment, Long receiptId, String receiptFilename,
        String receiptContentType, Long receiptSizeBytes) {

    /**
     * A line with no receipt (the common construction site: new lines, tests, and
     * the editor before an upload is buffered).
     */
    public static ExpenseLineDto of(Long id, Long expenseTypeId, String expenseTypeName,
            Long vatRateId, BigDecimal vatRatePercent, BigDecimal amount,
            String comment) {
        return new ExpenseLineDto(id, expenseTypeId, expenseTypeName, vatRateId,
                vatRatePercent, amount, comment, null, null, null, null);
    }

    /** This line with its receipt summary fields replaced (load path / optimistic). */
    public ExpenseLineDto withReceipt(Long receiptId, String receiptFilename,
            String receiptContentType, Long receiptSizeBytes) {
        return new ExpenseLineDto(id, expenseTypeId, expenseTypeName, vatRateId,
                vatRatePercent, amount, comment, receiptId, receiptFilename,
                receiptContentType, receiptSizeBytes);
    }

    /** This line with any receipt summary cleared (optimistic removal). */
    public ExpenseLineDto withoutReceipt() {
        return withReceipt(null, null, null, null);
    }

    /** Whether this line has been persisted (drives insert vs update). */
    public boolean isPersisted() {
        return id != null;
    }

    /**
     * Whether a receipt is attached or pending — keyed off the filename so it
     * holds both for a persisted receipt and an unsaved (id-less) buffered one.
     */
    public boolean hasReceipt() {
        return receiptFilename != null;
    }
}

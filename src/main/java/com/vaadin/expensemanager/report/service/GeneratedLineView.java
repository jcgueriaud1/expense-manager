package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;

import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.LineAmounts;

/**
 * One of a trip's <strong>server-computed</strong> generated lines, as the detail
 * view sees it (Phase 4.3). A trip carries a list of these on its
 * {@link TravelDto} — one per output it earned (per-diem, kilometre, meal,
 * parking) — replacing the earlier flat allowance breakdown so each line can also
 * carry its persistent id and an <em>attached receipt</em>.
 *
 * <p>Everything except the receipt is read-only in the UI: the money is recomputed
 * from the trip inputs (the client never sends money) and the {@link #comment} is
 * the calculator's explanation. Only a receipt may be attached — to any of the four
 * kinds — which is why this carries the same blob-free receipt summary fields as
 * {@link ExpenseLineDto} (ADR-0021). The {@link #vatRatePercent} lets the totals
 * card split a VAT-bearing parking line into Net/VAT live with the same
 * {@code LineAmounts} maths.
 *
 * <p>Like a manual line, a generated one carries a {@link #unitPrice} and a
 * {@link #quantity} with the gross {@linkplain #amount() derived} from them
 * (ADR-0023) — the kilometre line as {@code km × €/km rate}, the flat kinds
 * (per-diem, meal, parking) at quantity {@code 1}. The card renders the
 * {@code qty × unit = gross} breakdown from these two, and every total sums the
 * derived {@link #amount()}, so a preview and a persisted line can never disagree.
 *
 * <p>When the user has corrected the count (glossary: Quantity Override, ADR-0024)
 * the {@link #quantity} here is the <strong>effective</strong> one and
 * {@link #overrideReason} / {@link #calculatedQuantity} carry the reason and the
 * statutory baseline, so the row can show an "Overridden" badge and "what the rules
 * said" without parsing the {@link #comment} string. A count of {@code 0} means the
 * line was {@linkplain #isSuppressed() suppressed} — the report carries no line for
 * it at all, and this view exists only so the correction stays visible and
 * reversible (issue #132).
 *
 * @param kind               which generated line this is (routes it in the totals)
 * @param expenseTypeName    the expense type the line is filed under (for display)
 * @param unitPrice          gross unit price (each), EUR scale 2 (recomputed, read-only)
 * @param quantity           how many units — km for a kilometre line, else {@code 1}
 * @param vatRatePercent     the line's VAT rate (0 for the allowances, 25.5 for parking)
 * @param comment            the calculator's read-only explanation
 * @param lineId             the persisted expense-line id, or {@code null} if unsaved
 * @param receiptId          attached receipt's id, or {@code null} if none / unsaved
 * @param receiptFilename    attached receipt's filename, or {@code null} if none
 * @param receiptContentType attached receipt's sniffed MIME type, or {@code null}
 * @param receiptSizeBytes   attached receipt's byte length, or {@code null} if none
 * @param overrideReason     why the count was overridden, or {@code null} if it wasn't
 * @param calculatedQuantity the count the calculator produced, or {@code null} if
 *                           the line is not overridden (or the baseline is unknown)
 */
public record GeneratedLineView(GeneratedLineKind kind, String expenseTypeName,
        BigDecimal unitPrice, BigDecimal quantity, BigDecimal vatRatePercent,
        String comment, Long lineId, Long receiptId, String receiptFilename,
        String receiptContentType, Long receiptSizeBytes, String overrideReason,
        BigDecimal calculatedQuantity) {

    /** A flat (quantity-1) generated line with no receipt — per-diem, meal, parking. */
    public static GeneratedLineView of(GeneratedLineKind kind, String expenseTypeName,
            BigDecimal amount, BigDecimal vatRatePercent, String comment, Long lineId) {
        return of(kind, expenseTypeName, amount, BigDecimal.ONE, vatRatePercent, comment,
                lineId);
    }

    /** A unit-price × quantity generated line with no receipt (preview / load). */
    public static GeneratedLineView of(GeneratedLineKind kind, String expenseTypeName,
            BigDecimal unitPrice, BigDecimal quantity, BigDecimal vatRatePercent,
            String comment, Long lineId) {
        return new GeneratedLineView(kind, expenseTypeName, unitPrice, quantity,
                vatRatePercent, comment, lineId, null, null, null, null, null, null);
    }

    /** The gross — unit price × quantity, via the domain's single multiplier (ADR-0023). */
    public BigDecimal amount() {
        return LineAmounts.grossOf(unitPrice, quantity);
    }

    /** Whether the card shows a {@code qty × unit = gross} breakdown (quantity ≠ 1). */
    public boolean showsQuantity() {
        return quantity.compareTo(BigDecimal.ONE) != 0;
    }

    /** This line with its receipt summary fields replaced (load / optimistic buffer). */
    public GeneratedLineView withReceipt(Long receiptId, String receiptFilename,
            String receiptContentType, Long receiptSizeBytes) {
        return new GeneratedLineView(kind, expenseTypeName, unitPrice, quantity,
                vatRatePercent, comment, lineId, receiptId, receiptFilename,
                receiptContentType, receiptSizeBytes, overrideReason, calculatedQuantity);
    }

    /**
     * This line marked as carrying a {@linkplain
     * com.vaadin.expensemanager.report.domain.QuantityOverride Quantity Override}
     * (ADR-0024) — the reason the user gave and the count the calculator produced.
     * The {@link #quantity} is already the effective (overridden) one; this adds the
     * two facts the row needs to render a real badge and a real baseline rather than
     * parsing {@link #comment}.
     *
     * @param calculatedQuantity the statutory baseline, or {@code null} when it could
     *                           not be recomputed (the badge and reason still show)
     */
    public GeneratedLineView withOverride(String overrideReason,
            BigDecimal calculatedQuantity) {
        return new GeneratedLineView(kind, expenseTypeName, unitPrice, quantity,
                vatRatePercent, comment, lineId, receiptId, receiptFilename,
                receiptContentType, receiptSizeBytes, overrideReason, calculatedQuantity);
    }

    /** Whether the user replaced this line's calculated count (ADR-0024). */
    public boolean isOverridden() {
        return overrideReason != null;
    }

    /**
     * Whether this row stands for a line a {@code 0} Quantity Override
     * <strong>dropped</strong> from the report (issue #132) rather than a line the
     * report carries. No {@code ExpenseLine} exists for it and its
     * {@linkplain #amount() amount} is zero, so it adds nothing to any subtotal — it
     * is here so the correction stays visible and, above all,
     * <em>reversible</em>: without a row there is no "Reset to calculated" to click,
     * and a persisted override would go invisible and unreachable.
     *
     * <p>A suppressed row therefore carries no {@link #lineId} and no receipt (a
     * suppression destroys the receipt it had), and offers no attach affordance.
     */
    public boolean isSuppressed() {
        return isOverridden() && quantity.signum() == 0;
    }

    /**
     * The gross the calculator would have produced — {@code unit price × calculated
     * quantity} — for the row's "calculated" baseline; {@code null} when the baseline
     * is unknown or the line is not overridden.
     */
    public BigDecimal calculatedAmount() {
        return calculatedQuantity == null ? null
                : LineAmounts.grossOf(unitPrice, calculatedQuantity);
    }

    /** This line with any receipt summary cleared (optimistic removal). */
    public GeneratedLineView withoutReceipt() {
        return withReceipt(null, null, null, null);
    }

    /** Whether this generated line is a tax-free allowance (vs VAT-bearing parking). */
    public boolean isTaxFreeAllowance() {
        return kind.isTaxFreeAllowance();
    }

    /**
     * Whether a receipt is attached or pending — keyed off the filename so it holds
     * both for a persisted receipt and an unsaved (id-less) buffered one.
     */
    public boolean hasReceipt() {
        return receiptFilename != null;
    }
}

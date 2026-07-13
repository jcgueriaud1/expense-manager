package com.vaadin.expensemanager.report.service;

/**
 * A buffered receipt mutation for one working line, applied on the next
 * whole-aggregate save (ADR-0021). This is the carrier that lets received bytes
 * travel from the always-enabled upload control to persistence <em>without</em>
 * putting a {@code byte[]} on {@link ExpenseLineDto} / {@link ReportDetailDto}:
 * the summary DTOs stay blob-free, and buffered bytes ride this separate,
 * save-only channel instead.
 *
 * <p>Two shapes: an <strong>attach</strong> (non-null {@link #data}, overwriting
 * any existing receipt) or a {@link #REMOVE} sentinel that clears the line's
 * receipt. The stored {@code content_type} is re-sniffed by the service from
 * {@link #data} (never taken from the browser), so this carries only the bytes
 * and the original filename.
 *
 * @param data     the uploaded bytes, or {@code null} for a removal
 * @param filename the original client filename, for display; {@code null} on removal
 */
public record ReceiptUpload(byte[] data, String filename) {

    /** Clears the line's receipt on save. */
    public static final ReceiptUpload REMOVE = new ReceiptUpload(null, null);

    public boolean isRemoval() {
        return data == null;
    }
}

package com.vaadin.expensemanager.report.service;

/**
 * The <strong>dedicated download projection</strong> (ADR-0021, read-path slice):
 * the one query in the whole app that materializes a receipt's {@code data} bytea,
 * selecting only the fields needed to stream one receipt.
 *
 * <p>This is deliberately separate from {@link ReceiptSummaryView} (which omits
 * the bytes for the hot load path): the bytea is loaded <em>only</em> here, by id,
 * and only after the owning-report authorization check has passed in the query
 * (ADR-0008). It never rides an aggregate load and never reaches a UI DTO — the
 * service copies it straight into a {@link ReceiptContent} for streaming.
 */
public interface ReceiptDownloadView {

    byte[] getData();

    String getFilename();

    String getContentType();
}

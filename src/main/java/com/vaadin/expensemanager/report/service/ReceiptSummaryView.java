package com.vaadin.expensemanager.report.service;

/**
 * Spring Data projection of a {@link com.vaadin.expensemanager.report.domain.Receipt}
 * that deliberately <strong>omits the {@code data} bytea</strong> (ADR-0021).
 *
 * <p>This is how a receipt summary reaches the report load path without the blob
 * ever being materialized: the query selects only these small columns, so
 * rendering "line X has receipt Y.jpg (2 MB)" costs nothing on the hot path. The
 * bytes are read only by the dedicated download query on the read-path slice.
 */
public interface ReceiptSummaryView {

    Long getId();

    Long getExpenseLineId();

    String getFilename();

    String getContentType();

    long getSizeBytes();
}

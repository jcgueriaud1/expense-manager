package com.vaadin.expensemanager.report.domain;

/**
 * Thrown when an uploaded receipt fails validation (wrong type or over-cap) —
 * carries a user-facing message the upload UI shows verbatim (ADR-0021,
 * ADR-0020: no disabled control, explain the rejection instead).
 */
public class ReceiptRejectedException extends RuntimeException {

    public ReceiptRejectedException(String message) {
        super(message);
    }
}

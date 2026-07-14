package com.vaadin.expensemanager.report.service;

import com.vaadin.expensemanager.report.domain.GeneratedLineKind;

/**
 * Addresses one of a report's generated lines for a buffered receipt mutation
 * (Phase 4.3, ADR-0021) — the generated-line counterpart of the manual lines'
 * {@code Map<Integer, ReceiptUpload>} position key.
 *
 * <p>A generated line has no stable id until the aggregate is saved, so a receipt
 * buffered against it in the UI cannot be keyed by id. Instead it is keyed by the
 * trip's position in {@code ReportDetailDto.travels()} — the same order the
 * aggregate reconciles the trips into {@link
 * com.vaadin.expensemanager.report.domain.ExpenseReport#getTravels()} — and the
 * {@link GeneratedLineKind}. The service resolves the pair to the persisted line
 * after the flush and applies the receipt.
 *
 * @param travelIndex the trip's position in the saved trip list
 * @param kind        which of the trip's generated lines the receipt is for
 */
public record GeneratedLineRef(int travelIndex, GeneratedLineKind kind) {
}

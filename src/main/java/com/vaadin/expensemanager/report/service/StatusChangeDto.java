package com.vaadin.expensemanager.report.service;

import java.time.Instant;

import com.vaadin.expensemanager.report.domain.ReportStatus;

/**
 * Immutable view of one status-history entry for the detail view (ADR-0003,
 * glossary: Status Change).
 *
 * <p>Flattens a {@link com.vaadin.expensemanager.report.domain.StatusChange} into
 * display-ready fields — the transition ({@link #fromStatus} → {@link #toStatus}),
 * the acting user's display name, an optional comment, and when it happened — so
 * the UI can render the audit trail without ever dereferencing a JPA association
 * (the entity never leaves the service layer, ADR-0003).
 *
 * @param fromStatus     the status before the transition
 * @param toStatus       the status after the transition
 * @param actorName      the acting user's display name
 * @param comment        the transition comment, or {@code null} (mandatory only on
 *                       reject, from a later Phase 5 slice)
 * @param changedAt      when the transition happened
 */
public record StatusChangeDto(ReportStatus fromStatus, ReportStatus toStatus,
        String actorName, String comment, Instant changedAt) {
}

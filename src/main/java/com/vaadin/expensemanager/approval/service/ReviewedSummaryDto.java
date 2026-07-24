package com.vaadin.expensemanager.approval.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.vaadin.expensemanager.report.domain.ReportStatus;

/**
 * Immutable row model for the admin review history (Issue #110, ADR-0003) — the
 * counterpart to {@link ReviewSummaryDto} for reports in a terminal status
 * ({@code APPROVED} / {@code REJECTED}).
 *
 * <p>Where the queue sorts on <em>submitted-at</em>, a history is more useful
 * keyed on the <strong>decision</strong>: it carries the deciding admin's name
 * and the {@code → APPROVED} / {@code → REJECTED} transition's timestamp (the
 * history's sort key, newest decision first), plus the rejection comment when the
 * outcome was a rejection. Like the queue it also carries the submitter's display
 * name (the history spans every owner). Per ADR-0003 the entity never reaches the
 * UI — this record does.
 *
 * @param id                    persistent id (the review-route key)
 * @param reportDate            user-entered report date
 * @param additionalInformation optional free-text note, may be {@code null}
 * @param status                terminal status ({@code APPROVED} or {@code REJECTED})
 * @param total                 derived report total, EUR scale 2
 * @param submitterName         the owning user's display name
 * @param decidedByName         the deciding admin's display name, or {@code null}
 *                              if the decision change is somehow absent
 * @param decidedAt             when the report was approved/rejected, or
 *                              {@code null} if the decision change is somehow absent
 * @param rejectionComment      the rejection reason for a {@code REJECTED} report,
 *                              {@code null} otherwise
 */
public record ReviewedSummaryDto(Long id, LocalDate reportDate,
        String additionalInformation, ReportStatus status, BigDecimal total,
        String submitterName, String decidedByName, Instant decidedAt,
        String rejectionComment) {
}

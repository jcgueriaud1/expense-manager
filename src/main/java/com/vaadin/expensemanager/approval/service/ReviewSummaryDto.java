package com.vaadin.expensemanager.approval.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.vaadin.expensemanager.report.domain.ReportStatus;

/**
 * Immutable row model for the admin approval queue (Phase 5, ADR-0003).
 *
 * <p>Like {@code ReportSummaryDto} but carries the two things the queue needs
 * that the owner's own list never does: the <strong>submitter's display name</strong>
 * (the queue spans every owner) and the <strong>submitted-at</strong> timestamp
 * (from the SUBMITTED status change), the queue's sort key. Per ADR-0003 the
 * entity never reaches the UI — this record does.
 *
 * @param id                    persistent id (the review-route key)
 * @param reportDate            user-entered report date
 * @param additionalInformation optional free-text note, may be {@code null}
 * @param status                lifecycle status (always {@code SUBMITTED} here)
 * @param total                 derived report total, EUR scale 2
 * @param submitterName         the owning user's display name
 * @param submittedAt           when the report was submitted, or {@code null} if
 *                              the submit change is somehow absent
 */
public record ReviewSummaryDto(Long id, LocalDate reportDate,
        String additionalInformation, ReportStatus status, BigDecimal total,
        String submitterName, Instant submittedAt) {
}

package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.expensemanager.report.domain.ReportStatus;

/**
 * Immutable row model for the My Reports list (UC-002, ADR-0003).
 *
 * <p>Everything one report card draws, and nothing else: the report-level fields,
 * the derived {@link #total} (never a stored column), the report's
 * {@linkplain TripSummaryDto trips}, the created-on date the card's footer shows,
 * and — for a rejected report only — who rejected it and when.
 *
 * <p>Two dates, deliberately (issue #147). {@link #reportDate} is user-entered and
 * stays the list's <strong>sort and filter</strong> key; {@link #createdAt} is the
 * audit timestamp the footer renders as "Created on …". They are different facts
 * and the design uses both.
 *
 * <p>{@link #trips} is populated by a batched fetch in
 * {@link ExpenseReportService#listMine()}, never by a lazy per-row walk — see that
 * method for the query plan.
 *
 * @param id                    persistent id (the detail-route key)
 * @param reportDate            user-entered report date (the sort/filter key)
 * @param additionalInformation optional free-text note, may be {@code null}
 * @param status                lifecycle status
 * @param total                 derived report total, EUR scale 2
 * @param createdAt             when the report was first saved (the footer's date)
 * @param trips                 the report's trips in insertion order, never
 *                              {@code null} — empty for a report without travel
 * @param rejectedByName        the rejecting admin's display name, or {@code null}
 *                              unless the report is currently {@code REJECTED}
 * @param rejectedAt            when it was rejected, or {@code null} unless the
 *                              report is currently {@code REJECTED}
 */
public record ReportSummaryDto(Long id, LocalDate reportDate,
        String additionalInformation, ReportStatus status, BigDecimal total,
        Instant createdAt, List<TripSummaryDto> trips, String rejectedByName,
        Instant rejectedAt) {

    public ReportSummaryDto {
        trips = trips == null ? List.of() : List.copyOf(trips);
    }
}

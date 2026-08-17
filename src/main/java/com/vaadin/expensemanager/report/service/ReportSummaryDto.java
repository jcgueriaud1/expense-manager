package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.vaadin.expensemanager.report.domain.ReportStatus;

/**
 * Immutable row model for the My Reports list (UC-002, ADR-0003).
 *
 * <p>Exactly what the list card shows plus the id used to open the detail view.
 * The {@code total} is the derived report total, never a stored column.
 *
 * @param id                    persistent id (the detail-route key)
 * @param reportDate            user-entered report date (the sort key)
 * @param additionalInformation optional free-text note, may be {@code null}
 * @param status                lifecycle status
 * @param total                 derived report total, EUR scale 2
 * @param travel                where and when the report's trips went,
 *                              {@code null} when it has none
 * @param submittedAt           when it was last handed off for review, {@code
 *                              null} while it has never been submitted — what
 *                              makes "waiting N days" answerable from the list
 */
public record ReportSummaryDto(Long id, LocalDate reportDate,
        String additionalInformation, ReportStatus status, BigDecimal total,
        TravelSummary travel, Instant submittedAt) {

    /**
     * A report's trips folded into one line for the list card.
     *
     * <p>A report can hold any number of trips, so this is a fold, not a copy of
     * one {@code Travel}: {@code destination} joins what the trips say about
     * where they went and the range spans from the earliest departure to the
     * latest return. The card shows this only when it is present, which is what
     * makes a travel report legible without opening it.
     *
     * @param destination where the trips went, already joined for display
     * @param start       earliest departure date across the trips
     * @param end         latest return date across the trips
     * @param tripCount   how many trips were folded in (1 for most reports)
     */
    public record TravelSummary(String destination, LocalDate start, LocalDate end,
            int tripCount) {
    }
}

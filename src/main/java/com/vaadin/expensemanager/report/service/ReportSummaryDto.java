package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.vaadin.expensemanager.report.domain.ReportStatus;

/**
 * Immutable row model for the My Reports list (UC-002, ADR-0003).
 *
 * <p>Exactly the four columns the grid shows plus the id used to open the
 * detail view. The {@code total} is the derived report total (€0.00 until lines
 * arrive in Phase 2.3), never a stored column.
 *
 * @param id                    persistent id (the detail-route key)
 * @param reportDate            user-entered report date (the sort key)
 * @param additionalInformation optional free-text note, may be {@code null}
 * @param status                lifecycle status
 * @param total                 derived report total, EUR scale 2
 */
public record ReportSummaryDto(Long id, LocalDate reportDate,
        String additionalInformation, ReportStatus status, BigDecimal total) {
}

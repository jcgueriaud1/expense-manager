package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.expensemanager.report.domain.ReportStatus;

/**
 * Immutable working copy of a report for the detail view (UC-001/UC-005,
 * ADR-0019, ADR-0003).
 *
 * <p>The detail view holds one of these as its edit state; the service reads the
 * editable report-level fields ({@link #reportDate}, {@link #additionalInformation})
 * and the {@link #lines} on {@code create}/{@code update}, and ignores the
 * derived/read-only ones ({@link #status}, {@link #total}). Lines are added,
 * edited, and removed in memory; the first save INSERTs the whole aggregate and
 * subsequent saves reconcile the line collection by nullable id (ADR-0019).
 *
 * <p>{@link #version} carries the aggregate {@code @Version} the UI last saw and
 * is echoed back on save for the optimistic-lock check (ADR-0011); it is unused
 * for a not-yet-saved report ({@link #id} {@code null}).
 *
 * @param id                    persistent id, or {@code null} for a new report
 * @param reportDate            user-entered report date (required)
 * @param additionalInformation optional free-text note, may be {@code null}
 * @param status                lifecycle status ({@code DRAFT} when new)
 * @param version               optimistic-lock version last seen by the UI
 * @param total                 derived report total (gross), EUR scale 2
 * @param lines                 the report's expense lines, in order
 */
public record ReportDetailDto(Long id, LocalDate reportDate,
        String additionalInformation, ReportStatus status, long version,
        BigDecimal total, List<ExpenseLineDto> lines) {

    /**
     * A transient working copy for a brand-new report: no id, {@code DRAFT},
     * report date defaulting to {@code today}, empty note, no lines, zero total.
     * No row is persisted until the first save (ADR-0019).
     */
    public static ReportDetailDto forNew(LocalDate today) {
        return new ReportDetailDto(null, today, null, ReportStatus.DRAFT, 0L,
                BigDecimal.ZERO.setScale(2), List.of());
    }

    /** Whether this working copy has been persisted yet. */
    public boolean isPersisted() {
        return id != null;
    }
}

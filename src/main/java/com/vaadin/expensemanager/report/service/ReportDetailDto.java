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
 * and the {@link #lines} on {@code create}/{@code update} and ignores the
 * derived/read-only ones ({@link #status}, the totals). The line collection
 * reconciles by each line's nullable id on save (ADR-0019).
 *
 * <p>The totals ({@link #total} gross, {@link #netTotal}, {@link #vatTotal}) are
 * the persisted snapshot the service derived; while editing, the UI recomputes
 * them live from the working lines using the same domain maths (ADR-0010).
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
 * @param lines                 the report's expense lines in order (never null)
 * @param total                 derived gross total, EUR scale 2
 * @param netTotal              derived net total, EUR scale 2
 * @param vatTotal              derived VAT total, EUR scale 2
 */
public record ReportDetailDto(Long id, LocalDate reportDate,
        String additionalInformation, ReportStatus status, long version,
        List<ExpenseLineDto> lines, BigDecimal total, BigDecimal netTotal,
        BigDecimal vatTotal) {

    /**
     * A transient working copy for a brand-new report: no id, {@code DRAFT},
     * report date defaulting to {@code today}, empty note, no lines, zero totals.
     * No row is persisted until the first save (ADR-0019).
     */
    public static ReportDetailDto forNew(LocalDate today) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new ReportDetailDto(null, today, null, ReportStatus.DRAFT, 0L,
                List.of(), zero, zero, zero);
    }

    /** Whether this working copy has been persisted yet. */
    public boolean isPersisted() {
        return id != null;
    }
}

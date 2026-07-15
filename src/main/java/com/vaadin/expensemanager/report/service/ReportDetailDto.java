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
 * <p>The {@link #lines} are the <strong>manual</strong> (user-entered) lines the
 * view shows as editable cards; the {@link #travels} are the trips, each carrying
 * its server-computed allowance breakdown (Phase 4.2/4.3). The generated lines are
 * not carried here — they are represented by their travel and summed into the
 * three tax-free subtotals ({@link #perDiemTotal}, {@link #kilometreTotal},
 * {@link #mealTotal}). The VAT-bearing parking lines, by contrast, count in
 * {@link #netTotal}/{@link #vatTotal} alongside the manual lines, and
 * {@link #total} = Net + VAT + per-diem + kilometre + meal.
 *
 * @param id                    persistent id, or {@code null} for a new report
 * @param reportDate            user-entered report date (required)
 * @param additionalInformation optional free-text note, may be {@code null}
 * @param status                lifecycle status ({@code DRAFT} when new)
 * @param version               optimistic-lock version last seen by the UI
 * @param lines                 the report's manual expense lines in order (never null)
 * @param travels               the report's trips in order (never null)
 * @param total                 derived grand gross total, EUR scale 2
 * @param netTotal              derived net total of the VAT-bearing lines, EUR scale 2
 * @param vatTotal              derived VAT total of the VAT-bearing lines, EUR scale 2
 * @param perDiemTotal          derived tax-free per-diem subtotal, EUR scale 2
 * @param kilometreTotal        derived tax-free kilometre allowance subtotal, EUR scale 2
 * @param mealTotal             derived tax-free meal allowance subtotal, EUR scale 2
 * @param statusHistory         the report's status-change log, oldest first (never null)
 */
public record ReportDetailDto(Long id, LocalDate reportDate,
        String additionalInformation, ReportStatus status, long version,
        List<ExpenseLineDto> lines, List<TravelDto> travels, BigDecimal total,
        BigDecimal netTotal, BigDecimal vatTotal, BigDecimal perDiemTotal,
        BigDecimal kilometreTotal, BigDecimal mealTotal,
        List<StatusChangeDto> statusHistory) {

    /**
     * Constructor for a report whose status history is not needed by the caller
     * (every save/edit path, where the working copy is written, not read for its
     * audit trail): defaults {@link #statusHistory} empty.
     */
    public ReportDetailDto(Long id, LocalDate reportDate, String additionalInformation,
            ReportStatus status, long version, List<ExpenseLineDto> lines,
            List<TravelDto> travels, BigDecimal total, BigDecimal netTotal,
            BigDecimal vatTotal, BigDecimal perDiemTotal, BigDecimal kilometreTotal,
            BigDecimal mealTotal) {
        this(id, reportDate, additionalInformation, status, version, lines, travels,
                total, netTotal, vatTotal, perDiemTotal, kilometreTotal, mealTotal,
                List.of());
    }

    /**
     * Backward-compatible constructor for a report with no trips (seeds, tests,
     * and every pre-Phase-4 call site): defaults {@link #travels} empty and the
     * three tax-free subtotals to zero.
     */
    public ReportDetailDto(Long id, LocalDate reportDate, String additionalInformation,
            ReportStatus status, long version, List<ExpenseLineDto> lines,
            BigDecimal total, BigDecimal netTotal, BigDecimal vatTotal) {
        this(id, reportDate, additionalInformation, status, version, lines, List.of(),
                total, netTotal, vatTotal, BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
    }

    /**
     * A transient working copy for a brand-new report: no id, {@code DRAFT},
     * report date defaulting to {@code today}, empty note, no lines/trips, zero
     * totals. No row is persisted until the first save (ADR-0019).
     */
    public static ReportDetailDto forNew(LocalDate today) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new ReportDetailDto(null, today, null, ReportStatus.DRAFT, 0L,
                List.of(), List.of(), zero, zero, zero, zero, zero, zero);
    }

    /** Whether this working copy has been persisted yet. */
    public boolean isPersisted() {
        return id != null;
    }
}

package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vaadin.expensemanager.report.domain.ExpenseLine;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.domain.StatusChange;
import com.vaadin.expensemanager.report.domain.Travel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps the {@link ExpenseReport} aggregate to its boundary DTOs (ADR-0003).
 *
 * <p>Extracted from {@link ExpenseReportService} so the owner path and the
 * Phase-5 admin approval path share one mapping — the receipt-summary join, the
 * per-trip generated-line reconstruction, and the status-history flattening are
 * defined once. Read-only; per ADR-0003 the entity never escapes past this seam —
 * callers exchange {@link ReportDetailDto} / {@link ReportSummaryDto} records.
 *
 * <p>Its only reads beyond the aggregate are the blob-free receipt query and — for a
 * trip carrying a Quantity Override — {@link TravelCosting}, to recover the
 * <em>calculated baseline</em> the row shows beside the overridden figure (ADR-0024).
 * That lives here rather than in either service because this is the single seam both
 * the owner and the admin-review load paths pass through.
 *
 * <p>Must run inside the caller's transaction: it walks lazy associations (lines,
 * travels, and each status change's acting user) while mapping.
 */
@Component
public class ReportDtoMapper {

    private static final Logger log = LoggerFactory.getLogger(ReportDtoMapper.class);

    private final ReceiptRepository receiptRepository;
    private final TravelCosting travelCosting;

    public ReportDtoMapper(ReceiptRepository receiptRepository,
            TravelCosting travelCosting) {
        this.receiptRepository = receiptRepository;
        this.travelCosting = travelCosting;
    }

    /**
     * The list-row projection: the report-level fields and derived total, the
     * created-on date the card's footer shows, the report's trips, and — for a
     * report that is <em>currently</em> {@code REJECTED} — who rejected it and when
     * (issue #147).
     *
     * <p>Walks {@code lines}, {@code travels} and {@code statusHistory}, so the
     * caller must have fetched all three: {@code ExpenseReportService.listMine()}
     * does it in three batched queries. A report rejected and then resubmitted is
     * {@code SUBMITTED} again and carries no rejection meta — the footer entry
     * describes the report's present state, not its past.
     */
    public static ReportSummaryDto toSummary(ExpenseReport r) {
        var rejection = r.getStatus() == ReportStatus.REJECTED
                ? latestChangeTo(r, ReportStatus.REJECTED)
                : null;
        return new ReportSummaryDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.total(),
                r.getCreatedAt(), r.getTravels().stream()
                        .map(ReportDtoMapper::toTripSummary).toList(),
                rejection == null ? null : rejection.getActingUser().getName(),
                rejection == null ? null : rejection.getChangedAt());
    }

    /** One trip row: the route the user typed and the trip's own date range. */
    private static TripSummaryDto toTripSummary(Travel t) {
        return new TripSummaryDto(t.getDestinations(), t.getDepartureAt(),
                t.getReturnAt());
    }

    /**
     * The three list metrics for one owner's reports (issue #147), computed in a
     * single pass so the cards can never disagree with each other or with the list
     * below them. Owner-scoping is the caller's — this maps whatever it is given.
     *
     * <p>"Needs you" is {@link ReportStatus#isEditable()}, the predicate the list
     * already groups on, so there is exactly one notion of a report awaiting its
     * owner; {@code rejectedCount} breaks that figure down rather than adding to it.
     * The in-flight wait is the <em>longest</em> one — the number that says how
     * overdue the queue is — measured from the latest {@code SUBMITTED} change, not
     * from the user-entered report date.
     *
     * <p>A report counts as reimbursed in the year its approval <em>happened</em>,
     * from the latest {@code APPROVED} change; a report approved without a
     * recordable change (which the domain does not produce) falls back to its report
     * date rather than dropping out of the total silently.
     *
     * @param now  the instant the wait is measured against
     * @param zone the zone that decides which calendar year an approval fell in
     */
    public static ReportMetricsDto toMetrics(List<ExpenseReport> reports, Instant now,
            ZoneId zone) {
        int year = now.atZone(zone).getYear();
        if (reports.isEmpty()) {
            return ReportMetricsDto.empty(year);
        }
        var needsYou = reports.stream()
                .filter(r -> r.getStatus().isEditable()).toList();
        var inFlight = reports.stream()
                .filter(r -> r.getStatus() == ReportStatus.SUBMITTED).toList();
        var reimbursed = reports.stream()
                .filter(r -> r.getStatus() == ReportStatus.APPROVED)
                .filter(r -> approvalYear(r, zone) == year).toList();
        return new ReportMetricsDto(needsYou.size(), sumTotals(needsYou),
                (int) needsYou.stream()
                        .filter(r -> r.getStatus() == ReportStatus.REJECTED).count(),
                inFlight.size(), longestWaitDays(inFlight, now), year,
                sumTotals(reimbursed), reimbursed.size());
    }

    private static BigDecimal sumTotals(List<ExpenseReport> reports) {
        return reports.stream().map(ExpenseReport::total)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /**
     * Whole days the longest-waiting submitted report has been waiting. A report
     * whose {@code SUBMITTED} change is somehow absent contributes no wait rather
     * than an arbitrary one; with no submitted reports at all the answer is 0.
     */
    private static long longestWaitDays(List<ExpenseReport> submitted, Instant now) {
        return submitted.stream()
                .map(r -> latestChangeTo(r, ReportStatus.SUBMITTED))
                .filter(Objects::nonNull)
                .mapToLong(change -> ChronoUnit.DAYS.between(change.getChangedAt(), now))
                .max().orElse(0L);
    }

    /** The calendar year the report's approval fell in (report date as fallback). */
    private static int approvalYear(ExpenseReport r, ZoneId zone) {
        var approval = latestChangeTo(r, ReportStatus.APPROVED);
        return approval == null ? r.getReportDate().getYear()
                : approval.getChangedAt().atZone(zone).getYear();
    }

    /** The report's most recent transition into {@code toStatus}, or {@code null}. */
    private static StatusChange latestChangeTo(ExpenseReport r, ReportStatus toStatus) {
        return r.getStatusHistory().stream()
                .filter(change -> change.getToStatus() == toStatus)
                .max(Comparator.comparing(StatusChange::getChangedAt))
                .orElse(null);
    }

    /**
     * The full working copy for the detail view: manual lines as editable cards,
     * trips carrying their read-only generated lines, the split totals, and the
     * ordered status history. Both manual and generated lines can carry a receipt,
     * so the one blob-free receipt query covers every line id (ADR-0021).
     */
    public ReportDetailDto toDetail(ExpenseReport r) {
        var lineIds = r.getLines().stream().map(ExpenseLine::getId)
                .filter(Objects::nonNull).toList();
        Map<Long, ReceiptSummaryView> byLine = lineIds.isEmpty() ? Map.of()
                : receiptRepository.findSummariesByExpenseLineIdIn(lineIds).stream()
                        .collect(Collectors.toMap(ReceiptSummaryView::getExpenseLineId,
                                Function.identity()));
        var lineDtos = r.manualLines().stream()
                .map(line -> toLineDto(line, byLine.get(line.getId()))).toList();
        var travelDtos = r.getTravels().stream()
                .map(travel -> toTravelDto(r, travel, byLine)).toList();
        var history = r.getStatusHistory().stream()
                .map(ReportDtoMapper::toStatusChangeDto).toList();
        return new ReportDetailDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.getVersion(), lineDtos,
                travelDtos, r.total(), r.netTotal(), r.vatTotal(), r.perDiemTotal(),
                r.kilometreTotal(), r.mealTotal(), history);
    }

    private static StatusChangeDto toStatusChangeDto(StatusChange change) {
        return new StatusChangeDto(change.getFromStatus(), change.getToStatus(),
                change.getActingUser().getName(), change.getComment(),
                change.getChangedAt());
    }

    /**
     * Maps a persisted trip to its working-copy DTO, reading each generated line
     * (per kind, in kind order) off the report into a {@link GeneratedLineView}
     * with its unit price + quantity (ADR-0023), read-only explanation, id, and any
     * attached receipt. A trip carrying a Quantity Override also gets each overridden
     * line annotated with its reason and calculated baseline (ADR-0024).
     */
    private TravelDto toTravelDto(ExpenseReport r, Travel t,
            Map<Long, ReceiptSummaryView> byLine) {
        List<GeneratedLineView> generated = new ArrayList<>();
        for (GeneratedLineKind kind : GeneratedLineKind.values()) {
            r.generatedLineFor(t, kind).ifPresent(line -> {
                var view = GeneratedLineView.of(kind, line.getExpenseType().getName(),
                        line.getAmount(), line.getQuantity(),
                        line.getVatRate().getValue(), line.getComment(), line.getId());
                var receipt = byLine.get(line.getId());
                if (receipt != null) {
                    view = view.withReceipt(receipt.getId(), receipt.getFilename(),
                            receipt.getContentType(), receipt.getSizeBytes());
                }
                generated.add(view);
            });
        }
        var dto = new TravelDto(t.getId(), t.getDepartureAt(), t.getReturnAt(),
                t.getDestinations(), t.getPurpose(), t.getCountry(),
                t.isNotEligibleForAllowance(), t.isFreeLunch(), t.isChargeToCustomer(),
                t.getKilometres(), t.isPayMealAllowance(), t.getParkingFees(),
                t.getQuantityOverrides(), generated);
        return dto.quantityOverrides().isEmpty() ? dto : withOverrideDetail(dto);
    }

    /**
     * Annotates each overridden generated line with the reason the user gave and the
     * count the calculator produced (ADR-0024) — the two facts the row needs to render
     * a real "Overridden" badge and a real baseline rather than parsing the comment
     * string. This is the one seam <strong>both</strong> load paths pass through, so
     * the owner and the approver see the same thing.
     *
     * <p>The baseline is recomputed by costing an overrides-stripped copy of the trip.
     * That needs the trip-year rates and the allowance expense types, which a
     * long-since-saved report has no guarantee are still configured — so a failure
     * here is logged and swallowed rather than making the report unopenable: the badge
     * and reason still render, only "what the rules said" is missing.
     */
    private TravelDto withOverrideDetail(TravelDto dto) {
        Map<GeneratedLineKind, BigDecimal> baselines;
        try {
            baselines = travelCosting.calculatedQuantities(dto);
        } catch (RuntimeException uncostable) {
            log.warn("Could not recompute the calculated baseline for travel {}; "
                    + "showing the override without it.", dto.id(), uncostable);
            baselines = Map.of();
        }
        var annotated = new ArrayList<GeneratedLineView>(dto.generatedLines().size());
        for (GeneratedLineView line : dto.generatedLines()) {
            var override = dto.quantityOverrides().get(line.kind());
            annotated.add(override == null ? line
                    : line.withOverride(override.reason(), baselines.get(line.kind())));
        }
        return dto.withGeneratedLines(annotated);
    }

    private static ExpenseLineDto toLineDto(ExpenseLine line,
            ReceiptSummaryView receipt) {
        var type = line.getExpenseType();
        var rate = line.getVatRate();
        var dto = ExpenseLineDto.of(line.getId(), type.getId(), type.getName(),
                rate.getId(), rate.getValue(), line.getAmount(), line.getQuantity(),
                line.getComment());
        if (receipt == null) {
            return dto;
        }
        return dto.withReceipt(receipt.getId(), receipt.getFilename(),
                receipt.getContentType(), receipt.getSizeBytes());
    }
}

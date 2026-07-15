package com.vaadin.expensemanager.report.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vaadin.expensemanager.report.domain.ExpenseLine;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.StatusChange;
import com.vaadin.expensemanager.report.domain.Travel;

import org.springframework.stereotype.Component;

/**
 * Maps the {@link ExpenseReport} aggregate to its boundary DTOs (ADR-0003).
 *
 * <p>Extracted from {@link ExpenseReportService} so the owner path and the
 * Phase-5 admin approval path share one mapping — the receipt-summary join, the
 * per-trip generated-line reconstruction, and the status-history flattening are
 * defined once. Read-only and stateless apart from the blob-free receipt query;
 * per ADR-0003 the entity never escapes past this seam — callers exchange
 * {@link ReportDetailDto} / {@link ReportSummaryDto} records.
 *
 * <p>Must run inside the caller's transaction: it walks lazy associations (lines,
 * travels, and each status change's acting user) while mapping.
 */
@Component
public class ReportDtoMapper {

    private final ReceiptRepository receiptRepository;

    public ReportDtoMapper(ReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    /** The list-row projection: the four grid columns plus the detail-route id. */
    public static ReportSummaryDto toSummary(ExpenseReport r) {
        return new ReportSummaryDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.total());
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
     * with its amount, read-only explanation, id, and any attached receipt.
     */
    private static TravelDto toTravelDto(ExpenseReport r, Travel t,
            Map<Long, ReceiptSummaryView> byLine) {
        List<GeneratedLineView> generated = new ArrayList<>();
        for (GeneratedLineKind kind : GeneratedLineKind.values()) {
            r.generatedLineFor(t, kind).ifPresent(line -> {
                var view = GeneratedLineView.of(kind, line.getExpenseType().getName(),
                        line.gross(), line.getVatRate().getValue(), line.getComment(),
                        line.getId());
                var receipt = byLine.get(line.getId());
                if (receipt != null) {
                    view = view.withReceipt(receipt.getId(), receipt.getFilename(),
                            receipt.getContentType(), receipt.getSizeBytes());
                }
                generated.add(view);
            });
        }
        return new TravelDto(t.getId(), t.getDepartureAt(), t.getReturnAt(),
                t.getDestinations(), t.getPurpose(), t.getCountry(),
                t.isNotEligibleForAllowance(), t.isFreeLunch(), t.isChargeToCustomer(),
                t.getKilometres(), t.isPayMealAllowance(), t.getParkingFees(), generated);
    }

    private static ExpenseLineDto toLineDto(ExpenseLine line,
            ReceiptSummaryView receipt) {
        var type = line.getExpenseType();
        var rate = line.getVatRate();
        var dto = ExpenseLineDto.of(line.getId(), type.getId(), type.getName(),
                rate.getId(), rate.getValue(), line.getAmount(), line.getComment());
        if (receipt == null) {
            return dto;
        }
        return dto.withReceipt(receipt.getId(), receipt.getFilename(),
                receipt.getContentType(), receipt.getSizeBytes());
    }
}

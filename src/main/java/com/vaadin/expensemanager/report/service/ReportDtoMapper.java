package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /** How many distinct destinations a list card names before it says "+N more". */
    private static final int DESTINATIONS_SHOWN = 2;

    private final ReceiptRepository receiptRepository;
    private final TravelCosting travelCosting;

    public ReportDtoMapper(ReceiptRepository receiptRepository,
            TravelCosting travelCosting) {
        this.receiptRepository = receiptRepository;
        this.travelCosting = travelCosting;
    }

    /** The list-row projection: what the list card shows, plus the detail-route id. */
    public static ReportSummaryDto toSummary(ExpenseReport r) {
        return new ReportSummaryDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.total(),
                travelSummary(r), submittedAt(r));
    }

    /**
     * When the report was last handed off for review, or {@code null} if it never
     * was — read off the status history the aggregate already carries, so the list
     * can say how long something has been waiting without a second query.
     *
     * <p>The <em>last</em> transition into SUBMITTED, not the first: a rejected
     * report that was fixed and resubmitted has been waiting since the resubmission.
     */
    private static Instant submittedAt(ExpenseReport r) {
        return r.getStatusHistory().stream()
                .filter(change -> change.getToStatus() == ReportStatus.SUBMITTED)
                .map(StatusChange::getChangedAt)
                .max(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Folds a report's trips into the one line the list card shows, or
     * {@code null} when the report has no trips (which is what makes the card's
     * travel row conditional).
     *
     * <p>A report can hold any number of trips, so this summarises rather than
     * copies: destinations are de-duplicated in trip order and capped, so a
     * many-trip report reads as "Helsinki, Tampere +2 more" instead of an
     * unbounded run of text, and the range spans the earliest departure to the
     * latest return. A trip's {@code country} is appended only when it adds
     * something the destination text does not already say — a domestic trip
     * reads "Helsinki", a foreign one "Copenhagen · Denmark".
     */
    private static ReportSummaryDto.TravelSummary travelSummary(ExpenseReport r) {
        var travels = r.getTravels();
        if (travels.isEmpty()) {
            return null;
        }
        var destinations = travels.stream()
                .map(ReportDtoMapper::describeDestination)
                .distinct()
                .toList();
        var shown = destinations.stream().limit(DESTINATIONS_SHOWN)
                .collect(Collectors.joining(", "));
        var hidden = destinations.size() - Math.min(destinations.size(), DESTINATIONS_SHOWN);
        var destination = hidden == 0 ? shown : shown + " +" + hidden + " more";

        var start = travels.stream().map(Travel::getDepartureAt)
                .min(LocalDateTime::compareTo).orElseThrow().toLocalDate();
        var end = travels.stream().map(Travel::getReturnAt)
                .max(LocalDateTime::compareTo).orElseThrow().toLocalDate();
        return new ReportSummaryDto.TravelSummary(destination, start, end,
                travels.size());
    }

    /**
     * "Helsinki" for a domestic trip, "Copenhagen · Denmark" for a foreign one.
     *
     * <p>The country is appended only when it adds something. {@code destinations}
     * is free text, so it often already names the country — "Helsinki, Denmark"
     * with country Denmark must not render as "Helsinki, Denmark · Denmark" — and
     * a domestic trip's country is the sentinel every Finnish trip carries, which
     * tells the reader nothing.
     */
    private static String describeDestination(Travel travel) {
        var destinations = travel.getDestinations();
        var country = travel.getCountry();
        if (country == null || country.isBlank()
                || TravelDto.DOMESTIC_COUNTRY.equalsIgnoreCase(country)
                || destinations.toLowerCase(Locale.ROOT)
                        .contains(country.toLowerCase(Locale.ROOT))) {
            return destinations;
        }
        return destinations + " · " + country;
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

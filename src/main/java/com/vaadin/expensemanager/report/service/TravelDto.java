package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.vaadin.expensemanager.report.domain.GeneratedLineKind;

/**
 * Immutable working copy of one trip for the detail view (ADR-0003, ADR-0019).
 *
 * <p>Carries the trip <em>inputs</em> the user entered — dates, destinations,
 * purpose, the flags, and the kilometres / pay-meal-allowance / parking-fee
 * inputs — plus the <strong>server-computed</strong> {@link #generatedLines} the
 * trip earns (per-diem, kilometre, meal, parking). The client sends inputs, never
 * money: the amounts come from {@link ExpenseReportService#previewDomesticTravel}
 * (a preview) and are recomputed authoritatively on the report save. Each
 * generated line also carries its persistent id and any attached receipt, so the
 * detail view can list them under the trip and let a receipt be attached to any of
 * them (the amount and comment stay read-only, Phase 4.3).
 *
 * <p>{@link #id} is the reconciliation key (ADR-0019): {@code null} for a
 * not-yet-persisted trip the service will insert, non-null for one it will match
 * and regenerate. This slice is domestic only — {@link #country} is
 * {@code "Finland"}; the foreign country picker is Slice 4.
 *
 * @param id                      persistent travel id, or {@code null} for a new trip
 * @param departureAt             departure date & time (required)
 * @param returnAt                return date & time (required, after departure)
 * @param destinations            free-text destinations (required)
 * @param purpose                 free-text travel purpose (required)
 * @param country                 trip country (domestic → {@code "Finland"})
 * @param notEligibleForAllowance whether the trip earns no daily allowance
 * @param freeLunch               whether a free meal halved the per-diem
 * @param chargeToCustomer        whether the expenses are charged to the customer
 * @param kilometres              kilometres driven (for the kilometre allowance)
 * @param payMealAllowance        whether the trip pays a meal allowance
 * @param parkingFees             parking fees paid (a VAT-bearing expense)
 * @param generatedLines          the server-computed lines this trip earns (never null)
 */
public record TravelDto(Long id, LocalDateTime departureAt, LocalDateTime returnAt,
        String destinations, String purpose, String country,
        boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
        BigDecimal kilometres, boolean payMealAllowance, BigDecimal parkingFees,
        List<GeneratedLineView> generatedLines) {

    /** The country used for a domestic trip until the Slice 4 picker lands. */
    public static final String DOMESTIC_COUNTRY = "Finland";

    public TravelDto {
        generatedLines = generatedLines == null ? List.of() : List.copyOf(generatedLines);
    }

    /**
     * A fresh domestic trip from the dialog's inputs, before the outputs have been
     * computed (the service fills them in via {@link #withGeneratedLines}).
     */
    public static TravelDto domestic(Long id, LocalDateTime departureAt,
            LocalDateTime returnAt, String destinations, String purpose,
            boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
            BigDecimal kilometres, boolean payMealAllowance, BigDecimal parkingFees) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose,
                DOMESTIC_COUNTRY, notEligibleForAllowance, freeLunch, chargeToCustomer,
                kilometres, payMealAllowance, parkingFees, List.of());
    }

    /** This trip with its computed generated lines attached (preview / load path). */
    public TravelDto withGeneratedLines(List<GeneratedLineView> generatedLines) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose, country,
                notEligibleForAllowance, freeLunch, chargeToCustomer, kilometres,
                payMealAllowance, parkingFees, generatedLines);
    }

    /** The generated line of a given kind, if the trip earned one. */
    public Optional<GeneratedLineView> generatedLine(GeneratedLineKind kind) {
        return generatedLines.stream().filter(line -> line.kind() == kind).findFirst();
    }

    /** The amount of a given generated-line kind, or zero if the trip earned none. */
    public BigDecimal amountOf(GeneratedLineKind kind) {
        return generatedLine(kind).map(GeneratedLineView::amount)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    /** Whether this trip has been persisted yet. */
    public boolean isPersisted() {
        return id != null;
    }
}

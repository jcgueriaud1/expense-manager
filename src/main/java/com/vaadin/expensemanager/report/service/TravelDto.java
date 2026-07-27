package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.QuantityOverride;

/**
 * Immutable working copy of one trip for the detail view (ADR-0003, ADR-0019).
 *
 * <p>Carries the trip <em>inputs</em> the user entered — dates, destinations,
 * purpose, the flags, and the kilometres / pay-meal-allowance / parking-fee
 * inputs — plus the <strong>server-computed</strong> {@link #generatedLines} the
 * trip earns (per-diem, kilometre, meal, parking). The client sends inputs, never
 * money: the amounts come from {@link ExpenseReportService#previewTravel}
 * (a preview) and are recomputed authoritatively on the report save. Each
 * generated line also carries its persistent id and any attached receipt, so the
 * detail view can list them under the trip and let a receipt be attached to any of
 * them (the amount and comment stay read-only, Phase 4.3).
 *
 * <p>{@link #quantityOverrides} are trip inputs too (glossary: Quantity Override,
 * ADR-0024): the user's corrected <em>count</em> per generated-line kind, with a
 * mandatory reason. The client sends the count; the service applies it after the
 * calculator has run, so the money in {@link #generatedLines} stays the server's.
 *
 * <p>{@link #id} is the reconciliation key (ADR-0019): {@code null} for a
 * not-yet-persisted trip the service will insert, non-null for one it will match
 * and regenerate. {@link #country} is {@link #DOMESTIC_COUNTRY} for a domestic trip
 * or the chosen destination country for a foreign one (Phase 4.2 picker), which
 * decides whether the per-diem is costed domestically or against the country rate.
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
 * @param quantityOverrides       the trip's Quantity Overrides by kind (never null)
 * @param generatedLines          the server-computed lines this trip earns (never null)
 */
public record TravelDto(Long id, LocalDateTime departureAt, LocalDateTime returnAt,
        String destinations, String purpose, String country,
        boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
        BigDecimal kilometres, boolean payMealAllowance, BigDecimal parkingFees,
        Map<GeneratedLineKind, QuantityOverride> quantityOverrides,
        List<GeneratedLineView> generatedLines) {

    /** The sentinel country a domestic (Finnish) trip is costed against. */
    public static final String DOMESTIC_COUNTRY = "Finland";

    public TravelDto {
        quantityOverrides = quantityOverrides == null ? Map.of()
                : Map.copyOf(quantityOverrides);
        generatedLines = generatedLines == null ? List.of() : List.copyOf(generatedLines);
    }

    /**
     * A fresh trip from the dialog's inputs, before the outputs have been computed
     * (the service fills them in via {@link #withGeneratedLines}). {@code country}
     * is {@link #DOMESTIC_COUNTRY} for a domestic trip or a destination country name
     * for a foreign one (Phase 4.2 picker).
     */
    public static TravelDto of(Long id, LocalDateTime departureAt,
            LocalDateTime returnAt, String destinations, String purpose, String country,
            boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
            BigDecimal kilometres, boolean payMealAllowance, BigDecimal parkingFees) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose, country,
                notEligibleForAllowance, freeLunch, chargeToCustomer, kilometres,
                payMealAllowance, parkingFees, Map.of(), List.of());
    }

    /**
     * A fresh domestic (Finland) trip from the dialog's inputs — {@link #of} with
     * the country fixed to {@link #DOMESTIC_COUNTRY}.
     */
    public static TravelDto domestic(Long id, LocalDateTime departureAt,
            LocalDateTime returnAt, String destinations, String purpose,
            boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
            BigDecimal kilometres, boolean payMealAllowance, BigDecimal parkingFees) {
        return of(id, departureAt, returnAt, destinations, purpose, DOMESTIC_COUNTRY,
                notEligibleForAllowance, freeLunch, chargeToCustomer, kilometres,
                payMealAllowance, parkingFees);
    }

    /** This trip with its computed generated lines attached (preview / load path). */
    public TravelDto withGeneratedLines(List<GeneratedLineView> generatedLines) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose, country,
                notEligibleForAllowance, freeLunch, chargeToCustomer, kilometres,
                payMealAllowance, parkingFees, quantityOverrides, generatedLines);
    }

    /**
     * This trip with its Quantity Overrides replaced (ADR-0024). The generated lines
     * are <em>not</em> recomputed here — the client never computes money, so the
     * caller re-previews through the service to get the effective figures.
     */
    public TravelDto withQuantityOverrides(
            Map<GeneratedLineKind, QuantityOverride> overrides) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose, country,
                notEligibleForAllowance, freeLunch, chargeToCustomer, kilometres,
                payMealAllowance, parkingFees, overrides, generatedLines);
    }

    /** This trip with one kind's Quantity Override set (replacing any prior one). */
    public TravelDto withQuantityOverride(GeneratedLineKind kind,
            QuantityOverride override) {
        var overrides = mutableOverrides();
        overrides.put(kind, override);
        return withQuantityOverrides(overrides);
    }

    /** This trip with one kind's Quantity Override removed ("Reset to calculated"). */
    public TravelDto withoutQuantityOverride(GeneratedLineKind kind) {
        if (!quantityOverrides.containsKey(kind)) {
            return this;
        }
        var overrides = mutableOverrides();
        overrides.remove(kind);
        return withQuantityOverrides(overrides);
    }

    /**
     * A mutable copy of the override map. Built key-first rather than through
     * {@code new EnumMap<>(map)}, whose {@code Map} overload rejects an empty
     * non-{@code EnumMap} source — and this record's map is an immutable copy.
     */
    private Map<GeneratedLineKind, QuantityOverride> mutableOverrides() {
        var copy = new EnumMap<GeneratedLineKind, QuantityOverride>(
                GeneratedLineKind.class);
        copy.putAll(quantityOverrides);
        return copy;
    }

    /**
     * This trip with <strong>every</strong> Quantity Override stripped — the copy a
     * caller previews to see what the trip inputs alone produce, i.e. the calculated
     * baseline (ADR-0024). One call, no second service method.
     */
    public TravelDto withoutQuantityOverrides() {
        return quantityOverrides.isEmpty() ? this : withQuantityOverrides(Map.of());
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

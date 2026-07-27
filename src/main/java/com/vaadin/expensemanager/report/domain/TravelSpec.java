package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A resolved trip instruction for whole-aggregate reconciliation (ADR-0019),
 * the travel counterpart of {@link ExpenseLineSpec}.
 *
 * <p>The service turns each incoming {@code TravelDto} into one of these by
 * <strong>recomputing every trip output server-side</strong> (the client never
 * sends money) via the {@code AllowanceCalculator}, and resolving each generated
 * line's reference data. The result is {@link #generatedLines} — one
 * {@link GeneratedLineSpec} per output the trip earned (per-diem, kilometre, meal,
 * parking); a kind that produced nothing is simply absent. The aggregate then
 * matches on {@link #id} (non-null → update the existing trip, {@code null} →
 * insert), regenerates the linked lines by kind, and orphan-removes any trip (and
 * its lines) absent from the list.
 *
 * <p>{@link #quantityOverrides} rides along as a trip <em>input</em> (ADR-0024): by
 * the time a spec reaches the aggregate the service has already substituted the
 * overridden count into {@link #generatedLines}, so {@code ExpenseReport} reconciles
 * exactly as before and knows nothing about overrides. The trip persists them so the
 * next save re-applies them to the freshly recomputed figures.
 *
 * <p>Keeping the calculator and reference lookups in the service (not the domain)
 * is what lets the aggregate stay free of Spring and repositories while still
 * owning the trip↔line reconciliation invariant (ADR-0006, ADR-0003).
 *
 * @param id                       existing travel id to update, or {@code null} to insert
 * @param departureAt              trip departure date & time (required)
 * @param returnAt                 trip return date & time (required, after departure)
 * @param destinations             free-text destinations (required)
 * @param purpose                  free-text travel purpose (required)
 * @param country                  trip country (domestic → Finland)
 * @param notEligibleForAllowance  whether the trip earns no daily allowance
 * @param freeLunch                whether a free meal halved the per-diem
 * @param chargeToCustomer         whether the expenses are charged to the customer
 * @param kilometres               kilometres driven (for the kilometre allowance)
 * @param payMealAllowance         whether the trip pays a meal allowance
 * @param parkingFees              parking fees paid (a VAT-bearing expense)
 * @param quantityOverrides        the trip's Quantity Overrides by kind (may be empty)
 * @param generatedLines          the read-only lines this trip should own (may be empty)
 */
public record TravelSpec(Long id, LocalDateTime departureAt, LocalDateTime returnAt,
        String destinations, String purpose, String country,
        boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
        BigDecimal kilometres, boolean payMealAllowance, BigDecimal parkingFees,
        Map<GeneratedLineKind, QuantityOverride> quantityOverrides,
        List<GeneratedLineSpec> generatedLines) {

    public TravelSpec {
        quantityOverrides = quantityOverrides == null ? Map.of()
                : Map.copyOf(quantityOverrides);
        generatedLines = generatedLines == null ? List.of() : List.copyOf(generatedLines);
    }

    /** Whether the trip earned any generated line worth persisting. */
    public boolean hasGeneratedLines() {
        return !generatedLines.isEmpty();
    }
}

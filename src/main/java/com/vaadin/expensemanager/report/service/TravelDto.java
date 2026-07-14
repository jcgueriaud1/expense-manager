package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable working copy of one trip for the detail view (ADR-0003, ADR-0019).
 *
 * <p>Carries the trip <em>inputs</em> the user entered — dates, destinations,
 * purpose, the flags, and the kilometres / pay-meal-allowance / parking-fee
 * inputs — plus the <strong>server-computed</strong> {@link #allowances}
 * breakdown (per-diem, kilometre, meal, parking). The client sends inputs, never
 * money. The amounts shown while editing come from
 * {@link ExpenseReportService#previewDomesticTravel} (a preview) and are
 * recomputed authoritatively on the report save; each explanation is what lands
 * in the corresponding generated line's comment for the Phase-5 approval UI.
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
 * @param allowances              the server-computed money breakdown this trip earns
 */
public record TravelDto(Long id, LocalDateTime departureAt, LocalDateTime returnAt,
        String destinations, String purpose, String country,
        boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
        BigDecimal kilometres, boolean payMealAllowance, BigDecimal parkingFees,
        TravelAllowances allowances) {

    /** The country used for a domestic trip until the Slice 4 picker lands. */
    public static final String DOMESTIC_COUNTRY = "Finland";

    /**
     * A fresh domestic trip from the dialog's inputs, before the amounts have been
     * computed (the service fills them in via {@link #withAllowances}).
     */
    public static TravelDto domestic(Long id, LocalDateTime departureAt,
            LocalDateTime returnAt, String destinations, String purpose,
            boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
            BigDecimal kilometres, boolean payMealAllowance, BigDecimal parkingFees) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose,
                DOMESTIC_COUNTRY, notEligibleForAllowance, freeLunch, chargeToCustomer,
                kilometres, payMealAllowance, parkingFees, TravelAllowances.none());
    }

    /** This trip with its computed money breakdown attached (preview / load path). */
    public TravelDto withAllowances(TravelAllowances allowances) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose, country,
                notEligibleForAllowance, freeLunch, chargeToCustomer, kilometres,
                payMealAllowance, parkingFees, allowances);
    }

    /** Whether this trip has been persisted yet. */
    public boolean isPersisted() {
        return id != null;
    }
}

package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.VatRate;

/**
 * A resolved trip instruction for whole-aggregate reconciliation (ADR-0019),
 * the travel counterpart of {@link ExpenseLineSpec}.
 *
 * <p>The service turns each incoming {@code TravelDto} into one of these by
 * <strong>recomputing the per-diem server-side</strong> (the client never sends
 * money) via the {@code AllowanceCalculator}, and resolving the generated line's
 * reference data — the "Travel allowance" {@link ExpenseType} and the 0 %
 * {@link VatRate}. The aggregate then matches on {@link #id} (non-null → update
 * the existing trip, {@code null} → insert), regenerates the linked per-diem
 * line from {@link #perDiemAmount} / {@link #perDiemExplanation}, and
 * orphan-removes any trip (and its line) absent from the list.
 *
 * <p>A {@link #perDiemAmount} of zero means the trip earns no allowance
 * (not-eligible or too short): no line is generated. Keeping the calculator and
 * reference lookups in the service (not the domain) is what lets the aggregate
 * stay free of Spring and repositories while still owning the trip↔line
 * reconciliation invariant (ADR-0006, ADR-0003).
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
 * @param perDiemType              resolved expense type for the generated line (Travel allowance)
 * @param perDiemRate              resolved VAT rate for the generated line (0 %)
 * @param perDiemAmount            server-computed per-diem, EUR scale 2 (zero → no line)
 * @param perDiemExplanation       breakdown written into the generated line's comment
 */
public record TravelSpec(Long id, LocalDateTime departureAt, LocalDateTime returnAt,
        String destinations, String purpose, String country,
        boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
        ExpenseType perDiemType, VatRate perDiemRate, BigDecimal perDiemAmount,
        String perDiemExplanation) {

    /** Whether the trip earned a per-diem worth a generated line. */
    public boolean hasPerDiem() {
        return perDiemAmount != null && perDiemAmount.signum() != 0;
    }
}

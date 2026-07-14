package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable working copy of one trip for the detail view (ADR-0003, ADR-0019).
 *
 * <p>Carries the trip <em>inputs</em> the user entered plus the
 * <strong>server-computed</strong> per-diem ({@link #perDiemAmount},
 * {@link #perDiemExplanation}) — the client sends inputs, never money. The
 * amount shown while editing comes from {@link ExpenseReportService#previewDomesticTravel}
 * (a preview), and is recomputed authoritatively on the report save; the
 * explanation is what lands in the generated line's comment for the Phase-5
 * approval UI.
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
 * @param perDiemAmount           computed per-diem, EUR scale 2 ({@code 0.00} → none)
 * @param perDiemExplanation      breakdown of how the per-diem was reached
 */
public record TravelDto(Long id, LocalDateTime departureAt, LocalDateTime returnAt,
        String destinations, String purpose, String country,
        boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer,
        BigDecimal perDiemAmount, String perDiemExplanation) {

    /** The country used for a domestic trip until the Slice 4 picker lands. */
    public static final String DOMESTIC_COUNTRY = "Finland";

    /**
     * A fresh domestic trip from the dialog's inputs, before the per-diem has been
     * computed (the service fills it in via {@link #withPerDiem}).
     */
    public static TravelDto domestic(Long id, LocalDateTime departureAt,
            LocalDateTime returnAt, String destinations, String purpose,
            boolean notEligibleForAllowance, boolean freeLunch, boolean chargeToCustomer) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose,
                DOMESTIC_COUNTRY, notEligibleForAllowance, freeLunch, chargeToCustomer,
                null, null);
    }

    /** This trip with its computed per-diem attached (preview / load path). */
    public TravelDto withPerDiem(BigDecimal amount, String explanation) {
        return new TravelDto(id, departureAt, returnAt, destinations, purpose, country,
                notEligibleForAllowance, freeLunch, chargeToCustomer, amount, explanation);
    }

    /** Whether this trip has been persisted yet. */
    public boolean isPersisted() {
        return id != null;
    }
}

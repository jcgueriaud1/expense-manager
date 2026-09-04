package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mutable binding model for the trip editor ({@link TravelEditorDialog}).
 *
 * <p>A top-level class, not a dialog inner class (ADR-0022): {@code Binder} binds
 * its {@code DateTimePicker}/{@code TextField}/{@code Checkbox}/{@code
 * BigDecimalField} fields to these getters/setters, and extracting it keeps the
 * dialog focused on wiring. Holds the trip <em>inputs</em> only — including the
 * kilometres / pay-meal / parking-fee inputs; the resulting amounts are
 * server-computed and never bound to a field (the client sends inputs, never money).
 *
 * <p>The eligibility flags are two booleans here and in {@code TravelDto}, but
 * <em>one</em> choice in the form (travel-editor-dialog.md): the {@code Daily
 * allowance} radio group binds to {@link #getDailyAllowance()}, which reads and
 * writes both flags. The domain never sees the enum.
 */
final class TravelFormModel {

    /**
     * The one answer the {@code Daily allowance} radio group takes, and its mapping
     * onto the two domain flags (issue #93: a free lunch only <em>halves</em> a
     * per-diem the trip is eligible for, so the two flags are mutually exclusive
     * and two options could not express the default).
     */
    enum DailyAllowance {
        /** Eligible, no free lunch — the default. {@code false / false}. */
        FULL("Full daily allowance"),
        /** Eligible, free lunch provided. {@code notEligible=false, freeLunch=true}. */
        HALVED("Halved, free lunch provided"),
        /** Not eligible. {@code notEligible=true, freeLunch=false}. */
        NOT_ELIGIBLE("Not eligible");

        private final String label;

        DailyAllowance(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        /** The one option the two flags describe. */
        static DailyAllowance of(boolean notEligibleForAllowance, boolean freeLunch) {
            if (notEligibleForAllowance) {
                return NOT_ELIGIBLE;
            }
            return freeLunch ? HALVED : FULL;
        }

        boolean notEligibleForAllowance() {
            return this == NOT_ELIGIBLE;
        }

        boolean freeLunch() {
            return this == HALVED;
        }
    }

    private LocalDateTime departureAt;
    private LocalDateTime returnAt;
    private String country;
    private String destinations;
    private String purpose;
    private boolean notEligibleForAllowance;
    private boolean freeLunch;
    private boolean chargeToCustomer;
    private BigDecimal kilometres;
    private boolean payMealAllowance;
    private BigDecimal parkingFees;

    LocalDateTime getDepartureAt() {
        return departureAt;
    }

    void setDepartureAt(LocalDateTime departureAt) {
        this.departureAt = departureAt;
    }

    LocalDateTime getReturnAt() {
        return returnAt;
    }

    void setReturnAt(LocalDateTime returnAt) {
        this.returnAt = returnAt;
    }

    String getCountry() {
        return country;
    }

    void setCountry(String country) {
        this.country = country;
    }

    String getDestinations() {
        return destinations;
    }

    void setDestinations(String destinations) {
        this.destinations = destinations;
    }

    String getPurpose() {
        return purpose;
    }

    void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    boolean isNotEligibleForAllowance() {
        return notEligibleForAllowance;
    }

    void setNotEligibleForAllowance(boolean notEligibleForAllowance) {
        this.notEligibleForAllowance = notEligibleForAllowance;
    }

    boolean isFreeLunch() {
        return freeLunch;
    }

    void setFreeLunch(boolean freeLunch) {
        this.freeLunch = freeLunch;
    }

    /** The radio group's view of the two eligibility flags. */
    DailyAllowance getDailyAllowance() {
        return DailyAllowance.of(notEligibleForAllowance, freeLunch);
    }

    /** Writes both eligibility flags from the one option chosen. */
    void setDailyAllowance(DailyAllowance dailyAllowance) {
        var option = dailyAllowance == null ? DailyAllowance.FULL : dailyAllowance;
        this.notEligibleForAllowance = option.notEligibleForAllowance();
        this.freeLunch = option.freeLunch();
    }

    boolean isChargeToCustomer() {
        return chargeToCustomer;
    }

    void setChargeToCustomer(boolean chargeToCustomer) {
        this.chargeToCustomer = chargeToCustomer;
    }

    BigDecimal getKilometres() {
        return kilometres;
    }

    void setKilometres(BigDecimal kilometres) {
        this.kilometres = kilometres;
    }

    boolean isPayMealAllowance() {
        return payMealAllowance;
    }

    void setPayMealAllowance(boolean payMealAllowance) {
        this.payMealAllowance = payMealAllowance;
    }

    BigDecimal getParkingFees() {
        return parkingFees;
    }

    void setParkingFees(BigDecimal parkingFees) {
        this.parkingFees = parkingFees;
    }
}

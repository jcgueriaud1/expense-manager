package com.vaadin.expensemanager.report.ui;

import java.time.LocalDateTime;

/**
 * Mutable binding model for the trip editor ({@link TravelEditorDialog}).
 *
 * <p>A top-level class, not a dialog inner class (ADR-0022): {@code Binder} binds
 * its {@code DateTimePicker}/{@code TextField}/{@code Checkbox} fields to these
 * getters/setters, and extracting it keeps the dialog focused on wiring. Holds
 * the trip <em>inputs</em> only — the per-diem is server-computed and never
 * bound to a field (the client sends inputs, never money).
 */
final class TravelFormModel {

    private LocalDateTime departureAt;
    private LocalDateTime returnAt;
    private String destinations;
    private String purpose;
    private boolean notEligibleForAllowance;
    private boolean freeLunch;
    private boolean chargeToCustomer;

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

    boolean isChargeToCustomer() {
        return chargeToCustomer;
    }

    void setChargeToCustomer(boolean chargeToCustomer) {
        this.chargeToCustomer = chargeToCustomer;
    }
}

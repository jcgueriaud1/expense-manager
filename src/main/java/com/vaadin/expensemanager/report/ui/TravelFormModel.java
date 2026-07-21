package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Mutable binding model for the trip editor ({@link TravelEditorDialog}).
 *
 * <p>A top-level class, not a dialog inner class (ADR-0022): {@code Binder} binds
 * its {@code DatePicker}/{@code TimePicker}/{@code TextField}/{@code Checkbox}/{@code
 * BigDecimalField} fields to these getters/setters, and extracting it keeps the
 * dialog focused on wiring. Holds the trip <em>inputs</em> only — including the
 * kilometres / pay-meal / parking-fee inputs; the resulting amounts are
 * server-computed and never bound to a field (the client sends inputs, never money).
 *
 * <p>Departure and return are each collected as a <strong>required date</strong>
 * plus an <strong>optional time</strong> (issue #94): only the date is mandatory,
 * and an empty time defaults to midnight ({@code 00:00}). The composed
 * {@link #getDepartureAt()} / {@link #getReturnAt()} are what the trip DTO is built
 * from, so the midnight default lives here — the single place the two halves become
 * one {@link LocalDateTime}.
 */
final class TravelFormModel {

    private LocalDate departureDate;
    private LocalTime departureTime;
    private LocalDate returnDate;
    private LocalTime returnTime;
    private String country;
    private String destinations;
    private String purpose;
    private boolean notEligibleForAllowance;
    private boolean freeLunch;
    private boolean chargeToCustomer;
    private BigDecimal kilometres;
    private boolean payMealAllowance;
    private BigDecimal parkingFees;

    LocalDate getDepartureDate() {
        return departureDate;
    }

    void setDepartureDate(LocalDate departureDate) {
        this.departureDate = departureDate;
    }

    LocalTime getDepartureTime() {
        return departureTime;
    }

    void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    LocalDate getReturnDate() {
        return returnDate;
    }

    void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    LocalTime getReturnTime() {
        return returnTime;
    }

    void setReturnTime(LocalTime returnTime) {
        this.returnTime = returnTime;
    }

    /** Departure date & time, midnight if no time was entered (issue #94). */
    LocalDateTime getDepartureAt() {
        return combine(departureDate, departureTime);
    }

    /** Seeds the split date/time from a persisted trip's departure (edit path). */
    void setDepartureAt(LocalDateTime departureAt) {
        this.departureDate = departureAt == null ? null : departureAt.toLocalDate();
        this.departureTime = departureAt == null ? null : departureAt.toLocalTime();
    }

    /** Return date & time, midnight if no time was entered (issue #94). */
    LocalDateTime getReturnAt() {
        return combine(returnDate, returnTime);
    }

    /** Seeds the split date/time from a persisted trip's return (edit path). */
    void setReturnAt(LocalDateTime returnAt) {
        this.returnDate = returnAt == null ? null : returnAt.toLocalDate();
        this.returnTime = returnAt == null ? null : returnAt.toLocalTime();
    }

    /** A date + optional time as a {@link LocalDateTime}; empty time → midnight. */
    private static LocalDateTime combine(LocalDate date, LocalTime time) {
        return date == null ? null
                : LocalDateTime.of(date, time == null ? LocalTime.MIDNIGHT : time);
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

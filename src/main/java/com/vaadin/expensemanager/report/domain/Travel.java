package com.vaadin.expensemanager.report.domain;

import java.time.LocalDateTime;

import com.vaadin.expensemanager.base.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A trip on a report (glossary: Travel Calculator) — part of the
 * {@link ExpenseReport} aggregate, never a root of its own (ADR-0006).
 *
 * <p>Holds the <em>trip inputs</em> the user entered (departure/return date &
 * time, destinations, purpose, country, and the eligibility/free-meal/charge
 * flags). It carries <strong>no money</strong>: the per-diem it earns lives on
 * the generated {@link ExpenseLine} linked back to it via {@code travel_id}
 * (Phase 4.2/4.3), whose amount the aggregate regenerates from these inputs on
 * every save. Editing the trip regenerates that line; deleting the trip
 * orphan-removes it.
 *
 * <p>This slice is <strong>domestic only</strong> — {@link #country} is set to
 * Finland when the domestic dialog creates a trip; the foreign country picker is
 * Slice 4. Created and mutated only through the aggregate
 * ({@link ExpenseReport#reconcile}); the constructor and {@link #update} seam are
 * package-visible so the invariants stay under the aggregate's control.
 */
@Entity
@Table(name = "travel")
public class Travel extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "return_at", nullable = false)
    private LocalDateTime returnAt;

    @Column(name = "destinations", nullable = false)
    private String destinations;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "not_eligible_for_allowance", nullable = false)
    private boolean notEligibleForAllowance;

    @Column(name = "free_lunch", nullable = false)
    private boolean freeLunch;

    @Column(name = "charge_to_customer", nullable = false)
    private boolean chargeToCustomer;

    /** JPA constructor. */
    protected Travel() {
    }

    Travel(TravelSpec spec) {
        apply(spec);
    }

    /** Applies edited trip inputs to an existing travel (aggregate reconciliation seam). */
    void update(TravelSpec spec) {
        apply(spec);
    }

    private void apply(TravelSpec spec) {
        this.departureAt = requireNonNull(spec.departureAt(), "Departure date & time");
        this.returnAt = requireNonNull(spec.returnAt(), "Return date & time");
        if (!returnAt.isAfter(departureAt)) {
            throw new IllegalArgumentException("Return must be after the departure");
        }
        this.destinations = requireText(spec.destinations(), "Destinations");
        this.purpose = requireText(spec.purpose(), "Travel purpose");
        this.country = requireText(spec.country(), "Country");
        this.notEligibleForAllowance = spec.notEligibleForAllowance();
        this.freeLunch = spec.freeLunch();
        this.chargeToCustomer = spec.chargeToCustomer();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDepartureAt() {
        return departureAt;
    }

    public LocalDateTime getReturnAt() {
        return returnAt;
    }

    public String getDestinations() {
        return destinations;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getCountry() {
        return country;
    }

    public boolean isNotEligibleForAllowance() {
        return notEligibleForAllowance;
    }

    public boolean isFreeLunch() {
        return freeLunch;
    }

    public boolean isChargeToCustomer() {
        return chargeToCustomer;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}

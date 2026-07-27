package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.vaadin.expensemanager.base.AuditedEntity;
import com.vaadin.expensemanager.base.DomainRuleException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

/**
 * A trip on a report (glossary: Travel Calculator) — part of the
 * {@link ExpenseReport} aggregate, never a root of its own (ADR-0006).
 *
 * <p>Holds the <em>trip inputs</em> the user entered (departure/return date &
 * time, destinations, purpose, country, the eligibility/free-meal/charge flags,
 * and the kilometres driven / pay-meal-allowance / parking-fee inputs). It
 * carries <strong>no money</strong>: the per-diem, kilometre, meal, and parking
 * outputs it earns live on generated {@link ExpenseLine}s linked back to it via
 * {@code travel_id} (Phase 4.2/4.3), whose amounts the aggregate regenerates from
 * these inputs on every save. Editing the trip regenerates those lines; deleting
 * the trip orphan-removes them.
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

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

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

    /** Kilometres driven, for the kilometre allowance (0 → none). Scale 2. */
    @Column(name = "kilometres", nullable = false, precision = 19, scale = 2)
    private BigDecimal kilometres = ZERO;

    /** Whether the trip pays a meal allowance (ateriakorvaus). */
    @Column(name = "pay_meal_allowance", nullable = false)
    private boolean payMealAllowance;

    /** Parking fees paid, a VAT-bearing expense (0 → none). Scale 2 money. */
    @Column(name = "parking_fees", nullable = false, precision = 19, scale = 2)
    private BigDecimal parkingFees = ZERO;

    /**
     * The trip's {@linkplain QuantityOverride Quantity Overrides}, at most one per
     * generated-line kind (ADR-0024). An override is a trip <em>input</em> like
     * {@link #kilometres} — it says "claim 2 full days, not the 3 you calculated" —
     * so it lives here, and the generated lines stay pure derivations the aggregate
     * recomputes on every save. Keyed by kind, so the overridable-kind list is data
     * rather than schema; the rows live in {@code travel_override} and cascade with
     * the trip.
     */
    @ElementCollection
    @CollectionTable(name = "travel_override",
            joinColumns = @JoinColumn(name = "travel_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "generated_kind", length = 20)
    private Map<GeneratedLineKind, QuantityOverride> quantityOverrides =
            new EnumMap<>(GeneratedLineKind.class);

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
            throw new DomainRuleException("Return must be after the departure");
        }
        this.destinations = requireText(spec.destinations(), "Destinations");
        this.purpose = requireText(spec.purpose(), "Travel purpose");
        this.country = requireText(spec.country(), "Country");
        this.notEligibleForAllowance = spec.notEligibleForAllowance();
        this.freeLunch = spec.freeLunch();
        this.chargeToCustomer = spec.chargeToCustomer();
        this.kilometres = normalizeAmount(spec.kilometres());
        this.payMealAllowance = spec.payMealAllowance();
        this.parkingFees = normalizeAmount(spec.parkingFees());
        applyOverrides(spec.quantityOverrides());
    }

    /**
     * Replaces the trip's Quantity Overrides with the incoming set, rejecting any
     * that does not apply to its kind (ADR-0024). The value object has already
     * validated the count and reason; the kind-specific rules — which kinds are
     * overridable at all, and the partial-day cap — are enforced <em>here</em>,
     * because the map key is the trip's knowledge, not the override's. An override
     * for a non-overridable kind is <strong>rejected</strong>, never silently
     * dropped, so a client that sends one learns why.
     *
     * @throws DomainRuleException if an override does not apply to its kind
     */
    private void applyOverrides(Map<GeneratedLineKind, QuantityOverride> incoming) {
        quantityOverrides.clear();
        if (incoming == null) {
            return;
        }
        incoming.forEach((kind, override) -> {
            if (override == null) {
                throw new DomainRuleException("An override needs a count and a reason.");
            }
            override.assertValidFor(kind);
            quantityOverrides.put(kind, override);
        });
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

    public BigDecimal getKilometres() {
        return kilometres;
    }

    public boolean isPayMealAllowance() {
        return payMealAllowance;
    }

    public BigDecimal getParkingFees() {
        return parkingFees;
    }

    /**
     * Unmodifiable view of the trip's Quantity Overrides by kind (ADR-0024); empty
     * when the calculated figures were left alone.
     */
    public Map<GeneratedLineKind, QuantityOverride> getQuantityOverrides() {
        return Collections.unmodifiableMap(quantityOverrides);
    }

    /** A trip input amount at money scale, defaulting {@code null} to zero. */
    private static BigDecimal normalizeAmount(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new DomainRuleException(field + " is required");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleException(field + " is required");
        }
        return value.strip();
    }
}

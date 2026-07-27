package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.vaadin.expensemanager.base.DomainRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A user-entered replacement for a travel-generated line's <strong>quantity</strong>
 * — the count — carrying a mandatory reason (glossary: Quantity Override,
 * ADR-0024).
 *
 * <p><strong>The unit price is the law; the quantity is the judgement call.</strong>
 * This overrides the count only: the per-day / per-meal rate stays statutory,
 * admin-configured and server-computed, so the "client never sends money" contract
 * of {@link GeneratedLineSpec} holds verbatim — the client sends a count.
 *
 * <p>The pair is <strong>indivisible</strong>: a quantity without a reason is
 * invalid, which is why both live in one value object validated in its own
 * constructor (ADR-0006 — the invariant is domain, not service, and violations
 * raise {@link DomainRuleException} like every other trip rule). A count is a
 * count of discrete things, so it must be a whole number at money scale; the
 * kind-specific rules (which kinds may be overridden at all, and the
 * partial-day cap of one) belong to {@link Travel}, which alone knows the map key
 * — see {@link #assertValidFor}.
 *
 * <p>The floor is {@code 1} in this slice (issue #131). Issue #132 drops it to
 * {@code 0}, which suppresses the line, together with the receipt-destruction
 * warning that behaviour requires.
 */
@Embeddable
public class QuantityOverride {

    /**
     * The smallest count an override may claim. {@code 1} here (issue #131) —
     * issue #132 lowers it to {@code 0} so an override can suppress its line.
     */
    private static final BigDecimal MINIMUM = BigDecimal.ONE;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 2)
    private BigDecimal quantity;

    @Column(name = "reason", nullable = false)
    private String reason;

    /** JPA constructor. */
    protected QuantityOverride() {
    }

    /**
     * @param quantity the claimed count — a whole number, at least {@link #MINIMUM}
     * @param reason   why the calculated count does not fit the trip (required)
     * @throws DomainRuleException if the count is missing, fractional or below the
     *                             floor, or the reason is blank
     */
    public QuantityOverride(BigDecimal quantity, String reason) {
        this.quantity = requireWholeCount(quantity);
        this.reason = requireReason(reason);
    }

    /**
     * An override for one generated-line kind, validated against that kind's rules
     * as well as its own — the factory the UI uses, so the dialog surfaces exactly
     * the message the save would (ADR-0020). {@link Travel} re-checks the same rules
     * on save via {@link #assertValidFor}.
     *
     * @throws DomainRuleException if the kind is not overridable, the count breaks
     *                             that kind's cap, or the pair itself is invalid
     */
    public static QuantityOverride of(GeneratedLineKind kind, BigDecimal quantity,
            String reason) {
        var override = new QuantityOverride(quantity, reason);
        override.assertValidFor(kind);
        return override;
    }

    /**
     * Enforces the rules that depend on <em>which</em> line is being overridden:
     * only the per-diem and meal kinds may be overridden at all (the kilometre
     * distance and the parking fee are trip inputs with a single home, ADR-0024),
     * and {@code PER_DIEM_PARTIAL} is capped at {@code 1} because a trip's duration
     * yields at most one leftover day by construction — "2 partial days" is
     * arithmetically incoherent, not merely generous.
     *
     * @throws DomainRuleException if the override does not apply to {@code kind}
     */
    public void assertValidFor(GeneratedLineKind kind) {
        if (kind == null || !kind.isOverridable()) {
            throw new DomainRuleException((kind == null ? "That line" : kind.label())
                    + " cannot have its count overridden — change it on the trip.");
        }
        if (kind == GeneratedLineKind.PER_DIEM_PARTIAL
                && quantity.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainRuleException(
                    "A trip earns at most one partial day, so the partial-day count "
                            + "cannot be more than 1.");
        }
    }

    /** The claimed count, at money scale like every other quantity (ADR-0023). */
    public BigDecimal quantity() {
        return quantity;
    }

    /** Why the calculated count does not fit the trip — non-blank, trimmed. */
    public String reason() {
        return reason;
    }

    private static BigDecimal requireWholeCount(BigDecimal value) {
        if (value == null) {
            throw new DomainRuleException("A count is required.");
        }
        if (value.stripTrailingZeros().scale() > 0) {
            throw new DomainRuleException("The count must be a whole number.");
        }
        if (value.compareTo(MINIMUM) < 0) {
            throw new DomainRuleException(
                    "The count cannot be less than " + MINIMUM.toPlainString() + ".");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String requireReason(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleException("A reason for the override is required.");
        }
        return value.strip();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuantityOverride that)) {
            return false;
        }
        return quantity.compareTo(that.quantity) == 0
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quantity.stripTrailingZeros(), reason);
    }

    @Override
    public String toString() {
        return "QuantityOverride[" + quantity.toPlainString() + ", " + reason + "]";
    }
}

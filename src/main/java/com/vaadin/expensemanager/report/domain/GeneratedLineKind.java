package com.vaadin.expensemanager.report.domain;

/**
 * The kind of a generated (travel-owned, read-only) {@link ExpenseLine} — the
 * first-class marker a per-diem line lacked in Slice 2 (F-034). A single
 * {@link Travel} generates up to one line of each kind (Phase 4.3): the two
 * per-diem lines, the kilometre allowance, the meal allowance, and the parking
 * expense.
 *
 * <p>The kind is what routes a generated line in the totals (ADR-0010): the
 * {@linkplain #isTaxFreeAllowance() tax-free allowances} get their own subtotal
 * rows — the two {@linkplain #isPerDiem() per-diem kinds} sharing one — while
 * {@link #PARKING} — being VAT-bearing — flows into Net/VAT like a normal expense.
 * The declaration order is also the order the generated lines are grouped after the
 * manual lines on the report.
 */
public enum GeneratedLineKind {

    /**
     * The per-diem days valued at the <strong>full</strong>-day rate — tax-free, 0 %
     * VAT. {@code quantity = full days}, {@code unit price = full-day rate}
     * (ADR-0023). A foreign trip's per-diem is also this kind: every foreign day
     * counts once at the flat country rate.
     */
    PER_DIEM_FULL,

    /**
     * The domestic per-diem's leftover valued at the <strong>partial</strong>-day
     * rate — tax-free, 0 % VAT. {@code quantity = partial days}, {@code unit price =
     * partial-day rate}; generated only when the trip earns a partial day (ADR-0023).
     */
    PER_DIEM_PARTIAL,

    /** The kilometre compensation (km × €/km rate) — tax-free, 0 % VAT. */
    KILOMETRE,

    /** The meal allowance ({@code ateriakorvaus}) — tax-free, 0 % VAT. */
    MEAL,

    /** The parking fee, a VAT-bearing pass-through expense (the parking type's rate). */
    PARKING;

    /**
     * Whether this kind is a tax-free allowance broken out into its own totals
     * subtotal row, as opposed to a VAT-bearing expense that counts in Net/VAT.
     * Only {@link #PARKING} is VAT-bearing.
     */
    public boolean isTaxFreeAllowance() {
        return this != PARKING;
    }

    /**
     * Whether this kind is one of the two per-diem lines (issue #124). A trip's
     * per-diem is split per rate — full days and the partial leftover — so both kinds
     * group into the single per-diem subtotal.
     */
    public boolean isPerDiem() {
        return this == PER_DIEM_FULL || this == PER_DIEM_PARTIAL;
    }

    /**
     * Whether this kind accepts a {@link QuantityOverride} (ADR-0024). The test is
     * not <em>"is it calculated?"</em> — all five are — but <em>"can the trip inputs
     * already express the answer?"</em>: {@link #KILOMETRE}'s quantity <strong>is</strong>
     * {@code Travel.kilometres} and {@link #PARKING}'s amount <strong>is</strong>
     * {@code Travel.parkingFees}, so those keep a single home on the trip and are
     * edited there. The per-diem and meal kinds are reachable only through statutory
     * rules and a checkbox, so they get an override.
     */
    public boolean isOverridable() {
        return this == PER_DIEM_FULL || this == PER_DIEM_PARTIAL || this == MEAL;
    }

    /**
     * The human name of this kind, shared by the report row and the persisted line
     * comment an override composes (ADR-0024) — one spelling, so the screen and the
     * database tell the same story. The two per-diem kinds name the day they price
     * (issue #124).
     */
    public String label() {
        return switch (this) {
            case PER_DIEM_FULL -> "Per diem allowance (full day)";
            case PER_DIEM_PARTIAL -> "Per diem allowance (partial day)";
            case KILOMETRE -> "Kilometre allowance";
            case MEAL -> "Meal allowance";
            case PARKING -> "Parking";
        };
    }

    /**
     * The noun an override's count is measured in, singular or plural to match
     * {@code count} — "3 days", "1 meal". Only the {@linkplain #isOverridable()
     * overridable} kinds are ever asked; the rest fall back to a neutral "×".
     */
    public String countNoun(long count) {
        String singular = isPerDiem() ? "day" : this == MEAL ? "meal" : "×";
        return count == 1 ? singular : singular + "s";
    }
}

package com.vaadin.expensemanager.report.domain;

/**
 * The kind of a generated (travel-owned, read-only) {@link ExpenseLine} — the
 * first-class marker a per-diem line lacked in Slice 2 (F-034). A single
 * {@link Travel} generates up to one line of each kind (Phase 4.3): the domestic
 * per-diem, the kilometre allowance, the meal allowance, and the parking expense.
 *
 * <p>The kind is what routes a generated line in the totals (ADR-0010): the three
 * {@linkplain #isTaxFreeAllowance() tax-free allowances} each get their own
 * subtotal row, while {@link #PARKING} — being VAT-bearing — flows into Net/VAT
 * like a normal expense. The declaration order is also the order the generated
 * lines are grouped after the manual lines on the report.
 */
public enum GeneratedLineKind {

    /** The domestic per-diem (Verohallinto daily allowance) — tax-free, 0 % VAT. */
    PER_DIEM,

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
}

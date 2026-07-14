package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;

import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.VatRate;

/**
 * One resolved generated line a {@link Travel} should own, carried on its
 * {@link TravelSpec} (Phase 4.3, ADR-0019). The service builds these by
 * <strong>recomputing each amount server-side</strong> via the
 * {@code AllowanceCalculator} (the client never sends money) and resolving the
 * line's reference data (its {@link ExpenseType} and {@link VatRate}); the
 * aggregate then reconciles a travel's generated lines against this set, matching
 * an existing line by its {@link #kind}.
 *
 * <p>A spec is only present for a kind that produced something — a trip that
 * earned no per-diem, drove no kilometres, paid no meal allowance, or had no
 * parking fee simply omits that kind, so any prior line of it is removed. The
 * {@link #amount} is therefore always non-zero (the aggregate/{@link ExpenseLine}
 * reject a zero amount).
 *
 * @param kind        which generated line this is (routes it in the totals)
 * @param expenseType resolved expense type the line is filed under
 * @param vatRate     resolved VAT rate (0 % for the allowances, the parking rate for parking)
 * @param amount      server-computed amount, EUR scale 2 (non-zero)
 * @param comment     the calculator's explanation, written into the line's comment
 */
public record GeneratedLineSpec(GeneratedLineKind kind, ExpenseType expenseType,
        VatRate vatRate, BigDecimal amount, String comment) {
}

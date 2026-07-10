/**
 * Admin-editable reference data the expense lines are filed against (ADR-0018):
 * VAT rates and expense types.
 *
 * <p>Feature package (package-by-feature, ADR-0002). Holds the two config
 * aggregates ({@link com.vaadin.expensemanager.reference.VatRate},
 * {@link com.vaadin.expensemanager.reference.ExpenseType}), their repositories,
 * the transaction- and authorization-owning
 * {@link com.vaadin.expensemanager.reference.ReferenceDataService}, the immutable
 * DTO records the UI consumes (ADR-0003), and the ADMIN-only settings screen in
 * {@code ui}.
 *
 * <p>Both tables are Flyway-seeded (V3) with the Finnish (Verohallinto) 2026
 * figures and edited at runtime through the settings screen. History is
 * preserved by an {@code active} flag, never by deletion (ADR-0018).
 */
package com.vaadin.expensemanager.reference;

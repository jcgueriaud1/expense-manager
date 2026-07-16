/**
 * ADMIN-only settings UI for reference data (ADR-0018, ADR-0008): two screens —
 * {@link com.vaadin.expensemanager.reference.ui.VatRateView} for VAT rates and
 * {@link com.vaadin.expensemanager.reference.ui.ExpenseTypeView} for expense
 * types — sharing the heading + grid + row-action shape through the abstract
 * {@link com.vaadin.expensemanager.reference.ui.ReferenceConfigView} base, and
 * the editor dialog through the shared
 * {@link com.vaadin.expensemanager.base.ui.EditorDialog}. Only the
 * reference-specific percent formatting stays in
 * {@link com.vaadin.expensemanager.reference.ui.ReferenceViewSupport}.
 */
package com.vaadin.expensemanager.reference.ui;

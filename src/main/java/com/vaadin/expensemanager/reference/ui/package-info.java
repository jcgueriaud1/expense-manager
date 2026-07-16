/**
 * ADMIN-only settings UI for reference data (ADR-0018, ADR-0008): two screens —
 * {@link com.vaadin.expensemanager.reference.ui.VatRateView} for VAT rates and
 * {@link com.vaadin.expensemanager.reference.ui.ExpenseTypeView} for expense
 * types — each expressed as a
 * {@link com.vaadin.expensemanager.base.ui.ReferenceConfigEditor.Config} of the
 * shared, generic editor module (grid + add/edit dialog + reorder +
 * active-toggle). Only the reference-specific percent formatting stays here, in
 * {@link com.vaadin.expensemanager.reference.ui.ReferenceViewSupport}.
 */
package com.vaadin.expensemanager.reference.ui;

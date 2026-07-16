package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

/**
 * Reference-data display formatting shared by the two reference screens
 * ({@link VatRateView}, {@link ExpenseTypeView}).
 *
 * <p>The editor/dialog and action-button machinery these screens once carried
 * here now lives in the shared, generic
 * {@link com.vaadin.expensemanager.base.ui.ReferenceConfigEditor} (grid +
 * add/edit dialog + reorder + active-toggle) and
 * {@link com.vaadin.expensemanager.base.ui.AdminEditor} (dialog + icon buttons);
 * only the percent formatting remains reference-specific.
 */
final class ReferenceViewSupport {

    private ReferenceViewSupport() {
    }

    /** A percent with trailing zeros trimmed, e.g. {@code 25.5 %}, {@code 0 %}. */
    static String formatPercent(BigDecimal value) {
        var normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString() + " %";
    }
}

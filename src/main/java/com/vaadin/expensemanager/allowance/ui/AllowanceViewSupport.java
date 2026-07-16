package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money/rate display formatting for the allowance-rate settings screen
 * ({@link AllowanceRatesView}).
 *
 * <p>The editor/dialog machinery this class once carried — the always-enabled
 * Save + top-of-form error summary and the icon buttons — now lives in the
 * shared {@link com.vaadin.expensemanager.base.ui.AdminEditor}, and the foreign
 * per-diem grid is a {@link com.vaadin.expensemanager.base.ui.ReferenceConfigEditor}
 * config; only the allowance-specific formatting remains here.
 */
final class AllowanceViewSupport {

    private AllowanceViewSupport() {
    }

    /** EUR at scale 2, e.g. {@code €54.00}. */
    static String formatMoney(BigDecimal value) {
        return "€" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** A per-km rate, e.g. {@code €0.590 / km}. */
    static String formatRate(BigDecimal value) {
        return "€" + value.setScale(3, RoundingMode.HALF_UP).toPlainString() + " / km";
    }
}

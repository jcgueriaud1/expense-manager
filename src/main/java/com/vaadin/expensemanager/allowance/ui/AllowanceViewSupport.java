package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;

import com.vaadin.expensemanager.base.ui.AdminEditor;
import com.vaadin.expensemanager.base.ui.AdminEditor.DecimalHolder;
import com.vaadin.flow.data.binder.Binder;

/**
 * Money/rate display formatting and the single-value rate editor for the
 * allowance-rate settings screen ({@link AllowanceRatesView}).
 *
 * <p>The generic editor/dialog machinery — the always-enabled Save + top-of-form
 * error summary, the icon buttons, and the required non-negative decimal field —
 * lives in the shared {@link AdminEditor}, and the foreign per-diem grid is a
 * {@link com.vaadin.expensemanager.base.ui.ReferenceConfigEditor} config. What
 * stays here is allowance-specific: the €/rate formatting and the one-field
 * money/rate editor the kilometre and meal panels reuse.
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

    /**
     * Opens an editor for a single required, non-negative decimal value —
     * collapses the near-identical single-field money/rate editors (kilometre,
     * meal) into one call. {@code persist} receives the entered value on Save.
     */
    static void openDecimalEditor(String title, String fieldLabel, String noun,
            BigDecimal current, Consumer<BigDecimal> persist) {
        var model = new DecimalHolder();
        var binder = new Binder<DecimalHolder>();
        var field = AdminEditor.requiredDecimalField(fieldLabel, noun, binder,
                DecimalHolder::getValue, DecimalHolder::setValue);
        model.setValue(current);
        binder.readBean(model);
        AdminEditor.openEditor(title, field, binder, model, () -> persist.accept(model.getValue()));
    }
}

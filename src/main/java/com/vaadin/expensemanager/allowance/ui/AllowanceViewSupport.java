package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;

import com.vaadin.expensemanager.base.ui.EditorDialog;
import com.vaadin.expensemanager.base.ui.FormErrorHandler;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.data.binder.Binder;

/**
 * Money/rate display formatting and the single-value rate editor for the
 * allowance-rate settings screen ({@link AllowanceRatesView}).
 *
 * <p>The shared editor dialog (always-enabled Save + top-of-form error summary,
 * ADR-0020) lives in {@link EditorDialog}. What stays here is allowance-specific:
 * the €/rate formatting, and the one-field money/rate editor the kilometre, meal,
 * and foreign per-diem panels reuse.
 */
final class AllowanceViewSupport {

    private AllowanceViewSupport() {
    }

    /** EUR at scale 2, e.g. {@code €54.00}. */
    static String formatMoney(BigDecimal value) {
        return "€" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** A per-km rate, e.g. {@code €0.550 / km}. */
    static String formatRate(BigDecimal value) {
        return "€" + value.setScale(3, RoundingMode.HALF_UP).toPlainString() + " / km";
    }

    /**
     * Opens an {@link EditorDialog} for a single required, non-negative decimal
     * value — the kilometre, meal, and foreign per-diem editors are all this one
     * field. {@code persist} receives the entered value on a valid Save.
     */
    static void openDecimalEditor(String title, String fieldLabel, String noun,
            BigDecimal current, Consumer<BigDecimal> persist,
            FormErrorHandler errorHandler) {
        var model = new DecimalHolder();
        var field = new BigDecimalField(fieldLabel);
        var binder = new Binder<DecimalHolder>();
        binder.forField(field)
                .asRequired(noun + " is required")
                .withValidator(value -> value.signum() >= 0, noun + " must be zero or positive")
                .bind(DecimalHolder::getValue, DecimalHolder::setValue);
        model.setValue(current);
        binder.readBean(model);

        new EditorDialog<>(title, field, binder, model, errorHandler)
                .onSave(() -> persist.accept(model.getValue()))
                .open();
    }

    /** Single-value {@link BigDecimal} binding model (Binder needs a bean with a setter). */
    private static final class DecimalHolder {
        private BigDecimal value;

        BigDecimal getValue() {
            return value;
        }

        void setValue(BigDecimal value) {
            this.value = value;
        }
    }
}

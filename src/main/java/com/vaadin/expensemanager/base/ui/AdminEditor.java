package com.vaadin.expensemanager.base.ui;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.function.ValueProvider;

/**
 * Cross-cutting editor machinery shared by every admin CRUD-config screen
 * (reference data, allowance rates, …). Promoted to {@code base.ui} so any
 * feature package can reach it — before this, the always-enabled-Save +
 * top-of-form error-summary scaffold was hand-copied per package (F-045) and the
 * two reference/allowance copies of {@code openEditor}/{@code iconButton} had
 * diverged into verbatim duplicates.
 *
 * <p>Centralises the rules an editor must honour app-wide:
 * <ul>
 *   <li>the dialog's <strong>always-enabled Save + top-of-form error summary</strong>
 *       (never a disabled submit; ADR-0020) — {@link #openEditor};</li>
 *   <li>accessible, theme-agnostic action buttons — aria-labelled tertiary icon
 *       buttons — {@link #iconButton}; and</li>
 *   <li>the recurring required, non-negative decimal field so money/rate editors
 *       stop re-declaring the same field + validators — {@link #requiredDecimalField}
 *       / {@link #openDecimalEditor}.</li>
 * </ul>
 */
public final class AdminEditor {

    private AdminEditor() {
    }

    /**
     * Opens a modal editor with an always-enabled Save and a top-of-form error
     * summary (ADR-0020). Save validates through the binder; on failure the
     * summary (a {@code role="alert"} region) lists the messages, on success
     * {@code persist} runs and the dialog closes. {@code persist} may throw
     * {@link IllegalArgumentException} (a service-side guard) — its message shows
     * in the same summary and the dialog stays open.
     */
    public static <T> void openEditor(String title, Component form, Binder<T> binder,
            T model, Runnable persist) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(title);

        var errorSummary = new Div();
        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.getStyle().set("color", "var(--aura-red-text)");

        var save = new Button("Save", event -> {
            errorSummary.removeAll();
            errorSummary.setVisible(false);
            if (binder.writeBeanIfValid(model)) {
                try {
                    persist.run();
                    dialog.close();
                } catch (IllegalArgumentException ex) {
                    showErrors(errorSummary, List.of(ex.getMessage()));
                }
            } else {
                showErrors(errorSummary, binder.validate().getValidationErrors()
                        .stream().map(ValidationResult::getErrorMessage).toList());
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> dialog.close());

        var content = new VerticalLayout(errorSummary, form);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private static void showErrors(Div summary, List<String> messages) {
        summary.removeAll();
        if (messages.isEmpty()) {
            summary.setVisible(false);
            return;
        }
        var heading = new Span("Please fix the following:");
        heading.getStyle().setFontWeight("600");
        var list = new UnorderedList();
        messages.forEach(message -> list.add(new ListItem(message)));
        summary.add(heading, list);
        summary.setVisible(true);
    }

    /** An accessible, theme-agnostic (Aura/Lumo) tertiary icon button. */
    public static Button iconButton(VaadinIcon icon, String ariaLabel, Runnable action) {
        var button = new Button(new Icon(icon), event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel(ariaLabel);
        return button;
    }

    /**
     * A required {@link BigDecimalField} that also rejects negatives, bound to
     * {@code binder}. The two messages are given separately because a few forms
     * word the "required" and "non-negative" complaints with different nouns
     * (e.g. "Full-day amount is required" vs "Amount must be zero or positive").
     */
    public static <F> BigDecimalField requiredDecimalField(String label, String requiredMessage,
            String nonNegativeMessage, Binder<F> binder,
            ValueProvider<F, BigDecimal> getter, Setter<F, BigDecimal> setter) {
        var field = new BigDecimalField(label);
        field.setRequiredIndicatorVisible(true);
        binder.forField(field)
                .asRequired(requiredMessage)
                .withValidator(value -> value.signum() >= 0, nonNegativeMessage)
                .bind(getter, setter);
        return field;
    }

    /**
     * Convenience {@link #requiredDecimalField(String, String, String, Binder,
     * ValueProvider, Setter)} for the common case where both messages share one
     * noun: {@code "<noun> is required"} and {@code "<noun> must be zero or positive"}.
     */
    public static <F> BigDecimalField requiredDecimalField(String label, String noun,
            Binder<F> binder, ValueProvider<F, BigDecimal> getter, Setter<F, BigDecimal> setter) {
        return requiredDecimalField(label, noun + " is required",
                noun + " must be zero or positive", binder, getter, setter);
    }

    /**
     * Opens an editor for a single required, non-negative decimal value —
     * collapses the several near-identical single-field money/rate editors into
     * one call. {@code persist} receives the entered value on Save.
     */
    public static void openDecimalEditor(String title, String fieldLabel, String noun,
            BigDecimal current, Consumer<BigDecimal> persist) {
        var model = new DecimalHolder();
        var binder = new Binder<DecimalHolder>();
        var field = requiredDecimalField(fieldLabel, noun, binder,
                DecimalHolder::getValue, DecimalHolder::setValue);
        model.setValue(current);
        binder.readBean(model);
        openEditor(title, field, binder, model, () -> persist.accept(model.getValue()));
    }

    /** Single-value {@link BigDecimal} binding model (Binder needs a bean with a setter). */
    public static final class DecimalHolder {
        private BigDecimal value;

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }
    }
}

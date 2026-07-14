package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;

/**
 * Shared UI machinery for the allowance-rate settings screen
 * ({@link AllowanceRatesView}) — the allowance-package analogue of the
 * reference-data views' {@code ReferenceViewSupport}.
 *
 * <p>Centralises the always-enabled Save + top-of-form error summary editor
 * rule (ADR-0020 — never a disabled submit) and the money/rate formatting the
 * screen displays.
 */
final class AllowanceViewSupport {

    private AllowanceViewSupport() {
    }

    /**
     * Opens a modal editor with an always-enabled Save and a top-of-form error
     * summary (ADR-0020). Save validates through the binder; on failure the
     * summary (a {@code role="alert"} region) lists the messages, on success
     * {@code persist} runs and the dialog closes. {@code persist} may throw
     * {@link IllegalArgumentException} (a service-side guard) — its message shows
     * in the same summary.
     */
    static <T> void openEditor(String title, Component form, Binder<T> binder,
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

    static Button iconButton(VaadinIcon icon, String ariaLabel, Runnable action) {
        var button = new Button(new Icon(icon), event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel(ariaLabel);
        return button;
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

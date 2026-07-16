package com.vaadin.expensemanager.base.ui;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;

/**
 * A modal editor dialog with an <strong>always-enabled Save</strong> and a
 * <strong>top-of-form error summary</strong> (never a disabled submit; ADR-0020),
 * shared by every admin editor in the app. Promoted to {@code base.ui} so any
 * feature package can reuse it instead of hand-copying the same Dialog +
 * {@code role="alert"} summary + {@code writeBeanIfValid} scaffold (F-045).
 *
 * <p>Usage — build the form with the plain Vaadin API, hand it plus its binder
 * and bound bean to the dialog, then set the persist action:
 * <pre>{@code
 * var dialog = new EditorDialog<>("Edit VAT rate", form, binder, model);
 * dialog.onSave(() -> { service.updateVatRate(id, model.getValue()); refresh(); });
 * dialog.open();
 * }</pre>
 *
 * <p>On Save the bean is validated through the binder; on failure the summary
 * lists the messages and the dialog stays open. On success the {@code onSave}
 * action runs — if it throws {@link IllegalArgumentException} (a service-side
 * guard) its message shows in the same summary and the dialog stays open,
 * otherwise the dialog closes.
 *
 * @param <T> the form-bean type the binder writes to
 */
public class EditorDialog<T> extends Dialog {

    private final Binder<T> binder;
    private final T model;
    private final Div errorSummary = new Div();
    private Runnable onSave = () -> {
    };

    public EditorDialog(String title, Component form, Binder<T> binder, T model) {
        this.binder = binder;
        this.model = model;
        setHeaderTitle(title);

        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.getStyle().set("color", "var(--aura-red-text)");

        var save = new Button("Save", event -> save());
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> close());

        var content = new VerticalLayout(errorSummary, form);
        content.setPadding(false);
        content.setSpacing(true);
        add(content);
        getFooter().add(cancel, save);
    }

    /** Sets the action run on a valid Save (typically the service call + a refresh). */
    public EditorDialog<T> onSave(Runnable onSave) {
        this.onSave = onSave;
        return this;
    }

    private void save() {
        errorSummary.removeAll();
        errorSummary.setVisible(false);
        if (binder.writeBeanIfValid(model)) {
            try {
                onSave.run();
                close();
            } catch (IllegalArgumentException ex) {
                showErrors(List.of(ex.getMessage()));
            }
        } else {
            showErrors(binder.validate().getValidationErrors().stream()
                    .map(ValidationResult::getErrorMessage).toList());
        }
    }

    private void showErrors(List<String> messages) {
        errorSummary.removeAll();
        if (messages.isEmpty()) {
            errorSummary.setVisible(false);
            return;
        }
        var heading = new Span("Please fix the following:");
        heading.getStyle().setFontWeight("600");
        var list = new UnorderedList();
        messages.forEach(message -> list.add(new ListItem(message)));
        errorSummary.add(heading, list);
        errorSummary.setVisible(true);
    }
}

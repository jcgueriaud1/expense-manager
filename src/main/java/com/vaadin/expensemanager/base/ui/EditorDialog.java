package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.binder.Binder;

/**
 * A modal editor dialog with an <strong>always-enabled Save</strong> and a
 * <strong>top-of-form error summary</strong> (never a disabled submit; ADR-0020),
 * shared by every admin editor in the app. Promoted to {@code base.ui} so any
 * feature package can reuse it instead of hand-copying the same Dialog +
 * error-summary + {@code writeBeanIfValid} scaffold (F-045). The summary itself
 * is the shared {@link ErrorSummary} (accessible, field-linked; F-050).
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
 * action runs — if it throws a {@link DomainRuleException} (a user-actionable
 * service guard) its message shows in the same summary and the dialog stays open.
 * Any other failure is technical: it is left to propagate to the global
 * {@link UiErrorHandler}, which logs it and shows the generic error dialog rather
 * than leaking it into the summary (issue #86). Otherwise the dialog closes.
 *
 * @param <T> the form-bean type the binder writes to
 */
public class EditorDialog<T> extends Dialog {

    private final Binder<T> binder;
    private final T model;
    private final ErrorSummary errorSummary = new ErrorSummary();
    private Runnable onSave = () -> {
    };

    public EditorDialog(String title, Component form, Binder<T> binder, T model) {
        this.binder = binder;
        this.model = model;
        setHeaderTitle(title);

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
        errorSummary.clear();
        if (binder.writeBeanIfValid(model)) {
            try {
                onSave.run();
                close();
            } catch (DomainRuleException ex) {
                // A user-actionable rule lands in the summary; the dialog stays open.
                // Anything technical propagates to the global UiErrorHandler.
                errorSummary.show(ex.getMessage());
            }
        } else {
            errorSummary.showValidationErrors(binder.validate());
        }
    }
}

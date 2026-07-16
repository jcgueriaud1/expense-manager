package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.data.binder.Binder;

/**
 * A per-kind add/edit form, ready for {@link ReferenceConfigEditor} to open: the
 * dialog title, the form component, its {@link Binder} + bound model, and the
 * service call to run on a valid Save. Bundling these lets each config kind own
 * only its fields/validators/service calls while the editor module owns the
 * shared dialog behaviour.
 *
 * <p>Parameterised by the form-bean type {@code F} (the Binder's bean), which is
 * independent of the grid row type — a single-value form may back a multi-field
 * grid row, so the spec is kept self-typed and opened through {@link #open}
 * rather than exposing its bean to the caller.
 */
public final class EditorFormSpec<F> {

    private final String title;
    private final Component form;
    private final Binder<F> binder;
    private final F model;
    private final Runnable persist;

    public EditorFormSpec(String title, Component form, Binder<F> binder, F model,
            Runnable persist) {
        this.title = title;
        this.form = form;
        this.binder = binder;
        this.model = model;
        this.persist = persist;
    }

    /**
     * Opens this form through {@link AdminEditor#openEditor}; on a valid Save it
     * runs the kind's {@code persist} and then {@code afterPersist} (typically the
     * grid refresh). If {@code persist} throws {@link IllegalArgumentException},
     * {@code afterPersist} is skipped and the error surfaces in the dialog.
     */
    public void open(Runnable afterPersist) {
        AdminEditor.openEditor(title, form, binder, model, () -> {
            persist.run();
            afterPersist.run();
        });
    }
}

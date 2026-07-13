package com.vaadin.expensemanager.report.ui;

import java.util.List;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.shared.Registration;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatRate;

/**
 * The persistent side panel of the variant-D line editor (F-004): a form bound to
 * the <strong>selected</strong> {@link ReportLineModel} via its own
 * {@link Binder}. Selecting an expense type pre-fills its default VAT rate, which
 * stays overridable (ADR-0018).
 *
 * <p><strong>Combo items are set once (via {@link #setOptions}) while no bean is
 * bound, never on selection.</strong> Setting a ComboBox's items resets its
 * value; doing that mid-edit — while the write-through binder was still bound to
 * the previously selected line — used to write {@code null} back into that line
 * and silently drop its type/rate (regression fixed here). {@link #editLine} only
 * swaps the bean.
 */
final class ReportLineEditorPanel extends Div {

    private final H3 heading = new H3("Select an expense to edit");
    private final ComboBox<ExpenseTypeDto> typeField = new ComboBox<>("Expense type");
    private final BigDecimalField amountField = new BigDecimalField("Gross amount (paid)");
    private final ComboBox<VatRateDto> vatField = new ComboBox<>("VAT rate");
    private final TextField commentField = new TextField("Comment");
    private final Button removeButton = new Button("Remove expense");
    private final Binder<ReportLineModel> binder = new Binder<>();

    private transient List<VatRateDto> rateOptions = List.of();
    private Runnable changeListener = () -> { };
    private Runnable removeListener = () -> { };
    /** Suppresses the default-VAT and change callbacks while loading a bean. */
    private boolean populating;

    ReportLineEditorPanel() {
        typeField.setItemLabelGenerator(ExpenseTypeDto::name);
        vatField.setItemLabelGenerator(rate -> formatRate(rate.value()));
        amountField.setWidthFull();
        commentField.setWidthFull();
        commentField.setMaxLength(500);

        typeField.addValueChangeListener(event -> applyDefaultVat());
        binder.forField(typeField)
                .bind(ReportLineModel::getExpenseType, ReportLineModel::setExpenseType);
        binder.forField(amountField)
                .bind(ReportLineModel::getAmount, ReportLineModel::setAmount);
        binder.forField(vatField)
                .bind(ReportLineModel::getVatRate, ReportLineModel::setVatRate);
        binder.forField(commentField)
                .bind(ReportLineModel::getComment, ReportLineModel::setComment);
        binder.addValueChangeListener(event -> {
            if (!populating) {
                changeListener.run();
            }
        });

        removeButton.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        removeButton.addClickListener(event -> removeListener.run());

        var form = new FormLayout(typeField, amountField, vatField, commentField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(heading, form, removeButton);
        getStyle()
                .set("flex", "1 1 18rem")
                .set("background", "var(--vaadin-background-container)")
                .set("padding", "var(--vaadin-padding)")
                .set("border-radius", "var(--vaadin-radius-l)");
        clear();
    }

    /** Sets the pickers' options; call only while no line is bound. */
    void setOptions(List<ExpenseTypeDto> types, List<VatRateDto> rates) {
        this.rateOptions = rates;
        typeField.setItems(types);
        vatField.setItems(rates);
    }

    /** Binds a line into the form and enables editing. */
    void editLine(ReportLineModel model) {
        populating = true;
        binder.setBean(model);
        populating = false;
        heading.setText(model.getExpenseType() == null ? "New expense"
                : model.getExpenseType().name());
        setFieldsEnabled(true);
    }

    /** Detaches any bound line and disables the form. */
    void clear() {
        populating = true;
        binder.removeBean();
        populating = false;
        heading.setText("Select an expense to edit");
        setFieldsEnabled(false);
    }

    /** Refreshes the heading of the bound line after its type changed elsewhere. */
    void refreshHeading(ReportLineModel model) {
        heading.setText(model.getExpenseType() == null ? "New expense"
                : model.getExpenseType().name());
    }

    Registration addChangeListener(Runnable listener) {
        this.changeListener = listener;
        return () -> this.changeListener = () -> { };
    }

    Registration addRemoveListener(Runnable listener) {
        this.removeListener = listener;
        return () -> this.removeListener = () -> { };
    }

    private void applyDefaultVat() {
        if (populating || typeField.getValue() == null) {
            return;
        }
        heading.setText(typeField.getValue().name());
        Long defaultId = typeField.getValue().defaultVatRateId();
        rateOptions.stream().filter(r -> r.id().equals(defaultId)).findFirst()
                .ifPresent(vatField::setValue);
    }

    private void setFieldsEnabled(boolean enabled) {
        typeField.setEnabled(enabled);
        amountField.setEnabled(enabled);
        vatField.setEnabled(enabled);
        commentField.setEnabled(enabled);
        removeButton.setEnabled(enabled);
    }
}

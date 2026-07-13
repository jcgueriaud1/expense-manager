package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatPercent;

/**
 * The focused modal editor for one expense line (variant C, issue #24).
 *
 * <p>Editing a card opens this dialog over the report; adding a line opens it
 * empty. It binds an {@link ExpenseLineFormModel} with Binder + field validation
 * (ADR-0015): a missing type, missing/zero amount, or missing VAT rate surfaces
 * in a top-of-dialog error summary behind an <strong>always-enabled</strong>
 * Save (never a disabled button, ADR-0020). Choosing an expense type pre-fills
 * that type's default VAT rate, which the user can still override — done with a
 * value-change listener guarded by {@code isFromClient()}, since Binder can't
 * express a cross-field default declaratively (finding F-004).
 *
 * <p>New lines offer only <em>active</em> types/rates; when editing a historical
 * line whose type or rate has since been deactivated, that option is injected
 * into its ComboBox so the line still displays and round-trips (ADR-0018).
 */
final class LineEditorDialog extends Dialog {

    private final Binder<ExpenseLineFormModel> binder = new Binder<>();
    private final ExpenseLineFormModel model = new ExpenseLineFormModel();
    private final Div errorSummary = new Div();

    /**
     * @param types    active expense types offered to new lines, in display order
     * @param rates    active VAT rates offered to new lines, in display order
     * @param existing the line being edited, or {@code null} to add a new one
     * @param onSave   receives the edited/created line (id preserved for edits)
     */
    LineEditorDialog(List<ExpenseTypeDto> types, List<VatRateDto> rates,
            ExpenseLineDto existing, Consumer<ExpenseLineDto> onSave) {
        setHeaderTitle(existing == null ? "Add expense" : "Edit expense");
        setWidth("28rem");

        // Item sets start from the active options; a historical line's now-inactive
        // type/rate is added so it still shows when editing (ADR-0018).
        List<ExpenseTypeDto> typeItems = withHistoricalType(types, existing);
        List<VatRateDto> rateItems = withHistoricalRate(rates, existing);

        var typeField = new ComboBox<ExpenseTypeDto>("Expense type");
        typeField.setItems(typeItems);
        typeField.setItemLabelGenerator(ExpenseTypeDto::name);
        typeField.setRequiredIndicatorVisible(true);

        var vatField = new ComboBox<VatRateDto>("VAT rate");
        vatField.setItems(rateItems);
        vatField.setItemLabelGenerator(rate -> formatPercent(rate.value()));
        vatField.setRequiredIndicatorVisible(true);

        var amountField = new BigDecimalField("Gross amount (paid)");
        amountField.setRequiredIndicatorVisible(true);

        var commentField = new TextField("Comment");
        commentField.setMaxLength(500);

        // Choosing a type pre-fills its default rate (overridable). Guarded by
        // isFromClient() so binder.readBean below doesn't clobber a loaded rate.
        typeField.addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) {
                rateItems.stream()
                        .filter(rate -> rate.id().equals(event.getValue().defaultVatRateId()))
                        .findFirst()
                        .ifPresent(vatField::setValue);
            }
        });

        binder.forField(typeField)
                .asRequired("Expense type is required")
                .bind(ExpenseLineFormModel::getExpenseType,
                        ExpenseLineFormModel::setExpenseType);
        binder.forField(vatField)
                .asRequired("VAT rate is required")
                .bind(ExpenseLineFormModel::getVatRate,
                        ExpenseLineFormModel::setVatRate);
        binder.forField(amountField)
                .asRequired("Amount is required")
                .withValidator(amount -> amount == null || amount.signum() != 0,
                        "Amount must not be zero")
                .bind(ExpenseLineFormModel::getAmount, ExpenseLineFormModel::setAmount);
        binder.forField(commentField)
                .bind(ExpenseLineFormModel::getComment, ExpenseLineFormModel::setComment);

        if (existing != null) {
            model.setExpenseType(findById(typeItems, existing.expenseTypeId(),
                    ExpenseTypeDto::id));
            model.setVatRate(findById(rateItems, existing.vatRateId(),
                    VatRateDto::id));
            model.setAmount(existing.amount());
            model.setComment(existing.comment());
        }
        binder.readBean(model);

        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.getStyle().setColor("var(--aura-red-text)");

        var form = new FormLayout(typeField, amountField, vatField, commentField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(errorSummary, form);

        var save = new Button("Save expense", event -> save(existing, onSave));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> close());
        getFooter().add(cancel, save);
    }

    private void save(ExpenseLineDto existing, Consumer<ExpenseLineDto> onSave) {
        clearErrors();
        if (!binder.writeBeanIfValid(model)) {
            showErrors(binder.validate().getValidationErrors().stream()
                    .map(ValidationResult::getErrorMessage).distinct().toList());
            return;
        }
        var type = model.getExpenseType();
        var rate = model.getVatRate();
        var edited = new ExpenseLineDto(existing == null ? null : existing.id(),
                type.id(), type.name(), rate.id(), rate.value(),
                model.getAmount(), model.getComment());
        onSave.accept(edited);
        close();
    }

    private static List<ExpenseTypeDto> withHistoricalType(List<ExpenseTypeDto> active,
            ExpenseLineDto existing) {
        var items = new ArrayList<>(active);
        if (existing != null && existing.expenseTypeId() != null
                && items.stream().noneMatch(t -> t.id().equals(existing.expenseTypeId()))) {
            items.add(new ExpenseTypeDto(existing.expenseTypeId(),
                    existing.expenseTypeName() + " (inactive)", Integer.MAX_VALUE,
                    false, existing.vatRateId(), existing.vatRatePercent()));
        }
        return items;
    }

    private static List<VatRateDto> withHistoricalRate(List<VatRateDto> active,
            ExpenseLineDto existing) {
        var items = new ArrayList<>(active);
        if (existing != null && existing.vatRateId() != null
                && items.stream().noneMatch(r -> r.id().equals(existing.vatRateId()))) {
            items.add(new VatRateDto(existing.vatRateId(), existing.vatRatePercent(),
                    Integer.MAX_VALUE, false));
        }
        return items;
    }

    private static <T> T findById(List<T> items, Long id,
            java.util.function.Function<T, Long> idOf) {
        if (id == null) {
            return null;
        }
        return items.stream().filter(item -> id.equals(idOf.apply(item)))
                .findFirst().orElse(null);
    }

    private void clearErrors() {
        errorSummary.removeAll();
        errorSummary.setVisible(false);
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

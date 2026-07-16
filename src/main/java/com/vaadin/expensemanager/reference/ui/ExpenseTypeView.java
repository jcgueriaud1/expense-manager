package com.vaadin.expensemanager.reference.ui;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.expensemanager.base.ui.EditorFormSpec;
import com.vaadin.expensemanager.base.ui.ReferenceConfigEditor;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.formatPercent;

/**
 * ADMIN-only settings screen for expense types (issue #22, ADR-0018) — one of
 * the two reference-data screens (see {@link VatRateView} for VAT rates).
 *
 * <p>An admin can <strong>add, edit, reorder, and deactivate</strong> types and
 * set each type's <strong>required default VAT rate</strong>. Two-layer
 * authorization and history semantics match {@link VatRateView}: navigation is
 * gated by {@code @RolesAllowed("ADMIN")}, mutations are method-secured in
 * {@link ReferenceDataService}, and deactivate never deletes (ADR-0018).
 *
 * <p>The grid + add/edit dialog + reorder + active-toggle behaviour is the
 * shared, generic {@link ReferenceConfigEditor}; this class supplies only the
 * expense-type {@link ReferenceConfigEditor.Config}: its two columns, its
 * name + default-rate form, and its service calls. The one behaviour unique to
 * this screen — the default-rate {@code ComboBox} offering only <em>active</em>
 * VAT rates (plus the current one if since deactivated) — lives in
 * {@link #selectableRatesFor}.
 */
@Route("expense-types")
@PageTitle("Expense types")
@Menu(title = "Expense types", order = 3, icon = "vaadin:tags")
@RolesAllowed("ADMIN")
public class ExpenseTypeView extends ReferenceConfigEditor<ExpenseTypeDto> {

    public ExpenseTypeView(ReferenceDataService service) {
        super(config(service));
    }

    private static ReferenceConfigEditor.Config<ExpenseTypeDto> config(
            ReferenceDataService service) {
        return new ReferenceConfigEditor.Config<ExpenseTypeDto>()
                .heading("Expense types")
                .addButtonText("Add expense type")
                .intro("The expense types a line is classified as, each with a default "
                        + "VAT rate a new line pre-fills. Deactivating a type hides "
                        + "it from new lines but keeps it on existing ones — nothing "
                        + "is deleted.")
                .column("Name", ExpenseTypeDto::name, 1)
                .column("Default VAT rate", dto -> formatPercent(dto.defaultVatRateValue()), 0)
                .showStatus(true)
                .id(ExpenseTypeDto::id)
                .active(ExpenseTypeDto::active)
                .items(service::allExpenseTypes)
                .editLabel(dto -> "Edit expense type " + dto.name())
                .reorder(ExpenseTypeDto::name, service::moveExpenseType)
                .toggle(ExpenseTypeDto::name, service::setExpenseTypeActive)
                .editorForm(existing -> form(service, existing));
    }

    private static EditorFormSpec<?> form(ReferenceDataService service, ExpenseTypeDto existing) {
        var model = new ExpenseTypeForm();
        var nameField = new TextField("Name");
        nameField.setRequiredIndicatorVisible(true);

        var rateField = new ComboBox<VatRateDto>("Default VAT rate");
        rateField.setRequiredIndicatorVisible(true);
        rateField.setItemLabelGenerator(rate -> formatPercent(rate.value()));
        var selectable = selectableRatesFor(service, existing);
        rateField.setItems(selectable);

        var binder = new Binder<ExpenseTypeForm>();
        binder.forField(nameField)
                .asRequired("Name is required")
                .withValidator(name -> !name.isBlank(), "Name is required")
                .bind(ExpenseTypeForm::getName, ExpenseTypeForm::setName);
        binder.forField(rateField)
                .asRequired("A default VAT rate is required")
                .bind(ExpenseTypeForm::getDefaultVatRate, ExpenseTypeForm::setDefaultVatRate);

        if (existing != null) {
            model.setName(existing.name());
            selectable.stream()
                    .filter(rate -> rate.id().equals(existing.defaultVatRateId()))
                    .findFirst()
                    .ifPresent(model::setDefaultVatRate);
        }
        binder.readBean(model);

        var formLayout = new VerticalLayout(nameField, rateField);
        formLayout.setPadding(false);
        formLayout.setSpacing(false);

        return new EditorFormSpec<>(
                existing == null ? "Add expense type" : "Edit expense type",
                formLayout, binder, model, () -> {
                    Long rateId = model.getDefaultVatRate().id();
                    if (existing == null) {
                        service.createExpenseType(model.getName(), rateId);
                    } else {
                        service.updateExpenseType(existing.id(), model.getName(), rateId);
                    }
                });
    }

    /**
     * The rates offered as a type's default: the active rates, plus the type's
     * current default if it has since been deactivated (so editing never silently
     * drops a valid selection).
     */
    private static List<VatRateDto> selectableRatesFor(ReferenceDataService service,
            ExpenseTypeDto existing) {
        var rates = new ArrayList<>(service.activeVatRates());
        if (existing != null
                && rates.stream().noneMatch(r -> r.id().equals(existing.defaultVatRateId()))) {
            rates.add(0, new VatRateDto(existing.defaultVatRateId(),
                    existing.defaultVatRateValue(), -1, false));
        }
        return rates;
    }

    /** Mutable binding model for the editor. */
    private static final class ExpenseTypeForm {
        private String name;
        private VatRateDto defaultVatRate;

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        VatRateDto getDefaultVatRate() {
            return defaultVatRate;
        }

        void setDefaultVatRate(VatRateDto defaultVatRate) {
            this.defaultVatRate = defaultVatRate;
        }
    }
}

package com.vaadin.expensemanager.reference.ui;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.expensemanager.base.ui.EditorDialog;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
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
 * <p>The heading, grid, and row-action helpers come from
 * {@link ReferenceConfigView}; the editor is a shared {@link EditorDialog}. This
 * class owns the expense-type specifics: its two columns, its name + default-rate
 * form, and the one behaviour unique to this screen — the default-rate
 * {@code ComboBox} offering only <em>active</em> VAT rates (plus the current one
 * if since deactivated), in {@link #selectableRatesFor}.
 */
@Route("expense-types")
@PageTitle("Expense types")
@RolesAllowed("ADMIN")
public class ExpenseTypeView extends ReferenceConfigView<ExpenseTypeDto> {

    private final transient ReferenceDataService service;

    public ExpenseTypeView(ReferenceDataService service) {
        super("Expense types",
                "The expense types a line is classified as, each with a default "
                        + "VAT rate a new line pre-fills. Deactivating a type hides "
                        + "it from new lines but keeps it on existing ones — nothing "
                        + "is deleted.",
                "Add expense type");
        this.service = service;

        grid.addColumn(ExpenseTypeDto::name)
                .setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(dto -> formatPercent(dto.defaultVatRateValue()))
                .setHeader("Default VAT rate").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> statusLabel(dto.active()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions)
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(1);

        refresh();
    }

    @Override
    protected List<ExpenseTypeDto> fetchItems() {
        return service.allExpenseTypes();
    }

    private Component actions(ExpenseTypeDto dto) {
        int index = indexOf(dto);

        var edit = iconButton(VaadinIcon.EDIT, "Edit expense type " + dto.name(),
                () -> openEditor(dto));
        var up = reorderButton(VaadinIcon.ARROW_UP, "Move " + dto.name() + " up",
                index > 0, () -> {
                    service.moveExpenseType(dto.id(), -1);
                    refresh();
                });
        var down = reorderButton(VaadinIcon.ARROW_DOWN, "Move " + dto.name() + " down",
                index >= 0 && index < currentItems().size() - 1, () -> {
                    service.moveExpenseType(dto.id(), 1);
                    refresh();
                });
        var toggle = activeToggle(dto.active(), dto.name(), () -> {
            service.setExpenseTypeActive(dto.id(), !dto.active());
            refresh();
        });
        return new HorizontalLayout(edit, up, down, toggle);
    }

    @Override
    protected void openEditor(ExpenseTypeDto existing) {
        var model = new ExpenseTypeForm();
        var nameField = new TextField("Name");

        var rateField = new ComboBox<VatRateDto>("Default VAT rate");
        rateField.setItemLabelGenerator(rate -> formatPercent(rate.value()));
        var selectable = selectableRatesFor(existing);
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

        var form = new VerticalLayout(nameField, rateField);
        form.setPadding(false);
        form.setSpacing(false);

        new EditorDialog<>(existing == null ? "Add expense type" : "Edit expense type",
                form, binder, model)
                .onSave(() -> {
                    Long rateId = model.getDefaultVatRate().id();
                    if (existing == null) {
                        service.createExpenseType(model.getName(), rateId);
                    } else {
                        service.updateExpenseType(existing.id(), model.getName(), rateId);
                    }
                    refresh();
                })
                .open();
    }

    /**
     * The rates offered as a type's default: the active rates, plus the type's
     * current default if it has since been deactivated (so editing never silently
     * drops a valid selection).
     */
    private List<VatRateDto> selectableRatesFor(ExpenseTypeDto existing) {
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

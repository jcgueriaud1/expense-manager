package com.vaadin.expensemanager.reference.ui;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.activeToggle;
import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.formatPercent;
import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.iconButton;
import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.openEditor;
import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.reorderButton;
import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.statusLabel;

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
 * <p>Accessible and usable at ~360px (ADR-0020); editor forms keep Save always
 * enabled with a top-of-form error summary (via {@link ReferenceViewSupport}).
 */
@Route("expense-types")
@PageTitle("Expense types")
@Menu(title = "Expense types", order = 3, icon = "vaadin:tags")
@RolesAllowed("ADMIN")
public class ExpenseTypeView extends VerticalLayout {

    private final transient ReferenceDataService service;
    private final Grid<ExpenseTypeDto> grid = new Grid<>();

    public ExpenseTypeView(ReferenceDataService service) {
        this.service = service;
        setPadding(true);
        setSpacing(true);

        var addButton = new Button("Add expense type", new Icon(VaadinIcon.PLUS),
                event -> openExpenseTypeEditor(null));
        addButton.addThemeVariants(ButtonVariant.PRIMARY);

        var header = new HorizontalLayout(new H2("Expense types"), addButton);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        var intro = new Paragraph(
                "The expense types a line is classified as, each with a default "
                        + "VAT rate a new line pre-fills. Deactivating a type hides "
                        + "it from new lines but keeps it on existing ones — nothing "
                        + "is deleted.");

        grid.addColumn(ExpenseTypeDto::name)
                .setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(dto -> formatPercent(dto.defaultVatRateValue()))
                .setHeader("Default VAT rate").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> statusLabel(dto.active()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions)
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(1);
        grid.setAllRowsVisible(true);

        add(header, intro, grid);
        refresh();
    }

    private Component actions(ExpenseTypeDto dto) {
        var items = currentItems();
        int index = indexOf(items, dto.id());

        var edit = iconButton(VaadinIcon.EDIT, "Edit expense type " + dto.name(),
                () -> openExpenseTypeEditor(dto));
        var up = reorderButton(VaadinIcon.ARROW_UP, "Move " + dto.name() + " up",
                index > 0, () -> {
                    service.moveExpenseType(dto.id(), -1);
                    refresh();
                });
        var down = reorderButton(VaadinIcon.ARROW_DOWN, "Move " + dto.name() + " down",
                index >= 0 && index < items.size() - 1, () -> {
                    service.moveExpenseType(dto.id(), 1);
                    refresh();
                });
        var toggle = activeToggle(dto.active(), dto.name(), () -> {
            service.setExpenseTypeActive(dto.id(), !dto.active());
            refresh();
        });
        return new HorizontalLayout(edit, up, down, toggle);
    }

    private void openExpenseTypeEditor(ExpenseTypeDto existing) {
        var model = new ExpenseTypeForm();
        var nameField = new TextField("Name");
        nameField.setRequiredIndicatorVisible(true);

        var rateField = new ComboBox<VatRateDto>("Default VAT rate");
        rateField.setRequiredIndicatorVisible(true);
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

        openEditor(existing == null ? "Add expense type" : "Edit expense type",
                form, binder, model, () -> {
                    Long rateId = model.getDefaultVatRate().id();
                    if (existing == null) {
                        service.createExpenseType(model.getName(), rateId);
                    } else {
                        service.updateExpenseType(existing.id(), model.getName(), rateId);
                    }
                    refresh();
                });
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

    private void refresh() {
        grid.setItems(service.allExpenseTypes());
    }

    private List<ExpenseTypeDto> currentItems() {
        return grid.getGenericDataView().getItems().toList();
    }

    private static int indexOf(List<ExpenseTypeDto> items, Long id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
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

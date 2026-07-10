package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * ADMIN-only settings screen for the reference data expense lines are filed
 * against (issue #22, ADR-0018): VAT rates and expense types.
 *
 * <p>An admin can <strong>add, edit, reorder, and deactivate</strong> both, and
 * set each type's default VAT rate. Two-layer authorization (ADR-0008):
 * {@code @RolesAllowed("ADMIN")} gates <em>navigation</em> (a USER can't reach
 * the route and the auto-menu hides its {@code @Menu} entry), while the real
 * enforcement is {@link ReferenceDataService}'s method security — every mutation
 * this view invokes is itself ADMIN-guarded.
 *
 * <p><strong>History semantics (ADR-0018).</strong> There is no delete — the only
 * removal action is <em>deactivate</em>, which flips the {@code active} flag so
 * the row is hidden from new line choices but retained for historical lines.
 * Both grids show active <em>and</em> inactive rows so an admin can reactivate.
 *
 * <p><strong>Forms (F, error-summary rule).</strong> Every editor keeps its Save
 * action <em>always enabled</em> and surfaces validation failures in an error
 * summary at the top of the dialog (a {@code role="alert"} region) — never a
 * disabled submit button. Accessible and usable at ~360px (ADR-0020): sections
 * stack vertically and each grid scrolls within its own container.
 */
@Route("reference-data")
@PageTitle("Reference data")
@Menu(title = "Reference data", order = 2, icon = "vaadin:table")
@RolesAllowed("ADMIN")
public class ReferenceDataView extends VerticalLayout {

    private final transient ReferenceDataService service;

    private final Grid<VatRateDto> vatRateGrid = new Grid<>();
    private final Grid<ExpenseTypeDto> expenseTypeGrid = new Grid<>();

    public ReferenceDataView(ReferenceDataService service) {
        this.service = service;
        setPadding(true);
        setSpacing(true);

        var heading = new H2("Reference data");
        var intro = new Paragraph(
                "Manage the VAT rates and expense types that expense lines are "
                        + "filed against. Deactivating a rate or type hides it from "
                        + "new lines but keeps it on existing ones — nothing is "
                        + "deleted, so past reports keep their original values.");

        add(heading, intro,
                buildVatRateSection(),
                buildExpenseTypeSection());

        refreshVatRates();
        refreshExpenseTypes();
    }

    // ------------------------------------------------------------- VAT rates UI

    private Component buildVatRateSection() {
        var addButton = new Button("Add VAT rate", new Icon(VaadinIcon.PLUS),
                event -> openVatRateEditor(null));
        addButton.addThemeVariants(ButtonVariant.PRIMARY);

        vatRateGrid.addColumn(dto -> formatPercent(dto.value()))
                .setHeader("Rate").setAutoWidth(true).setFlexGrow(0);
        vatRateGrid.addColumn(dto -> statusLabel(dto.active()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        vatRateGrid.addComponentColumn(this::vatRateActions)
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(1);
        vatRateGrid.setAllRowsVisible(true);

        return section("VAT rates", addButton, vatRateGrid);
    }

    private Component vatRateActions(VatRateDto dto) {
        var items = currentVatRates();
        int index = indexOfVatRate(items, dto);

        var edit = iconButton(VaadinIcon.EDIT, "Edit rate " + formatPercent(dto.value()),
                () -> openVatRateEditor(dto));
        var up = reorderButton(VaadinIcon.ARROW_UP, "Move rate " + formatPercent(dto.value()) + " up",
                index > 0, () -> {
                    service.moveVatRate(dto.id(), -1);
                    refreshVatRates();
                });
        var down = reorderButton(VaadinIcon.ARROW_DOWN, "Move rate " + formatPercent(dto.value()) + " down",
                index >= 0 && index < items.size() - 1, () -> {
                    service.moveVatRate(dto.id(), 1);
                    refreshVatRates();
                });
        var toggle = activeToggle(dto.active(),
                "rate " + formatPercent(dto.value()), () -> {
                    service.setVatRateActive(dto.id(), !dto.active());
                    refreshVatRates();
                });
        return new HorizontalLayout(edit, up, down, toggle);
    }

    private void openVatRateEditor(VatRateDto existing) {
        var model = new VatRateForm();
        var valueField = new BigDecimalField("Rate (%)");
        valueField.setRequiredIndicatorVisible(true);

        var binder = new Binder<VatRateForm>();
        binder.forField(valueField)
                .asRequired("Rate is required")
                .withValidator(value -> value.signum() >= 0,
                        "Rate must be zero or positive")
                .bind(VatRateForm::getValue, VatRateForm::setValue);

        if (existing != null) {
            model.setValue(existing.value());
        }
        binder.readBean(model);

        openEditor(existing == null ? "Add VAT rate" : "Edit VAT rate",
                valueField, binder, model, () -> {
                    if (existing == null) {
                        service.createVatRate(model.getValue());
                    } else {
                        service.updateVatRate(existing.id(), model.getValue());
                    }
                    refreshVatRates();
                });
    }

    // -------------------------------------------------------- Expense types UI

    private Component buildExpenseTypeSection() {
        var addButton = new Button("Add expense type", new Icon(VaadinIcon.PLUS),
                event -> openExpenseTypeEditor(null));
        addButton.addThemeVariants(ButtonVariant.PRIMARY);

        expenseTypeGrid.addColumn(ExpenseTypeDto::name)
                .setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        expenseTypeGrid.addColumn(dto -> formatPercent(dto.defaultVatRateValue()))
                .setHeader("Default VAT rate").setAutoWidth(true).setFlexGrow(0);
        expenseTypeGrid.addColumn(dto -> statusLabel(dto.active()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        expenseTypeGrid.addComponentColumn(this::expenseTypeActions)
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(1);
        expenseTypeGrid.setAllRowsVisible(true);

        return section("Expense types", addButton, expenseTypeGrid);
    }

    private Component expenseTypeActions(ExpenseTypeDto dto) {
        var items = currentExpenseTypes();
        int index = indexOfExpenseType(items, dto);

        var edit = iconButton(VaadinIcon.EDIT, "Edit expense type " + dto.name(),
                () -> openExpenseTypeEditor(dto));
        var up = reorderButton(VaadinIcon.ARROW_UP, "Move " + dto.name() + " up",
                index > 0, () -> {
                    service.moveExpenseType(dto.id(), -1);
                    refreshExpenseTypes();
                });
        var down = reorderButton(VaadinIcon.ARROW_DOWN, "Move " + dto.name() + " down",
                index >= 0 && index < items.size() - 1, () -> {
                    service.moveExpenseType(dto.id(), 1);
                    refreshExpenseTypes();
                });
        var toggle = activeToggle(dto.active(), dto.name(), () -> {
            service.setExpenseTypeActive(dto.id(), !dto.active());
            refreshExpenseTypes();
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
        rateField.setItems(selectableRatesFor(existing));

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
            selectableRatesFor(existing).stream()
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
                    refreshExpenseTypes();
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

    // ------------------------------------------------------ shared editor shell

    /**
     * Opens a modal editor with an always-enabled Save and a top-of-form error
     * summary (F / ADR-0020). Save validates through the binder; on failure the
     * summary lists the messages, on success {@code persist} runs and the dialog
     * closes. {@code persist} may throw {@link IllegalArgumentException} (a
     * service-side guard) — its message is shown in the same summary.
     */
    private <T> void openEditor(String title, Component form, Binder<T> binder,
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

    // --------------------------------------------------------- small UI helpers

    private Component section(String title, Component addButton, Grid<?> grid) {
        var sectionHeading = new H2(title);
        sectionHeading.getStyle().setFontSize("var(--aura-font-size-l)");

        var header = new HorizontalLayout(sectionHeading, addButton);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        var layout = new VerticalLayout(header, grid);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();
        return layout;
    }

    private static Button iconButton(VaadinIcon icon, String ariaLabel, Runnable action) {
        var button = new Button(new Icon(icon), event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel(ariaLabel);
        return button;
    }

    private static Button reorderButton(VaadinIcon icon, String ariaLabel,
            boolean enabled, Runnable action) {
        var button = iconButton(icon, ariaLabel, action);
        button.setEnabled(enabled);
        return button;
    }

    private static Button activeToggle(boolean active, String subject, Runnable action) {
        var button = new Button(active ? "Deactivate" : "Activate",
                event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel((active ? "Deactivate " : "Activate ") + subject);
        return button;
    }

    /** Text status, never colour alone (ADR-0020 no colour-only meaning). */
    private static String statusLabel(boolean active) {
        return active ? "Active" : "Inactive";
    }

    private static String formatPercent(BigDecimal value) {
        var normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString() + " %";
    }

    // --------------------------------------------------------- data refresh

    private void refreshVatRates() {
        vatRateGrid.setItems(service.allVatRates());
    }

    private void refreshExpenseTypes() {
        expenseTypeGrid.setItems(service.allExpenseTypes());
    }

    private List<VatRateDto> currentVatRates() {
        return vatRateGrid.getGenericDataView().getItems().toList();
    }

    private List<ExpenseTypeDto> currentExpenseTypes() {
        return expenseTypeGrid.getGenericDataView().getItems().toList();
    }

    private static int indexOfVatRate(List<VatRateDto> items, VatRateDto dto) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(dto.id())) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfExpenseType(List<ExpenseTypeDto> items, ExpenseTypeDto dto) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(dto.id())) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ form models

    /** Mutable binding model for the VAT-rate editor (Binder needs setters). */
    private static final class VatRateForm {
        private BigDecimal value;

        BigDecimal getValue() {
            return value;
        }

        void setValue(BigDecimal value) {
            this.value = value;
        }
    }

    /** Mutable binding model for the expense-type editor. */
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

package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
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
 * ADMIN-only settings screen for VAT rates (issue #22, ADR-0018) — one of the
 * two reference-data screens (see {@link ExpenseTypeView} for expense types).
 *
 * <p>An admin can <strong>add, edit, reorder, and deactivate</strong> rates.
 * Two-layer authorization (ADR-0008): {@code @RolesAllowed("ADMIN")} gates
 * navigation (a USER can't reach the route and the auto-menu hides its
 * {@code @Menu} entry), while the real enforcement is
 * {@link ReferenceDataService}'s method security. There is no delete — the only
 * removal is <em>deactivate</em>, which flips the {@code active} flag so the row
 * is hidden from new line choices but retained for historical lines (ADR-0018);
 * the grid shows active and inactive rows so an admin can reactivate.
 *
 * <p>Accessible and usable at ~360px (ADR-0020): the grid scrolls within its own
 * container. Editor forms keep Save always enabled with a top-of-form error
 * summary (via {@link ReferenceViewSupport}).
 */
@Route("vat-rates")
@PageTitle("VAT rates")
@Menu(title = "VAT rates", order = 2, icon = "vaadin:money")
@RolesAllowed("ADMIN")
public class VatRateView extends VerticalLayout {

    private final transient ReferenceDataService service;
    private final Grid<VatRateDto> grid = new Grid<>();

    public VatRateView(ReferenceDataService service) {
        this.service = service;
        setPadding(true);
        setSpacing(true);

        var addButton = new Button("Add VAT rate", new Icon(VaadinIcon.PLUS),
                event -> openVatRateEditor(null));
        addButton.addThemeVariants(ButtonVariant.PRIMARY);

        var header = new HorizontalLayout(new H2("VAT rates"), addButton);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        var intro = new Paragraph(
                "The VAT rates expense lines are filed against. Deactivating a "
                        + "rate hides it from new lines but keeps it on existing "
                        + "ones — nothing is deleted, so past reports keep their "
                        + "original rate.");

        grid.addColumn(dto -> formatPercent(dto.value()))
                .setHeader("Rate").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> statusLabel(dto.active()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions)
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(1);
        grid.setAllRowsVisible(true);

        add(header, intro, grid);
        refresh();
    }

    private Component actions(VatRateDto dto) {
        var items = currentItems();
        int index = indexOf(items, dto.id());
        String label = formatPercent(dto.value());

        var edit = iconButton(VaadinIcon.EDIT, "Edit rate " + label,
                () -> openVatRateEditor(dto));
        var up = reorderButton(VaadinIcon.ARROW_UP, "Move rate " + label + " up",
                index > 0, () -> {
                    service.moveVatRate(dto.id(), -1);
                    refresh();
                });
        var down = reorderButton(VaadinIcon.ARROW_DOWN, "Move rate " + label + " down",
                index >= 0 && index < items.size() - 1, () -> {
                    service.moveVatRate(dto.id(), 1);
                    refresh();
                });
        var toggle = activeToggle(dto.active(), "rate " + label, () -> {
            service.setVatRateActive(dto.id(), !dto.active());
            refresh();
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
                    refresh();
                });
    }

    private void refresh() {
        grid.setItems(service.allVatRates());
    }

    private List<VatRateDto> currentItems() {
        return grid.getGenericDataView().getItems().toList();
    }

    private static int indexOf(List<VatRateDto> items, Long id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Mutable binding model for the editor (Binder needs setters). */
    private static final class VatRateForm {
        private BigDecimal value;

        BigDecimal getValue() {
            return value;
        }

        void setValue(BigDecimal value) {
            this.value = value;
        }
    }
}

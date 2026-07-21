package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.allowance.DomesticPerDiemDto;
import com.vaadin.expensemanager.allowance.ForeignPerDiemDto;
import com.vaadin.expensemanager.base.ui.EditorDialog;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

import static com.vaadin.expensemanager.allowance.ui.AllowanceViewSupport.formatMoney;
import static com.vaadin.expensemanager.allowance.ui.AllowanceViewSupport.formatRate;
import static com.vaadin.expensemanager.allowance.ui.AllowanceViewSupport.openDecimalEditor;

/**
 * ADMIN-only settings screen for the per-year allowance rate config (issue #48,
 * PRD 4.1/4.4, ADR-0008) — the allowance-package analogue of the reference-data
 * screens ({@code VatRateView} / {@code ExpenseTypeView}).
 *
 * <p>A year selector scopes the screen to one year; for that year an admin can
 * <strong>edit</strong> the domestic per-diem, kilometre rate, and meal
 * allowance, and <strong>add / edit</strong> foreign per-diems by country.
 * <strong>Add year</strong> seeds a whole new year from the most recent existing
 * one (PRD 4.4) without touching prior years.
 *
 * <p>Two-layer authorization (ADR-0008): {@code @RolesAllowed("ADMIN")} gates
 * navigation (a USER can't reach the route and the auto-menu hides its
 * {@code @Menu} entry), while the real enforcement is
 * {@link AllowanceRateService}'s method security. Every editor is a shared
 * {@link EditorDialog} (always-enabled Save + top-of-form error summary,
 * ADR-0020); the single-value rate editors go through
 * {@link AllowanceViewSupport#openDecimalEditor}.
 */
@Route("allowance-rates")
@PageTitle("Allowance rates")
@Menu(title = "Allowance rates", order = 4, icon = "vaadin:coin-piles")
@RolesAllowed("ADMIN")
public class AllowanceRatesView extends VerticalLayout {

    private final transient AllowanceRateService service;

    private final ComboBox<Integer> yearSelector = new ComboBox<>("Year");
    private final VerticalLayout yearSection = new VerticalLayout();
    private final Grid<ForeignPerDiemDto> foreignGrid = new Grid<>();

    public AllowanceRatesView(AllowanceRateService service) {
        this.service = service;
        setPadding(true);
        setSpacing(true);

        var addYear = new Button("Add year", new Icon(VaadinIcon.PLUS),
                event -> openAddYearEditor());
        addYear.addThemeVariants(ButtonVariant.PRIMARY);

        var header = new HorizontalLayout(new H2("Allowance rates"), addYear);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        var intro = new Paragraph(
                "The per-year rates the travel calculator costs against. History "
                        + "is kept per year — adding a new year copies the latest "
                        + "year's rates as a starting point and never changes prior "
                        + "years. Verify each figure against the Verohallinto "
                        + "decision for that year.");

        yearSelector.setAllowCustomValue(false);
        yearSelector.addValueChangeListener(event -> renderYear(event.getValue()));

        yearSection.setPadding(false);
        yearSection.setSpacing(true);
        yearSection.setWidthFull();

        add(header, intro, yearSelector, yearSection);
        refreshYears(null);
    }

    /** Reloads the year list; selects {@code preferred} if given, else the newest. */
    private void refreshYears(Integer preferred) {
        var years = service.availableYears();
        yearSelector.setItems(years);
        Integer selection = preferred != null && years.contains(preferred)
                ? preferred
                : years.stream().findFirst().orElse(null);
        yearSelector.setValue(selection);
        // A value change to the same value doesn't fire the listener; render anyway.
        renderYear(selection);
    }

    private void renderYear(Integer year) {
        yearSection.removeAll();
        if (year == null) {
            return;
        }
        var domestic = service.domesticPerDiem(year).orElse(null);
        var km = service.kilometreRate(year).orElse(null);
        var meal = service.mealAllowance(year).orElse(null);

        if (domestic != null) {
            yearSection.add(ratePanel("Domestic per diem",
                    "Full day (over " + domestic.fullDayMinHours() + " h): "
                            + formatMoney(domestic.fullDayAmount()) + " · Partial day (over "
                            + domestic.partialDayMinHours() + " h): "
                            + formatMoney(domestic.partialDayAmount()),
                    "Edit domestic per diem", () -> openDomesticEditor(year, domestic)));
        }
        if (km != null) {
            yearSection.add(ratePanel("Kilometre allowance", formatRate(km.amountPerKm()),
                    "Edit kilometre allowance", () -> openKilometreEditor(year, km.amountPerKm())));
        }
        if (meal != null) {
            yearSection.add(ratePanel("Meal allowance", formatMoney(meal.amount()),
                    "Edit meal allowance", () -> openMealEditor(year, meal.amount())));
        }
        yearSection.add(foreignSection(year));
    }

    // ------------------------------------------------------------- rendering

    /** A titled rate row: label + value on the left, an Edit button on the right. */
    private Component ratePanel(String title, String value, String editAriaLabel, Runnable edit) {
        var text = new VerticalLayout(new H3(title), new Span(value));
        text.setPadding(false);
        text.setSpacing(false);

        var editButton = new Button(new Icon(VaadinIcon.EDIT), event -> edit.run());
        editButton.addThemeVariants(ButtonVariant.TERTIARY);
        editButton.setAriaLabel(editAriaLabel);

        var row = new HorizontalLayout(text, editButton);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return row;
    }

    /** The foreign per-diem list for {@code year}: a titled grid with add/edit only. */
    private Component foreignSection(int year) {
        var addCountry = new Button("Add country", new Icon(VaadinIcon.PLUS),
                event -> openAddCountryEditor(year));
        addCountry.addThemeVariants(ButtonVariant.TERTIARY);

        var header = new HorizontalLayout(new H3("Foreign per diem"), addCountry);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        foreignGrid.removeAllColumns();
        foreignGrid.addColumn(ForeignPerDiemDto::country)
                .setHeader("Country").setAutoWidth(true).setFlexGrow(1);
        foreignGrid.addColumn(dto -> formatMoney(dto.amount()))
                .setHeader("Per-diem").setAutoWidth(true).setFlexGrow(0);
        foreignGrid.addComponentColumn(dto -> {
            var edit = new Button(new Icon(VaadinIcon.EDIT),
                    event -> openForeignEditor(year, dto));
            edit.addThemeVariants(ButtonVariant.TERTIARY);
            edit.setAriaLabel("Edit per-diem for " + dto.country());
            return edit;
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
        foreignGrid.setAllRowsVisible(true);
        foreignGrid.setItems(service.foreignPerDiems(year));

        var section = new VerticalLayout(header, foreignGrid);
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();
        return section;
    }

    // -------------------------------------------------------------- editors

    private void openAddYearEditor() {
        var model = new IntField();
        var yearField = new IntegerField("Year");
        yearField.setStepButtonsVisible(true);

        var binder = new Binder<IntField>();
        binder.forField(yearField)
                .asRequired("Year is required")
                .withValidator(value -> value >= 2000 && value <= 2100,
                        "Enter a year between 2000 and 2100")
                .bind(IntField::getValue, IntField::setValue);
        binder.readBean(model);

        new EditorDialog<>("Add year", yearField, binder, model)
                .onSave(() -> {
                    service.addYear(model.getValue());
                    refreshYears(model.getValue());
                })
                .open();
    }

    private void openDomesticEditor(int year, DomesticPerDiemDto existing) {
        var model = new DomesticForm();
        var full = new BigDecimalField("Full-day amount (€)");
        var partial = new BigDecimalField("Partial-day amount (€)");
        var fullHours = new IntegerField("Full-day hours");
        var partialHours = new IntegerField("Partial-day hours");

        var binder = new Binder<DomesticForm>();
        binder.forField(full).asRequired("Full-day amount is required")
                .withValidator(v -> v.signum() >= 0, "Amount must be zero or positive")
                .bind(DomesticForm::getFullAmount, DomesticForm::setFullAmount);
        binder.forField(partial).asRequired("Partial-day amount is required")
                .withValidator(v -> v.signum() >= 0, "Amount must be zero or positive")
                .bind(DomesticForm::getPartialAmount, DomesticForm::setPartialAmount);
        binder.forField(fullHours).asRequired("Full-day hours are required")
                .withValidator(v -> v > 0, "Hours must be positive")
                .bind(DomesticForm::getFullHours, DomesticForm::setFullHours);
        binder.forField(partialHours).asRequired("Partial-day hours are required")
                .withValidator(v -> v > 0, "Hours must be positive")
                .bind(DomesticForm::getPartialHours, DomesticForm::setPartialHours);

        model.setFullAmount(existing.fullDayAmount());
        model.setPartialAmount(existing.partialDayAmount());
        model.setFullHours(existing.fullDayMinHours());
        model.setPartialHours(existing.partialDayMinHours());
        binder.readBean(model);

        var form = new VerticalLayout(full, partial, fullHours, partialHours);
        form.setPadding(false);
        form.setSpacing(false);

        new EditorDialog<>("Edit domestic per diem — " + year, form, binder, model)
                .onSave(() -> {
                    service.updateDomesticPerDiem(year, model.getFullAmount(),
                            model.getPartialAmount(), model.getFullHours(),
                            model.getPartialHours());
                    renderYear(year);
                })
                .open();
    }

    private void openKilometreEditor(int year, BigDecimal current) {
        openDecimalEditor("Edit kilometre allowance — " + year, "Rate (€/km)", "Rate",
                current, value -> {
                    service.updateKilometreRate(year, value);
                    renderYear(year);
                });
    }

    private void openMealEditor(int year, BigDecimal current) {
        openDecimalEditor("Edit meal allowance — " + year, "Amount (€)", "Amount",
                current, value -> {
                    service.updateMealAllowance(year, value);
                    renderYear(year);
                });
    }

    private void openForeignEditor(int year, ForeignPerDiemDto existing) {
        openDecimalEditor("Edit per-diem — " + existing.country() + " " + year,
                "Amount (€)", "Amount", existing.amount(), value -> {
                    service.updateForeignPerDiem(existing.id(), value);
                    renderYear(year);
                });
    }

    private void openAddCountryEditor(int year) {
        var model = new ForeignForm();
        var country = new TextField("Country");
        var amount = new BigDecimalField("Amount (€)");

        var binder = new Binder<ForeignForm>();
        binder.forField(country).asRequired("Country is required")
                .withValidator(v -> !v.isBlank(), "Country is required")
                .bind(ForeignForm::getCountry, ForeignForm::setCountry);
        binder.forField(amount).asRequired("Amount is required")
                .withValidator(v -> v.signum() >= 0, "Amount must be zero or positive")
                .bind(ForeignForm::getAmount, ForeignForm::setAmount);
        binder.readBean(model);

        var form = new VerticalLayout(country, amount);
        form.setPadding(false);
        form.setSpacing(false);

        new EditorDialog<>("Add country — " + year, form, binder, model)
                .onSave(() -> {
                    service.addForeignPerDiem(year, model.getCountry(), model.getAmount());
                    renderYear(year);
                })
                .open();
    }

    // --------------------------------------------------- mutable form beans

    /** Single-value {@code int} binding model (Binder needs a bean with a setter). */
    private static final class IntField {
        private Integer value;

        Integer getValue() {
            return value;
        }

        void setValue(Integer value) {
            this.value = value;
        }
    }

    private static final class DomesticForm {
        private BigDecimal fullAmount;
        private BigDecimal partialAmount;
        private Integer fullHours;
        private Integer partialHours;

        BigDecimal getFullAmount() {
            return fullAmount;
        }

        void setFullAmount(BigDecimal fullAmount) {
            this.fullAmount = fullAmount;
        }

        BigDecimal getPartialAmount() {
            return partialAmount;
        }

        void setPartialAmount(BigDecimal partialAmount) {
            this.partialAmount = partialAmount;
        }

        Integer getFullHours() {
            return fullHours;
        }

        void setFullHours(Integer fullHours) {
            this.fullHours = fullHours;
        }

        Integer getPartialHours() {
            return partialHours;
        }

        void setPartialHours(Integer partialHours) {
            this.partialHours = partialHours;
        }
    }

    private static final class ForeignForm {
        private String country;
        private BigDecimal amount;

        String getCountry() {
            return country;
        }

        void setCountry(String country) {
            this.country = country;
        }

        BigDecimal getAmount() {
            return amount;
        }

        void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}

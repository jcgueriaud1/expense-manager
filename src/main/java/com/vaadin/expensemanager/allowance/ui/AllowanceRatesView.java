package com.vaadin.expensemanager.allowance.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.allowance.DomesticPerDiemDto;
import com.vaadin.expensemanager.allowance.ForeignPerDiemDto;
import com.vaadin.expensemanager.base.ui.AdminEditor;
import com.vaadin.expensemanager.base.ui.EditorFormSpec;
import com.vaadin.expensemanager.base.ui.ReferenceConfigEditor;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
 * {@link AllowanceRateService}'s method security. All editors run through the
 * shared {@link AdminEditor} (always-enabled Save + top-of-form error summary);
 * the single-value rate editors collapse onto
 * {@link AdminEditor#openDecimalEditor}, and the foreign per-diem grid is a
 * {@link ReferenceConfigEditor} config.
 */
@Route("allowance-rates")
@PageTitle("Allowance rates")
@Menu(title = "Allowance rates", order = 4, icon = "vaadin:coin-piles")
@RolesAllowed("ADMIN")
public class AllowanceRatesView extends VerticalLayout {

    private final transient AllowanceRateService service;

    private final ComboBox<Integer> yearSelector = new ComboBox<>("Year");
    private final VerticalLayout yearSection = new VerticalLayout();

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

        var row = new HorizontalLayout(text, AdminEditor.iconButton(VaadinIcon.EDIT, editAriaLabel, edit));
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return row;
    }

    /**
     * The foreign per-diem list for {@code year} as a {@link ReferenceConfigEditor}
     * config: an embedded (H3, tertiary-add) grid with add/edit only — no reorder
     * or active-toggle, since foreign per-diems carry neither.
     */
    private Component foreignSection(int year) {
        var config = new ReferenceConfigEditor.Config<ForeignPerDiemDto>()
                .heading("Foreign per diem")
                .headingLevel(3)
                .addButtonText("Add country")
                .addButtonVariant(ButtonVariant.TERTIARY)
                .actionsFlexGrow(0)
                .column("Country", ForeignPerDiemDto::country, 1)
                .column("Per-diem", dto -> formatMoney(dto.amount()), 0)
                .id(ForeignPerDiemDto::id)
                .items(() -> service.foreignPerDiems(year))
                .editLabel(dto -> "Edit per-diem for " + dto.country())
                .editorForm(existing -> foreignForm(year, existing));

        var editor = new ReferenceConfigEditor<>(config);
        editor.setPadding(false);
        editor.setSpacing(true);
        editor.setWidthFull();
        return editor;
    }

    // -------------------------------------------------------------- editors

    private void openAddYearEditor() {
        var model = new IntField();
        var yearField = new IntegerField("Year");
        yearField.setRequiredIndicatorVisible(true);
        yearField.setStepButtonsVisible(true);

        var binder = new Binder<IntField>();
        binder.forField(yearField)
                .asRequired("Year is required")
                .withValidator(value -> value >= 2000 && value <= 2100,
                        "Enter a year between 2000 and 2100")
                .bind(IntField::getValue, IntField::setValue);
        binder.readBean(model);

        AdminEditor.openEditor("Add year", yearField, binder, model, () -> {
            service.addYear(model.getValue());
            refreshYears(model.getValue());
        });
    }

    private void openDomesticEditor(int year, DomesticPerDiemDto existing) {
        var model = new DomesticForm();
        var binder = new Binder<DomesticForm>();

        var full = AdminEditor.requiredDecimalField("Full-day amount (€)",
                "Full-day amount is required", "Amount must be zero or positive", binder,
                DomesticForm::getFullAmount, DomesticForm::setFullAmount);
        var partial = AdminEditor.requiredDecimalField("Partial-day amount (€)",
                "Partial-day amount is required", "Amount must be zero or positive", binder,
                DomesticForm::getPartialAmount, DomesticForm::setPartialAmount);
        var fullHours = new IntegerField("Full-day hours");
        var partialHours = new IntegerField("Partial-day hours");
        fullHours.setRequiredIndicatorVisible(true);
        partialHours.setRequiredIndicatorVisible(true);
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

        AdminEditor.openEditor("Edit domestic per diem — " + year, form, binder, model, () -> {
            service.updateDomesticPerDiem(year, model.getFullAmount(), model.getPartialAmount(),
                    model.getFullHours(), model.getPartialHours());
            renderYear(year);
        });
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

    /**
     * The foreign per-diem form for {@code year}: add supplies country + amount,
     * edit supplies only the amount (the country is fixed).
     */
    private EditorFormSpec<?> foreignForm(int year, ForeignPerDiemDto existing) {
        if (existing != null) {
            var model = new AdminEditor.DecimalHolder();
            var binder = new Binder<AdminEditor.DecimalHolder>();
            var amount = AdminEditor.requiredDecimalField("Amount (€)", "Amount", binder,
                    AdminEditor.DecimalHolder::getValue, AdminEditor.DecimalHolder::setValue);
            model.setValue(existing.amount());
            binder.readBean(model);
            return new EditorFormSpec<>(
                    "Edit per-diem — " + existing.country() + " " + year,
                    amount, binder, model,
                    () -> service.updateForeignPerDiem(existing.id(), model.getValue()));
        }

        var model = new ForeignForm();
        var binder = new Binder<ForeignForm>();
        var country = new TextField("Country");
        country.setRequiredIndicatorVisible(true);
        binder.forField(country).asRequired("Country is required")
                .withValidator(v -> !v.isBlank(), "Country is required")
                .bind(ForeignForm::getCountry, ForeignForm::setCountry);
        var amount = AdminEditor.requiredDecimalField("Amount (€)", "Amount", binder,
                ForeignForm::getAmount, ForeignForm::setAmount);
        binder.readBean(model);

        var form = new VerticalLayout(country, amount);
        form.setPadding(false);
        form.setSpacing(false);

        return new EditorFormSpec<>("Add country — " + year, form, binder, model,
                () -> service.addForeignPerDiem(year, model.getCountry(), model.getAmount()));
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

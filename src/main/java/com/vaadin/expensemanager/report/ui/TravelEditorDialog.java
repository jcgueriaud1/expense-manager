package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.report.service.GeneratedLineView;
import com.vaadin.expensemanager.report.service.TravelDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker.DateTimePickerI18n;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.generatedLineLabel;

/**
 * The focused modal editor for one trip (glossary: Travel Calculator, Phase
 * 4.2/4.3) — the "Insert travel info" dialog.
 *
 * <p>Collects the <strong>domestic</strong> subset of trip inputs (departure /
 * return date & time, destinations, purpose, and the not-eligible / free-lunch /
 * charge-to-customer flags), then previews the Finnish domestic per-diem the app
 * computes server-side on <em>Continue</em> and commits the trip on <em>Save</em>.
 * The client sends only inputs — the money is always the server's (ADR-0006).
 *
 * <p>Validation follows the project rule (ADR-0020): both actions stay
 * <strong>always enabled</strong>; a missing field or invalid trip (e.g. a return
 * before the departure, or no rate configured for the trip year) surfaces in a
 * top-of-dialog error summary and generates nothing. New lines the trip produces
 * are read-only and regenerated whenever the trip is edited.
 *
 * <p>The <strong>destination-country picker</strong> (Phase 4.2) lists Finland
 * (domestic) plus every country that has a foreign per-diem rate for the trip's
 * year. Picking Finland keeps the domestic per-diem; picking a foreign country
 * costs the per-diem against that country's rate. The list is year-dependent, so it
 * refreshes whenever the departure's year changes.
 */
final class TravelEditorDialog extends Dialog {

    private final Binder<TravelFormModel> binder = new Binder<>();
    private final TravelFormModel model = new TravelFormModel();
    private final ErrorSummary errorSummary = new ErrorSummary();
    private final Div preview = new Div();

    private final TravelDto existing;
    private final UnaryOperator<TravelDto> costPreview;

    /**
     * @param existing               the trip being edited, or {@code null} to add one
     * @param costPreview            computes the server-authoritative trip outputs for
     *                               a trip's inputs (the service preview); may throw
     *                               {@link IllegalArgumentException} for invalid input
     *                               (including a foreign country with no rate)
     * @param foreignCountriesForYear the destination countries with a foreign rate for
     *                               a given year (listed after Finland in the picker)
     * @param onSave                 receives the committed trip (with its computed outputs)
     */
    TravelEditorDialog(TravelDto existing, UnaryOperator<TravelDto> costPreview,
            IntFunction<List<String>> foreignCountriesForYear,
            Consumer<TravelDto> onSave) {
        this.existing = existing;
        this.costPreview = costPreview;
        setHeaderTitle(existing == null ? "Insert travel info" : "Edit trip");
        setWidth("32rem");
        addClassName("travel-dialog");

        var departure = new DateTimePicker("Departure");
        departure.setStep(Duration.ofMinutes(15));
        departure.setRequiredIndicatorVisible(true);
        departure.setI18n(dateTimeErrorMessages());
        var returnAt = new DateTimePicker("Return");
        returnAt.setStep(Duration.ofMinutes(15));
        returnAt.setRequiredIndicatorVisible(true);
        returnAt.setI18n(dateTimeErrorMessages());

        // Keep the range valid from the pickers themselves: once a departure is
        // chosen the return overlay can't go earlier than it, and vice versa — so
        // the "Return must be after the departure" error only ever appears if the
        // user types an invalid date/time by hand (the overlay can't produce one).
        // Registered before readBean so an edited trip's values initialise them.
        departure.addValueChangeListener(event -> returnAt.setMin(event.getValue()));
        returnAt.addValueChangeListener(event -> departure.setMax(event.getValue()));

        // Destination country: Finland (domestic) + every country with a foreign
        // rate for the trip's year. The list depends on the departure year, so it is
        // (re)populated from it; Finland shows as "Finland (domestic)".
        var country = new ComboBox<String>("Destination country");
        country.setRequiredIndicatorVisible(true);
        country.setItemLabelGenerator(name ->
                TravelDto.DOMESTIC_COUNTRY.equals(name)
                        ? "Finland (domestic)" : name);
        Runnable refreshCountries = () -> {
            // Prefer the field, fall back to the model's departure (so the initial
            // population before readBean uses an edited trip's year), else this year.
            LocalDateTime departureFor = departure.getValue() != null
                    ? departure.getValue() : model.getDepartureAt();
            int year = departureFor != null
                    ? departureFor.getYear() : Year.now().getValue();
            List<String> items = new ArrayList<>();
            items.add(TravelDto.DOMESTIC_COUNTRY);
            items.addAll(foreignCountriesForYear.apply(year));
            String selected = country.getValue();
            country.setItems(items);
            if (selected != null && items.contains(selected)) {
                country.setValue(selected);
            }
        };

        var destinations = new TextField("Destinations");
        destinations.setRequiredIndicatorVisible(true);
        destinations.setPlaceholder("e.g. Helsinki, Espoo");
        var purpose = new TextField("Travel purpose");
        purpose.setRequiredIndicatorVisible(true);

        var notEligible = new Checkbox("Trip not eligible for daily allowance");
        var freeLunch = new Checkbox("Free lunch provided?");
        var chargeToCustomer = new Checkbox("Charge expenses from customer?");

        var kilometres = new BigDecimalField("Kilometre allowance (km)");
        kilometres.setPlaceholder("e.g. 120");
        var payMeal = new Checkbox("Pay meal allowance?");
        var parkingFees = new BigDecimalField("Parking fees (€)");
        parkingFees.setPlaceholder("e.g. 12.00");

        preview.addClassName("travel-preview");
        preview.setVisible(false);

        // The per-diem depends only on the two dates and the eligibility/free-lunch
        // flags, so preview it live as the user fills those in — no separate
        // "compute" step. Recompute server-side (money is never client-side) once
        // both dates are set; while incomplete or invalid the preview simply hides
        // (Save still surfaces any reason). Registered before readBean so an edited
        // trip previews immediately on open.
        Runnable recompute = () -> refreshPreview(departure.getValue(),
                returnAt.getValue(), country.getValue(), destinations.getValue(),
                purpose.getValue(), notEligible.getValue(), freeLunch.getValue(),
                chargeToCustomer.getValue(), kilometres.getValue(), payMeal.getValue(),
                parkingFees.getValue());
        // The available countries depend on the departure year, so refresh them
        // first, then recompute the preview (which reads the country).
        departure.addValueChangeListener(event -> {
            refreshCountries.run();
            recompute.run();
        });
        returnAt.addValueChangeListener(event -> recompute.run());
        country.addValueChangeListener(event -> recompute.run());
        notEligible.addValueChangeListener(event -> recompute.run());
        freeLunch.addValueChangeListener(event -> recompute.run());
        kilometres.addValueChangeListener(event -> recompute.run());
        payMeal.addValueChangeListener(event -> recompute.run());
        parkingFees.addValueChangeListener(event -> recompute.run());

        binder.forField(departure)
                .asRequired("Departure date & time is required")
                .bind(TravelFormModel::getDepartureAt, TravelFormModel::setDepartureAt);
        binder.forField(returnAt)
                .asRequired("Return date & time is required")
                .bind(TravelFormModel::getReturnAt, TravelFormModel::setReturnAt);
        binder.forField(country)
                .asRequired("Destination country is required")
                .bind(TravelFormModel::getCountry, TravelFormModel::setCountry);
        binder.forField(destinations)
                .asRequired("Destinations are required")
                .bind(TravelFormModel::getDestinations, TravelFormModel::setDestinations);
        binder.forField(purpose)
                .asRequired("Travel purpose is required")
                .bind(TravelFormModel::getPurpose, TravelFormModel::setPurpose);
        binder.forField(notEligible).bind(TravelFormModel::isNotEligibleForAllowance,
                TravelFormModel::setNotEligibleForAllowance);
        binder.forField(freeLunch).bind(TravelFormModel::isFreeLunch,
                TravelFormModel::setFreeLunch);
        binder.forField(chargeToCustomer).bind(TravelFormModel::isChargeToCustomer,
                TravelFormModel::setChargeToCustomer);
        binder.forField(kilometres).bind(TravelFormModel::getKilometres,
                TravelFormModel::setKilometres);
        binder.forField(payMeal).bind(TravelFormModel::isPayMealAllowance,
                TravelFormModel::setPayMealAllowance);
        binder.forField(parkingFees).bind(TravelFormModel::getParkingFees,
                TravelFormModel::setParkingFees);

        if (existing != null) {
            model.setDepartureAt(existing.departureAt());
            model.setReturnAt(existing.returnAt());
            model.setCountry(existing.country());
            model.setDestinations(existing.destinations());
            model.setPurpose(existing.purpose());
            model.setNotEligibleForAllowance(existing.notEligibleForAllowance());
            model.setFreeLunch(existing.freeLunch());
            model.setChargeToCustomer(existing.chargeToCustomer());
            model.setKilometres(zeroToNull(existing.kilometres()));
            model.setPayMealAllowance(existing.payMealAllowance());
            model.setParkingFees(zeroToNull(existing.parkingFees()));
        }
        // Populate the country list for the trip's year before readBean so an edited
        // trip's stored country preselects.
        refreshCountries.run();
        binder.readBean(model);

        var form = new FormLayout(departure, returnAt, country, destinations, purpose,
                kilometres, parkingFees, notEligible, freeLunch, payMeal,
                chargeToCustomer);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("24rem", 2));
        form.setColspan(country, 2);
        form.setColspan(destinations, 2);
        form.setColspan(purpose, 2);
        form.setColspan(notEligible, 2);
        form.setColspan(freeLunch, 2);
        form.setColspan(payMeal, 2);
        form.setColspan(chargeToCustomer, 2);
        add(errorSummary, form, preview);

        var save = new Button("Save trip", event -> onSave(onSave));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> close());
        getFooter().add(cancel, save);
    }

    /** Commits the trip with its server-authoritative per-diem (dialog "Save"). */
    private void onSave(Consumer<TravelDto> onSave) {
        TravelDto computed = validateAndCompute();
        if (computed != null) {
            onSave.accept(computed);
            close();
        }
    }

    /**
     * Recomputes and shows the live allowance preview from the current inputs. The
     * amounts are always the server's ({@link #costPreview}); the preview hides
     * while the dates are incomplete or the range is invalid (Save surfaces any
     * reason).
     */
    private void refreshPreview(LocalDateTime departure, LocalDateTime returnAt,
            String country, String destinations, String purpose, boolean notEligible,
            boolean freeLunch, boolean chargeToCustomer, BigDecimal kilometres,
            boolean payMeal, BigDecimal parkingFees) {
        if (departure == null || returnAt == null || country == null) {
            preview.setVisible(false);
            return;
        }
        var input = TravelDto.of(existing == null ? null : existing.id(),
                departure, returnAt, destinations, purpose, country, notEligible,
                freeLunch, chargeToCustomer, kilometres, payMeal, parkingFees);
        try {
            renderPreview(costPreview.apply(input));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            preview.setVisible(false);
        }
    }

    /**
     * Validates the form and computes the per-diem server-side (ADR-0020: never a
     * disabled button — invalid input lands in the error summary instead).
     *
     * @return the trip with its computed per-diem, or {@code null} if invalid
     */
    private TravelDto validateAndCompute() {
        errorSummary.clear();
        if (!binder.writeBeanIfValid(model)) {
            preview.setVisible(false);
            errorSummary.showValidationErrors(binder.validate());
            return null;
        }
        var input = TravelDto.of(existing == null ? null : existing.id(),
                model.getDepartureAt(), model.getReturnAt(), model.getDestinations(),
                model.getPurpose(), model.getCountry(), model.isNotEligibleForAllowance(),
                model.isFreeLunch(), model.isChargeToCustomer(), model.getKilometres(),
                model.isPayMealAllowance(), model.getParkingFees());
        try {
            return costPreview.apply(input);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            preview.setVisible(false);
            errorSummary.show(invalid.getMessage());
            return null;
        }
    }

    /**
     * Shows the computed trip outputs below the form: one line per non-zero
     * allowance/expense (per-diem, kilometre, meal, parking) with its breakdown,
     * plus a grand-total heading — or a "no allowances" note when the trip earns
     * nothing (Phase 4.3).
     */
    private void renderPreview(TravelDto trip) {
        preview.removeAll();
        BigDecimal total = trip.generatedLines().stream()
                .map(GeneratedLineView::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        var heading = new Span(total.signum() == 0
                ? "No allowances for this trip"
                : "Trip total: " + formatEur(total));
        heading.addClassName("travel-preview-amount");
        preview.add(heading);

        trip.generatedLines().forEach(line -> addPreviewLine(
                generatedLineLabel(line.kind()), line.amount(), line.comment()));
        preview.setVisible(true);
    }

    /** Adds one "label — amount / explanation" preview line. */
    private void addPreviewLine(String label, BigDecimal amount, String explanation) {
        var line = new Span(label + ": " + formatEur(amount));
        line.addClassName("travel-preview-line");
        preview.add(line);
        if (explanation != null) {
            var detail = new Span(explanation);
            detail.addClassName("muted");
            detail.addClassName("muted-xs");
            preview.add(detail);
        }
    }

    /**
     * Error messages for a departure/return picker's non-configurable input
     * constraints. Without these the field goes invalid with a <em>blank</em> message
     * when the user enters only the date and not the time (V25 treats that partial
     * input as invalid) or types an unparseable value — which surfaced as an empty
     * bullet in the error summary (issue #85). The required-field message stays with
     * the binder's {@code asRequired}.
     */
    private static DateTimePickerI18n dateTimeErrorMessages() {
        return new DateTimePickerI18n()
                .setIncompleteInputErrorMessage("Enter both a date and a time")
                .setBadInputErrorMessage("Enter a valid date and time");
    }

    /** Blank a zero amount so an untouched money field shows empty, not "0.00". */
    private static BigDecimal zeroToNull(BigDecimal amount) {
        return amount == null || amount.signum() == 0 ? null : amount;
    }

}

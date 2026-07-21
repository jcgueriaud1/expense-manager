package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.report.service.GeneratedLineView;
import com.vaadin.expensemanager.report.service.TravelDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.timepicker.TimePicker.TimePickerI18n;
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
 * <p>Each of departure and return is a <strong>required date</strong> plus an
 * <strong>optional time</strong> (issue #94): a {@link DatePicker} paired with a
 * {@link TimePicker}, since {@link com.vaadin.flow.component.datetimepicker.DateTimePicker}
 * treats a date without a time as incomplete and cannot leave the time blank. An
 * empty time means midnight (00:00), and choosing a departure date for the first
 * time (while the return date is empty) fills the return date to match it.
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

        // Departure / return are each a required date + an optional time (issue #94):
        // DateTimePicker rejects a date without a time (its incomplete-input
        // constraint is non-configurable), so a DatePicker + TimePicker pair is what
        // lets the time be left blank. Only the date carries the required indicator.
        var departureDate = new DatePicker("Departure date");
        departureDate.setRequiredIndicatorVisible(true);
        departureDate.setI18n(dateErrorMessages());
        var departureTime = new TimePicker("Departure time");
        departureTime.setStep(Duration.ofMinutes(15));
        departureTime.setPlaceholder("00:00");
        departureTime.setI18n(timeErrorMessages());
        var returnDate = new DatePicker("Return date");
        returnDate.setRequiredIndicatorVisible(true);
        returnDate.setI18n(dateErrorMessages());
        var returnTime = new TimePicker("Return time");
        returnTime.setStep(Duration.ofMinutes(15));
        returnTime.setPlaceholder("00:00");
        returnTime.setI18n(timeErrorMessages());

        // The composed departure / return the preview and DTO are built from: a date
        // with its time, or midnight when the time is left blank (issue #94).
        Supplier<LocalDateTime> departureAt =
                () -> combine(departureDate.getValue(), departureTime.getValue());
        Supplier<LocalDateTime> returnAt =
                () -> combine(returnDate.getValue(), returnTime.getValue());

        // Keep the range valid from the date pickers themselves: once a departure
        // date is chosen the return can't go to an earlier day, and vice versa — so
        // the "Return must be after the departure" error only ever appears if the
        // user picks the same day with an earlier return time (the return-before-
        // departure guard lives at the domain/service layers). Registered before
        // readBean so an edited trip's values initialise them.
        departureDate.addValueChangeListener(event -> returnDate.setMin(event.getValue()));
        returnDate.addValueChangeListener(event -> departureDate.setMax(event.getValue()));
        // Choosing a departure date for the first time fills the return date to match
        // it (issue #94): only when the return date is still empty, so it never
        // overwrites a return the user has already set.
        departureDate.addValueChangeListener(event -> {
            if (event.getValue() != null && returnDate.isEmpty()) {
                returnDate.setValue(event.getValue());
            }
        });

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
            LocalDateTime departureFor = departureAt.get() != null
                    ? departureAt.get() : model.getDepartureAt();
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
        freeLunch.setHelperText(
                "Halves the daily allowance — applies only when the trip earns one.");
        var chargeToCustomer = new Checkbox("Charge expenses from customer?");

        var kilometres = new BigDecimalField("Kilometre allowance (km)");
        kilometres.setPlaceholder("e.g. 120");
        var payMeal = new Checkbox("Pay meal allowance?");
        payMeal.setHelperText(
                "Only when the trip earns no daily allowance — selecting it marks "
                        + "the trip not eligible.");
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
        Runnable recompute = () -> refreshPreview(departureAt.get(),
                returnAt.get(), country.getValue(), destinations.getValue(),
                purpose.getValue(), notEligible.getValue(), freeLunch.getValue(),
                chargeToCustomer.getValue(), kilometres.getValue(), payMeal.getValue(),
                parkingFees.getValue());
        // The available countries depend on the departure year, so refresh them
        // first, then recompute the preview (which reads the country).
        departureDate.addValueChangeListener(event -> {
            refreshCountries.run();
            recompute.run();
        });
        departureTime.addValueChangeListener(event -> recompute.run());
        returnDate.addValueChangeListener(event -> recompute.run());
        returnTime.addValueChangeListener(event -> recompute.run());
        country.addValueChangeListener(event -> recompute.run());
        // Domain coupling (issue #93): a meal allowance (ateriakorvaus) is paid
        // only when no per-diem applies, and the free-meal reduction exists only to
        // halve a per-diem — so "Pay meal allowance" and "Free lunch" sit in
        // mutually exclusive worlds either side of the eligibility flag (invariants
        // payMeal ⟹ not-eligible, freeLunch ⟹ eligible). The checkboxes stay
        // enabled (ADR-0020, no disabled inputs); clicking one auto-corrects the
        // others. setValue is a no-op when unchanged, so these cascades converge.
        notEligible.addValueChangeListener(event -> {
            if (event.getValue()) {
                freeLunch.setValue(false);   // no per-diem left to halve
            } else {
                payMeal.setValue(false);     // per-diem applies → no meal allowance
            }
            recompute.run();
        });
        freeLunch.addValueChangeListener(event -> {
            if (event.getValue()) {
                notEligible.setValue(false); // a free meal only halves a per-diem
            }
            recompute.run();
        });
        payMeal.addValueChangeListener(event -> {
            if (event.getValue()) {
                notEligible.setValue(true);  // meal allowance only when no per-diem
            }
            recompute.run();
        });
        kilometres.addValueChangeListener(event -> recompute.run());
        parkingFees.addValueChangeListener(event -> recompute.run());

        // Only the dates are required; the times are optional (empty → 00:00, the
        // midnight default lives in the model's composed getters, issue #94).
        binder.forField(departureDate)
                .asRequired("Departure date is required")
                .bind(TravelFormModel::getDepartureDate, TravelFormModel::setDepartureDate);
        binder.forField(departureTime)
                .bind(TravelFormModel::getDepartureTime, TravelFormModel::setDepartureTime);
        binder.forField(returnDate)
                .asRequired("Return date is required")
                .bind(TravelFormModel::getReturnDate, TravelFormModel::setReturnDate);
        binder.forField(returnTime)
                .bind(TravelFormModel::getReturnTime, TravelFormModel::setReturnTime);
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

        // Two columns: departure date | time on one row, return date | time on the
        // next, then the wide fields span both.
        var form = new FormLayout(departureDate, departureTime, returnDate, returnTime,
                country, destinations, purpose, kilometres, parkingFees, notEligible,
                freeLunch, payMeal, chargeToCustomer);
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

    /** A date + optional time as a {@link LocalDateTime}; empty time → midnight. */
    private static LocalDateTime combine(LocalDate date, LocalTime time) {
        return date == null ? null
                : LocalDateTime.of(date, time == null ? LocalTime.MIDNIGHT : time);
    }

    /**
     * Bad-input message for a departure/return <em>date</em> picker. Without it the
     * field goes invalid with a <em>blank</em> message when the user types an
     * unparseable value — which surfaced as an empty bullet in the error summary
     * (issue #85). The required-field message stays with the binder's
     * {@code asRequired}.
     */
    private static DatePickerI18n dateErrorMessages() {
        return new DatePickerI18n().setBadInputErrorMessage("Enter a valid date");
    }

    /**
     * Bad-input message for a departure/return <em>time</em> picker (the time is
     * optional, issue #94, so there is no required message). Without it an
     * unparseable time would reach the error summary as an empty bullet (issue #85).
     */
    private static TimePickerI18n timeErrorMessages() {
        return new TimePickerI18n().setBadInputErrorMessage("Enter a valid time");
    }

    /** Blank a zero amount so an untouched money field shows empty, not "0.00". */
    private static BigDecimal zeroToNull(BigDecimal amount) {
        return amount == null || amount.signum() == 0 ? null : amount;
    }

}

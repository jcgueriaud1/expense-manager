package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
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
import com.vaadin.flow.component.html.Paragraph;
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
 * <strong>always enabled</strong>; a missing field or invalid trip (a domain rule —
 * e.g. a return before the departure) surfaces in a top-of-dialog error summary and
 * generates nothing. A technical failure instead (e.g. no rate configured for the
 * trip year) is logged and shown as the generic error dialog, never leaked into the
 * summary (issue #86). New lines the trip produces are read-only and regenerated
 * whenever the trip is edited.
 *
 * <p>The <strong>destination-country picker</strong> (Phase 4.2) lists Finland
 * (domestic) plus every country that has a foreign per-diem rate for the trip's
 * year. Picking Finland keeps the domestic per-diem; picking a foreign country
 * costs the per-diem against that country's rate. The list is year-dependent, so it
 * refreshes whenever the departure's year changes.
 *
 * <p><strong>The preview shows what the trip inputs produce</strong> — the
 * <em>calculated</em> figures, never the effective ones (ADR-0024, issue #133).
 * Where a {@linkplain QuantityOverride Quantity Override} is in force the preview
 * line says so, so the two numbers on screen (the preview's and the report row's)
 * are never confusing; and because the preview is the calculation, a
 * {@code "3 → 5 days"} clearing warning reads against it. Saving an edit that moves
 * the calculated count for an overridden kind therefore <strong>clears that kind's
 * override</strong> — {@linkplain #confirmClearing behind a confirm} naming the kind,
 * the change and what is being discarded. Cancelling that confirm abandons the save
 * and leaves the trip, and the override, exactly as they were.
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

    /**
     * Validates the form, then commits the trip with its server-authoritative
     * allowances (dialog "Save") — asking first when the recalculation would clear a
     * Quantity Override (issue #133). Validation follows ADR-0020: never a disabled
     * button, so invalid input lands in the error summary instead.
     */
    private void onSave(Consumer<TravelDto> onSave) {
        errorSummary.clear();
        if (!binder.writeBeanIfValid(model)) {
            preview.setVisible(false);
            errorSummary.showValidationErrors(binder.validate());
            return;
        }
        try {
            // The calculated ("after") trip: the edited inputs with no override in
            // force, which is both what the preview shows and one half of the
            // comparison below.
            var calculated = costPreview.apply(inputFromModel());
            var cleared = clearedOverrides(calculated);
            if (cleared.isEmpty()) {
                commit(onSave, calculated, existingOverrides());
                return;
            }
            confirmClearing(cleared, () -> {
                try {
                    commit(onSave, calculated, surviving(cleared));
                } catch (DomainRuleException invalid) {
                    errorSummary.show(invalid.getMessage());
                }
            });
        } catch (DomainRuleException ex) {
            // An invalid trip (a domain rule — e.g. return before departure) lands in
            // the summary. A technical failure (e.g. no rate configured for the year)
            // propagates to the global UiErrorHandler as the generic dialog (issue #86).
            preview.setVisible(false);
            errorSummary.show(ex.getMessage());
        }
    }

    /**
     * Hands the edited trip out with {@code overrides} in force, re-costed
     * <strong>server-side</strong> so the committed trip carries the effective
     * figures the report row shows (the client never computes money). No override
     * left standing means the calculated trip already is that answer.
     */
    private void commit(Consumer<TravelDto> onSave, TravelDto calculated,
            Map<GeneratedLineKind, QuantityOverride> overrides) {
        onSave.accept(overrides.isEmpty() ? calculated
                : costPreview.apply(calculated.withQuantityOverrides(overrides)));
        close();
    }

    /**
     * Recomputes and shows the live allowance preview from the current inputs. The
     * amounts are always the server's ({@link #costPreview}) and always the
     * <em>calculated</em> ones — overrides are stripped, and annotated on the line
     * instead (ADR-0024). The preview hides while the dates are incomplete or the
     * range is invalid (Save surfaces any reason).
     */
    private void refreshPreview(LocalDateTime departure, LocalDateTime returnAt,
            String country, String destinations, String purpose, boolean notEligible,
            boolean freeLunch, boolean chargeToCustomer, BigDecimal kilometres,
            boolean payMeal, BigDecimal parkingFees) {
        if (departure == null || returnAt == null || country == null) {
            preview.setVisible(false);
            return;
        }
        var input = TravelDto.of(existing == null ? null : existing.id(), departure,
                returnAt, destinations, purpose, country, notEligible, freeLunch,
                chargeToCustomer, kilometres, payMeal, parkingFees);
        try {
            renderPreview(costPreview.apply(input));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            preview.setVisible(false);
        }
    }

    /**
     * The trip the validated form describes, carrying <strong>no</strong> Quantity
     * Override — so previewing it yields the calculated baseline the inputs alone
     * produce (ADR-0024). The overrides are put back, minus any this edit clears, when
     * the trip is {@linkplain #commit committed}.
     */
    private TravelDto inputFromModel() {
        return TravelDto.of(existing == null ? null : existing.id(),
                model.getDepartureAt(), model.getReturnAt(), model.getDestinations(),
                model.getPurpose(), model.getCountry(),
                model.isNotEligibleForAllowance(), model.isFreeLunch(),
                model.isChargeToCustomer(), model.getKilometres(),
                model.isPayMealAllowance(), model.getParkingFees());
    }

    /**
     * Shows the <em>calculated</em> trip outputs below the form: one line per non-zero
     * allowance/expense (per-diem, kilometre, meal, parking) with its breakdown, plus
     * a grand-total heading — or a "no allowances" note when the trip earns nothing
     * (Phase 4.3). A kind carrying a Quantity Override gets an extra note saying so,
     * so the user is not confused by the preview and the report showing different
     * numbers (ADR-0024).
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

        trip.generatedLines().forEach(line -> {
            addPreviewLine(generatedLineLabel(line.kind()), line.amount(),
                    line.comment());
            overrideNote(line.kind()).ifPresent(this::addPreviewNote);
        });
        // An override on a kind the trip earns nothing for has no preview line to
        // annotate, so it gets its own note — otherwise a suppressed line would
        // vanish from the dialog without explanation.
        existingOverrides().keySet().stream()
                .filter(kind -> trip.generatedLine(kind).isEmpty())
                .sorted()
                .forEach(kind -> overrideNote(kind).ifPresent(note ->
                        addPreviewNote(generatedLineLabel(kind) + " — " + note)));
        preview.setVisible(true);
    }

    /** Adds one "label — amount / explanation" preview line. */
    private void addPreviewLine(String label, BigDecimal amount, String explanation) {
        var line = new Span(label + ": " + formatEur(amount));
        line.addClassName("travel-preview-line");
        preview.add(line);
        if (explanation != null) {
            addPreviewNote(explanation);
        }
    }

    /** Adds one muted note under the preview line above it. */
    private void addPreviewNote(String text) {
        var detail = new Span(text);
        detail.addClassName("muted");
        detail.addClassName("muted-xs");
        preview.add(detail);
    }

    /**
     * "Overridden: 2 days on the report (the Wednesday was personal)" — what the
     * report shows for a kind whose count the user corrected, beside what the rules
     * calculate. Empty when the kind carries no override (ADR-0024).
     */
    private Optional<String> overrideNote(GeneratedLineKind kind) {
        var override = existingOverrides().get(kind);
        if (override == null) {
            return Optional.empty();
        }
        return Optional.of("Overridden: " + claimOf(kind, override)
                + " on the report (" + override.reason() + ")");
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

    // ------------------------------------- clearing an override on recalculation

    /**
     * One Quantity Override this trip edit invalidates: the kind, the calculated count
     * before and after the edit, and the correction being discarded (issue #133).
     */
    private record ClearedOverride(GeneratedLineKind kind, BigDecimal was,
            BigDecimal now, QuantityOverride override) {

        /**
         * Whether the recalculated count for this kind actually moved — the
         * <strong>only</strong> trigger (ADR-0024). Not "a field changed", not even "a
         * calculation-relevant field changed": a typo fixed in the purpose, or a
         * departure nudged by a quarter of an hour the day count absorbs, leaves this
         * false and the override alone. A confirm dialog that cries wolf gets clicked
         * through, which is exactly the silent data loss it exists to prevent.
         */
        boolean countMoved() {
            return was.compareTo(now) != 0;
        }

        /**
         * "Per diem allowance (full day): calculated 3 → 5 days. Your override
         * (2 days — "the Wednesday was personal") will be cleared." — the kind, the
         * change, and what is discarded, in one sentence.
         */
        String sentence() {
            return kind.label() + ": calculated " + count(was) + " → " + count(now) + " "
                    + kind.countNoun(now.longValue()) + ". Your override ("
                    + claimOf(kind, override) + " — \"" + override.reason()
                    + "\") will be cleared.";
        }
    }

    /**
     * The overrides this edit invalidates, in kind order — each kind whose
     * <em>calculated</em> count differs between the trip as it was and the trip as
     * edited.
     *
     * <p>Both sides are read by previewing the trip with its overrides stripped, which
     * is the {@link #costPreview} contract from issue #131 rather than a second service
     * method: one call for the trip before the edit, and the {@code calculated} the save
     * already has for after. A kind the trip no longer earns counts as {@code 0}, so an
     * edit that drops a line clears the override standing on it too.
     *
     * @param calculated the edited trip previewed with no override in force
     */
    private List<ClearedOverride> clearedOverrides(TravelDto calculated) {
        if (existingOverrides().isEmpty()) {
            return List.of();
        }
        TravelDto before = costPreview.apply(existing.withoutQuantityOverrides());
        return existingOverrides().entrySet().stream()
                .map(entry -> new ClearedOverride(entry.getKey(),
                        calculatedCount(before, entry.getKey()),
                        calculatedCount(calculated, entry.getKey()), entry.getValue()))
                .filter(ClearedOverride::countMoved)
                .sorted(Comparator.comparing(ClearedOverride::kind))
                .toList();
    }

    /** The count the rules awarded a kind, or zero if the trip earns no such line. */
    private static BigDecimal calculatedCount(TravelDto calculated,
            GeneratedLineKind kind) {
        return calculated.generatedLine(kind).map(GeneratedLineView::quantity)
                .orElse(BigDecimal.ZERO);
    }

    /** The edited trip's overrides — everything this edit does not clear. */
    private Map<GeneratedLineKind, QuantityOverride> surviving(
            List<ClearedOverride> cleared) {
        var overrides = new EnumMap<GeneratedLineKind, QuantityOverride>(
                GeneratedLineKind.class);
        overrides.putAll(existingOverrides());
        cleared.forEach(entry -> overrides.remove(entry.kind()));
        return overrides;
    }

    /** The Quantity Overrides standing on the trip being edited (none for a new one). */
    private Map<GeneratedLineKind, QuantityOverride> existingOverrides() {
        return existing == null ? Map.of() : existing.quantityOverrides();
    }

    /**
     * Asks before a trip edit discards a Quantity Override (ADR-0024 decision 6). The
     * override is a standing correction to a specific calculated number ("2 full days,
     * not the 3 you calculated"); once the edit moves that number the correction no
     * longer applies to anything, so it goes — but never silently, and never without
     * naming the change, because clearing it destroys the user's count <em>and</em> the
     * reason they gave for it.
     *
     * <p>Cancelling ({@code Keep editing}) is a full retreat: the trip is not saved,
     * the override stands, and the form is still there to be corrected or abandoned.
     */
    private void confirmClearing(List<ClearedOverride> cleared, Runnable proceed) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(cleared.size() == 1 ? "Clear your override?"
                : "Clear your overrides?");
        // Capped to a readable measure, like the other confirms: unconstrained, these
        // sentences render as ~100-character lines on a desktop viewport, the worst
        // possible shape for the one thing the user must actually read.
        dialog.setWidth("32rem");
        dialog.setMaxWidth("100%");
        dialog.add(new Paragraph("Saving this trip recalculates its allowances, and "
                + (cleared.size() == 1 ? "the count you corrected is"
                        : "the counts you corrected are")
                + " no longer what the rules produce:"));
        cleared.forEach(entry -> dialog.add(new Paragraph(entry.sentence())));

        var confirm = new Button("Clear and save trip", event -> {
            dialog.close();
            proceed.run();
        });
        confirm.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
        var cancel = new Button("Keep editing", event -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    /** The count an override claims — "2 days", or "no line" for a suppression. */
    private static String claimOf(GeneratedLineKind kind, QuantityOverride override) {
        return override.isSuppression() ? "no line"
                : count(override.quantity()) + " "
                        + kind.countNoun(override.quantity().longValue());
    }

    /** A count without trailing zeros — {@code "2"}, not {@code "2.00"}. */
    private static String count(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    /** Blank a zero amount so an untouched money field shows empty, not "0.00". */
    private static BigDecimal zeroToNull(BigDecimal amount) {
        return amount == null || amount.signum() == 0 ? null : amount;
    }

}

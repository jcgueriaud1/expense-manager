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
import com.vaadin.expensemanager.report.ui.TravelFormModel.DailyAllowance;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker.DateTimePickerI18n;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.generatedLineLabel;

/**
 * The focused modal editor for one trip (glossary: Travel Calculator, Phase
 * 4.2/4.3) — the "Add travel info" / "Edit travel info" dialog, built to
 * {@code docs/design/components/travel-editor-dialog.md} (frame {@code 253:10597}).
 *
 * <p>Collects the trip inputs in two sections of one two-column grid —
 * <em>Destinations</em> (departure / return date & time, destination country,
 * destinations, purpose) and <em>Expenses</em> (charge-to-customer, kilometres,
 * parking, the daily-allowance choice and the meal-allowance flag) — then draws the
 * allowance lines the trip earns as rows of the form ending in {@code Trip total},
 * recomputed server-side on every change, and commits the trip on <em>Save</em>.
 * The client sends only inputs — the money is always the server's (ADR-0006).
 *
 * <p>Eligibility is <strong>one</strong> choice with three answers: the
 * {@code Daily allowance} radio group binds to {@link TravelFormModel#getDailyAllowance()},
 * which maps onto the two domain flags — so {@code TravelDto} and the service are
 * untouched by the form's shape.
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

    /**
     * The departure/return pickers' granularity — also the gap the range bounds are
     * shifted by, since the trip rule is strict where {@code setMin}/{@code setMax}
     * are inclusive (issue #140).
     */
    private static final Duration TRIP_STEP = Duration.ofMinutes(15);

    /** The one wording for the range rule, wherever it surfaces. */
    private static final String RANGE_RULE = "Return must be after the departure";

    private final Binder<TravelFormModel> binder = new Binder<>();
    private final TravelFormModel model = new TravelFormModel();
    private final ErrorSummary errorSummary = new ErrorSummary();

    /**
     * The earned-lines block, a colspan-2 row of the form — transparent rows of the
     * grid rather than a box (travel-editor-dialog.md § The totals block). The rule
     * above it is {@link #totalsRule}, shown and hidden with it.
     */
    private final Div tripTotals = new Div();
    private final Hr totalsRule = formRule();

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
        // The add state is undrawn; the noun is the design's (#179 delta 11).
        setHeaderTitle(existing == null ? "Add travel info" : "Edit travel info");
        // 560 — the design's 556 rounded to the whole rem the line editor set.
        setWidth("35rem");

        var departure = new DateTimePicker("Departure");
        departure.setStep(TRIP_STEP);
        departure.setRequiredIndicatorVisible(true);
        departure.setI18n(dateTimeErrorMessages());
        var returnAt = new DateTimePicker("Return");
        returnAt.setStep(TRIP_STEP);
        returnAt.setRequiredIndicatorVisible(true);
        returnAt.setI18n(dateTimeErrorMessages());

        // Keep the range valid from the pickers themselves: once a departure is
        // chosen the return overlay can't reach back to it, and vice versa. The
        // bounds are shifted by one step because the trip rule is *strict* (a return
        // equal to the departure is no trip) while setMin/setMax are inclusive —
        // unshifted, the overlay offers the same instant on both sides and the user
        // walks into the rule with no way to see it coming (issue #140).
        // Registered before readBean so an edited trip's values initialise them.
        departure.addValueChangeListener(event ->
                returnAt.setMin(shiftedBy(event.getValue(), TRIP_STEP)));
        returnAt.addValueChangeListener(event ->
                departure.setMax(shiftedBy(event.getValue(), TRIP_STEP.negated())));

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

        // One choice with three answers, in place of the "not eligible" and "free
        // lunch" checkboxes (travel-editor-dialog.md § Eligibility): the two flags
        // are mutually exclusive (issue #93), and a radio group cannot be un-picked,
        // so the default (eligible, no free lunch) has to be an option of its own.
        // Vertical — Aura's default; three ~250px labels do not fit the row. The
        // group label the frame omits is the accessibility floor.
        var dailyAllowance = new RadioButtonGroup<DailyAllowance>("Daily allowance");
        dailyAllowance.setItems(DailyAllowance.values());
        dailyAllowance.setItemLabelGenerator(DailyAllowance::label);
        var chargeToCustomer = new Checkbox("Charge expenses from customer?");

        var kilometres = new BigDecimalField("Kilometre allowance (km)");
        kilometres.setPlaceholder("e.g. 120");
        var payMeal = new Checkbox("Pay meal allowance?");
        payMeal.setHelperText(
                "Only when the trip earns no daily allowance — selecting it marks "
                        + "the trip not eligible.");
        var parkingFees = new BigDecimalField("Parking fees (€)");
        parkingFees.setPlaceholder("e.g. 12.00");

        tripTotals.addClassName("trip-totals");
        setTotalsVisible(false);

        // The per-diem depends only on the two dates and the eligibility/free-lunch
        // flags, so preview it live as the user fills those in — no separate
        // "compute" step. Recompute server-side (money is never client-side) once
        // both dates are set; while incomplete or invalid the preview simply hides
        // (Save still surfaces any reason). Registered before readBean so an edited
        // trip previews immediately on open.
        Runnable recompute = () -> refreshPreview(departure.getValue(),
                returnAt.getValue(), country.getValue(), destinations.getValue(),
                purpose.getValue(), chosen(dailyAllowance), chargeToCustomer.getValue(),
                kilometres.getValue(), payMeal.getValue(), parkingFees.getValue());
        // The available countries depend on the departure year, so refresh them
        // first, then recompute the preview (which reads the country).
        departure.addValueChangeListener(event -> {
            refreshCountries.run();
            recompute.run();
        });
        returnAt.addValueChangeListener(event -> recompute.run());
        country.addValueChangeListener(event -> recompute.run());
        // Domain coupling (issue #93): a meal allowance (ateriakorvaus) is paid
        // only when no per-diem applies — so "Pay meal allowance" and an eligible
        // daily-allowance option sit in mutually exclusive worlds (invariant
        // payMeal ⟹ not eligible). The free-lunch half of the old cascade is now
        // structural: it is an option of the same radio group as "not eligible".
        // Nothing is ever disabled (ADR-0020); picking one control auto-corrects the
        // other. setValue is a no-op when unchanged, so the cascade converges.
        dailyAllowance.addValueChangeListener(event -> {
            if (event.getValue() != null && !event.getValue().notEligibleForAllowance()) {
                payMeal.setValue(false);     // per-diem applies → no meal allowance
            }
            recompute.run();
        });
        payMeal.addValueChangeListener(event -> {
            if (event.getValue()) {
                // meal allowance only when no per-diem
                dailyAllowance.setValue(DailyAllowance.NOT_ELIGIBLE);
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
        binder.forField(dailyAllowance).bind(TravelFormModel::getDailyAllowance,
                TravelFormModel::setDailyAllowance);
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

        // The design's two-section grid (travel-editor-dialog.md § Layout): the same
        // two-column grid and the same two gaps the line editor settled, so the two
        // dialogs share one rhythm. The one-column step below 24rem is the app's —
        // the frame draws no small-screen state, and two 242px fields do not
        // survive a phone (ADR-0020). Eyebrows, rules and the totals are rows OF the
        // grid, so its 20px row gap supplies every clearance the frame draws and
        // none of them carries a margin.
        var form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("24rem", 2));
        form.setColumnSpacing("var(--em-section-gap)");
        form.setRowSpacing("var(--em-card-padding)");
        form.add(sectionLabel("Destinations"), 2);
        form.add(departure, 1);
        form.add(returnAt, 1);
        form.add(country, 1);
        form.add(destinations, 1);
        form.add(purpose, 2);
        form.add(formRule(), 2);
        form.add(sectionLabel("Expenses"), 2);
        form.add(chargeToCustomer, 2);
        form.add(kilometres, 1);
        form.add(parkingFees, 1);
        form.add(dailyAllowance, 2);
        form.add(payMeal, 2);
        form.add(totalsRule, 2);
        form.add(tripTotals, 2);
        add(errorSummary, form);

        var save = new Button("Save", event -> onSave(onSave));
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
            setTotalsVisible(false);
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
            setTotalsVisible(false);
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
            String country, String destinations, String purpose,
            DailyAllowance dailyAllowance, boolean chargeToCustomer,
            BigDecimal kilometres, boolean payMeal, BigDecimal parkingFees) {
        if (departure == null || returnAt == null || country == null) {
            setTotalsVisible(false);
            return;
        }
        var input = TravelDto.of(existing == null ? null : existing.id(), departure,
                returnAt, destinations, purpose, country,
                dailyAllowance.notEligibleForAllowance(), dailyAllowance.freeLunch(),
                chargeToCustomer, kilometres, payMeal, parkingFees);
        try {
            renderTotals(costPreview.apply(input));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            setTotalsVisible(false);
        }
    }

    /**
     * The radio group's value, defaulting to the full allowance — a group whose value
     * the binder has not yet read is the untouched default, not "no answer".
     */
    private static DailyAllowance chosen(RadioButtonGroup<DailyAllowance> group) {
        return group.getValue() == null ? DailyAllowance.FULL : group.getValue();
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
     * Draws the <em>calculated</em> trip outputs as rows of the form: one row per
     * non-zero allowance/expense (per-diem, kilometre, meal, parking) — label left,
     * amount right, the breakdown ("120 km × €0.550/km") right-aligned under the
     * amount — ending in the {@code Trip total} row; or the "no allowances" line in
     * their place when the trip earns nothing (Phase 4.3). The rows are transparent,
     * 20px apart with a rule between them, and reuse the totals card's text classes
     * (travel-editor-dialog.md § The totals block, totals-card.md).
     *
     * <p>A kind carrying a Quantity Override gets a note saying so, so the user is not
     * confused by this block and the report showing different numbers (ADR-0024).
     */
    private void renderTotals(TravelDto trip) {
        tripTotals.removeAll();
        BigDecimal total = trip.generatedLines().stream()
                .map(GeneratedLineView::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        trip.generatedLines().forEach(line -> tripTotals.add(totalsRow(
                generatedLineLabel(line.kind()), line.amount(),
                notesFor(line.kind(), line.amount(), line.comment()))));
        // An override on a kind the trip earns nothing for has no row to annotate,
        // so it gets its own note — otherwise a suppressed line would vanish from
        // the dialog without explanation.
        existingOverrides().keySet().stream()
                .filter(kind -> trip.generatedLine(kind).isEmpty())
                .sorted()
                .forEach(kind -> overrideNote(kind).ifPresent(note ->
                        tripTotals.add(note(generatedLineLabel(kind) + " — " + note))));

        if (total.signum() == 0) {
            var none = new Span("No allowances for this trip");
            none.addClassName("totals-row-label");
            tripTotals.add(none);
        } else {
            tripTotals.add(tripTotalRow(total));
        }
        setTotalsVisible(true);
    }

    /** The block and the rule above it show and hide together. */
    private void setTotalsVisible(boolean visible) {
        totalsRule.setVisible(visible);
        tripTotals.setVisible(visible);
    }

    /** The line's breakdown, then the override note if one stands. */
    private List<String> notesFor(GeneratedLineKind kind, BigDecimal amount,
            String explanation) {
        var notes = new ArrayList<String>();
        breakdown(generatedLineLabel(kind), amount, explanation).ifPresent(notes::add);
        overrideNote(kind).ifPresent(notes::add);
        return notes;
    }

    /**
     * The generated line's explanation as the row's <em>breakdown</em> — the frame's
     * {@code 120 km × €0.550/km} under {@code €66.00}. The service composes the
     * string for a persisted row that must be self-describing on its own
     * ({@code "Kilometre allowance: 120 km × €0.550/km = €66.00"}, ADR-0024); here
     * the label sits at the row's start and the amount at its end, so what they
     * already say is trimmed, and an explanation that only restates the amount
     * ({@code "Meal allowance: €13.50"}) is dropped — the frame draws none there. A
     * prefix that adds something ({@code Foreign per-diem (Germany)}) stays.
     */
    private static Optional<String> breakdown(String label, BigDecimal amount,
            String explanation) {
        if (explanation == null) {
            return Optional.empty();
        }
        String eur = formatEur(amount);
        String text = explanation.strip();
        if (text.endsWith(" = " + eur)) {
            text = text.substring(0, text.length() - (" = " + eur).length());
        }
        int colon = text.indexOf(": ");
        String figures = colon < 0 ? text : text.substring(colon + 2);
        if (figures.isBlank() || figures.equals(eur)) {
            return Optional.empty();
        }
        if (text.startsWith(label + ": ")) {
            text = text.substring(label.length() + 2);
        }
        return Optional.of(text);
    }

    /**
     * One earned-line row: the label (secondary) at the start, and at the end the
     * amount with its notes right-aligned beneath it — the same label/value classes
     * the report's totals card uses, so a figure reads the same in both places.
     */
    private static HorizontalLayout totalsRow(String label, BigDecimal amount,
            List<String> notes) {
        var name = new Span(label);
        name.addClassName("totals-row-label");
        var value = new Span(formatEur(amount));
        value.addClassName("totals-row-value");

        var figures = new VerticalLayout(value);
        figures.setPadding(false);
        figures.setSpacing(false);
        figures.setWidth("auto");
        figures.setAlignItems(FlexComponent.Alignment.END);
        notes.forEach(text -> figures.add(note(text)));

        var row = new HorizontalLayout(name, figures);
        row.setWidthFull();
        row.setPadding(false);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return row;
    }

    /** The last row — the total the trip earns, in the totals card's grand figure. */
    private static HorizontalLayout tripTotalRow(BigDecimal total) {
        var label = new Span("Trip total");
        label.addClassName("totals-total-row");
        var value = new Span(formatEur(total));
        value.addClassName("totals-grand");
        var row = new HorizontalLayout(label, value);
        row.setWidthFull();
        row.setPadding(false);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return row;
    }

    /** One muted note — a breakdown or an override explanation. */
    private static Span note(String text) {
        var detail = new Span(text);
        detail.addClassName("muted");
        return detail;
    }

    /** A section eyebrow — uppercased in CSS, never in the string (screen readers spell it). */
    private static Span sectionLabel(String text) {
        var label = new Span(text);
        label.addClassName("section-label");
        return label;
    }

    /** A hairline rule between the form's sections; the grid's row gap spaces it. */
    private static Hr formRule() {
        var rule = new Hr();
        rule.addClassName("form-rule");
        return rule;
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
     *
     * <p>The min/max messages are the <em>range rule</em>: the two pickers bound each
     * other, so a departure past its max and a return before its min are the same
     * violation read from either end, and both say so in the same words the server
     * uses (issue #140).
     */
    private static DateTimePickerI18n dateTimeErrorMessages() {
        return new DateTimePickerI18n()
                .setIncompleteInputErrorMessage("Enter both a date and a time")
                .setBadInputErrorMessage("Enter a valid date and time")
                .setMinErrorMessage(RANGE_RULE)
                .setMaxErrorMessage(RANGE_RULE);
    }

    /**
     * {@code value} moved by {@code step}, or {@code null} when the field is empty —
     * which clears the opposite picker's bound rather than pinning it to an instant
     * derived from nothing.
     */
    private static LocalDateTime shiftedBy(LocalDateTime value, Duration step) {
        return value == null ? null : value.plus(step);
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

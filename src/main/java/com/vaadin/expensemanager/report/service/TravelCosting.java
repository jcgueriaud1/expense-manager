package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.vaadin.expensemanager.allowance.AllowanceAmount;
import com.vaadin.expensemanager.allowance.AllowanceCalculator;
import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.allowance.DomesticPerDiemDto;
import com.vaadin.expensemanager.allowance.DomesticPerDiemResult;
import com.vaadin.expensemanager.allowance.ForeignPerDiemDto;
import com.vaadin.expensemanager.allowance.KilometreAllowance;
import com.vaadin.expensemanager.allowance.KilometreRateDto;
import com.vaadin.expensemanager.allowance.MealAllowanceDto;
import com.vaadin.expensemanager.allowance.PerDiemComponent;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.GeneratedLineSpec;
import com.vaadin.expensemanager.report.domain.LineAmounts;
import com.vaadin.expensemanager.report.domain.QuantityOverride;

import org.springframework.stereotype.Component;

/**
 * Costs one trip: turns its inputs into the {@link GeneratedLineSpec}s it earns,
 * <strong>server-side</strong> (the client never sends money), applying any
 * {@linkplain QuantityOverride Quantity Override} on top (ADR-0024).
 *
 * <p>Extracted from {@code ExpenseReportService} so the <em>one</em> costing path
 * serves all three callers: the dialog preview, the save path, and
 * {@link ReportDtoMapper}, which needs the <em>calculated baseline</em> of an
 * overridden line when a persisted report is loaded (for the owner and the approver
 * alike). Without a shared seam the mapper would have to call back into the service
 * that owns it.
 *
 * <p><strong>An override rescales an earned line; it never conjures one.</strong>
 * {@link #applyOverride} refuses to touch a spec the rules did not award, and the
 * existing {@link GeneratedLineSpec#isEarned()} gate — which an unearned rule fails
 * on its <em>unit price</em>, not just its count — drops it anyway. That is what
 * preserves the per-diem/meal interlock the calculator enforces: a conjured per-diem
 * would otherwise sit beside a meal allowance with nothing to catch it. Both belts
 * are deliberate; removing either silently enables conjuring.
 *
 * <p>{@link AllowanceCalculator} stays pure and untouched (ADR-0024): it computes
 * what the rules award and has no opinion about corrections. The correction, and the
 * comment that documents it, are composed here.
 */
@Component
public class TravelCosting {

    // The expense types the five generated line kinds are filed under, resolved by
    // name (the accepted coupling — F-034). The three allowances are 0 %-VAT;
    // parking is filed under the VAT-bearing parking type at its own rate.
    private static final String PER_DIEM_EXPENSE_TYPE = "Travel allowance";
    private static final String KILOMETRE_EXPENSE_TYPE = "Kilometre allowance";
    private static final String MEAL_EXPENSE_TYPE = "Meal allowance";
    private static final String PARKING_EXPENSE_TYPE = "Parking/supplies/goods";
    private static final BigDecimal ZERO_VAT = BigDecimal.ZERO.setScale(2);

    private final ExpenseTypeRepository expenseTypeRepository;
    private final VatRateRepository vatRateRepository;
    private final AllowanceRateService allowanceRateService;

    /** Pure, stateless per-diem maths (ADR-0006) — a plain instance, not a bean. */
    private final AllowanceCalculator calculator = new AllowanceCalculator();

    public TravelCosting(ExpenseTypeRepository expenseTypeRepository,
            VatRateRepository vatRateRepository,
            AllowanceRateService allowanceRateService) {
        this.expenseTypeRepository = expenseTypeRepository;
        this.vatRateRepository = vatRateRepository;
        this.allowanceRateService = allowanceRateService;
    }

    /** The resolved reference data the generated lines are filed under, per save. */
    public record GeneratedLineTypes(ExpenseType perDiemType, ExpenseType kilometreType,
            ExpenseType mealType, ExpenseType parkingType, VatRate zeroVat) {
    }

    /**
     * Resolves the reference data once, so a report with several trips does not
     * repeat the lookups.
     */
    public GeneratedLineTypes resolveTypes() {
        return new GeneratedLineTypes(
                allowanceExpenseType(PER_DIEM_EXPENSE_TYPE),
                allowanceExpenseType(KILOMETRE_EXPENSE_TYPE),
                allowanceExpenseType(MEAL_EXPENSE_TYPE),
                allowanceExpenseType(PARKING_EXPENSE_TYPE),
                zeroVatRate());
    }

    /**
     * The trip with its server-computed generated lines filled in — the preview
     * (Phase 4.2/4.3). Overrides are <strong>applied</strong> (ADR-0024): the views
     * carry the effective quantity, the composed comment, the reason and the
     * calculated baseline, so a preview and the persisted report cannot disagree.
     * A caller wanting the calculated figures previews
     * {@link TravelDto#withoutQuantityOverrides()} instead.
     *
     * @throws IllegalArgumentException if the inputs are invalid or a rate is missing
     */
    public TravelDto withComputedLines(TravelDto input) {
        GeneratedLineTypes types = resolveTypes();
        var baselines = calculatedBaselines(input, types);
        var views = earnedLines(input, types).stream()
                .map(spec -> toView(spec, input, baselines)).toList();
        return input.withGeneratedLines(views, suppressedLines(input, baselines));
    }

    /**
     * The calculated (pre-override) spec per kind — the statutory baseline a row shows
     * beside an overridden figure, and the whole description of a line a zero override
     * {@linkplain QuantityOverride#suppresses() suppressed} (issue #132), which has no
     * earned spec of its own left to read. Empty when the trip carries no override, so
     * an ordinary trip costs nothing extra.
     */
    public Map<GeneratedLineKind, GeneratedLineSpec> calculatedBaselines(TravelDto trip) {
        return calculatedBaselines(trip, null);
    }

    private Map<GeneratedLineKind, GeneratedLineSpec> calculatedBaselines(TravelDto trip,
            GeneratedLineTypes resolved) {
        if (trip.quantityOverrides().isEmpty()) {
            return Map.of();
        }
        GeneratedLineTypes types = resolved == null ? resolveTypes() : resolved;
        var baselines = new EnumMap<GeneratedLineKind, GeneratedLineSpec>(
                GeneratedLineKind.class);
        earnedLines(trip.withoutQuantityOverrides(), types)
                .forEach(spec -> baselines.put(spec.kind(), spec));
        return baselines;
    }

    /**
     * The lines a zero override <strong>removed</strong> from the report, in kind
     * order (issue #132) — each one a view at quantity {@code 0} carrying the reason
     * and the statutory count it replaced.
     *
     * <p>These are not lines: nothing persists them and no total sums them, which is
     * exactly the point. They exist so the correction stays visible and reversible —
     * a suppressed kind whose row simply vanished would leave the user no way back to
     * "Reset to calculated" and no record that they had dropped anything. A kind the
     * rules never awarded has no baseline and so no row: an override that removes
     * nothing shows nothing.
     */
    public static List<GeneratedLineView> suppressedLines(TravelDto trip,
            Map<GeneratedLineKind, GeneratedLineSpec> baselines) {
        if (trip.quantityOverrides().isEmpty()) {
            return List.of();
        }
        List<GeneratedLineView> suppressed = new ArrayList<>(1);
        for (GeneratedLineKind kind : GeneratedLineKind.values()) {
            QuantityOverride override = trip.quantityOverrides().get(kind);
            GeneratedLineSpec baseline = baselines.get(kind);
            if (override == null || !override.suppresses() || baseline == null) {
                continue;
            }
            suppressed.add(GeneratedLineView
                    .of(kind, baseline.expenseType().getName(), baseline.unitPrice(),
                            BigDecimal.ZERO, baseline.vatRate().getValue(), null, null)
                    .withOverride(override.reason(), baseline.quantity()));
        }
        return suppressed;
    }

    /**
     * The generated lines a trip earns (ADR-0006): one {@link GeneratedLineSpec} per
     * non-zero output — the full-day and partial-day per-diem, kilometre, meal,
     * parking — recomputed server-side from the trip-year rates, with any Quantity
     * Override substituted for the calculated count. A kind that produced nothing is
     * omitted, so the aggregate removes any prior line of it. A missing rate for a
     * requested output is surfaced (ADR-0020).
     */
    public List<GeneratedLineSpec> earnedLines(TravelDto t, GeneratedLineTypes types) {
        if (t.departureAt() == null || t.returnAt() == null) {
            throw new IllegalArgumentException(
                    "Departure and return date & time are required");
        }
        List<GeneratedLineSpec> lines = new ArrayList<>(5);
        // The destination country decides how the per-diem is costed: a Finnish trip
        // against the domestic full/partial rates — two lines, one per rate (issue
        // #124) — a foreign one against the country's flat per-year rate, every day at
        // the full rate (never a silent Finnish default). Each line is an honest
        // days × per-day rate (ADR-0023), and both kinds share the per-diem subtotal.
        if (isForeign(t.country())) {
            addPerDiemSpec(lines, t, GeneratedLineKind.PER_DIEM_FULL, costForeign(t),
                    types);
        } else {
            DomesticPerDiemResult perDiem = costDomestic(t);
            addPerDiemSpec(lines, t, GeneratedLineKind.PER_DIEM_FULL, perDiem.full(),
                    types);
            addPerDiemSpec(lines, t, GeneratedLineKind.PER_DIEM_PARTIAL,
                    perDiem.partial(), types);
        }
        // The one genuine multiple (ADR-0023): the line carries the distance as its
        // quantity and the year's €/km rate as its unit price, so its card reads
        // "12.5 × €0.55 = €6.88" and the euros are unchanged.
        KilometreAllowance km = costKilometre(t);
        addSpec(lines, t, GeneratedLineKind.KILOMETRE, types.kilometreType(),
                types.zeroVat(), km.ratePerKm(), km.kilometres(), km.explanation());
        AllowanceAmount meal = costMeal(t);
        addFlatSpec(lines, t, GeneratedLineKind.MEAL, types.mealType(), types.zeroVat(),
                meal.amount(), meal.explanation());
        AllowanceAmount parking = calculator.parking(t.parkingFees());
        addFlatSpec(lines, t, GeneratedLineKind.PARKING, types.parkingType(),
                types.parkingType().getDefaultVatRate(), parking.amount(),
                parking.explanation());
        return lines;
    }

    // ------------------------------------------------------- override application

    /**
     * Adds a unit-price × quantity generated line, with the trip's override for that
     * kind applied, if the rule earned one.
     */
    private static void addSpec(List<GeneratedLineSpec> into, TravelDto t,
            GeneratedLineKind kind, ExpenseType type, VatRate rate, BigDecimal unitPrice,
            BigDecimal quantity, String comment) {
        var spec = applyOverride(
                new GeneratedLineSpec(kind, type, rate, unitPrice, quantity, comment),
                t.quantityOverrides().get(kind));
        if (spec.isEarned()) {
            into.add(spec);
        }
    }

    /**
     * Substitutes the overridden count for the calculated one and recomposes the
     * line comment (ADR-0024). The <em>unit price is untouched</em> — it is the law.
     *
     * <p>Returns the calculated spec unchanged when the kind is not overridable or
     * when the rules awarded nothing ({@code !isEarned()}): an override rescales an
     * earned line, it never conjures one, so overriding a per-diem the trip is not
     * eligible for does nothing — and cannot break the per-diem/meal interlock.
     *
     * <p>A count of <strong>zero</strong> needs nothing here either (issue #132): the
     * substituted spec is simply unearned, so {@link #addSpec} leaves it out and the
     * aggregate orphan-removes any line of that kind — suppression is the earned-line
     * gate doing its existing job, not a branch of its own.
     */
    private static GeneratedLineSpec applyOverride(GeneratedLineSpec calculated,
            QuantityOverride override) {
        if (override == null || !calculated.kind().isOverridable()
                || !calculated.isEarned()) {
            return calculated;
        }
        return new GeneratedLineSpec(calculated.kind(), calculated.expenseType(),
                calculated.vatRate(), calculated.unitPrice(), override.quantity(),
                overriddenComment(calculated, override));
    }

    /**
     * The comment a persisted overridden line carries: the <strong>effective</strong>
     * figures, then the baseline it replaced and the user's reason — one string, e.g.
     * {@code "Per diem allowance (full day): 2 × €54.00 = €108.00 — overridden from 3
     * days: the Wednesday was personal"}.
     *
     * <p>Quantity, gross and comment therefore agree in the database, so the row is
     * self-describing to an export, an audit, or the ProCountor hand-off — not only to
     * a viewer of the detail screen. The kind's {@linkplain GeneratedLineKind#label()
     * label} is the same one the row shows, so screen and database read alike.
     */
    private static String overriddenComment(GeneratedLineSpec calculated,
            QuantityOverride override) {
        BigDecimal quantity = override.quantity();
        BigDecimal gross = LineAmounts.grossOf(calculated.unitPrice(), quantity);
        return calculated.kind().label() + ": " + count(quantity) + " × "
                + eur(calculated.unitPrice()) + " = " + eur(gross)
                + " — overridden from " + count(calculated.quantity()) + " "
                + calculated.kind().countNoun(calculated.quantity().longValue()) + ": "
                + override.reason();
    }

    /** A count without trailing zeros — {@code "2"}, not {@code "2.00"}. */
    private static String count(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    private static String eur(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // --------------------------------------------------------------- the rules

    /**
     * Adds one per-diem line — {@code quantity = days}, {@code unit price = the
     * per-day rate} (ADR-0023, issue #124) — if the trip earned that component. A
     * component with no days is dropped by {@link #addSpec}, so a trip earning no
     * partial day generates no partial-day line and any prior one is removed.
     */
    private static void addPerDiemSpec(List<GeneratedLineSpec> into, TravelDto t,
            GeneratedLineKind kind, PerDiemComponent component,
            GeneratedLineTypes types) {
        addSpec(into, t, kind, types.perDiemType(), types.zeroVat(), component.perDay(),
                component.quantity(), component.explanation());
    }

    /** Adds a flat (quantity-1) generated line — meal or parking (ADR-0023). */
    private static void addFlatSpec(List<GeneratedLineSpec> into, TravelDto t,
            GeneratedLineKind kind, ExpenseType type, VatRate rate, BigDecimal amount,
            String comment) {
        addSpec(into, t, kind, type, rate, amount, BigDecimal.ONE, comment);
    }

    /** A preview view of an earned generated line — no id or receipt yet. */
    private static GeneratedLineView toView(GeneratedLineSpec spec, TravelDto trip,
            Map<GeneratedLineKind, GeneratedLineSpec> baselines) {
        var view = GeneratedLineView.of(spec.kind(), spec.expenseType().getName(),
                spec.unitPrice(), spec.quantity(), spec.vatRate().getValue(),
                spec.comment(), null);
        var override = trip.quantityOverrides().get(spec.kind());
        return override == null ? view
                : view.withOverride(override.reason(),
                        baselineQuantity(baselines.get(spec.kind())));
    }

    /** A baseline spec's count, or {@code null} when the baseline is unknown. */
    static BigDecimal baselineQuantity(GeneratedLineSpec baseline) {
        return baseline == null ? null : baseline.quantity();
    }

    /** Server-authoritative domestic per-diem for a trip's inputs (ADR-0006). */
    private DomesticPerDiemResult costDomestic(TravelDto t) {
        int year = t.departureAt().getYear();
        DomesticPerDiemDto rate = allowanceRateService.domesticPerDiem(year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No domestic per-diem rate is configured for " + year));
        return calculator.domesticPerDiem(t.departureAt(), t.returnAt(),
                t.notEligibleForAllowance(), t.freeLunch(), rate);
    }

    /**
     * Server-authoritative foreign per-diem for a trip's inputs (ADR-0006): the
     * destination country's flat per-year rate × the allowance-day count. A country
     * with no rate for the trip year surfaces a clear failure — never a silent
     * Finnish default (the "do better than ProCountor" payoff). The day-count
     * thresholds come from the year's domestic rate (the statutory 10 h / 6 h
     * thresholds shared by every per-diem).
     */
    private PerDiemComponent costForeign(TravelDto t) {
        int year = t.departureAt().getYear();
        ForeignPerDiemDto rate = allowanceRateService.foreignPerDiem(year, t.country())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No foreign per-diem rate is configured for " + t.country()
                                + " in " + year));
        DomesticPerDiemDto thresholds = allowanceRateService.domesticPerDiem(year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No per-diem rate is configured for " + year));
        return calculator.foreignPerDiem(t.departureAt(), t.returnAt(),
                t.notEligibleForAllowance(), rate, thresholds.fullDayMinHours(),
                thresholds.partialDayMinHours());
    }

    /** Whether a trip's country is a foreign destination (not domestic Finland). */
    private static boolean isForeign(String country) {
        return country != null && !country.isBlank()
                && !country.strip().equalsIgnoreCase(TravelDto.DOMESTIC_COUNTRY);
    }

    /** Server-authoritative kilometre allowance; needs a rate only when km &gt; 0. */
    private KilometreAllowance costKilometre(TravelDto t) {
        BigDecimal km = t.kilometres();
        if (km == null || km.signum() <= 0) {
            return calculator.kilometreAllowance(km, null);
        }
        int year = t.departureAt().getYear();
        KilometreRateDto rate = allowanceRateService.kilometreRate(year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No kilometre rate is configured for " + year));
        return calculator.kilometreAllowance(km, rate);
    }

    /**
     * Server-authoritative meal allowance; paid only when the trip both requests it
     * and is not eligible for a per-diem (the two are mutually exclusive, issue #93),
     * so the rate is looked up only when a meal allowance is actually payable.
     */
    private AllowanceAmount costMeal(TravelDto t) {
        boolean payable = t.payMealAllowance() && t.notEligibleForAllowance();
        if (!payable) {
            return calculator.mealAllowance(t.payMealAllowance(),
                    t.notEligibleForAllowance(), null);
        }
        int year = t.departureAt().getYear();
        MealAllowanceDto rate = allowanceRateService.mealAllowance(year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No meal allowance is configured for " + year));
        return calculator.mealAllowance(true, true, rate);
    }

    private ExpenseType allowanceExpenseType(String name) {
        return expenseTypeRepository
                .findFirstByNameIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(name)
                .orElseThrow(() -> new IllegalStateException("No active '" + name
                        + "' expense type is configured for generated travel lines"));
    }

    private VatRate zeroVatRate() {
        return vatRateRepository
                .findFirstByValueAndActiveTrueOrderByDisplayOrderAscIdAsc(ZERO_VAT)
                .orElseThrow(() -> new IllegalStateException(
                        "No active 0% VAT rate is configured for allowance lines"));
    }
}

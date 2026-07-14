package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allowance rate config service for the four per-year rate kinds the Travel
 * Calculator (Phase 4.2/4.3) costs against — domestic per-diem, kilometre rate,
 * meal allowance, and foreign per-diem by country (PRD 4.1/4.4, Phase 4.1).
 *
 * <p>Mirrors {@code ReferenceDataService} in shape: it owns the transaction
 * boundary and entity↔DTO mapping (ADR-0003) — entities never leave this class;
 * callers exchange the {@code *Dto} records.
 *
 * <p><strong>Two-layer authorization (ADR-0008).</strong> Every mutating
 * operation and the admin year listing is {@code @RolesAllowed("ADMIN")} — the
 * real enforcement point, independent of the ADMIN-only route hosting the
 * settings UI. The <em>per-year reads</em> the calculator consumes
 * ({@link #domesticPerDiem}, {@link #kilometreRate}, {@link #mealAllowance},
 * {@link #foreignPerDiems}, {@link #foreignPerDiem}) are
 * {@code @RolesAllowed("USER")}, since the calculator is a USER surface; an admin
 * reaches them through the {@code ADMIN > USER} hierarchy.
 *
 * <p><strong>History by year, not by an {@code active} flag</strong> — the
 * deliberate contrast with ADR-0018. Each year's decision is its own row;
 * {@link #addYear} seeds a new year from the most recent existing one and never
 * mutates a prior year's rows.
 */
@Service
public class AllowanceRateService {

    private final DomesticPerDiemRateRepository domesticRepository;
    private final KilometreRateRepository kilometreRepository;
    private final MealAllowanceRateRepository mealRepository;
    private final ForeignPerDiemRateRepository foreignRepository;

    public AllowanceRateService(DomesticPerDiemRateRepository domesticRepository,
            KilometreRateRepository kilometreRepository,
            MealAllowanceRateRepository mealRepository,
            ForeignPerDiemRateRepository foreignRepository) {
        this.domesticRepository = domesticRepository;
        this.kilometreRepository = kilometreRepository;
        this.mealRepository = mealRepository;
        this.foreignRepository = foreignRepository;
    }

    // ------------------------------------------------- per-year reads (USER)

    /** The domestic per-diem rate for a year, if configured (calculator input). */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public Optional<DomesticPerDiemDto> domesticPerDiem(int year) {
        return domesticRepository.findByYear(year).map(AllowanceRateService::toDto);
    }

    /** The kilometre rate for a year, if configured (calculator input). */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public Optional<KilometreRateDto> kilometreRate(int year) {
        return kilometreRepository.findByYear(year).map(AllowanceRateService::toDto);
    }

    /** The meal allowance for a year, if configured (calculator input). */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public Optional<MealAllowanceDto> mealAllowance(int year) {
        return mealRepository.findByYear(year).map(AllowanceRateService::toDto);
    }

    /** All foreign per-diems for a year, in country order (calculator input). */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public List<ForeignPerDiemDto> foreignPerDiems(int year) {
        return foreignRepository.findByYearOrderByCountryAsc(year).stream()
                .map(AllowanceRateService::toDto)
                .toList();
    }

    /** The foreign per-diem for a (year, country) pair, if configured (calculator lookup). */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public Optional<ForeignPerDiemDto> foreignPerDiem(int year, String country) {
        if (country == null || country.isBlank()) {
            return Optional.empty();
        }
        return foreignRepository.findByYearAndCountryIgnoreCase(year, country.strip())
                .map(AllowanceRateService::toDto);
    }

    // --------------------------------------------------- admin listing (ADMIN)

    /** The years that have allowance rates configured, newest first. */
    @RolesAllowed("ADMIN")
    @Transactional(readOnly = true)
    public List<Integer> availableYears() {
        return domesticRepository.findAllByOrderByYearDesc().stream()
                .map(DomesticPerDiemRate::getYear)
                .toList();
    }

    // ------------------------------------------------------- mutations (ADMIN)

    /**
     * Seeds a whole new year's rates from the most recent existing year, leaving
     * every prior year untouched. The copied figures are a starting point an
     * admin then corrects against that year's Verohallinto decision. Rejects a
     * year that already exists, or when no prior year exists to copy from.
     */
    @RolesAllowed("ADMIN")
    @Transactional
    public void addYear(int year) {
        if (domesticRepository.findByYear(year).isPresent()) {
            throw new IllegalArgumentException("Year " + year + " already has allowance rates");
        }
        int source = availableYears().stream().mapToInt(Integer::intValue).max()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existing year to copy rates from"));

        var domestic = domesticRepository.findByYear(source).orElseThrow();
        domesticRepository.save(new DomesticPerDiemRate(year, domestic.getFullDayAmount(),
                domestic.getPartialDayAmount(), domestic.getFullDayMinHours(),
                domestic.getPartialDayMinHours()));

        kilometreRepository.findByYear(source).ifPresent(km ->
                kilometreRepository.save(new KilometreRate(year, km.getAmountPerKm())));
        mealRepository.findByYear(source).ifPresent(meal ->
                mealRepository.save(new MealAllowanceRate(year, meal.getAmount())));
        foreignRepository.findByYearOrderByCountryAsc(source).forEach(fpd ->
                foreignRepository.save(new ForeignPerDiemRate(year, fpd.getCountry(), fpd.getAmount())));
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public DomesticPerDiemDto updateDomesticPerDiem(int year, BigDecimal fullDayAmount,
            BigDecimal partialDayAmount, int fullDayMinHours, int partialDayMinHours) {
        var rate = requireDomestic(year);
        rate.setFullDayAmount(normalizeMoney(fullDayAmount, "Full-day amount"));
        rate.setPartialDayAmount(normalizeMoney(partialDayAmount, "Partial-day amount"));
        rate.setFullDayMinHours(requireHours(fullDayMinHours, "Full-day hours"));
        rate.setPartialDayMinHours(requireHours(partialDayMinHours, "Partial-day hours"));
        if (rate.getPartialDayMinHours() >= rate.getFullDayMinHours()) {
            throw new IllegalArgumentException(
                    "Partial-day hours must be less than full-day hours");
        }
        return toDto(rate);
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public KilometreRateDto updateKilometreRate(int year, BigDecimal amountPerKm) {
        var rate = requireKilometre(year);
        rate.setAmountPerKm(normalizeRate(amountPerKm));
        return toDto(rate);
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public MealAllowanceDto updateMealAllowance(int year, BigDecimal amount) {
        var rate = requireMeal(year);
        rate.setAmount(normalizeMoney(amount, "Meal allowance"));
        return toDto(rate);
    }

    /** Creates a (year, country) foreign per-diem row; rejects a duplicate country. */
    @RolesAllowed("ADMIN")
    @Transactional
    public ForeignPerDiemDto addForeignPerDiem(int year, String country, BigDecimal amount) {
        var name = requireCountry(country);
        if (foreignRepository.findByYearAndCountryIgnoreCase(year, name).isPresent()) {
            throw new IllegalArgumentException(
                    country.strip() + " already has a per-diem for " + year);
        }
        var rate = new ForeignPerDiemRate(year, name, normalizeMoney(amount, "Amount"));
        return toDto(foreignRepository.save(rate));
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public ForeignPerDiemDto updateForeignPerDiem(Long id, BigDecimal amount) {
        var rate = foreignRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("No foreign per-diem with id " + id));
        rate.setAmount(normalizeMoney(amount, "Amount"));
        return toDto(rate);
    }

    // --------------------------------------------------------------- internals

    private DomesticPerDiemRate requireDomestic(int year) {
        return domesticRepository.findByYear(year).orElseThrow(
                () -> new IllegalArgumentException("No domestic per-diem for year " + year));
    }

    private KilometreRate requireKilometre(int year) {
        return kilometreRepository.findByYear(year).orElseThrow(
                () -> new IllegalArgumentException("No kilometre rate for year " + year));
    }

    private MealAllowanceRate requireMeal(int year) {
        return mealRepository.findByYear(year).orElseThrow(
                () -> new IllegalArgumentException("No meal allowance for year " + year));
    }

    /** Money is stored at scale 2 (ADR-0010); reject null/negative. */
    private static BigDecimal normalizeMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be zero or positive");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** A per-km rate is stored at scale 3; reject null/negative. */
    private static BigDecimal normalizeRate(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Rate must be zero or positive");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static int requireHours(int hours, String field) {
        if (hours <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return hours;
    }

    private static String requireCountry(String country) {
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required");
        }
        return country.strip();
    }

    private static DomesticPerDiemDto toDto(DomesticPerDiemRate r) {
        return new DomesticPerDiemDto(r.getId(), r.getYear(), r.getFullDayAmount(),
                r.getPartialDayAmount(), r.getFullDayMinHours(), r.getPartialDayMinHours());
    }

    private static KilometreRateDto toDto(KilometreRate r) {
        return new KilometreRateDto(r.getId(), r.getYear(), r.getAmountPerKm());
    }

    private static MealAllowanceDto toDto(MealAllowanceRate r) {
        return new MealAllowanceDto(r.getId(), r.getYear(), r.getAmount());
    }

    private static ForeignPerDiemDto toDto(ForeignPerDiemRate r) {
        return new ForeignPerDiemDto(r.getId(), r.getYear(), r.getCountry(), r.getAmount());
    }
}

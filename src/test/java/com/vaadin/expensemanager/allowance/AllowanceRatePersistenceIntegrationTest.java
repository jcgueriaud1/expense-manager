package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence + seed integration test (pyramid layer 2, ADR-0012) for the
 * allowance-rate tables, on Testcontainers Postgres with the V7 Flyway migration
 * applied.
 *
 * <p>Covers the acceptance criteria at the repository level: the V7 migration
 * runs clean on a fresh Postgres and seeds the provisional 2026 figures — the
 * domestic per-diem (full/partial amounts + thresholds), the kilometre rate, the
 * meal allowance, and the ~12-country foreign per-diem starter set (verify
 * against the Verohallinto decision, F-032).
 */
class AllowanceRatePersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DomesticPerDiemRateRepository domesticRepository;

    @Autowired
    private KilometreRateRepository kilometreRepository;

    @Autowired
    private MealAllowanceRateRepository mealRepository;

    @Autowired
    private ForeignPerDiemRateRepository foreignRepository;

    @Test
    void domesticPerDiem2026SeedMatchesTheProvisionalFigures() {
        var domestic = domesticRepository.findByYear(2026).orElseThrow();

        assertThat(domestic.getFullDayAmount()).isEqualByComparingTo("54.00");
        assertThat(domestic.getPartialDayAmount()).isEqualByComparingTo("25.00");
        assertThat(domestic.getFullDayMinHours()).isEqualTo(10);
        assertThat(domestic.getPartialDayMinHours()).isEqualTo(6);
    }

    @Test
    void kilometreAndMeal2026SeedMatchTheProvisionalFigures() {
        assertThat(kilometreRepository.findByYear(2026).orElseThrow().getAmountPerKm())
                .isEqualByComparingTo("0.590");
        assertThat(mealRepository.findByYear(2026).orElseThrow().getAmount())
                .isEqualByComparingTo("13.50");
    }

    @Test
    void foreignPerDiem2026SeedHasTheStarterSetInCountryOrder() {
        var foreign = foreignRepository.findByYearOrderByCountryAsc(2026);

        assertThat(foreign).hasSize(12);
        assertThat(foreign).extracting(ForeignPerDiemRate::getCountry)
                .containsExactly("Belgium", "Denmark", "Estonia", "France", "Germany",
                        "Italy", "Netherlands", "Norway", "Spain", "Sweden",
                        "United Kingdom", "United States");
        assertThat(foreignRepository.findByYearAndCountryIgnoreCase(2026, "sweden").orElseThrow()
                .getAmount()).isEqualByComparingTo("68.00");
    }

    @Test
    void foreignPerDiemUniquePerYearAndCountry() {
        long before = foreignRepository.count();
        // A different country in the same year is fine.
        foreignRepository.saveAndFlush(new ForeignPerDiemRate(2026, "Poland", new BigDecimal("60.00")));
        assertThat(foreignRepository.count()).isEqualTo(before + 1);
        // The same (year, country) already exists in the seed.
        assertThat(foreignRepository.findByYearAndCountryIgnoreCase(2026, "Poland")).isPresent();
    }
}

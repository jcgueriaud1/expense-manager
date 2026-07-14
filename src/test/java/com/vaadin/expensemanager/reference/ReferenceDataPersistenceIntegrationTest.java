package com.vaadin.expensemanager.reference;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence + seed integration test (pyramid layer 2, ADR-0012) for the
 * reference-data tables, on Testcontainers Postgres with Flyway applied.
 *
 * <p>Covers the acceptance criteria at the repository level: the V3 seed is
 * present and correct (the Finnish 2026 VAT figures and the six expense types
 * with their default rates, F-003), and the "active options" query returns only
 * active rows in display order while a deactivated row is <em>retained</em> in
 * the table, not deleted (ADR-0018 history semantics).
 */
class ReferenceDataPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VatRateRepository vatRateRepository;

    @Autowired
    private ExpenseTypeRepository expenseTypeRepository;

    @Test
    void vatRateSeedMatchesTheFinnish2026Figures() {
        var rates = vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc();

        assertThat(rates).extracting(VatRate::getValue)
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(
                        new java.math.BigDecimal("25.5"),
                        new java.math.BigDecimal("13.5"),
                        new java.math.BigDecimal("10"),
                        new java.math.BigDecimal("0"));
        assertThat(rates).allMatch(VatRate::isActive);
    }

    @Test
    void expenseTypeSeedHasCorrectNamesOrderAndDefaultRates() {
        var types = expenseTypeRepository.findAllByOrderByDisplayOrderAscIdAsc();

        // The Kilometre/Meal allowance types are the Phase 4.3 additions (V9),
        // both 0 %-VAT and distinct from "Travel allowance".
        assertThat(types).extracting(ExpenseType::getName).containsExactly(
                "Travel allowance", "Taxi/transport", "Accommodation",
                "Restaurant/meals", "Parking/supplies/goods", "Publications",
                "Kilometre allowance", "Meal allowance");
        assertThat(types).allMatch(ExpenseType::isActive);

        assertThat(types).extracting(t -> t.getDefaultVatRate().getValue())
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(
                        new java.math.BigDecimal("0"),      // Travel allowance
                        new java.math.BigDecimal("13.5"),   // Taxi/transport
                        new java.math.BigDecimal("13.5"),   // Accommodation
                        new java.math.BigDecimal("13.5"),   // Restaurant/meals
                        new java.math.BigDecimal("25.5"),   // Parking/supplies/goods
                        new java.math.BigDecimal("10"),     // Publications
                        new java.math.BigDecimal("0"),      // Kilometre allowance
                        new java.math.BigDecimal("0"));     // Meal allowance
    }

    @Test
    void activeQueryExcludesDeactivatedRatesButKeepsTheRow() {
        var reduced = vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(r -> r.getValue().compareTo(new java.math.BigDecimal("13.5")) == 0)
                .findFirst().orElseThrow();
        long totalBefore = vatRateRepository.count();

        reduced.setActive(false);
        vatRateRepository.saveAndFlush(reduced);

        assertThat(vatRateRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc())
                .as("active-options query excludes the deactivated rate")
                .noneMatch(r -> r.getId().equals(reduced.getId()));
        assertThat(vatRateRepository.findById(reduced.getId()))
                .as("the row is retained, not deleted (history)")
                .isPresent();
        assertThat(vatRateRepository.count()).isEqualTo(totalBefore);
    }

    @Test
    void activeQueryExcludesDeactivatedExpenseTypesButKeepsTheRow() {
        var type = expenseTypeRepository.findAllByOrderByDisplayOrderAscIdAsc().getFirst();
        long totalBefore = expenseTypeRepository.count();

        type.setActive(false);
        expenseTypeRepository.saveAndFlush(type);

        assertThat(expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc())
                .noneMatch(t -> t.getId().equals(type.getId()));
        assertThat(expenseTypeRepository.findById(type.getId())).isPresent();
        assertThat(expenseTypeRepository.count()).isEqualTo(totalBefore);
    }
}

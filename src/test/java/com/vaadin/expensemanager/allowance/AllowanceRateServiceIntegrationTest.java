package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.base.DomainRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service + method-security integration test (pyramid layer 2, ADR-0012,
 * ADR-0008) for {@link AllowanceRateService}, on Testcontainers Postgres.
 *
 * <p>Covers the DTO-boundary behaviour an admin drives through the settings
 * screen — per-year reads, edits, add-a-year, add-a-country — and the two-layer
 * authorization contract: the ADMIN-guarded mutations and the year listing
 * reject a plain USER, while the per-year reads the calculator needs are
 * USER-callable.
 */
class AllowanceRateServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AllowanceRateService service;

    // ------------------------------------------------------- per-year reads

    @Test
    @WithMockUser(roles = "ADMIN")
    void perYearReadsReturnSeeded2026Values() {
        assertThat(service.domesticPerDiem(2026)).get()
                .satisfies(d -> {
                    assertThat(d.fullDayAmount()).isEqualByComparingTo("54.00");
                    assertThat(d.partialDayAmount()).isEqualByComparingTo("25.00");
                    assertThat(d.fullDayMinHours()).isEqualTo(10);
                    assertThat(d.partialDayMinHours()).isEqualTo(6);
                });
        assertThat(service.kilometreRate(2026)).get()
                .satisfies(k -> assertThat(k.amountPerKm()).isEqualByComparingTo("0.550"));
        assertThat(service.mealAllowance(2026)).get()
                .satisfies(m -> assertThat(m.amount()).isEqualByComparingTo("13.50"));
        assertThat(service.foreignPerDiems(2026)).hasSize(12);
        assertThat(service.foreignPerDiem(2026, "Sweden")).get()
                .satisfies(f -> assertThat(f.amount()).isEqualByComparingTo("68.00"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void perYearReadsAreEmptyForAnUnconfiguredYear() {
        assertThat(service.domesticPerDiem(1999)).isEmpty();
        assertThat(service.foreignPerDiems(1999)).isEmpty();
        assertThat(service.foreignPerDiem(2026, "Atlantis")).isEmpty();
    }

    // --------------------------------------------------------------- edits

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDomesticPerDiemPersistsNormalizedValues() {
        var updated = service.updateDomesticPerDiem(2026, new BigDecimal("55"),
                new BigDecimal("26"), 11, 7);
        assertThat(updated.fullDayAmount()).isEqualByComparingTo("55.00");
        assertThat(updated.partialDayAmount()).isEqualByComparingTo("26.00");
        assertThat(updated.fullDayMinHours()).isEqualTo(11);
        assertThat(updated.partialDayMinHours()).isEqualTo(7);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDomesticPerDiemRejectsPartialHoursNotBelowFull() {
        assertThatThrownBy(() -> service.updateDomesticPerDiem(2026,
                new BigDecimal("54"), new BigDecimal("25"), 8, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateKilometreAndMealPersistAtTheirScales() {
        assertThat(service.updateKilometreRate(2026, new BigDecimal("0.61")).amountPerKm())
                .isEqualByComparingTo("0.610");
        assertThat(service.updateMealAllowance(2026, new BigDecimal("14")).amount())
                .isEqualByComparingTo("14.00");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRejectsNegativeAmount() {
        assertThatThrownBy(() -> service.updateMealAllowance(2026, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --------------------------------------------------------- add a year

    @Test
    @WithMockUser(roles = "ADMIN")
    void addYearCopiesLatestYearAndLeavesPriorUntouched() {
        service.addYear(2027);

        // 2027 is seeded from 2026's values (all four kinds copied).
        assertThat(service.domesticPerDiem(2027)).get()
                .satisfies(d -> assertThat(d.fullDayAmount()).isEqualByComparingTo("54.00"));
        assertThat(service.kilometreRate(2027)).isPresent();
        assertThat(service.mealAllowance(2027)).isPresent();
        assertThat(service.foreignPerDiems(2027)).hasSize(12);
        assertThat(service.availableYears()).containsExactly(2027, 2026);

        // Editing 2027 must not touch 2026.
        service.updateMealAllowance(2027, new BigDecimal("20"));
        assertThat(service.mealAllowance(2027).orElseThrow().amount()).isEqualByComparingTo("20.00");
        assertThat(service.mealAllowance(2026).orElseThrow().amount()).isEqualByComparingTo("13.50");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addYearRejectsAnExistingYear() {
        assertThatThrownBy(() -> service.addYear(2026))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------- copy a year

    @Test
    @WithMockUser(roles = "ADMIN")
    void copyYearSeedsANamedTargetFromANamedSource() {
        service.copyYear(2026, 2030);

        assertThat(service.domesticPerDiem(2030)).get()
                .satisfies(d -> assertThat(d.fullDayAmount())
                        .isEqualByComparingTo("54.00"));
        assertThat(service.kilometreRate(2030)).isPresent();
        assertThat(service.mealAllowance(2030)).isPresent();
        assertThat(service.foreignPerDiems(2030)).hasSize(12);
        assertThat(service.availableYears()).containsExactly(2030, 2026);

        // The source year is untouched — history by year, never mutated in place.
        service.updateMealAllowance(2030, new BigDecimal("20"));
        assertThat(service.mealAllowance(2026).orElseThrow().amount())
                .isEqualByComparingTo("13.50");
    }

    /**
     * Both refusals are {@code DomainRuleException}, so the editor dialog can put
     * them in its error summary instead of the generic error dialog (ADR-0020).
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void copyYearRejectsAnExistingTargetAndAMissingSource() {
        assertThatThrownBy(() -> service.copyYear(2026, 2026))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("2026 already has allowance rates");
        assertThatThrownBy(() -> service.copyYear(1999, 2030))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("1999 has no allowance rates to copy");
        assertThat(service.availableYears()).containsExactly(2026);
    }

    /** Add Year is Copy Year with the source defaulted, and shares its guards. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void addYearIsCopyYearFromTheLatestSource() {
        service.copyYear(2026, 2028);
        service.addYear(2029);

        // Seeded from 2028, the latest — not from 2026.
        service.updateMealAllowance(2028, new BigDecimal("20"));
        assertThat(service.availableYears()).containsExactly(2029, 2028, 2026);
        assertThat(service.mealAllowance(2029)).isPresent();
    }

    @Test
    @WithMockUser(roles = "USER")
    void copyYearIsAdminOnly() {
        assertThatThrownBy(() -> service.copyYear(2026, 2030))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------- add a country

    @Test
    @WithMockUser(roles = "ADMIN")
    void addForeignPerDiemCreatesAYearCountryRow() {
        int before = service.foreignPerDiems(2026).size();

        var created = service.addForeignPerDiem(2026, "Japan", new BigDecimal("85"));
        assertThat(created.country()).isEqualTo("Japan");
        assertThat(created.amount()).isEqualByComparingTo("85.00");
        assertThat(service.foreignPerDiems(2026)).hasSize(before + 1);
        assertThat(service.foreignPerDiem(2026, "Japan")).isPresent();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addForeignPerDiemRejectsADuplicateCountry() {
        assertThatThrownBy(() -> service.addForeignPerDiem(2026, "sweden", new BigDecimal("70")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateForeignPerDiemChangesTheAmount() {
        var sweden = service.foreignPerDiem(2026, "Sweden").orElseThrow();
        var updated = service.updateForeignPerDiem(sweden.id(), new BigDecimal("72"));
        assertThat(updated.amount()).isEqualByComparingTo("72.00");
    }

    // ----------------------------------------------------- authorization

    @Test
    @WithMockUser(roles = "USER")
    void userMayReadPerYearRatesButNotMutateNorListYears() {
        // The calculator's per-year reads are USER-callable.
        assertThat(service.domesticPerDiem(2026)).isPresent();
        assertThat(service.kilometreRate(2026)).isPresent();
        assertThat(service.mealAllowance(2026)).isPresent();
        assertThat(service.foreignPerDiems(2026)).isNotEmpty();
        assertThat(service.foreignPerDiem(2026, "Sweden")).isPresent();

        // Mutations and the admin year listing are ADMIN-only.
        assertThatThrownBy(() -> service.updateMealAllowance(2026, new BigDecimal("14")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.addYear(2028))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.addForeignPerDiem(2026, "Japan", new BigDecimal("85")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.availableYears())
                .isInstanceOf(AccessDeniedException.class);
    }
}

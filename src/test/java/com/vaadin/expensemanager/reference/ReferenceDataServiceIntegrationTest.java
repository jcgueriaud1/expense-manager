package com.vaadin.expensemanager.reference;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service + method-security integration test (pyramid layer 2, ADR-0012,
 * ADR-0008) for {@link ReferenceDataService}, on Testcontainers Postgres.
 *
 * <p>Covers the DTO-boundary service behaviour an admin drives through the
 * settings screen — create, edit, reorder, deactivate, set a type's default rate
 * — the active-options filtering, and the two-layer authorization contract: the
 * ADMIN-guarded mutations reject a plain USER, while the USER-level active reads
 * (the Phase 2.3 line editor's inputs) succeed.
 */
class ReferenceDataServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReferenceDataService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void activeVatRatesExcludeDeactivatedButAllVatRatesRetainsIt() {
        var reduced = service.allVatRates().stream()
                .filter(r -> r.value().compareTo(new BigDecimal("13.5")) == 0)
                .findFirst().orElseThrow();

        service.setVatRateActive(reduced.id(), false);

        assertThat(service.activeVatRates())
                .noneMatch(r -> r.id().equals(reduced.id()));
        assertThat(service.allVatRates())
                .anyMatch(r -> r.id().equals(reduced.id()) && !r.active());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createEditAndReorderVatRate() {
        int before = service.allVatRates().size();

        var created = service.createVatRate(new BigDecimal("8.5"));
        assertThat(created.value()).isEqualByComparingTo("8.50");
        assertThat(created.active()).isTrue();
        assertThat(service.allVatRates()).hasSize(before + 1);

        service.updateVatRate(created.id(), new BigDecimal("9"));
        assertThat(currentValue(service.allVatRates(), created.id()))
                .isEqualByComparingTo("9.00");

        // Newly created rate is last; moving it up must swap with its predecessor.
        var ordered = service.allVatRates();
        var predecessor = ordered.get(ordered.size() - 2);
        service.moveVatRate(created.id(), -1);

        var reordered = service.allVatRates();
        assertThat(reordered.get(reordered.size() - 2).id()).isEqualTo(created.id());
        assertThat(reordered.getLast().id()).isEqualTo(predecessor.id());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAndRepointExpenseTypeDefaultRate() {
        var rate0 = service.activeVatRates().stream()
                .filter(r -> r.value().compareTo(BigDecimal.ZERO) == 0).findFirst().orElseThrow();
        var rate25 = service.activeVatRates().stream()
                .filter(r -> r.value().compareTo(new BigDecimal("25.5")) == 0).findFirst().orElseThrow();

        var created = service.createExpenseType("Mileage", rate0.id(), "map-pin");
        assertThat(created.defaultVatRateId()).isEqualTo(rate0.id());
        assertThat(created.icon()).isEqualTo("map-pin");

        service.updateExpenseType(created.id(), "Mileage allowance", rate25.id(), null);

        var reloaded = service.allExpenseTypes().stream()
                .filter(t -> t.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Mileage allowance");
        assertThat(reloaded.defaultVatRateId()).isEqualTo(rate25.id());
        // Clearing the picker stores no glyph, rather than an empty string.
        assertThat(reloaded.icon()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpenseTypeRejectsBlankName() {
        var rate = service.activeVatRates().getFirst();
        assertThatThrownBy(() -> service.createExpenseType("  ", rate.id(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createVatRateRejectsNegativeValue() {
        assertThatThrownBy(() -> service.createVatRate(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    void userMayReadActiveOptionsButNotMutate() {
        assertThat(service.activeVatRates()).isNotEmpty();
        assertThat(service.activeExpenseTypes()).isNotEmpty();

        assertThatThrownBy(() -> service.createVatRate(new BigDecimal("5")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.allVatRates())
                .as("the admin listing (active + inactive) is ADMIN-only")
                .isInstanceOf(AccessDeniedException.class);
    }

    private static BigDecimal currentValue(List<VatRateDto> rates, Long id) {
        return rates.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow().value();
    }
}

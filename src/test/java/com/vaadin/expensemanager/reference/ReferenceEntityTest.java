package com.vaadin.expensemanager.reference;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Domain unit test (pyramid layer 1, ADR-0012): pure JUnit, no Spring, for the
 * reference-config entities. Confirms the small mutators the admin CRUD and the
 * reorder/deactivate flows depend on — new rows start active, deactivation flips
 * the flag without any other state change, and edits land.
 */
class ReferenceEntityTest {

    @Test
    void newVatRateStartsActive() {
        var rate = new VatRate(new BigDecimal("25.50"), 0);

        assertThat(rate.isActive()).isTrue();
        assertThat(rate.getValue()).isEqualByComparingTo("25.50");
        assertThat(rate.getDisplayOrder()).isZero();
    }

    @Test
    void deactivateOnlyFlipsTheFlag() {
        var rate = new VatRate(new BigDecimal("13.50"), 1);

        rate.setActive(false);

        assertThat(rate.isActive()).isFalse();
        // The value is untouched — history preservation is a flag, not a mutation.
        assertThat(rate.getValue()).isEqualByComparingTo("13.50");
    }

    @Test
    void newExpenseTypeStartsActiveWithItsDefaultRate() {
        var rate = new VatRate(new BigDecimal("13.50"), 1);
        var type = new ExpenseType("Taxi/transport", 1, rate);

        assertThat(type.isActive()).isTrue();
        assertThat(type.getName()).isEqualTo("Taxi/transport");
        assertThat(type.getDefaultVatRate()).isSameAs(rate);
    }

    @Test
    void expenseTypeCanBeRenamedAndRepointedToAnotherRate() {
        var reduced = new VatRate(new BigDecimal("13.50"), 1);
        var general = new VatRate(new BigDecimal("25.50"), 0);
        var type = new ExpenseType("Meals", 3, reduced);

        type.setName("Restaurant/meals");
        type.setDefaultVatRate(general);

        assertThat(type.getName()).isEqualTo("Restaurant/meals");
        assertThat(type.getDefaultVatRate()).isSameAs(general);
    }
}

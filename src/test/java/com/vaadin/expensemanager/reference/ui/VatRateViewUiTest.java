package com.vaadin.expensemanager.reference.ui;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link VatRateView} —
 * the ADMIN-only VAT-rate settings screen (issue #22, ADR-0008/0018).
 *
 * <p>The grid + add/edit + reorder + active-toggle mechanics are the shared
 * {@link com.vaadin.expensemanager.base.ui.ReferenceConfigEditor}, covered once
 * in {@link ReferenceConfigEditorUiTest} (driven through this view). This class
 * asserts only what is <em>kind-specific</em> to VAT rates: the two-layer access
 * control and the single percent-formatted rate column.
 */
class VatRateViewUiTest extends AbstractReferenceDataViewUiTest {

    private static final int RATE_COL = 0;

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeededRatesRendered() {
        navigate(VatRateView.class);
        // Percent formatting (trailing zeros trimmed) is the VAT-rate column's own concern.
        var rates = columnText(findGrid(VatRateDto.class), RATE_COL);
        assertThat(rates).containsExactly("25.5 %", "13.5 %", "10 %", "0 %");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).contains("vat-rates");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNoMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).doesNotContain("vat-rates");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl() {
        assertThatThrownBy(() -> navigate(VatRateView.class))
                .isInstanceOf(Exception.class);
    }
}

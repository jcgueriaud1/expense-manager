package com.vaadin.expensemanager.reference.ui;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.sidenav.SideNavItem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Server-side view test (pyramid layer 3, ADR-0012) for the two ADMIN-only
 * reference-data settings screens — {@link VatRateView} and
 * {@link ExpenseTypeView} (issue #22, ADR-0008/0018).
 *
 * <p>Verifies the acceptance criteria for both screens: an admin reaches each
 * route and its grid is populated from the seed; each auto-registered
 * {@code @Menu} entry shows for an admin and is hidden from a plain USER; and a
 * USER cannot reach either route by URL (so their mutating actions are never
 * exposed). Authenticates via {@link WithUserDetails} against the same seeded
 * records the form-stub uses.
 *
 * <p>Reuses the singleton-container pattern rather than
 * {@code AbstractIntegrationTest} because {@link SpringBrowserlessTest} occupies
 * the single inheritance slot (F-008). Grid content is asserted on the loaded
 * data-view items, not rendered text (F-018).
 */
@SpringBootTest
@ActiveProfiles("test")
class ReferenceDataViewsUiTest extends SpringBrowserlessTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesVatRateViewWithSeededRates() {
        navigate(VatRateView.class);
        assertThat(loadedGridItems()).anyMatch(item -> item.contains("value=25.5"));
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesExpenseTypeViewWithSeededTypes() {
        navigate(ExpenseTypeView.class);
        assertThat(loadedGridItems()).anyMatch(item -> item.contains("Restaurant/meals"));
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesBothMenuEntries() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).contains("vat-rates", "expense-types");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNeitherMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths())
                .doesNotContain("vat-rates")
                .doesNotContain("expense-types");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachVatRateRouteByUrl() {
        assertThatThrownBy(() -> navigate(VatRateView.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachExpenseTypeRouteByUrl() {
        assertThatThrownBy(() -> navigate(ExpenseTypeView.class))
                .isInstanceOf(Exception.class);
    }

    // The browserless tester doesn't render grid cell text (rows stream via the
    // data provider), so assert the grid's loaded items instead (F-018).
    private List<String> loadedGridItems() {
        var items = new ArrayList<String>();
        $(Grid.class).all().forEach(grid ->
                grid.getGenericDataView().getItems()
                        .forEach(item -> items.add(item.toString())));
        return items;
    }

    private List<String> menuItemPaths() {
        return $(SideNavItem.class).all().stream()
                .map(SideNavItem::getPath)
                .toList();
    }
}

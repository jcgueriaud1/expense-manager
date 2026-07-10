package com.vaadin.expensemanager.reference.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
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
 * Server-side view test (pyramid layer 3, ADR-0012) for the ADMIN-only
 * reference-data settings screen (issue #22, ADR-0008/0018).
 *
 * <p>Verifies the acceptance criteria: an admin reaches the route and sees the
 * seeded rates/types; the auto-registered {@code @Menu} entry shows for an admin
 * and is hidden from a plain USER; and a USER cannot reach the route by typing
 * its URL (so its mutating actions are never exposed to a USER). Authenticates
 * via {@link WithUserDetails} against the same seeded records the form-stub uses.
 *
 * <p>Reuses the singleton-container pattern rather than
 * {@code AbstractIntegrationTest} because {@link SpringBrowserlessTest} occupies
 * the single inheritance slot (F-008).
 */
@SpringBootTest
@ActiveProfiles("test")
class ReferenceDataViewUiTest extends SpringBrowserlessTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesRouteAndSeesSeededReferenceData() {
        var view = navigate(ReferenceDataView.class);

        assertThat(view.getElement().getTextRecursively()).contains("Reference data");

        // The browserless tester doesn't render grid cell text (rows stream via
        // the data provider), so assert the grids were populated from the seed
        // by inspecting their loaded items. Two grids: VAT rates and types.
        var loadedItems = new java.util.ArrayList<String>();
        $(com.vaadin.flow.component.grid.Grid.class).all().forEach(grid ->
                grid.getGenericDataView().getItems()
                        .forEach(item -> loadedItems.add(item.toString())));
        assertThat(loadedItems).anyMatch(item -> item.contains("value=25.5"));
        assertThat(loadedItems).anyMatch(item -> item.contains("Restaurant/meals"));
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesReferenceDataMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).contains("reference-data");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userDoesNotSeeReferenceDataMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).doesNotContain("reference-data");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachReferenceDataRouteByUrl() {
        // Route security rejects a USER navigating to the ADMIN-only settings
        // screen: the view never instantiates, so its mutating actions (add /
        // edit / reorder / deactivate) are never exposed to a USER.
        assertThatThrownBy(() -> navigate(ReferenceDataView.class))
                .isInstanceOf(Exception.class);
    }

    private java.util.List<String> menuItemPaths() {
        return $(SideNavItem.class).all().stream()
                .map(SideNavItem::getPath)
                .toList();
    }
}

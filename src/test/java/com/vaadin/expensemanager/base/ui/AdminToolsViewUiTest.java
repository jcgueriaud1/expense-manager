package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
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
 * Server-side view test (pyramid layer 3, ADR-0012) for the route-security layer
 * of two-layer authorization (ADR-0008, Phase 1.2).
 *
 * <p>Verifies the acceptance criteria that route security drives navigation UX:
 * the auto-generated side menu shows the stand-in ADMIN-only destination only to
 * an admin, and a plain USER cannot reach the {@code /admin} route by typing its
 * URL. Authenticates as the seeded admin and plain user via
 * {@link WithUserDetails} — the same records the form-stub logs in.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminToolsViewUiTest extends SpringBrowserlessTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesAdminRouteAndRunsPrivilegedOperation() {
        var view = navigate(AdminToolsView.class);

        var text = view.getElement().getTextRecursively();
        assertThat(text).contains("Admin tools");
        assertThat(text).contains("privileged-admin-operation-executed");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesAdminMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).contains("admin");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userDoesNotSeeAdminMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths())
                .doesNotContain("admin")
                .contains(""); // the always-visible Dashboard entry is present
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachAdminRouteByUrl() {
        // Navigation access control rejects a USER typing /admin: the target view
        // never instantiates (its constructor would run the privileged op), so
        // the navigation does not resolve to an AdminToolsView.
        assertThatThrownBy(() -> navigate(AdminToolsView.class))
                .isInstanceOf(Exception.class);
    }

    private java.util.List<String> menuItemPaths() {
        return $(SideNavItem.class).all().stream()
                .map(SideNavItem::getPath)
                .toList();
    }
}

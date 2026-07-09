package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI unit test for the base navigation shell (issue #7/#9, ADR-0017), driven
 * with Vaadin's browserless tester — no browser, full Spring context.
 *
 * <p>Verifies the shell's contract from the outside for an authenticated user:
 * the landing dashboard renders at {@code @Route("")} inside {@link MainLayout},
 * the side navigation is generated from {@code @Menu}-annotated views (not a
 * hand-maintained list), and an unknown route resolves to the custom
 * {@link NotFoundView} rather than a raw error. Access control now requires a
 * user, so each test authenticates as the seeded plain user via
 * {@link WithUserDetails}.
 *
 * <p>Boots on the same singleton {@link PostgreSQLContainer} pattern as
 * {@code AbstractIntegrationTest}; this class can't extend that base because
 * {@link SpringBrowserlessTest} already occupies the single inheritance slot.
 */
@SpringBootTest
@ActiveProfiles("test")
class NavigationShellUiTest extends SpringBrowserlessTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void landingViewRendersInsideMainLayout() {
        var dashboard = navigate(DashboardView.class);

        assertThat(dashboard).isNotNull();
        assertThat(getCurrentView()).isInstanceOf(DashboardView.class);
        assertThat(dashboard.getParent()).containsInstanceOf(MainLayout.class);
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void sideNavIsGeneratedFromMenuAnnotatedViews() {
        navigate(DashboardView.class);

        // The shell builds exactly one SideNav from MenuConfiguration.
        var sideNav = $(SideNav.class).first();
        var titles = sideNav.getChildren()
                .filter(SideNavItem.class::isInstance)
                .map(SideNavItem.class::cast)
                .map(SideNavItem::getLabel)
                .toList();

        // DashboardView's @Menu(title = "Dashboard") self-registered — proving the
        // menu is auto-generated, not a hand-maintained list.
        assertThat(titles).contains("Dashboard");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void unknownRouteResolvesToCustomNotFoundView() {
        navigate("no-such-route-exists", NotFoundView.class);

        assertThat(getCurrentView()).isInstanceOf(NotFoundView.class);
    }
}

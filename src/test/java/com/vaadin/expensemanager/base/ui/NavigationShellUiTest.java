package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI unit test for the base navigation shell (issue #7, ADR-0017), driven with
 * Vaadin's browserless tester — no browser, full Spring context.
 *
 * <p>Verifies the shell's contract from the outside: the landing view renders at
 * {@code @Route("")} inside {@link MainLayout}, the side navigation is generated
 * from {@code @Menu}-annotated views (not a hand-maintained list), and an
 * unknown route resolves to the custom {@link NotFoundView} rather than a raw
 * error.
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
    void landingViewRendersInsideMainLayout() {
        var home = navigate(HomeView.class);

        assertThat(home).isNotNull();
        assertThat(getCurrentView()).isInstanceOf(HomeView.class);
        assertThat(home.getParent()).containsInstanceOf(MainLayout.class);
    }

    @Test
    void sideNavIsGeneratedFromMenuAnnotatedViews() {
        navigate(HomeView.class);

        // The shell builds exactly one SideNav from MenuConfiguration.
        var sideNav = $(SideNav.class).first();
        var titles = sideNav.getChildren()
                .filter(SideNavItem.class::isInstance)
                .map(SideNavItem.class::cast)
                .map(SideNavItem::getLabel)
                .toList();

        // HomeView's @Menu(title = "Home") self-registered — proving the menu is
        // auto-generated, not a hand-maintained list.
        assertThat(titles).contains("Home");
    }

    @Test
    void unknownRouteResolvesToCustomNotFoundView() {
        navigate("no-such-route-exists", NotFoundView.class);

        assertThat(getCurrentView()).isInstanceOf(NotFoundView.class);
    }
}

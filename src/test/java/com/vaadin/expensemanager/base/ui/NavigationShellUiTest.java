package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Objects;

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

        // The top group is the first SideNav, built from MenuConfiguration.
        var titles = itemLabels($(SideNav.class).first());

        // DashboardView's @Menu(title = "Dashboard") self-registered — proving the
        // menu is auto-generated, not a hand-maintained list.
        assertThat(titles).contains("Dashboard");
    }

    /**
     * Issue #91: the drawer is grouped into sections. The everyday views lead
     * in an unlabelled top group; the administrative reference tables and user
     * management follow as their own labelled sections at the end.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminDrawerIsGroupedIntoSectionsWithAdminAtTheEnd() {
        navigate(DashboardView.class);

        var navs = $(SideNav.class).all();

        // Section labels, in drawer order: top group unlabelled, admin last.
        var labels = navs.stream().map(SideNav::getLabel).toList();
        assertThat(labels)
                .containsExactly(null, "Reference tables", "User management");

        // Everyday views lead the top group, in Dashboard → My reports →
        // Approvals → Review history order; no reference/user-management views
        // leak into it.
        assertThat(itemLabels(navs.get(0)))
                .containsExactly("Dashboard", "My reports", "Approvals", "Review history");

        // The reference tables and user management are their own sections.
        assertThat(itemLabels(navs.get(1)))
                .containsExactly("VAT rates", "Expense types", "Allowance rates");
        assertThat(itemLabels(navs.get(2))).containsExactly("Users");
    }

    /**
     * A section with no accessible entries renders nothing: the plain user, who
     * can reach neither the reference tables nor user management, sees only the
     * unlabelled top group.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void plainUserSeesNoAdminSections() {
        navigate(DashboardView.class);

        var navs = $(SideNav.class).all();

        assertThat(navs).hasSize(1);
        assertThat(navs.get(0).getLabel()).isNull();
        assertThat(itemLabels(navs.get(0))).containsExactly("Dashboard", "My reports");
    }

    /**
     * The drawer toggle follows the drawer: the shell carries one toggle in each
     * surface and hides the wrong one in CSS off the {@code drawer-opened}
     * attribute. Which one is <em>visible</em> is a browser concern (covered by
     * the Playwright pass); what this layer can pin down is the contract the CSS
     * depends on — both toggles exist, each in the right surface, carrying the
     * class name the stylesheet keys off and its own state-specific label.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void shellCarriesAToggleInBothTheDrawerAndTheNavbar() {
        navigate(DashboardView.class);

        var toggles = $(DrawerToggle.class).all();

        assertThat(toggles).hasSize(2);
        assertThat(toggles).extracting(DrawerToggle::getClassName)
                .containsExactlyInAnyOrder("shell-drawer-toggle",
                        "shell-navbar-toggle");
        assertThat(toggles).extracting(toggle -> toggle.getAriaLabel().orElseThrow())
                .containsExactlyInAnyOrder("Collapse menu", "Expand menu");
    }

    /**
     * Every {@code @Menu} icon must name a vendored Lucide SVG that actually
     * exists. These paths can't come from the {@link LucideIcon} enum — an
     * annotation needs a compile-time constant — so this is what keeps the
     * literals honest. A typo here renders no icon at all, silently.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void everyMenuIconResolvesToAVendoredSvg() {
        navigate(DashboardView.class);

        var icons = MenuConfiguration.getMenuEntries().stream()
                .map(MenuEntry::icon)
                .filter(Objects::nonNull)
                .toList();

        assertThat(icons).isNotEmpty();
        assertThat(icons).allSatisfy(icon -> assertThat(getClass().getClassLoader()
                .getResource("META-INF/resources/" + icon))
                .as("@Menu icon %s has no file behind it", icon)
                .isNotNull());
    }

    private static List<String> itemLabels(SideNav nav) {
        return nav.getChildren()
                .filter(SideNavItem.class::isInstance)
                .map(SideNavItem.class::cast)
                .map(SideNavItem::getLabel)
                .toList();
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void unknownRouteResolvesToCustomNotFoundView() {
        navigate("no-such-route-exists", NotFoundView.class);

        assertThat(getCurrentView()).isInstanceOf(NotFoundView.class);
    }
}

package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.approval.ui.ApprovalQueueView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.user.ui.UserManagementView;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;
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
 * UI unit test for the base navigation shell (issue #7/#9, ADR-0025), driven with
 * Vaadin's browserless tester — no browser, full Spring context.
 *
 * <p>Verifies the shell's contract from the outside for an authenticated user:
 * the landing dashboard renders at {@code @Route("")} inside {@link MainLayout},
 * the header menu offers the three top-level destinations with Admin gated by
 * role, the administrative screens are {@link AdminLayout} sub-tabs generated
 * from {@code @Menu} (not a hand-maintained list), and an unknown route resolves
 * to the custom {@link NotFoundView} rather than a raw error. Access control
 * requires a user, so each test authenticates via {@link WithUserDetails}.
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

    /** "" is the reports list now — there is no dashboard (ADR-0026). */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void landingViewIsMyReportsInsideMainLayout() {
        var landing = navigate("", MyReportsView.class);

        assertThat(landing).isNotNull();
        assertThat(landing.getParent()).containsInstanceOf(MainLayout.class);
    }

    /**
     * A plain user gets no menu at all: their reports are the only screen they can
     * reach and also where "" lands, so a menu would be one item pointing at the
     * page they are already on (ADR-0026). Nothing in the shell checks the role to
     * decide this — the access-filtered {@code @Menu} set simply has one entry.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void plainUserGetsNoMenu() {
        navigate(MyReportsView.class);

        assertThat(headerMenuLabels()).isEmpty();
    }

    /** An admin also gets the two grouped destinations. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesTheGroupedDestinations() {
        navigate(MyReportsView.class);

        assertThat(headerMenuLabels()).containsExactly("My reports",
                "Reference tables", "Admin");
    }

    /**
     * The admin screens are sub-tabs of one destination, generated from their
     * {@code @Menu} entries in {@code order} — so an administrative view still
     * self-registers its navigation by annotating itself, with no edit to the
     * shell.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminScreensAreSubTabsGeneratedFromMenuAnnotations() {
        var approvals = navigate(ApprovalQueueView.class);

        // AdminLayout renders the screen into a slot of its own, so it is an
        // ancestor rather than the immediate parent.
        assertThat(ancestors(approvals)).hasAtLeastOneElementOfType(AdminLayout.class);
        assertThat(tabLabels()).containsExactly("Approvals", "Review history",
                "Users");
    }

    /**
     * The reference tables are their own destination, not a corner of Admin — a
     * screen's group is whichever layout its {@code @Route} names, so this is
     * what keeps the two sets apart.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void referenceTablesAreTheirOwnTabbedDestination() {
        var vatRates = navigate(VatRateView.class);

        assertThat(ancestors(vatRates)).hasAtLeastOneElementOfType(ReferenceLayout.class);
        assertThat(ancestors(vatRates)).doesNotHaveAnyElementsOfTypes(AdminLayout.class);
        assertThat(tabLabels()).containsExactly("VAT rates", "Expense types",
                "Allowance rates");
    }

    /**
     * The selected tab follows the route, not a click — arriving by deep link has
     * to light the right tab, which is what makes the admin screens individually
     * addressable rather than reachable only by clicking through.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void deepLinkingToAnAdminScreenSelectsItsTab() {
        navigate(UserManagementView.class);

        var tabs = $(Tabs.class).first();
        assertThat(tabs.getSelectedTab()).isNotNull();
        assertThat(label(tabs.getSelectedTab())).isEqualTo("Users");
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
        navigate(MyReportsView.class);

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

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void unknownRouteResolvesToCustomNotFoundView() {
        navigate("no-such-route-exists", NotFoundView.class);

        assertThat(getCurrentView()).isInstanceOf(NotFoundView.class);
    }

    /** Every component above {@code component} in the tree, nearest first. */
    private static List<com.vaadin.flow.component.Component> ancestors(
            com.vaadin.flow.component.Component component) {
        var chain = new java.util.ArrayList<com.vaadin.flow.component.Component>();
        for (var parent = component.getParent(); parent.isPresent();
                parent = parent.get().getParent()) {
            chain.add(parent.get());
        }
        return chain;
    }

    /** The header menu's link texts, in order. */
    private List<String> headerMenuLabels() {
        return $(Nav.class).first().getChildren()
                .filter(RouterLink.class::isInstance)
                .map(RouterLink.class::cast)
                .map(RouterLink::getText)
                .toList();
    }

    /** The admin sub-tab labels, in order. */
    private List<String> tabLabels() {
        return $(Tabs.class).first().getChildren()
                .filter(Tab.class::isInstance)
                .map(Tab.class::cast)
                .map(NavigationShellUiTest::label)
                .toList();
    }

    /** A tab's label — it wraps a RouterLink, so the text is one level down. */
    private static String label(Tab tab) {
        return tab.getChildren()
                .filter(RouterLink.class::isInstance)
                .map(RouterLink.class::cast)
                .map(RouterLink::getText)
                .findFirst()
                .orElseGet(tab::getLabel);
    }
}

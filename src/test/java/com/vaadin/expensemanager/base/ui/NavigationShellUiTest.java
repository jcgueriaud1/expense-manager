package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.component.sidenav.SideNav;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI unit test for the redesigned app shell (issue #146, ADR-0017), driven with
 * Vaadin's browserless tester — no browser, full Spring context.
 *
 * <p>Verifies the shell's contract from the outside for an authenticated user:
 * the landing dashboard renders inside {@link MainLayout}, the top bar carries
 * the three hand-authored {@link NavGroup} links filtered by the user's access,
 * the current group follows the <em>group</em> rather than the route, the view
 * chooses its own header state, and everything the drawer used to hold is
 * reachable from the avatar menu.
 *
 * <p>What it deliberately cannot check is appearance — the coral, the pill, the
 * 900px column and the 360px behaviour are all visual, and are verified in the
 * browser instead (see {@code docs/manual-verification.md}).
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
        assertThat(dashboard.findAncestor(MainLayout.class)).isNotNull();
    }

    /**
     * The structural half of the redesign: the drawer shell is gone, not merely
     * restyled. Nothing in the app may still be rendering one.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theDrawerShellIsGone() {
        navigate(DashboardView.class);

        assertThat($(AppLayout.class).all()).isEmpty();
        assertThat($(SideNav.class).all()).isEmpty();
        assertThat($(AppHeader.class).all()).hasSize(1);
    }

    /** Eight views, three links — and the groups are in the design's order. */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesTheThreeDesignedGroups() {
        navigate(DashboardView.class);

        assertThat(navLabels())
                .containsExactly("My Expenses", "Admin Tasks", "Reference Tables");
    }

    /**
     * The Reference Tables pill is a plain link since #169, not a menu button:
     * its three routes are reached through {@code ReferenceTabs} inside the
     * content column, so the group has one destination and the shell already
     * rendered a one-entry group as a link. Admin Tasks still opens a menu, which
     * is why this asserts the pill's own type rather than counting ContextMenus.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void theReferenceTablesPillIsAPlainLinkWithNoMenuBehindIt() {
        navigate(DashboardView.class);

        var pill = navPills().stream()
                .filter(p -> "Reference Tables"
                        .equals(p.getElement().getTextRecursively()))
                .findFirst()
                .orElseThrow();

        assertThat(pill).isInstanceOf(RouterLink.class);
        assertThat(((RouterLink) pill).getHref()).isEqualTo("vat-rates");
        // No ContextMenu is attached to it — the one left in the bar is Admin
        // Tasks', and a menu on this pill is exactly what #169 removed.
        assertThat($(ContextMenu.class).all())
                .allSatisfy(menu -> assertThat(menu.getTarget()).isNotSameAs(pill));
    }

    /**
     * A group whose every view is off-limits renders nothing at all — the plain
     * user is left with the one group they can use.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void plainUserSeesOnlyTheGroupTheyCanUse() {
        navigate(DashboardView.class);

        assertThat(navLabels()).containsExactly("My Expenses");
    }

    /**
     * The acceptance criterion the old per-route nav could not express: sitting
     * on a report's detail page keeps <em>My Expenses</em> current, even though
     * that route is not an entry of its own.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void currentGroupFollowsTheGroupNotTheRoute() {
        navigate(MyReportsView.class);
        assertThat(currentNavLabel()).isEqualTo("My Expenses");

        navigate("report", com.vaadin.expensemanager.report.ui.ReportDetailView.class);
        assertThat(currentNavLabel()).isEqualTo("My Expenses");
    }

    /** The dashboard has no nav item in the design, so no group is current. */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void theDashboardLeavesEveryGroupIdle() {
        navigate(DashboardView.class);

        assertThat(currentNavLabel()).isNull();
    }

    /**
     * Which header a view gets is the view's choice: the report list asks for
     * the tall greeting variant, everything else takes the plain bar.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void theViewChoosesItsHeaderVariant() {
        navigate(MyReportsView.class);
        assertThat(headerClasses()).contains(HeaderState.HOME.className());

        navigate(DashboardView.class);
        assertThat(headerClasses())
                .contains(HeaderState.DEFAULT.className())
                .doesNotContain(HeaderState.HOME.className());
    }

    /**
     * Everything the drawer used to hold — the name, the colour-scheme switcher
     * and sign-out — is still reachable, now from behind the avatar (#145
     * decision 1). Dark mode is a requirement, so the switcher could not simply
     * be dropped with the navbar that held it.
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void theAvatarMenuHoldsIdentityThemeAndSignOut() {
        navigate(DashboardView.class);

        assertThat(accountMenuLabels())
                .contains("Demo User", ThemeSwitcher.LABEL, "Sign out");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void unknownRouteResolvesToCustomNotFoundView() {
        navigate("no-such-route-exists", NotFoundView.class);

        assertThat(getCurrentView()).isInstanceOf(NotFoundView.class);
    }

    // ---- helpers -----------------------------------------------------------

    /** The nav pills, in bar order, whatever element type each renders as. */
    private List<Component> navPills() {
        return $(AppHeader.class).first().getChildren()
                .flatMap(Component::getChildren)
                .filter(child -> child.getElement().getClassList().contains("app-nav"))
                .flatMap(Component::getChildren)
                .toList();
    }

    private List<String> navLabels() {
        return navPills().stream().map(pill -> pill.getElement().getTextRecursively()).toList();
    }

    /** The label of the group marked current, or {@code null} if none is. */
    private String currentNavLabel() {
        return navPills().stream()
                .filter(pill -> "page"
                        .equals(pill.getElement().getAttribute("aria-current")))
                .map(pill -> pill.getElement().getTextRecursively())
                .findFirst()
                .orElse(null);
    }

    private List<String> headerClasses() {
        return List.copyOf($(AppHeader.class).first().getElement().getClassList());
    }

    private List<String> accountMenuLabels() {
        return $(com.vaadin.flow.component.menubar.MenuBar.class).all().stream()
                .flatMap(bar -> bar.getItems().stream())
                .flatMap(item -> item.getSubMenu().getItems().stream())
                .map(item -> item.getElement().getTextRecursively())
                .toList();
    }
}

package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.allowance.ui.AllowanceRatesView;
import com.vaadin.expensemanager.approval.ui.ApprovalQueueView;
import com.vaadin.expensemanager.approval.ui.ReviewHistoryView;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.ui.UserManagementView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.spring.security.AuthenticationContext;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The three editorial groups of the top navigation (issue #146).
 *
 * <p><strong>Hand-authored, not derived.</strong> The design collapses eight
 * views into three links — My Expenses, Admin Tasks, Reference Tables — and that
 * grouping is an editorial judgement no per-view annotation can express. So
 * {@code @Menu} was removed from every view with this change and this enum is
 * the single source of truth for what the navigation shows.
 *
 * <p>Members are named as <em>view classes</em>, never as route strings, so a
 * renamed {@code @Route} cannot silently drop a view out of its group. The one
 * exception is {@link #ADMIN_TASKS}, which additionally claims
 * {@link ReportDetailView}'s {@code review} <em>alias</em>: the approver's path
 * and the owner's path are the same class behind two routes, so the class alone
 * cannot tell them apart. That alias is read off the annotation rather than
 * typed out, for the same rename-safety.
 *
 * <p>{@link #linked} views are what the navigation offers. {@link #covered}
 * views only light the group up: {@code /report/5} shows <em>My Expenses</em> as
 * current without appearing as a menu entry of its own. The dashboard at
 * {@code /} belongs to no group — the design gives it no nav item — and is
 * reached from the logo instead.
 */
public enum NavGroup {

    /** The owner's own reports. One entry, so it renders as a plain link. */
    MY_EXPENSES("My Expenses",
            List.of(new NavItem("My reports", MyReportsView.class)),
            List.of(ReportDetailView.class),
            Set.of()),

    /**
     * Approving and administering. Three entries, so it renders as a menu; the
     * views behind it keep their pre-redesign appearance until each is designed.
     */
    ADMIN_TASKS("Admin Tasks",
            List.of(new NavItem("Approvals", ApprovalQueueView.class),
                    new NavItem("Review history", ReviewHistoryView.class),
                    new NavItem("Users", UserManagementView.class)),
            List.of(),
            aliasesOf(ReportDetailView.class)),

    /** The reference tables. Three entries, so it renders as a menu. */
    REFERENCE_TABLES("Reference Tables",
            List.of(new NavItem("VAT rates", VatRateView.class),
                    new NavItem("Expense types", ExpenseTypeView.class),
                    new NavItem("Allowance rates", AllowanceRatesView.class)),
            List.of(),
            Set.of());

    /** One navigable entry: the label the navigation shows, and where it goes. */
    public record NavItem(String label, Class<? extends Component> view) {
    }

    private final String label;
    private final List<NavItem> linked;
    private final List<Class<? extends Component>> covered;
    private final Set<String> claimedRouteAliases;

    NavGroup(String label, List<NavItem> linked,
            List<Class<? extends Component>> covered,
            Set<String> claimedRouteAliases) {
        this.label = label;
        this.linked = linked;
        this.covered = covered;
        this.claimedRouteAliases = claimedRouteAliases;
    }

    public String label() {
        return label;
    }

    /** The entries the navigation offers, in display order. */
    public List<NavItem> linked() {
        return linked;
    }

    /**
     * The group a view belongs to, or empty when it belongs to none — the
     * dashboard, the login view, the error views.
     *
     * <p>{@code routeSegment} is the first path segment of the current location.
     * It only ever decides a route <em>alias</em> that another group has claimed;
     * everything else is settled by the view class.
     */
    public static Optional<NavGroup> of(Class<?> view, String routeSegment) {
        // A claimed alias outranks class membership, and has to: the class it
        // belongs to is a member of another group, which would otherwise win.
        return Arrays.stream(values())
                .filter(group -> group.claimedRouteAliases.contains(routeSegment))
                .findFirst()
                .or(() -> Arrays.stream(values())
                        .filter(group -> group.contains(view))
                        .findFirst());
    }

    /**
     * Assignability, not equality — and that is load-bearing. Every view here
     * carries {@code @RolesAllowed} or {@code @PermitAll} at class level, so
     * Spring method security proxies it: the class the navigation chain hands
     * back is {@code MyReportsView$$SpringCGLIB$$0}, not {@code MyReportsView},
     * and {@code equals} quietly matches nothing at all (F-070). The old side
     * nav never met this because {@code MenuEntry#menuClass()} reports the
     * declared class.
     */
    private boolean contains(Class<?> view) {
        return view != null
                && (linked.stream().anyMatch(item -> item.view().isAssignableFrom(view))
                        || covered.stream().anyMatch(c -> c.isAssignableFrom(view)));
    }

    /**
     * The entries this user may reach, which is empty when the whole group is
     * off-limits — an admin-only group simply does not render for a plain user.
     *
     * <p>Filtering reads each view's own {@code @RolesAllowed} / {@code @PermitAll}
     * through Vaadin's {@link AccessAnnotationChecker}, the same annotations the
     * router enforces (ADR-0008). Before #146 this came free from
     * {@code MenuConfiguration}; with {@code @Menu} gone, the annotations are read
     * directly, so navigation is still access-filtered from one source and cannot
     * drift from what the router permits.
     */
    public List<NavItem> visibleTo(AccessAnnotationChecker checker,
            Principal principal, Function<String, Boolean> roleChecker) {
        return linked.stream()
                .filter(item -> checker.hasAccess(item.view(), principal, roleChecker))
                .toList();
    }

    /**
     * The same filter against the signed-in user. The shell renders from this,
     * and so do the view tests that used to assert "an admin sees this in the
     * menu" — the entries behind a group's menu are not reachable from the
     * browserless component tree (F-071), so the model is where that guarantee
     * is checked and the rendered menu is left to visual verification.
     */
    public List<NavItem> visibleTo(AuthenticationContext authenticationContext) {
        Principal principal = () -> authenticationContext.getPrincipalName().orElse(null);
        return visibleTo(new AccessAnnotationChecker(), principal,
                authenticationContext::hasRole);
    }

    /** Every entry, across every group, this user may reach. */
    public static List<NavItem> allVisibleTo(AuthenticationContext context) {
        return Arrays.stream(values())
                .flatMap(group -> group.visibleTo(context).stream())
                .toList();
    }

    /** The {@code @RouteAlias} paths declared on {@code view}, if any. */
    private static Set<String> aliasesOf(Class<? extends Component> view) {
        return Arrays.stream(view.getAnnotationsByType(RouteAlias.class))
                .map(RouteAlias::value)
                .collect(Collectors.toUnmodifiableSet());
    }
}

package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.allowance.ui.AllowanceRatesView;
import com.vaadin.expensemanager.approval.ui.ApprovalQueueView;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.ui.UserManagementView;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The navigation model on its own — no Spring, no UI (issue #146).
 *
 * <p>Two things live here because they are pure functions and deserve to be
 * tested as such: which group a route belongs to, and which of a group's entries
 * a given user may see. The rendered bar is covered by
 * {@link NavigationShellUiTest}.
 */
class NavGroupTest {

    private static final Principal ADMIN = () -> "admin@vaadin.com";
    private static final Principal USER = () -> "user@vaadin.com";

    private final AccessAnnotationChecker checker = new AccessAnnotationChecker();

    @Test
    void everyLinkedViewResolvesToItsOwnGroup() {
        for (var group : NavGroup.values()) {
            for (var item : group.linked()) {
                assertThat(NavGroup.of(item.view(), "")).contains(group);
            }
        }
    }

    /**
     * The acceptance criterion the old side nav could not meet: a report's
     * detail route is not a nav entry of its own, and must still light the
     * group it belongs to.
     */
    @Test
    void reportDetailLightsMyExpensesWithoutBeingAnEntry() {
        assertThat(NavGroup.of(ReportDetailView.class, "report"))
                .contains(NavGroup.MY_EXPENSES);
        assertThat(NavGroup.MY_EXPENSES.linked())
                .extracting(NavGroup.NavItem::view)
                .containsExactly(MyReportsView.class);
    }

    /**
     * {@code /review/5} and {@code /report/5} are the same class behind two
     * routes — the approver's path and the owner's. The path segment is the only
     * thing that tells them apart, and the approver's belongs under Admin Tasks.
     */
    @Test
    void theReviewAliasBelongsToAdminTasksNotMyExpenses() {
        assertThat(NavGroup.of(ReportDetailView.class, "review"))
                .contains(NavGroup.ADMIN_TASKS);
    }

    /** The dashboard is in no group: the design gives {@code /} no nav item. */
    @Test
    void theDashboardBelongsToNoGroup() {
        assertThat(NavGroup.of(DashboardView.class, "")).isEmpty();
    }

    @Test
    void adminSeesEveryEntryOfEveryGroup() {
        assertThat(visible(NavGroup.ADMIN_TASKS, ADMIN, "ADMIN"))
                .containsExactly("Approvals", "Review history", "Users");
        // ONE entry since #169: the reference group's three routes are reached
        // through ReferenceTabs, and the shell offers a single way in.
        assertThat(visible(NavGroup.REFERENCE_TABLES, ADMIN, "ADMIN"))
                .containsExactly("VAT rates");
        assertThat(visible(NavGroup.MY_EXPENSES, ADMIN, "ADMIN"))
                .containsExactly("My reports");
    }

    /**
     * The other two reference routes light the pill without being entries of
     * their own — the same {@code covered()} mechanism {@code /report/5} uses for
     * My Expenses, and the reason the pill stays {@code aria-current} across all
     * three routes.
     */
    @Test
    void theOtherTwoReferenceRoutesAreCoveredRatherThanLinked() {
        assertThat(NavGroup.of(ExpenseTypeView.class, "expense-types"))
                .contains(NavGroup.REFERENCE_TABLES);
        assertThat(NavGroup.of(AllowanceRatesView.class, "allowance-rates"))
                .contains(NavGroup.REFERENCE_TABLES);
        assertThat(visible(NavGroup.REFERENCE_TABLES, ADMIN, "ADMIN"))
                .doesNotContain("Expense types", "Allowance rates");
    }

    /**
     * A plain user sees no admin entry at all — so the whole group renders
     * nothing, which is how the bar stays three links wide for everyone.
     */
    @Test
    void plainUserSeesNoAdminEntry() {
        assertThat(visible(NavGroup.ADMIN_TASKS, USER, "USER")).isEmpty();
        assertThat(visible(NavGroup.REFERENCE_TABLES, USER, "USER")).isEmpty();
        assertThat(visible(NavGroup.MY_EXPENSES, USER, "USER"))
                .containsExactly("My reports");
    }

    /**
     * Filtering reads the view's own annotations, not a copy of them: the
     * entries a user may see are exactly the routes the router would let
     * through (ADR-0008).
     */
    @Test
    void filteringAgreesWithTheViewsOwnAnnotations() {
        assertThat(checker.hasAccess(UserManagementView.class, USER, role -> false))
                .isFalse();
        assertThat(checker.hasAccess(ApprovalQueueView.class, ADMIN,
                "ADMIN"::equals)).isTrue();
        assertThat(checker.hasAccess(AllowanceRatesView.class, ADMIN,
                "ADMIN"::equals)).isTrue();
        assertThat(checker.hasAccess(MyReportsView.class, USER, role -> false))
                .isTrue();
    }

    private java.util.List<String> visible(NavGroup group, Principal principal,
            String... roles) {
        var granted = Set.of(roles);
        return group.visibleTo(checker, principal, granted::contains).stream()
                .map(NavGroup.NavItem::label)
                .toList();
    }
}

package com.vaadin.expensemanager.base.ui;

import java.security.Principal;
import java.util.List;

import com.vaadin.expensemanager.allowance.ui.AllowanceRatesView;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.spring.security.AuthenticationContext;

/**
 * The sub-navigation across the three reference-table routes — VAT Rates,
 * Expense Types, Allowance Rates — drawn as a pill-shaped tab bar at the top of
 * the content column, above the page heading. The spec is
 * {@code docs/design/components/reference-tabs.md}.
 *
 * <p><strong>Route navigation, not in-place content switching.</strong> Each tab
 * is a link to a different {@code @Route}, so there is no {@code TabSheet} and no
 * content area behind the bar. A view that wants to switch content it already
 * owns uses {@code TabSheet} instead.
 *
 * <p><strong>Every tab wraps a real {@link RouterLink}.</strong> With the shell's
 * Reference Tables menu gone (#169) these tabs are the only way into two of the
 * three routes, so they have to be middle-clickable and carry their destination
 * in an {@code href} — the same reasoning {@code app-shell.md} gives for its
 * single-destination pill. A {@code Tabs} selection listener calling
 * {@code UI.navigate()} is not equivalent, and is not what the spec asks for.
 *
 * <p><strong>Entries are filtered by access, not hidden by CSS.</strong> A user
 * who cannot reach {@code /vat-rates} sees no tab for it, through the same
 * {@link AccessAnnotationChecker} pass {@link NavGroup} performs against the same
 * annotations the router enforces (ADR-0008). All three views are
 * {@code @RolesAllowed("ADMIN")} today, so in practice the bar is
 * all-or-nothing; the filter is what keeps that true when one of them opens up.
 *
 * <p>The entry list is this class's own and deliberately not
 * {@link NavGroup#REFERENCE_TABLES}'s. That group now carries one
 * {@code linked()} entry and two {@code covered()} ones, because the shell draws
 * a single pill into the group and the tabs do the rest — so the two lists say
 * different things and sharing one would force a lie on whichever read it second.
 *
 * <p>Both {@code aria-current="page"} and {@code Tabs}' own
 * {@code aria-selected} apply here and mean different things: the first goes on
 * the link, the second is {@code Tabs}'. The bar is a {@code tablist} by markup
 * and a navigation by behaviour, a known tension the spec flags — if it reads
 * badly under a screen reader the fallback is plain {@code Nav} + links keeping
 * the same visual spec. That check has not been made.
 */
public class ReferenceTabs extends Tabs {

    /** One tab: the label the design draws, and the route behind it. */
    private record Entry(String label, Class<? extends Component> view) {
    }

    /** In the design's left-to-right order (node {@code 156:5401}). */
    private static final List<Entry> ENTRIES = List.of(
            new Entry("VAT Rates", VatRateView.class),
            new Entry("Expense Types", ExpenseTypeView.class),
            new Entry("Allowance Rates", AllowanceRatesView.class));

    /**
     * @param currentView the view class hosting this bar; its tab renders
     *                    selected and its link carries {@code aria-current}
     * @param authenticationContext the signed-in user, for the access filter
     */
    public ReferenceTabs(Class<? extends Component> currentView,
            AuthenticationContext authenticationContext) {
        addClassName("reference-tabs");

        var checker = new AccessAnnotationChecker();
        Principal principal =
                () -> authenticationContext.getPrincipalName().orElse(null);

        Tab current = null;
        for (var entry : ENTRIES) {
            if (!checker.hasAccess(entry.view(), principal,
                    authenticationContext::hasRole)) {
                continue;
            }
            var link = new RouterLink(entry.label(), entry.view());
            var tab = new Tab(link);
            add(tab);

            // Assignability, not equality: every view here is @RolesAllowed at
            // class level, so Spring method security hands back a CGLIB subclass
            // and equals() matches nothing at all (F-070).
            if (entry.view().isAssignableFrom(currentView)) {
                current = tab;
                // aria-current is what a screen reader announces; the selected
                // pill is only the sighted half of the same fact.
                link.getElement().setAttribute("aria-current", "page");
            }
        }

        // Tabs selects its first tab on add, so a host outside the three (or one
        // whose own route the user cannot reach) would otherwise light the wrong
        // pill rather than none.
        if (current != null) {
            setSelectedTab(current);
        } else {
            setSelectedIndex(-1);
        }
    }
}

package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.expensemanager.user.CurrentUser;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * The role-aware landing view at {@code @Route("")} (UC-007, ADR-0017).
 *
 * <p>A single adaptive dashboard — not separate user/admin routes — that greets
 * the logged-in user by name and renders role-conditional content (an admin
 * sees a different note than a plain user). Per-role <em>navigation</em> is
 * handled separately by the shell's hand-authored top navigation; this view
 * handles per-role <em>content</em>.
 *
 * <p>The design gives {@code /} no nav item of its own (#146), so the shell's
 * logo links here.
 *
 * <p>Deliberately thin for Phase 1: no placeholder cards for unbuilt features.
 * Later phases accrete their own sections (P2 recent reports, P5 pending
 * approvals for admins). Replaces the Phase 0 throwaway {@code HomeView}.
 */
@Route("")
@PageTitle("Dashboard")
@PermitAll
public class DashboardView extends VerticalLayout {

    public DashboardView(CurrentUserProvider currentUserProvider) {
        CurrentUser user = currentUserProvider.require();

        var greeting = new H2("Welcome, " + user.name());

        var roleNote = user.isAdmin()
                ? new Paragraph("You are signed in as an administrator. Admin tools "
                        + "for reviewing and configuring reports appear here as later "
                        + "phases land.")
                : new Paragraph("You are signed in as a user. Your expense reports and "
                        + "their status will appear here as later phases land.");

        add(greeting, roleNote);
        setSpacing(false);
        setPadding(true);
    }
}

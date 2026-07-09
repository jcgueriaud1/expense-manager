package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Throwaway landing view at {@code @Route("")} that makes the shell demoable in
 * Phase 0, before any feature data exists (ADR-0017).
 *
 * <p>Deliberately disposable scaffolding: Phase 1 replaces it with a single
 * adaptive {@code DashboardView} (UC-007) that greets the user by name and
 * shows their role. Deleting this class then is expected, not churn.
 *
 * <p>Its {@code @Menu} annotation is what registers "Home" in
 * {@link MainLayout}'s auto-generated side navigation — the mechanism every
 * feature view reuses.
 */
@Route("")
@PageTitle("Home")
@Menu(title = "Home", order = 0, icon = "vaadin:home")
@AnonymousAllowed
public class HomeView extends VerticalLayout {

    public HomeView() {
        add(new EmptyState(
                "vaadin:coin-piles",
                "Welcome to Expense Manager",
                "The application shell is up. Feature views will appear in the "
                        + "navigation as later phases add them."));
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);
    }
}

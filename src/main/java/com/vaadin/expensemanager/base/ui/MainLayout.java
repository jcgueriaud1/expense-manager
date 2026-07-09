package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

/**
 * The application's Aura-themed navigation shell (ADR-0017).
 *
 * <p>Registered as the automatic {@link Layout @Layout} for the whole app, so
 * every {@code @Route} view without an explicit layout renders inside this
 * {@link AppLayout}: a drawer holding the app name and side navigation, and a
 * navbar with the drawer toggle plus the current-user identity and a logout
 * action.
 *
 * <p><strong>Navigation is auto-generated, never hand-maintained.</strong> The
 * {@link SideNav} is built from {@link MenuConfiguration#getMenuEntries()},
 * which collects every {@code @Menu}-annotated view and is already filtered by
 * the user's access (ADR-0008), so role-aware navigation comes for free.
 *
 * <p>{@link PermitAll} guards the shell: it hosts only authenticated views, so
 * a current user is always present when it renders (the public login view opts
 * out via {@code autoLayout = false}). The header therefore always resolves an
 * identity, and logout goes through Vaadin's
 * {@link AuthenticationContext#logout()} (ADR-0017).
 */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    private final transient AuthenticationContext authenticationContext;

    public MainLayout(CurrentUserProvider currentUserProvider,
            AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;

        setPrimarySection(Section.DRAWER);
        addToNavbar(new DrawerToggle(), createUserMenu(currentUserProvider));
        addToDrawer(createHeader(), new Scroller(createSideNav()));
    }

    private Component createHeader() {
        var appName = new H1("Expense Manager");
        appName.getStyle()
                .setFontSize("var(--lumo-font-size-l)")
                .setMargin("var(--lumo-space-m)");
        return appName;
    }

    private Component createUserMenu(CurrentUserProvider currentUserProvider) {
        var userName = new Span(currentUserProvider.get()
                .map(user -> user.name())
                .orElse(""));
        userName.getStyle().setFontWeight("500");

        var logout = new Button("Sign out", new Icon(VaadinIcon.SIGN_OUT),
                event -> authenticationContext.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        var bar = new HorizontalLayout(userName, logout);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setWidthFull();
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        bar.getStyle().setPaddingRight("var(--lumo-space-m)");
        return bar;
    }

    private SideNav createSideNav() {
        var nav = new SideNav();
        MenuConfiguration.getMenuEntries()
                .forEach(entry -> nav.addItem(createSideNavItem(entry)));
        return nav;
    }

    private SideNavItem createSideNavItem(MenuEntry entry) {
        var item = entry.icon() != null
                ? new SideNavItem(entry.title(), entry.path(), new Icon(entry.icon()))
                : new SideNavItem(entry.title(), entry.path());
        item.setMatchNested(true);
        return item;
    }
}

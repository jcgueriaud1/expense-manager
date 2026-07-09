package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;

/**
 * The application's Aura-themed navigation shell (ADR-0017).
 *
 * <p>Registered as the automatic {@link Layout @Layout} for the whole app, so
 * every {@code @Route} view without an explicit layout renders inside this
 * {@link AppLayout}: a drawer holding the app name and side navigation, and a
 * navbar with the drawer toggle.
 *
 * <p><strong>Navigation is auto-generated, never hand-maintained.</strong> The
 * {@link SideNav} is built from {@link MenuConfiguration#getMenuEntries()},
 * which collects every {@code @Menu}-annotated view. Features self-register
 * their nav entry simply by annotating their view — adding a feature never
 * requires editing this class. The entry list is already filtered by the user's
 * access (ADR-0008), so role-aware navigation comes for free once route
 * security lands (Phase 1.4).
 *
 * <p>{@link AnonymousAllowed} keeps the shell renderable before authentication
 * exists; a {@code @Layout} needs its own access annotation independent of the
 * views it hosts. Phase 1 tightens this to {@code @PermitAll} and adds the
 * current-user identity + logout controls to the header (ADR-0017).
 */
@Layout
@AnonymousAllowed
public class MainLayout extends AppLayout {

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        addToNavbar(new DrawerToggle());
        addToDrawer(createHeader(), new Scroller(createSideNav()));
    }

    private Component createHeader() {
        var appName = new H1("Expense Manager");
        appName.getStyle()
                .setFontSize("var(--lumo-font-size-l)")
                .setMargin("var(--lumo-space-m)");
        return appName;
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

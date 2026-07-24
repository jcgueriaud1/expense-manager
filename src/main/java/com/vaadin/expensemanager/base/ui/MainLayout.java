package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.allowance.ui.AllowanceRatesView;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.expensemanager.user.ui.UserManagementView;
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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.sidenav.SideNavVariant;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * items come from {@link MenuConfiguration#getMenuEntries()}, which collects
 * every {@code @Menu}-annotated view and is already filtered by the user's
 * access (ADR-0008), so role-aware navigation comes for free.
 *
 * <p><strong>Grouped into sections (issue #91).</strong> Everyday views —
 * Dashboard, My reports, Approvals — head an unlabelled {@link SideNav}. The
 * administrative reference tables and user management follow as their own
 * labelled sections at the end. Only the section-to-view mapping in
 * {@link #ADMIN_SECTIONS} is hand-maintained, and it references the view
 * classes directly (via {@link MenuEntry#menuClass()}) rather than repeating
 * their {@code @Route} paths — so a renamed route can never silently drop a
 * view out of its section. Membership, order within a section, and access
 * filtering all still flow from the {@code @Menu} entries, and any view not
 * claimed by an admin section falls into the top group by default. A section
 * with no accessible entries renders nothing.
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

    /**
     * The administrative sections shown at the end of the drawer, in order.
     * Each maps a section label to the view classes that belong under it; any
     * menu entry whose view is not listed here stays in the top group.
     */
    private static final List<NavSection> ADMIN_SECTIONS = List.of(
            new NavSection("Reference tables",
                    List.of(VatRateView.class, ExpenseTypeView.class,
                            AllowanceRatesView.class)),
            new NavSection("User management", List.of(UserManagementView.class)));

    private final transient AuthenticationContext authenticationContext;

    public MainLayout(CurrentUserProvider currentUserProvider,
            AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;

        setPrimarySection(Section.DRAWER);
        addToNavbar(new DrawerToggle(), createUserMenu(currentUserProvider));
        addToDrawer(createHeader(), new Scroller(createNavigation()));
    }

    /** A labelled group of navigation items and the views it collects. */
    private record NavSection(String label,
            List<Class<? extends Component>> views) {
    }

    private Component createHeader() {
        var appName = new H1("Expense Manager");
        // Colour lives in the .app-name class: the drawer's computed --vaadin-text-color
        // does not re-resolve for a slotted H1 under the scoped dark scheme, so the
        // light-on-navy title is set explicitly there (issue #113).
        appName.addClassName("app-name");
        appName.getStyle()
                .setFontSize("var(--aura-font-size-l)")
                .setMargin("var(--vaadin-padding-m)");
        return appName;
    }

    private Component createUserMenu(CurrentUserProvider currentUserProvider) {
        var userName = new Span(currentUserProvider.get()
                .map(user -> user.name())
                .orElse(""));
        userName.getStyle().setFontWeight("500");

        var logout = new Button("Sign out", new Icon(VaadinIcon.SIGN_OUT),
                event -> authenticationContext.logout());
        logout.addThemeVariants(ButtonVariant.TERTIARY);

        var bar = new HorizontalLayout(new ThemeSwitcher(), userName, logout);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setWidthFull();
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        bar.getStyle().setPaddingRight("var(--vaadin-padding-m)");
        return bar;
    }

    private Component createNavigation() {
        var entries = MenuConfiguration.getMenuEntries();

        Set<Class<? extends Component>> adminViews = new HashSet<>();
        ADMIN_SECTIONS.forEach(section -> adminViews.addAll(section.views()));

        var container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing("var(--vaadin-gap-m)");

        // Top group: every entry not claimed by an admin section, kept in the
        // order MenuConfiguration returns them (by @Menu order).
        var topEntries = entries.stream()
                .filter(entry -> !adminViews.contains(entry.menuClass()))
                .toList();
        addSection(container, null, topEntries);

        // Admin sections at the end, each in its declared view order.
        for (var section : ADMIN_SECTIONS) {
            var sectionEntries = section.views().stream()
                    .flatMap(view -> entries.stream()
                            .filter(entry -> view.equals(entry.menuClass())))
                    .toList();
            addSection(container, section.label(), sectionEntries);
        }
        return container;
    }

    private void addSection(VerticalLayout container, String label,
            List<MenuEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        var nav = new SideNav();
        // FILLED gives the current item a solid accent fill (issue #113 design),
        // instead of the default subtle container tint.
        nav.addThemeVariants(SideNavVariant.AURA_FILLED);
        if (label != null) {
            nav.setLabel(label);
        }
        entries.forEach(entry -> nav.addItem(createSideNavItem(entry)));
        container.add(nav);
    }

    private SideNavItem createSideNavItem(MenuEntry entry) {
        var item = entry.icon() != null
                ? new SideNavItem(entry.title(), entry.path(), new Icon(entry.icon()))
                : new SideNavItem(entry.title(), entry.path());
        item.setMatchNested(true);
        return item;
    }
}

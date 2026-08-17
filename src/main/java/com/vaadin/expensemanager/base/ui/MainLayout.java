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
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
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
 * navbar with the current-user identity and a logout action. The drawer toggle
 * follows the drawer — inside it while it is open, back in the navbar once it
 * collapses (see {@link #createToggle}).
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
public class MainLayout extends AppLayout implements AfterNavigationObserver {

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

    /**
     * The current view's title, shown at the head of the navbar. The views no
     * longer carry their own copy — see {@link #afterNavigation}.
     */
    private final H1 viewTitle = new H1();

    public MainLayout(CurrentUserProvider currentUserProvider,
            AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;

        viewTitle.addClassName("shell-view-title");

        setPrimarySection(Section.DRAWER);
        addToNavbar(createToggle("shell-navbar-toggle", "Expand menu"), viewTitle,
                createUserMenu(currentUserProvider));
        addToDrawer(createHeader(), new Scroller(createNavigation()));
    }

    /**
     * Retitles the navbar for the view that was just navigated to.
     *
     * <p>{@link MenuConfiguration#getPageHeader(Component)} exists for exactly
     * this — it resolves {@link com.vaadin.flow.router.HasDynamicTitle
     * HasDynamicTitle} first, then the view's {@code @PageTitle} — so the title
     * shown here is the same string the browser tab gets, and a view declares it
     * in one place.
     *
     * <p>This hangs off {@link AfterNavigationObserver} rather than overriding
     * {@code AppLayout.afterNavigation()}, which is package-private in Vaadin 25
     * and so not ours to override (F-061).
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        viewTitle.setText(MenuConfiguration.getPageHeader(getContent()).orElse(""));
    }

    /** A labelled group of navigation items and the views it collects. */
    private record NavSection(String label,
            List<Class<? extends Component>> views) {
    }

    /**
     * The drawer's own header: the app name paired with a drawer toggle.
     *
     * <p>The toggle here is the <em>collapse</em> half of the pair described on
     * {@link #createToggle}; the navbar holds the <em>expand</em> half.
     */
    private Component createHeader() {
        // A Span, not a heading: the page's one H1 is the view title in the
        // navbar. The app name is a brand mark, not this page's heading, and two
        // competing H1s would leave a screen reader with no clear page title.
        var appName = new Span("Expense Manager");
        appName.addClassName("shell-app-name");

        var header = new HorizontalLayout(appName,
                createToggle("shell-drawer-toggle", "Collapse menu"));
        header.setWidthFull();
        // setPadding takes only a boolean — there is no String overload for a
        // custom value, unlike setSpacing (F-058).
        header.setPadding(true);
        header.setSpacing("var(--vaadin-gap-s)");
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return header;
    }

    /**
     * One half of the drawer-toggle pair.
     *
     * <p>The shell carries <strong>two</strong> toggles — one in the drawer
     * header, one in the navbar — and shows whichever belongs to the current
     * drawer state: while the drawer is open the toggle sits inside it, and once
     * it collapses the toggle reappears in the view header. Both are always in
     * the DOM; {@code styles.css} hides the wrong one off the {@code
     * drawer-opened} attribute that {@code <vaadin-app-layout>} reflects on its
     * host, so the swap costs no server round-trip and survives a drawer state
     * change made from either side. It hides with {@code display: none}, which
     * also keeps the hidden one out of the accessibility tree — assistive tech
     * and tab order see a single toggle, hence the state-specific labels.
     */
    private Component createToggle(String className, String ariaLabel) {
        var toggle = new DrawerToggle();
        toggle.addClassName(className);
        toggle.setAriaLabel(ariaLabel);
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        return toggle;
    }

    private Component createUserMenu(CurrentUserProvider currentUserProvider) {
        var userName = new Span(currentUserProvider.get()
                .map(user -> user.name())
                .orElse(""));
        userName.getStyle().setFontWeight("500");

        var logout = new Button("Sign out", LucideIcon.LOG_OUT.create(),
                event -> authenticationContext.logout());
        logout.addThemeVariants(ButtonVariant.TERTIARY);

        // Sized to its content, not the full row: the view title beside it is what
        // takes up the slack (see .shell-view-title), which both keeps this bar at
        // the trailing edge and stops it from squeezing the title into an ellipsis.
        var bar = new HorizontalLayout(new ThemeSwitcher(), userName, logout);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
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
        // VerticalLayout aligns children to START, which on a column axis makes
        // each SideNav shrink to its widest label — so the items, and the
        // current-item highlight, stopped short of the drawer's edge. STRETCH
        // fills the drawer width.
        container.setDefaultHorizontalComponentAlignment(
                FlexComponent.Alignment.STRETCH);

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
        if (label != null) {
            nav.setLabel(label);
        }
        entries.forEach(entry -> nav.addItem(createSideNavItem(entry)));
        container.add(nav);
    }

    /**
     * Builds one navigation item, rendering the {@code @Menu} entry's icon.
     *
     * <p>{@code @Menu(icon = …)} is an uninterpreted string: Flow hands it
     * through verbatim and it is this method that decides what it means. Ours
     * carry a path to a vendored Lucide SVG ({@link LucideIcon}), so they need
     * {@link SvgIcon}, which sets the {@code src} attribute. The {@link
     * com.vaadin.flow.component.icon.Icon Icon} class cannot render a path —
     * given one it silently looks up a non-existent name inside the Vaadin
     * iconset and renders nothing at all, no error — so the annotation values
     * and this line have to change together.
     */
    private SideNavItem createSideNavItem(MenuEntry entry) {
        var item = entry.icon() != null
                ? new SideNavItem(entry.title(), entry.path(),
                        new SvgIcon(entry.icon()))
                : new SideNavItem(entry.title(), entry.path());
        item.setMatchNested(true);
        return item;
    }
}

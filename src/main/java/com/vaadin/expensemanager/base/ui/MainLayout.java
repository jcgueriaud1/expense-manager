package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The application's Aura-themed navigation shell (ADR-0025, superseding the
 * drawer shell of ADR-0017).
 *
 * <p>Registered as the automatic {@link Layout @Layout} for the whole app, so
 * every {@code @Route} view without an explicit layout renders inside this
 * {@link AppLayout}. There is no drawer: navigation lives in the header as
 * <strong>logo · menu · account</strong>, with the menu centred. {@code AppLayout}
 * is still the container even with an empty drawer — it publishes the measured
 * navbar height as the content's top offset (so a taller header can never
 * overlap a view), it re-slots navbar children into a bottom bar on coarse
 * pointers, and Aura's whole app chrome is keyed to {@code vaadin-app-layout}
 * selectors. The component detects the empty drawer and collapses it to zero
 * width, so nothing needs switching off.
 *
 * <p><strong>The menu is derived, not listed.</strong> A screen becomes its own
 * destination by declaring {@code @Menu} and no layout of its own; it joins a
 * grouped destination — Reference tables, Admin — by naming that group's layout
 * in its {@code @Route}. So membership is declared where the screen is, and the
 * only navigation this class hand-maintains is {@link #SECTIONS}: a name per
 * group. That keeps the property ADR-0017 was built around, a view registering
 * its own access-filtered navigation with no edit to the shell (ADR-0008), and it
 * role-gates the grouped items without a check of its own — {@code
 * getMenuEntries()} is already access-filtered, so a plain user's groups come
 * back empty and neither item renders.
 *
 * <p>The header carries no view title: the wireframe puts the screen's name in
 * the content, where each view renders it as its own heading.
 *
 * <p>{@link PermitAll} guards the shell: it hosts only authenticated views, so a
 * current user is always present when it renders (the public login view opts out
 * via {@code autoLayout = false}). Logout goes through Vaadin's
 * {@link AuthenticationContext#logout()}.
 */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    /**
     * The header's grouped destinations, in order: a tab-shell layout and the
     * name it goes by. Everything else about a group — which screens it holds,
     * their order, who may see them — comes from the screens themselves.
     *
     * <p>This map is the whole of the shell's hand-maintained navigation. A
     * screen joins a group by naming that group's layout in its {@code @Route},
     * so adding one needs no edit here; only a brand-new group does.
     */
    private static final Map<Class<? extends RouterLayout>, String> SECTIONS =
            new LinkedHashMap<>();

    static {
        SECTIONS.put(ReferenceLayout.class, "Reference tables");
        SECTIONS.put(AdminLayout.class, "Admin");
    }

    /**
     * The width every screen's content column is held to.
     *
     * <p>One value, shared: the reports list, the approval queue and the tabbed
     * sections all read as the same document rather than three layouts that happen
     * to sit under one header. None of this app's tables is wide enough to want the
     * full window.
     */
    public static final String CONTENT_MAX_WIDTH = "1000px";

    private final transient AuthenticationContext authenticationContext;

    public MainLayout(CurrentUserProvider currentUserProvider,
            AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;

        var header = new Div(logo(), menu(), account(currentUserProvider));
        header.addClassName("shell-header");
        header.setWidthFull();
        addToNavbar(header);
    }

    /** Brand mark, doubling as the way home. */
    private Component logo() {
        var mark = LucideIcon.RECEIPT.create();
        var wordmark = new Span("Expense Manager");
        wordmark.addClassName("shell-wordmark");

        var link = new RouterLink();
        link.setRoute(MyReportsView.class);
        link.add(mark, wordmark);
        link.addClassName("shell-logo");
        // A logo is not a nav item — without this it would light up as "current"
        // on the dashboard, since its route is "" and the default highlight
        // condition is a location *prefix* match.
        link.setHighlightCondition(HighlightConditions.never());
        return link;
    }

    /**
     * The centred menu: every screen that stands on its own, then every group
     * that has anything in it for this user.
     *
     * <p>Nothing here is a list of screens. A screen appears as its own
     * destination by declaring {@code @Menu} and no layout; it appears inside a
     * group by naming that group's layout. Both come from
     * {@link MenuConfiguration}, which has already dropped whatever the user may
     * not see — so a group with nothing accessible in it renders no item at all,
     * and that is the whole of the role gating (ADR-0008).
     *
     * <p>{@link RouterLink} rather than tabs or a menu bar: it is a real anchor
     * (keyboard, middle-click, "open in new tab" all work) and it already tracks
     * the current route — Flow toggles a {@code highlight} attribute on it after
     * navigation, which is what {@code styles.css} styles as the current item.
     */
    private Component menu() {
        var nav = new Nav();
        nav.addClassName("shell-nav");

        var standalone = entriesFor(UI.class);
        var sections = SECTIONS.entrySet().stream()
                .filter(section -> !entriesFor(section.getKey()).isEmpty())
                .toList();

        // A menu of one is not a menu. A plain user can reach exactly one screen —
        // their reports, which is also where "" lands — so they get no navigation
        // at all rather than a lone item that only ever points at the page they are
        // already on (ADR-0026).
        if (standalone.size() + sections.size() < 2) {
            return nav;
        }

        standalone.forEach(entry -> nav.add(standaloneLink(entry)));
        sections.forEach(section -> nav.add(
                sectionLink(section.getValue(), entriesFor(section.getKey()))));
        return nav;
    }

    /** A destination that is one screen — its {@code @Menu} title links to it. */
    private RouterLink standaloneLink(MenuEntry entry) {
        var path = normalizePath(entry.path());
        var link = new RouterLink(entry.title(), asComponent(entry.menuClass()));
        link.addClassName("shell-nav-link");
        // A route of "" is a prefix of every other location, so a prefix match
        // would keep such a link lit everywhere; it needs an exact match — plus its
        // aliases, since the landing screen also answers on /reports.
        var aliases = aliasPaths(entry.menuClass());
        link.setHighlightCondition(path.isEmpty()
                ? (target, event) -> event.getLocation().getPath().isEmpty()
                        || aliases.contains(firstSegment(event.getLocation().getPath()))
                : HighlightConditions.locationPrefix(path));
        return link;
    }

    /**
     * A destination that stands for several screens: it opens the first of them
     * and stays lit for any, so the header keeps showing which <em>area</em> you
     * are in while that area's tabs show which screen.
     */
    private RouterLink sectionLink(String title, List<MenuEntry> members) {
        var paths = members.stream().map(entry -> firstSegment(entry.path()))
                .collect(Collectors.toUnmodifiableSet());

        var link = new RouterLink();
        link.setRoute(asComponent(members.getFirst().menuClass()));
        link.setText(title);
        link.addClassName("shell-nav-link");
        link.setHighlightCondition((target, event) ->
                paths.contains(firstSegment(event.getLocation().getPath())));
        return link;
    }

    /** The {@code @RouteAlias} paths of a view — locations it also answers on. */
    private static Set<String> aliasPaths(Class<?> viewClass) {
        return Arrays.stream(viewClass.getAnnotationsByType(RouteAlias.class))
                .map(alias -> firstSegment(alias.value()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String firstSegment(String path) {
        var trimmed = normalizePath(path);
        var slash = trimmed.indexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(0, slash);
    }

    /**
     * A path with no leading slash, so a {@code @Menu} entry and a
     * {@link com.vaadin.flow.router.Location Location} can be compared — they do
     * not agree on the slash, and a mismatch fails silently.
     */
    static String normalizePath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * The accessible {@code @Menu} screens whose {@code @Route} names
     * {@code layout} as their parent, in {@code @Menu order}.
     *
     * <p>{@code UI.class} is {@code @Route}'s default, so passing it asks for the
     * screens that declared no layout of their own — the standalone destinations.
     * This is the one place the shell reads a view's layout, and it is what lets
     * membership live on the screen instead of in a list here.
     */
    static List<MenuEntry> entriesFor(Class<? extends RouterLayout> layout) {
        return MenuConfiguration.getMenuEntries().stream()
                .filter(entry -> entry.menuClass() != null)
                .filter(entry -> declaredLayout(entry.menuClass()) == layout)
                .toList();
    }

    private static Class<? extends RouterLayout> declaredLayout(Class<?> viewClass) {
        var route = viewClass.getAnnotation(Route.class);
        return route == null ? UI.class : route.layout();
    }

    @SuppressWarnings("unchecked")
    static Class<? extends Component> asComponent(Class<?> menuClass) {
        return (Class<? extends Component>) menuClass;
    }

    /**
     * The account control at the trailing edge: an initials avatar whose menu
     * carries who you are, the colour-scheme choice, and sign-out.
     */
    private Component account(CurrentUserProvider currentUserProvider) {
        var name = currentUserProvider.get().map(user -> user.name()).orElse("");
        var avatar = new Avatar(name);
        avatar.addClassName("shell-avatar");

        var bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.TERTIARY);
        bar.addClassName("shell-account");
        var trigger = bar.addItem(avatar);
        trigger.setAriaLabel("Account");

        SubMenu menu = trigger.getSubMenu();
        // addComponent, not addItem: who you are is a label in the menu, not a
        // command — an item would be focusable and clickable to no effect.
        var who = new Span(name);
        who.addClassName("shell-account-name");
        menu.addComponent(who);
        menu.addSeparator();
        ThemeSwitcher.addChoicesTo(menu.addItem("Theme").getSubMenu());
        menu.addSeparator();
        menu.addItem("Sign out", event -> authenticationContext.logout());
        return bar;
    }
}

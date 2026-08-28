package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The coral top bar of the app shell — the design's {@code Header} component
 * (Figma {@code 116:3876}), which replaced {@code AppLayout}'s drawer and
 * navbar in issue #146.
 *
 * <p>Left to right: the logo and wordmark, the three {@link NavGroup} links, and
 * an avatar. The avatar carries what the designed bar has no room for and the
 * app cannot drop — the colour-scheme switcher, the signed-in name and sign-out
 * (#145 decision 1).
 *
 * <p>A group with one entry renders as a {@link RouterLink}; a group with
 * several renders as a button opening a {@link ContextMenu} of links, because
 * the design draws one pill per group and the app has eight routes to reach
 * through three of them. Groups whose every view is off-limits to the current
 * user do not render at all.
 *
 * <p>{@link HeaderState#HOME} additionally draws the greeting hero. Its
 * illustration is decorative — {@code alt=""}, and hidden outright below the
 * breakpoint where it would crowd the greeting.
 *
 * <p>Colour, geometry and the pill styling live in {@code styles.css} under
 * {@code .app-header}; the spec is {@code docs/design/components/app-shell.md}.
 */
class AppHeader extends Header {

    /** The wordmark beside the logo mark. */
    private static final String APP_NAME = "Expense Manager";

    private final Nav nav = new Nav();
    private final Div hero = new Div();
    private final H1 greeting = new H1();
    private final Span heroMessage = new Span();
    private final Map<NavGroup, Component> pills = new EnumMap<>(NavGroup.class);

    private HeaderState state = HeaderState.DEFAULT;

    AppHeader(CurrentUserProvider currentUserProvider,
            AuthenticationContext authenticationContext) {
        addClassNames("app-header", state.className());

        var userName = currentUserProvider.get()
                .map(user -> user.name())
                .orElse("");

        var row = new Div(logo(), navigation(authenticationContext),
                accountMenu(userName, authenticationContext));
        row.addClassName("app-header__row");

        buildHero(userName);
        add(row, hero);
    }

    /** The mark and wordmark, linking to the dashboard. */
    private Component logo() {
        var mark = new Image("images/logo.svg", "");
        mark.setWidth("31px");
        mark.setHeight("30px");
        mark.getElement().setAttribute("aria-hidden", "true");

        // The dashboard is the one route the design gives no nav item, so the
        // logo is how it stays reachable.
        var link = new RouterLink("", DashboardView.class);
        link.add(mark, new Span(APP_NAME));
        link.addClassName("app-header__logo");
        link.getElement().setAttribute("aria-label", APP_NAME + " — dashboard");
        return link;
    }

    private Component navigation(AuthenticationContext authenticationContext) {
        nav.addClassName("app-nav");
        nav.getElement().setAttribute("aria-label", "Main");

        for (var group : NavGroup.values()) {
            var entries = group.visibleTo(authenticationContext);
            if (entries.isEmpty()) {
                continue;
            }
            var pill = entries.size() == 1
                    ? link(group, entries.get(0))
                    : menu(group, entries);
            pills.put(group, pill);
            nav.add(pill);
        }
        return nav;
    }

    /** A group with a single destination: a real link, middle-clickable. */
    private Component link(NavGroup group, NavGroup.NavItem only) {
        var link = new RouterLink(group.label(), only.view());
        link.addClassName("app-nav__item");
        // The pill follows the group, so RouterLink's own route-exact highlight
        // would disagree with it on /report/5. Only one of them may be in charge.
        link.setHighlightCondition(HighlightConditions.never());
        return link;
    }

    /** A group with several destinations: a button opening a menu of links. */
    private Component menu(NavGroup group, List<NavGroup.NavItem> entries) {
        var trigger = new Button(group.label());
        trigger.addThemeVariants(ButtonVariant.TERTIARY);
        trigger.addClassName("app-nav__item");

        var menu = new ContextMenu(trigger);
        menu.setOpenOnClick(true);
        entries.forEach(entry -> menu.addItem(
                new RouterLink(entry.label(), entry.view())));
        return trigger;
    }

    /**
     * The avatar and everything behind it. {@code MenuBar} rather than a bare
     * {@link ContextMenu} so the trigger is a real menu button with the keyboard
     * handling and ARIA that implies.
     */
    private Component accountMenu(String userName,
            AuthenticationContext authenticationContext) {
        var avatar = new Avatar(userName);
        // The design annotates the avatar as <vaadin-avatar color-index="2">.
        avatar.setColorIndex(2);

        var bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.TERTIARY);
        bar.addClassName("app-header__account");

        var trigger = bar.addItem(avatar);
        trigger.getElement()
                .setAttribute("aria-label", "Account menu for " + userName);

        var menu = trigger.getSubMenu();
        var name = menu.addItem(userName);
        name.setEnabled(false);
        ThemeSwitcher.addTo(menu);
        menu.addItem("Sign out", event -> authenticationContext.logout());
        return bar;
    }

    private void buildHero(String userName) {
        hero.addClassName("app-header__hero");
        // Only HOME draws it; setState reveals it.
        hero.setVisible(false);

        greeting.setText("Hi " + userName + ",");
        greeting.addClassName("app-header__greeting");
        heroMessage.addClassName("app-header__message");

        var illustration = new Image("images/header-illustration.svg", "");
        illustration.addClassName("app-header__illustration");
        illustration.getElement().setAttribute("aria-hidden", "true");

        var text = new Div(greeting, heroMessage);
        text.addClassName("app-header__hero-text");
        hero.add(text, illustration);
    }

    /** Switches the bar between the design's five states. */
    void setState(HeaderState newState, String message) {
        removeClassName(state.className());
        state = newState;
        addClassName(state.className());

        hero.setVisible(state.isTall());
        heroMessage.setText(message == null ? "" : message);
        heroMessage.setVisible(message != null && !message.isBlank());
    }

    /**
     * Lights the group the current route belongs to, and only that one — the
     * pill follows the group, so {@code /report/5} keeps <em>My Expenses</em>
     * current. A route in no group (the dashboard) leaves every pill idle.
     */
    void setActiveGroup(NavGroup active) {
        pills.forEach((group, pill) -> {
            pill.getElement().getClassList()
                    .set("app-nav__item--active", group == active);
            // aria-current is what a screen reader announces; the pill is only
            // the sighted half of the same fact.
            if (group == active) {
                pill.getElement().setAttribute("aria-current", "page");
            } else {
                pill.getElement().removeAttribute("aria-current");
            }
        });
    }
}

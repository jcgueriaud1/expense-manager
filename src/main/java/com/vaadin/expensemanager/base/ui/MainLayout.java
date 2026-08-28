package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

/**
 * The application's Aura-themed navigation shell (ADR-0017, redesigned in #146).
 *
 * <p>Registered as the automatic {@link Layout @Layout} for the whole app, so
 * every {@code @Route} view without an explicit layout renders inside it: a
 * full-width coral {@link AppHeader} carrying the logo, three nav links and an
 * avatar, and beneath it a rounded card whose top corners tuck 35px under the
 * bar, holding a centred content column.
 *
 * <p><strong>Not an {@code AppLayout}.</strong> The design has no drawer and no
 * toggle, so there is nothing left of that component to use; this is a plain
 * {@link RouterLayout} over two elements. The content card is the shell's own,
 * which is why {@code --aura-app-layout-radius} went away with the drawer — it
 * only ever styled {@code AppLayout}'s content area.
 *
 * <p><strong>Navigation is hand-authored, and no longer generated.</strong> The
 * design collapses eight views into three editorial groups, which no per-view
 * annotation can express, so {@code @Menu} was removed from every view and
 * {@link NavGroup} took over. Access filtering did not come along for free with
 * that change and is done explicitly, against the same {@code @RolesAllowed} /
 * {@code @PermitAll} the router enforces (ADR-0008).
 *
 * <p><strong>The header is the view's choice.</strong> Each view may implement
 * {@link HasHeaderState}; the shell reads it as the view attaches, and falls
 * back to {@link HeaderState#DEFAULT}. A view whose header depends on data it
 * loads later calls {@link #setHeaderState} instead.
 *
 * <p>{@link PermitAll} guards the shell: it hosts only authenticated views, so
 * a current user is always present when it renders (the public login view opts
 * out via {@code autoLayout = false}). The header therefore always resolves an
 * identity, and logout goes through Vaadin's
 * {@link AuthenticationContext#logout()} (ADR-0017).
 */
@Layout
@PermitAll
public class MainLayout extends Div
        implements RouterLayout, AfterNavigationObserver {

    private final AppHeader header;

    /** The centred column the router's view is rendered into. */
    private final Main content = new Main();

    public MainLayout(CurrentUserProvider currentUserProvider,
            AuthenticationContext authenticationContext) {
        addClassName("app-shell");

        header = new AppHeader(currentUserProvider, authenticationContext);
        content.addClassName("app-shell__content");

        var card = new Div(content);
        card.addClassName("app-shell__card");

        add(header, card);
    }

    @Override
    public void showRouterLayoutContent(HasElement view) {
        content.getElement().appendChild(view.getElement());
        if (view instanceof Component component) {
            header.setState(HasHeaderState.of(component),
                    HasHeaderState.messageOf(component));
        }
    }

    @Override
    public void removeRouterLayoutContent(HasElement oldContent) {
        content.getElement().removeAllChildren();
    }

    /**
     * The current route decides which nav group is current, and it is read here
     * rather than in {@link #showRouterLayoutContent} for one reason: navigating
     * between two URLs of the same view — {@code /report/5} to {@code /report/6},
     * or {@code /report/5} to its {@code /review/5} alias — reuses the view
     * instance and never calls that method.
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        var chain = event.getActiveChain();
        var view = chain.isEmpty() ? null : chain.get(0).getClass();
        header.setActiveGroup(NavGroup
                .of(view, event.getLocation().getFirstSegment())
                .orElse(null));
    }

    /**
     * Switches the header to {@code state} — for a view whose header depends on
     * data it does not have at attach time. Reach it with
     * {@code findAncestor(MainLayout.class)}.
     */
    public void setHeaderState(HeaderState state) {
        setHeaderState(state, null);
    }

    /**
     * The same, carrying the hero's status line. Only {@link HeaderState#HOME}
     * draws one; every other state ignores it.
     */
    public void setHeaderState(HeaderState state, String message) {
        header.setState(state, message);
    }
}

package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.ParentLayout;
import com.vaadin.flow.router.RouteNotFoundError;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom 404 target for unknown routes (ADR-0017).
 *
 * <p>Overrides the framework's {@link RouteNotFoundError} so a mistyped or dead
 * link shows a friendly, shell-hosted page instead of a bare "Could not
 * navigate" message. Rendered inside {@link MainLayout} via
 * {@link ParentLayout}, so the user keeps the navigation and can recover.
 *
 * <p>{@link AnonymousAllowed} lets navigation access control render it for
 * unauthenticated users too; the returned {@code SC_NOT_FOUND} status keeps the
 * HTTP semantics correct.
 */
@ParentLayout(MainLayout.class)
@AnonymousAllowed
public class NotFoundView extends RouteNotFoundError {

    @Override
    public int setErrorParameter(BeforeEnterEvent event,
            ErrorParameter<NotFoundException> parameter) {
        var content = new VerticalLayout(new EmptyState(
                LucideIcon.MAP_PIN_OFF,
                "Page not found",
                "The page you were looking for does not exist. Use the navigation "
                        + "to get back on track."));
        content.setSizeFull();
        content.setJustifyContentMode(VerticalLayout.JustifyContentMode.CENTER);
        content.setAlignItems(VerticalLayout.Alignment.CENTER);
        getElement().appendChild(content.getElement());
        return HttpServletResponse.SC_NOT_FOUND;
    }
}

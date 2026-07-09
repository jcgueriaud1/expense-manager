package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.ParentLayout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graceful catch-all for uncaught exceptions raised during navigation
 * (ADR-0017).
 *
 * <p>Implements {@link HasErrorParameter} for the top of the exception
 * hierarchy, so any error a view fails to handle lands here instead of dumping
 * a raw stack trace at the user. The technical detail is logged server-side for
 * diagnosis; the user sees only a reassuring, shell-hosted message.
 *
 * <p>Rendered inside {@link MainLayout} via {@link ParentLayout} so the user
 * keeps navigation and can retry.
 */
@ParentLayout(MainLayout.class)
@AnonymousAllowed
public class ErrorView extends VerticalLayout
        implements HasErrorParameter<Exception> {

    private static final Logger log = LoggerFactory.getLogger(ErrorView.class);

    public ErrorView() {
        add(new EmptyState(
                "vaadin:warning",
                "Something went wrong",
                "An unexpected error occurred. It has been logged and the team can "
                        + "look into it. Please try again."));
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);
    }

    @Override
    public int setErrorParameter(BeforeEnterEvent event,
            ErrorParameter<Exception> parameter) {
        log.error("Uncaught exception rendering route '{}'",
                event.getLocation().getPath(), parameter.getCaughtException());
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }
}

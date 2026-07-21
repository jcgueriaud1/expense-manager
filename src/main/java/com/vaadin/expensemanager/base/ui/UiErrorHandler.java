package com.vaadin.expensemanager.base.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ErrorHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * The application-wide handler for a <strong>technical error</strong> (issue #86):
 * any exception that escapes a UI action uncaught. Instead of Vaadin's default
 * (log-only, nothing shown to the user) it logs the full cause and shows the
 * generic {@link ErrorDialog} — the real message hidden except under the local
 * developer profile.
 *
 * <p>This is deliberately the <em>only</em> place that renders a technical failure,
 * so views no longer each wire up their own "log + dialog" catch. It is installed on
 * every {@link com.vaadin.flow.server.VaadinSession} via the
 * {@link VaadinServiceInitListener} it also implements, and Vaadin routes any
 * uncaught listener/navigation exception here — a safety net even for actions no one
 * remembered to wrap.
 *
 * <p>What it does <strong>not</strong> own is the form-local outcomes: a
 * {@link com.vaadin.expensemanager.base.DomainRuleException} that should land in a
 * specific form's {@link ErrorSummary}, or an optimistic-lock conflict that should
 * offer that form's reload affordance. A global hook has no reference to the form
 * that failed, so those stay as small, explicit catches at the call site; only the
 * technical remainder falls through to here.
 */
@Component
public class UiErrorHandler implements ErrorHandler, VaadinServiceInitListener {

    private static final Logger log = LoggerFactory.getLogger(UiErrorHandler.class);

    private final boolean showTechnicalDetail;

    @Autowired
    UiErrorHandler(Environment environment) {
        // Only the local developer profile reveals the underlying cause in the UI;
        // staging/prod hide it and rely on the server log (issue #86).
        this.showTechnicalDetail = environment.matchesProfiles("local");
    }

    /** Testing seam: pin the detail-visibility flag without a Spring context. */
    UiErrorHandler(boolean showTechnicalDetail) {
        this.showTechnicalDetail = showTechnicalDetail;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(
                sessionInit -> sessionInit.getSession().setErrorHandler(this));
    }

    @Override
    public void error(ErrorEvent event) {
        Throwable error = event.getThrowable();
        log.error("Technical error surfaced to the user as a generic dialog", error);
        UI ui = UI.getCurrent();
        if (ui != null) {
            // The error is delivered on the locked UI thread for interaction-driven
            // failures, so the dialog attaches directly; a background-thread error
            // (no current UI) is logged only — there is no view to interrupt.
            technicalDialog(error).open();
        }
    }

    /**
     * Builds (but does not open) the technical dialog for {@code error} — the detail
     * is included only under the local profile. Split from {@link #error} so the
     * detail rule can be asserted without a live UI.
     */
    ErrorDialog technicalDialog(Throwable error) {
        return new ErrorDialog(showTechnicalDetail ? detailOf(error) : null);
    }

    private static String detailOf(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName()
                + (message == null ? "" : ": " + message);
    }
}

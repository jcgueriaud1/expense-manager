package com.vaadin.expensemanager.base.ui;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.vaadin.expensemanager.base.DomainRuleException;

/**
 * The single place that decides how a failed form action is shown (issue #86), so
 * the classify-and-render logic is written once here instead of being copy-pasted
 * into every editor's catch block.
 *
 * <p>Three buckets:
 * <ul>
 *   <li>an optimistic-lock conflict ({@link ObjectOptimisticLockingFailureException})
 *       → the caller's conflict affordance (the ADR-0011 reload);</li>
 *   <li>a broken domain rule ({@link DomainRuleException}) → the caller's error
 *       summary, carrying its user-actionable message;</li>
 *   <li>anything else — a technical failure the user cannot act on → logged in full
 *       and shown as a generic {@link ErrorDialog}; the real message is hidden
 *       except under the local (developer) profile.</li>
 * </ul>
 *
 * <p>Callers hand the raw exception and their own summary/conflict surfaces; this
 * owns only the technical path (the log + the dialog + the hide-except-local rule),
 * which is exactly the behaviour the issue asked to stop duplicating.
 */
@Component
public class FormErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(FormErrorHandler.class);

    private final boolean showTechnicalDetail;

    @Autowired
    FormErrorHandler(Environment environment) {
        // Only the local developer profile reveals the underlying cause in the UI;
        // staging/prod hide it and rely on the server log (issue #86).
        this.showTechnicalDetail = environment.matchesProfiles("local");
    }

    /** Testing seam: pin the detail-visibility flag without a Spring context. */
    FormErrorHandler(boolean showTechnicalDetail) {
        this.showTechnicalDetail = showTechnicalDetail;
    }

    /**
     * Routes an error thrown by a form action: a domain-rule message to
     * {@code onDomainRule} (typically the error summary), a conflict to
     * {@code onConflict} (the reload affordance), and everything else to the logged,
     * generic {@link ErrorDialog}.
     */
    public void handle(Throwable error, Consumer<String> onDomainRule,
            Runnable onConflict) {
        if (error instanceof ObjectOptimisticLockingFailureException) {
            onConflict.run();
        } else if (error instanceof DomainRuleException) {
            onDomainRule.accept(error.getMessage());
        } else {
            showTechnical(error);
        }
    }

    /**
     * As {@link #handle(Throwable, Consumer, Runnable)}, for editors with no conflict
     * affordance — there a stale write is simply technical (logged + dialog).
     */
    public void handle(Throwable error, Consumer<String> onDomainRule) {
        handle(error, onDomainRule, () -> showTechnical(error));
    }

    private void showTechnical(Throwable error) {
        log.error("Technical error surfaced to the user as a generic dialog", error);
        technicalDialog(error).open();
    }

    /**
     * Builds (but does not open) the technical dialog for {@code error} — the detail
     * is included only under the local profile. Split from {@link #showTechnical} so
     * the classify/detail rules can be asserted without a live UI.
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

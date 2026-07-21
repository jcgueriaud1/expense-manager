package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * The shared modal for a <strong>technical error</strong> (issue #86): an
 * unexpected failure the user cannot act on. It shows only a reassuring, generic
 * message — the real cause is logged server-side by {@link FormErrorHandler} and
 * never shown — with one exception: under the local (developer) profile the
 * underlying detail is appended so a developer sees what broke without tailing the
 * log.
 *
 * <p>Deliberately distinct from the top-of-form {@link ErrorSummary}, which carries
 * user-actionable validation messages ({@link com.vaadin.expensemanager.base.DomainRuleException}).
 * A technical error is a modal interruption, not a field-linked list — the user's
 * input was fine, something behind it broke. The wording mirrors the navigation
 * catch-all {@link ErrorView} so the two "something went wrong" surfaces read alike.
 */
public class ErrorDialog extends Dialog {

    static final String TITLE = "Something went wrong";
    static final String MESSAGE = "An unexpected error occurred. It has been logged "
            + "and the team can look into it. Please try again.";

    /**
     * @param detail a developer-facing cause to show under the message, or
     *               {@code null} to show only the generic message (the production
     *               default — the real cause is logged, not shown)
     */
    public ErrorDialog(String detail) {
        setHeaderTitle(TITLE);

        var content = new VerticalLayout(new Paragraph(MESSAGE));
        content.setPadding(false);
        content.setSpacing(true);
        if (detail != null && !detail.isBlank()) {
            var pre = new Pre(detail);
            pre.addClassName("error-dialog-detail");
            content.add(pre);
        }
        add(content);

        var close = new Button("Close", event -> close());
        close.addThemeVariants(ButtonVariant.PRIMARY);
        getFooter().add(close);
    }
}

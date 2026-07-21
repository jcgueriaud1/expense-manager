package com.vaadin.expensemanager.base;

/**
 * A broken <strong>domain rule</strong> the user can act on — a validation or
 * business-rule violation whose message is safe and useful to read in a form's
 * error summary ("Add at least one line before submitting.", "Name is required"),
 * as opposed to a <em>technical</em> failure (a row missing, a lookup by a stale
 * id, a bug) that the user cannot fix and should never see (issue #86).
 *
 * <p>It extends {@link IllegalArgumentException} deliberately: the domain and
 * services already model "bad input" as an {@code IllegalArgumentException}, and so
 * do their tests, so keeping that supertype preserves every existing type contract.
 * The UI's {@link com.vaadin.expensemanager.base.ui.FormErrorHandler} catches this
 * <em>before</em> the plain {@code IllegalArgumentException}/{@code IllegalStateException}
 * technical bucket, routing its message to the summary while the un-marked
 * exceptions fall through to a generic, logged error dialog.
 */
public class DomainRuleException extends IllegalArgumentException {

    public DomainRuleException(String message) {
        super(message);
    }
}

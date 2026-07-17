package com.vaadin.expensemanager.base.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.binder.BindingValidationStatus;
import com.vaadin.flow.data.binder.ValidationResult;

/**
 * The single, accessible <strong>top-of-form error summary</strong> for every
 * form and editor in the app. It replaces the {@code Div} + {@code role} +
 * hand-rolled {@code showErrors}/{@code clearErrors} scaffold that was
 * copy-pasted across each view (F-045): the container styling, ARIA wiring and
 * post-submit focus behaviour now live here, once.
 *
 * <p>Behaviour (adapted from the GOV.UK / reindeer-plus error-summary pattern —
 * the styling is our own Aura, only the behaviour is borrowed):
 * <ul>
 *   <li><strong>Focus on submit.</strong> The summary is a labelled group
 *       ({@code role="group"} + {@code aria-labelledby} → its heading,
 *       {@code tabindex="0"} — see the constructor for why not {@code -1}). Each
 *       {@code show*} call moves keyboard focus to
 *       it, so a screen reader announces "There are N errors" and the summary
 *       scrolls into view — no silent, off-screen failure.</li>
 *   <li><strong>Field-linked entries.</strong> {@link #showValidationErrors} turns
 *       each field-level error from a {@link BinderValidationStatus} into a
 *       control that, when activated, calls {@code focus()} on the offending
 *       field — one click/Enter from the summary to the input to fix. Vaadin
 *       already wires the reverse link (each invalid field points at its own
 *       error message via {@code aria-describedby} once the binder has
 *       validated), so the two together let a keyboard/AT user navigate
 *       summary → field → message.</li>
 * </ul>
 *
 * <p>Three entry points, one behaviour:
 * <ul>
 *   <li>{@link #showValidationErrors(BinderValidationStatus)} — the accessible,
 *       field-linked path; use wherever a {@link Binder} is available.</li>
 *   <li>{@link #show(String...)} / {@link #show(List)} — plain messages with no
 *       field to focus (service-side guards, cross-field rules).</li>
 *   <li>{@link #showCustom(String, Component...)} — a custom body (e.g. the
 *       optimistic-lock "reload" affordance) in the same styled, focused box.</li>
 * </ul>
 * {@link #clear()} empties and hides it. The summary starts hidden.
 */
public final class ErrorSummary extends Div implements Focusable<ErrorSummary> {

    private static final Logger log = LoggerFactory.getLogger(ErrorSummary.class);

    /** Per-instance heading id so several summaries (view + dialog) can coexist. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static final String DEFAULT_HEADING = "Please fix the following:";

    /**
     * Last-resort text for a validation error that reached the summary with a blank
     * message. This should never happen — every constraint that can fail must carry
     * its own message (e.g. a picker's bad-input / incomplete-input i18n text). A
     * blank message is a configuration bug, so we substitute this and {@code warn}
     * (issue #85) rather than render an empty, meaningless bullet.
     */
    private static final String BLANK_MESSAGE_FALLBACK = "This field is invalid";

    private final H3 heading = new H3();

    public ErrorSummary() {
        addClassName("error-summary");
        setVisible(false);
        // A labelled, programmatically-focusable group — not role="alert": we move
        // focus here on submit, and an alert that also receives focus double-speaks.
        getElement().setAttribute("role", "group");
        // tabindex=0, not the -1 the GOV.UK pattern would use. Inside a Dialog the
        // host carries tabindex=0 (Vaadin PR #10024), so it is the first node in the
        // dialog's focus trap. A -1 summary is absent from that trap list, so after
        // we focus it on submit the trap's next-Tab math (indexOf = -1 → index 0)
        // lands on the dialog frame — a dead stop before the fields. Making the
        // summary a real trap member (0) sends the next Tab to the first error link
        // instead. Cost: it becomes a manual tab stop wherever the summary shows.
        // Revert to -1 once Vaadin fixes the trap's handling of focused -1 elements:
        // https://github.com/vaadin/web-components/issues/3486
        setTabIndex(0);

        String headingId = "error-summary-heading-" + SEQUENCE.incrementAndGet();
        heading.setId(headingId);
        heading.addClassName("summary-heading");
        getElement().setAttribute("aria-labelledby", headingId);
    }

    /**
     * Renders each error from a binder validation, linking every field-level
     * error to its field so activating the entry focuses the input. Bean-level
     * (cross-field) errors are listed as plain text. Nothing shows and the
     * summary stays hidden when the status is OK.
     *
     * @return {@code true} if the status carried errors and the summary is now shown
     */
    public boolean showValidationErrors(BinderValidationStatus<?> status) {
        var items = new ArrayList<ListItem>();
        for (BindingValidationStatus<?> fieldError : status.getFieldValidationErrors()) {
            fieldError.getMessage().ifPresent(message ->
                    items.add(fieldEntry(orFallback(message), fieldError.getField())));
        }
        status.getBeanValidationErrors().stream()
                .filter(ValidationResult::isError)
                .map(ValidationResult::getErrorMessage)
                .forEach(message -> items.add(new ListItem(orFallback(message))));
        return render(items);
    }

    /** Shows plain messages with no field to focus (service guards, etc.). */
    public boolean show(String... messages) {
        return show(List.of(messages));
    }

    /** Shows plain messages with no field to focus (service guards, etc.). */
    public boolean show(List<String> messages) {
        return render(messages.stream().distinct().map(ListItem::new).toList());
    }

    /**
     * Shows a custom body under a custom heading in the same styled, focused box —
     * for summaries that aren't a plain message list (e.g. a heading, an
     * explanation and a "Reload" button for an optimistic-lock conflict).
     */
    public void showCustom(String headingText, Component... body) {
        removeAll();
        heading.setText(headingText);
        add(heading);
        add(body);
        setVisible(true);
        focus();
    }

    /** Empties and hides the summary. */
    public void clear() {
        removeAll();
        setVisible(false);
    }

    private boolean render(List<ListItem> items) {
        removeAll();
        if (items.isEmpty()) {
            setVisible(false);
            return false;
        }
        heading.setText(DEFAULT_HEADING);
        var list = new UnorderedList(items.toArray(ListItem[]::new));
        add(heading, list);
        setVisible(true);
        // Moves keyboard focus here and scrolls it into view — the whole point of
        // the pattern for AT and keyboard users after a failed submit.
        focus();
        return true;
    }

    /**
     * Guards against a validation error reaching the summary with a blank message —
     * which would otherwise render an empty, meaningless bullet (issue #85). Returns
     * the message as-is when it carries text, or the {@link #BLANK_MESSAGE_FALLBACK}
     * (plus a {@code warn}) when it doesn't, since a blank message is a configuration
     * bug in the offending field's constraints, not something the user can act on.
     */
    private static String orFallback(String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        log.warn("A validation error reached the error summary with a blank message; "
                + "a field is missing the error text for a failing constraint "
                + "(e.g. a picker's bad-input / incomplete-input i18n message). "
                + "Falling back to \"{}\".", BLANK_MESSAGE_FALLBACK);
        return BLANK_MESSAGE_FALLBACK;
    }

    private static ListItem fieldEntry(String message, HasValue<?, ?> field) {
        if (field instanceof Focusable<?> focusable) {
            // A button (not an anchor): focus() drives the field reliably server-side
            // regardless of whether the field's focusable element carries an id.
            var link = new Button(message, event -> focusable.focus());
            link.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            link.addClassName("error-summary-link");
            return new ListItem(link);
        }
        return new ListItem(message);
    }
}

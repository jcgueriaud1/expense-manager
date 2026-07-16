package com.vaadin.expensemanager.base.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
 *       {@code tabindex="-1"}). Each {@code show*} call moves keyboard focus to
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

    /** Per-instance heading id so several summaries (view + dialog) can coexist. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static final String DEFAULT_HEADING = "Please fix the following:";

    private final H3 heading = new H3();

    public ErrorSummary() {
        addClassName("error-summary");
        setVisible(false);
        // A labelled, programmatically-focusable group — not role="alert": we move
        // focus here on submit, and an alert that also receives focus double-speaks.
        getElement().setAttribute("role", "group");
        setTabIndex(-1);

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
                    items.add(fieldEntry(message, fieldError.getField())));
        }
        status.getBeanValidationErrors().stream()
                .filter(ValidationResult::isError)
                .map(ValidationResult::getErrorMessage)
                .forEach(message -> items.add(new ListItem(message)));
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

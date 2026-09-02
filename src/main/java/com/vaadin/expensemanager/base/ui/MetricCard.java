package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * One at-a-glance aggregate above a list — a caption, one large figure, and a
 * one-line breakdown under it (docs/design/components/metric-card.md).
 *
 * <p><strong>A summary, never a control.</strong> Nothing in it is clickable, it
 * is not a tab stop, and it has no hover or focus affordance: a figure that
 * filtered the list would be a different component and would need a design.
 *
 * <p>Parameterised by caption/figure/sub-line rather than hard-coded to one
 * view's three, because My Expenses and Approvals draw the identical band with
 * different copy.
 *
 * <p>The sub-line is <strong>partially</strong> coloured: the optional
 * {@code alert} fragment is a {@link Span} <em>nested inside</em> the sub-line
 * rather than a sibling, so the "·" separating them stays secondary while only
 * the fragment turns red.
 */
public class MetricCard extends VerticalLayout {

    /** A card whose whole sub-line is plain secondary text. */
    public MetricCard(String caption, String figure, String subLine) {
        this(caption, figure, subLine, null);
    }

    /**
     * @param caption the eyebrow, e.g. {@code "Needs you"} or
     *                {@code "Reimbursed 2026"} — the caption is what says whether
     *                the figure is a count or an amount, so never append a unit
     *                to the figure to disambiguate
     * @param figure  the single large number, already formatted
     * @param subLine the breakdown under the figure
     * @param alert   the one fragment of the sub-line to call out in the alert
     *                colour, or {@code null} for none; the "·" before it is added
     *                here so it cannot be coloured by mistake
     */
    public MetricCard(String caption, String figure, String subLine, String alert) {
        addClassName("metric-card");
        setPadding(false);
        setSpacing("var(--vaadin-gap-m)");

        var label = new Span(caption);
        label.addClassName("metric-card-label");

        var value = new Span(figure);
        value.addClassName("metric-card-value");

        var sub = new Span(subLine);
        sub.addClassName("metric-card-sub");
        if (alert != null && !alert.isBlank()) {
            var fragment = new Span(alert);
            fragment.addClassName("metric-card-sub-alert");
            sub.add(new Text(" · "), fragment);
        }

        add(label, value, sub);
    }
}

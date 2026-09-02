package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * One titled, collapsible group of cards in a list — a disclosure chevron, an
 * uppercase label, a right-aligned count, and the stack of cards under it
 * (docs/design/components/report-list-section.md).
 *
 * <p>Built on {@link Details} rather than a hand-rolled header + toggle so the
 * accessibility comes from the platform component: the summary is the control
 * (one tab stop, {@code aria-expanded}, Enter/Space), it carries Aura's focus
 * ring around chevron, label <em>and</em> count, and the collapsed content
 * leaves the accessible tree instead of merely being hidden (ADR-0020).
 *
 * <p><strong>The count is part of the summary</strong>, so it stays visible when
 * the group is collapsed — it is then the only thing saying what is inside.
 *
 * <p>The label is uppercased in CSS, never in the string: a screen reader
 * announcing "N-E-E-D-S" is a real outcome of an uppercase literal.
 *
 * <p>Generic on purpose (label + count + content): My Expenses and Approvals
 * draw the same group.
 */
public class ReportListSection extends Details {

    /**
     * @param label   the group's name in sentence case, e.g. {@code "Closed"} —
     *                CSS does the uppercasing
     * @param count   the pre-formatted count, e.g. {@code "2 reports"}
     * @param content the card stack; shown expanded, which is the default for
     *                every section (collapse is per-session and not persisted)
     */
    public ReportListSection(String label, String count, Component content) {
        addClassName("report-list-section");
        /*
         * Aura indents a Details' content under its summary and pads it; the
         * design does neither, so the padding variant is dropped and the
         * header-to-cards gap set in CSS instead. AURA_NO_PADDING has no
         * theme-agnostic twin — the variant only exists under Aura, which is the
         * only theme this app runs.
         */
        addThemeVariants(DetailsVariant.AURA_NO_PADDING);

        var labelSpan = new Span(label);
        labelSpan.addClassName("section-label");

        var countSpan = new Span(count);
        countSpan.addClassName("report-list-section-count");

        var header = new HorizontalLayout(labelSpan, countSpan);
        header.addClassName("report-list-section-header");
        header.setWidthFull();
        header.setPadding(false);
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        setSummary(header);
        add(content);
        setOpened(true);
    }
}

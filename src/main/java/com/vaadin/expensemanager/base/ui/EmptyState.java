package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.AbstractIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Shared "there is nothing here yet" placeholder for empty collections, unfiltered
 * results, and not-yet-populated views (ADR-0017).
 *
 * <p>A thin skeleton by design: a centred icon, a heading, and an explanatory
 * line. Real polish (illustrations, calls to action) lands with the first
 * feature that renders each state; features reuse this instead of reinventing
 * their own empty layout so UX states stay consistent across the app.
 */
public class EmptyState extends VerticalLayout {

    /**
     * @param icon        the glyph, built by the caller — e.g.
     *                    {@code LucideIcon.INBOX.create()}; {@code null} for no
     *                    icon. It used to be a {@code "collection:name"} string,
     *                    which only the Lumo font-icon sets can be addressed by
     *                    and so hardcoded the collection here for all five callers
     *                    (#163). {@link AbstractIcon} is the supertype of every
     *                    icon Vaadin has, and the one that carries
     *                    {@code setSize}, which this class needs.
     * @param heading     short headline, e.g. "No expense reports yet"
     * @param description one-line explanation of why it is empty / what to do
     */
    public EmptyState(AbstractIcon<?> icon, String heading, String description) {
        setAlignItems(Alignment.CENTER);
        setSpacing(false);
        getStyle().setTextAlign(com.vaadin.flow.dom.Style.TextAlign.CENTER);

        if (icon != null) {
            // An empty state's glyph sits in a layout this class drew, so nothing
            // else sizes it — one of the few places LucideIcon#create(String) is
            // right. 3em keeps it proportional to the heading below it.
            icon.setSize("3em");
            icon.getStyle().setColor("var(--vaadin-text-color-secondary)");
            add(new Div(icon));
        }

        var title = new H2(heading);
        title.getStyle().setFontSize("var(--aura-font-size-xl)");
        add(title);

        var body = new Paragraph(description);
        body.getStyle().setColor("var(--vaadin-text-color-secondary)");
        add(body);
    }
}

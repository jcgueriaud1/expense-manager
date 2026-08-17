package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
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
     * @param icon        the glyph to show, e.g. {@link LucideIcon#INBOX};
     *                    {@code null} for no icon
     * @param heading     short headline, e.g. "No expense reports yet"
     * @param description one-line explanation of why it is empty / what to do
     */
    public EmptyState(LucideIcon icon, String heading, String description) {
        setAlignItems(Alignment.CENTER);
        setSpacing(false);
        getStyle().setTextAlign(com.vaadin.flow.dom.Style.TextAlign.CENTER);

        if (icon != null) {
            var iconComponent = icon.create();
            iconComponent.setSize("3em");
            iconComponent.getStyle().setColor("var(--vaadin-text-color-secondary)");
            add(new Div(iconComponent));
        }

        var title = new H2(heading);
        title.getStyle().setFontSize("var(--aura-font-size-xl)");
        add(title);

        var body = new Paragraph(description);
        body.getStyle().setColor("var(--vaadin-text-color-secondary)");
        add(body);
    }
}

package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.Span;

/**
 * One number a screen can answer about itself, shown above the work it describes.
 *
 * <p>The app has no dashboard: each screen carries the few statistics that belong
 * to it, drawn from the data it has already loaded (ADR-0026). It reads, it does
 * not act — the filters below it are the controls.
 *
 * <p>Built on the official {@link Card}: the label goes in the header slot, the
 * value in the content, the qualifier in the footer, and the padding, radius and
 * slot spacing come from the component's own base styles — every one a
 * {@code --vaadin-card-*} token — with Aura contributing the surface colour. Ours
 * is the value's size, because "big number" is not something a card slot
 * expresses, and the surface level.
 */
public class StatCard extends Card {

    private final Span value = new Span();

    /**
     * @param label     what is being counted, e.g. "Needs you"
     * @param qualifier the detail under the number ("€234 · 1 rejected"), or
     *                  {@code null} for none
     */
    public StatCard(String label, String value, String qualifier) {
        // The bare constants, not the AURA_*/LUMO_* aliases: those are deprecated
        // in favour of these, which carry the same theme string.
        addThemeVariants(CardVariant.OUTLINED);
        addClassName("stat-card");

        var caption = new Span(label);
        caption.addClassName("stat-card-label");
        setHeader(caption);

        this.value.addClassName("stat-card-value");
        this.value.setText(value);
        add(this.value);

        if (qualifier != null) {
            var footer = new Span(qualifier);
            footer.addClassName("muted-xs");
            addToFooter(footer);
        }
        // The label already names the number; a screen reader should hear them as
        // one thing rather than two loose spans.
        setAriaLabel(label + ": " + value + (qualifier == null ? "" : ", " + qualifier));
    }
}

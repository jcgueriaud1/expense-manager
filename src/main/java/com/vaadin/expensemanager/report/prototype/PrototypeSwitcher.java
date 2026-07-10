package com.vaadin.expensemanager.report.prototype;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * PROTOTYPE — the shared floating variant switcher (bottom-centre pill).
 * Left/right arrows cycle variants (wrapping) and rewrite the {@code ?variant=}
 * query param so the choice is shareable and reload-stable.
 *
 * <p>Keyboard: <kbd>Alt</kbd>+<kbd>←</kbd> / <kbd>Alt</kbd>+<kbd>→</kbd> cycle.
 * Plain arrows are deliberately NOT bound — this screen is full of number
 * fields and combo boxes where the arrows must edit values, not switch pages.
 */
final class PrototypeSwitcher extends HorizontalLayout {

    record Variant(String key, String name) {
    }

    PrototypeSwitcher(List<Variant> variants, String currentKey, Consumer<String> onSelect) {
        int idx = indexOf(variants, currentKey);
        Variant current = variants.get(idx);

        var prev = new Button(VaadinIcon.CHEVRON_LEFT.create());
        var next = new Button(VaadinIcon.CHEVRON_RIGHT.create());
        // Aura has no tertiary-inline or contrast variant (both Lumo-only), so
        // these icon-only nav buttons use the plain Aura tertiary variant.
        prev.addThemeVariants(ButtonVariant.TERTIARY);
        next.addThemeVariants(ButtonVariant.TERTIARY);

        int size = variants.size();
        String prevKey = variants.get((idx - 1 + size) % size).key();
        String nextKey = variants.get((idx + 1) % size).key();
        prev.addClickListener(e -> onSelect.accept(prevKey));
        next.addClickListener(e -> onSelect.accept(nextKey));

        var label = new Span(current.key() + " — " + current.name());
        label.getStyle().setFontWeight("600").setColor("var(--vaadin-background-color)");

        var hint = new Span("Alt + ← →");
        hint.getStyle()
                .setFontSize("var(--aura-font-size-xs)")
                .setColor("var(--vaadin-background-color)")
                .set("opacity", "0.7");

        add(prev, label, hint, next);
        setAlignItems(FlexComponent.Alignment.CENTER);
        setSpacing(true);

        getStyle()
                .set("position", "fixed")
                .set("bottom", "20px")
                .set("left", "50%")
                .set("transform", "translateX(-50%)")
                .set("z-index", "1000")
                .set("background", "var(--vaadin-text-color)")
                .set("padding", "6px 14px")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("box-shadow", "var(--aura-shadow-m)")
                .set("gap", "10px")
                .set("align-items", "center");

        // Alt+Arrow global cycling (won't fight text editing).
        Shortcuts.addShortcutListener(this, () -> onSelect.accept(prevKey),
                Key.ARROW_LEFT, KeyModifier.ALT);
        Shortcuts.addShortcutListener(this, () -> onSelect.accept(nextKey),
                Key.ARROW_RIGHT, KeyModifier.ALT);
    }

    private static int indexOf(List<Variant> variants, String key) {
        for (int i = 0; i < variants.size(); i++) {
            if (variants.get(i).key().equals(key)) {
                return i;
            }
        }
        return 0;
    }
}

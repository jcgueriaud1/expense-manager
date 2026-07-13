package com.vaadin.expensemanager.report.ui;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatRate;

/**
 * The receipt-style card list of the variant-D line editor (F-004): one card per
 * line showing its type, comment/rate, and derived gross + net/VAT breakdown. The
 * selected card is highlighted so the list and the editor panel stay visually
 * linked. Cards are keyboard-operable (ADR-0020): a focusable {@code button}-role
 * element that responds to Enter/Space and carries an accessible name.
 */
final class ReportLineCards extends VerticalLayout {

    private Consumer<ReportLineModel> selectListener = model -> { };

    ReportLineCards() {
        setPadding(false);
        setSpacing(true);
        setWidthFull();
    }

    void setSelectListener(Consumer<ReportLineModel> listener) {
        this.selectListener = listener;
    }

    /** Renders the cards, highlighting {@code selected} (may be {@code null}). */
    void render(List<ReportLineModel> lines, ReportLineModel selected) {
        removeAll();
        if (lines.isEmpty()) {
            var empty = new Span("No expenses yet — add your first.");
            empty.getStyle().setColor("var(--vaadin-text-color-secondary)");
            add(empty);
            return;
        }
        for (ReportLineModel line : lines) {
            add(card(line, line == selected));
        }
    }

    private Div card(ReportLineModel line, boolean isSelected) {
        var name = new Span(line.getExpenseType() == null ? "New expense"
                : line.getExpenseType().name());
        name.getStyle().setFontWeight("600");
        String subtitleText = line.getComment() != null && !line.getComment().isBlank()
                ? line.getComment()
                : (line.getVatRate() == null ? ""
                        : "VAT " + formatRate(line.getVatRate().value()));
        var subtitle = new Span(subtitleText);
        subtitle.getStyle().setColor("var(--vaadin-text-color-secondary)")
                .setFontSize("var(--aura-font-size-s)");
        var left = new VerticalLayout(name, subtitle);
        left.setPadding(false);
        left.setSpacing(false);

        var gross = new Span(line.hasAmount() ? formatEur(line.grossAmount()) : "€—");
        gross.getStyle().setFontWeight("700");
        var breakdown = new Span(line.hasAmount() && line.getVatRate() != null
                ? "net " + formatEur(line.netAmount()) + " · VAT " + formatEur(line.vatAmount())
                : "");
        breakdown.getStyle().setColor("var(--vaadin-text-color-secondary)")
                .setFontSize("var(--aura-font-size-xs)");
        var amounts = new VerticalLayout(gross, breakdown);
        amounts.setPadding(false);
        amounts.setSpacing(false);
        amounts.setAlignItems(FlexComponent.Alignment.END);

        var body = new HorizontalLayout(left, amounts);
        body.setWidthFull();
        body.setAlignItems(FlexComponent.Alignment.CENTER);
        body.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        body.setFlexGrow(1, left);

        var cardDiv = new Div(body);
        cardDiv.getStyle()
                .setWidth("100%")
                .set("padding", "var(--vaadin-padding)")
                .set("border", isSelected ? "2px solid var(--aura-accent-color)"
                        : "1px solid var(--vaadin-border-color)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("cursor", "pointer")
                .set("background", isSelected ? "var(--aura-accent-surface)"
                        : "var(--aura-surface-color)");
        cardDiv.getElement().setAttribute("role", "button");
        cardDiv.getElement().setAttribute("tabindex", "0");
        cardDiv.getElement().setAttribute("aria-label", "Edit " + name.getText()
                + (line.hasAmount() ? ", " + formatEur(line.grossAmount()) : ""));
        cardDiv.addClickListener(event -> selectListener.accept(line));
        cardDiv.getElement().addEventListener("keydown", event -> selectListener.accept(line))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        return cardDiv;
    }
}

package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.expensemanager.report.domain.LineMoney;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;

/**
 * The sticky totals bar: live net/VAT/gross plus the report actions. The totals
 * are <strong>derived reactively via Signals</strong> (ADR-0015) — the view pushes
 * the current lines with {@link #setLines}, and the bound text recomputes the
 * per-line-then-sum totals (ADR-0010, ADR-0019).
 */
final class ReportTotalsBar extends Div {

    private final ValueSignal<List<ReportLineModel>> lines = new ValueSignal<>(List.of());
    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");

    ReportTotalsBar(Runnable onSave, Runnable onDelete) {
        var total = new Span();
        total.getStyle().setFontWeight("700").setFontSize("var(--aura-font-size-xl)");
        total.bindText(Signal.computed(() -> formatEur(grossTotal())));

        var breakdown = new Span();
        breakdown.getStyle().setColor("var(--vaadin-text-color-secondary)")
                .setFontSize("var(--aura-font-size-s)");
        breakdown.bindText(Signal.computed(() ->
                "net " + formatEur(netTotal()) + "  ·  VAT " + formatEur(vatTotal())));

        var totals = new VerticalLayout(new Span("Report total"), total, breakdown);
        totals.setPadding(false);
        totals.setSpacing(false);

        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(event -> onSave.run());
        delete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        delete.addClickListener(event -> onDelete.run());
        var actions = new HorizontalLayout(save, delete);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        var bar = new HorizontalLayout(totals, actions);
        bar.setWidthFull();
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.getStyle().setFlexWrap(Style.FlexWrap.WRAP);

        add(bar);
        getStyle()
                .set("background", "var(--aura-accent-surface)")
                .set("padding", "var(--vaadin-padding)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .setWidth("100%");
    }

    /** Pushes the current lines; the bound totals recompute reactively. */
    void setLines(List<ReportLineModel> value) {
        lines.set(value == null ? List.of() : value);
    }

    void setSaveVisible(boolean visible) {
        save.setVisible(visible);
    }

    void setDeleteVisible(boolean visible) {
        delete.setVisible(visible);
    }

    private BigDecimal grossTotal() {
        return lines.get().stream().filter(ReportLineModel::hasAmount)
                .map(ReportLineModel::grossAmount)
                .reduce(LineMoney.zero(), BigDecimal::add);
    }

    private BigDecimal netTotal() {
        return lines.get().stream()
                .filter(l -> l.hasAmount() && l.getVatRate() != null)
                .map(ReportLineModel::netAmount)
                .reduce(LineMoney.zero(), BigDecimal::add);
    }

    private BigDecimal vatTotal() {
        return lines.get().stream()
                .filter(l -> l.hasAmount() && l.getVatRate() != null)
                .map(ReportLineModel::vatAmount)
                .reduce(LineMoney.zero(), BigDecimal::add);
    }
}

package com.vaadin.expensemanager.report.prototype;

import com.vaadin.expensemanager.report.prototype.PrototypeModel.ExpenseType;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.LineDraft;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.ReportDraft;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.VatRate;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

/**
 * VARIANT D — Cards + persistent side panel (the C×B hybrid).
 *
 * <p>Variant C's receipt-style stacked cards and sticky report total, but
 * selecting a card loads it into Variant B's persistent right-hand form instead
 * of opening a modal. The list stays a scannable receipt; the editor is always
 * in the same place, never covers the list, and gives Phase 3 receipts an
 * obvious home. The selected card is highlighted so the list and the form stay
 * visually linked.
 */
final class VariantDCardsSidePanel extends VerticalLayout {

    static final String NAME = "Cards + side panel";

    private final ReportDraft report;
    private final VerticalLayout cardList = new VerticalLayout();
    private final Span stickyGross = new Span();
    private final Span stickyBreakdown = new Span();

    // Persistent side form (Variant B style)
    private final ComboBox<ExpenseType> typeField = new ComboBox<>("Expense type");
    private final ComboBox<VatRate> vatField = new ComboBox<>("VAT rate");
    private final BigDecimalField amountField = new BigDecimalField("Gross amount (paid)");
    private final TextField commentField = new TextField("Comment");
    private final H3 formHeading = new H3("Select a card to edit");
    private final Binder<LineDraft> binder = new Binder<>();
    private final Button removeButton = new Button("Remove line");
    private final Button applyButton = new Button("Apply");
    private LineDraft current;

    VariantDCardsSidePanel(ReportDraft report) {
        this.report = report;
        setSizeFull();
        setPadding(true);

        add(header());
        var left = leftColumn();
        var split = new HorizontalLayout(left, sidePanel());
        split.setWidthFull();
        split.setFlexGrow(1, left);
        add(split);

        configureForm();
        renderCards();
        refreshTotals();
        setFormEnabled(false);
    }

    private Div header() {
        var title = new H2("Expense report");
        var date = new DatePicker("Report date");
        date.setValue(report.reportDate);
        var info = new TextField("Additional information");
        info.setValue(report.additionalInformation);
        var status = new Span(report.status);
        status.getElement().getThemeList().add("badge");

        var top = new HorizontalLayout(title, status);
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        var wrap = new Div(top, new HorizontalLayout(date, info));
        return wrap;
    }

    private VerticalLayout leftColumn() {
        cardList.setPadding(false);
        cardList.setSpacing(true);

        var add = new Button("Add expense", VaadinIcon.PLUS.create(), e -> {
            var line = new LineDraft();
            report.lines.add(line);
            renderCards();
            edit(line);
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.setWidthFull();

        var col = new VerticalLayout(stickyTotals(), cardList, add);
        col.setPadding(false);
        return col;
    }

    private Div stickyTotals() {
        stickyGross.getStyle().setFontWeight("700").setFontSize("var(--aura-font-size-xl)");
        stickyBreakdown.getStyle()
                .setColor("var(--vaadin-text-color-secondary)").setFontSize("var(--aura-font-size-s)");

        var left = new VerticalLayout(new Span("Report total"), stickyGross);
        left.setPadding(false);
        left.setSpacing(false);

        var right = new VerticalLayout(stickyBreakdown,
                new HorizontalLayout(saveReport(), submit()));
        right.setPadding(false);
        right.setAlignItems(FlexComponent.Alignment.END);

        var bar = new HorizontalLayout(left, right);
        bar.setWidthFull();
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.getStyle()
                .set("position", "sticky").set("top", "0").set("z-index", "5")
                .set("background", "var(--aura-accent-surface)")
                .set("padding", "var(--vaadin-padding)")
                .set("border-radius", "var(--vaadin-radius-l)");
        var wrap = new Div(bar);
        wrap.getStyle().set("width", "100%");
        return wrap;
    }

    private void renderCards() {
        cardList.removeAll();
        if (report.lines.isEmpty()) {
            cardList.add(new Span("No expenses yet — add your first."));
            return;
        }
        for (LineDraft line : report.lines) {
            cardList.add(card(line));
        }
    }

    private Div card(LineDraft line) {
        var t = PrototypeModel.lineTotals(line);
        var name = new Span(line.expenseType == null ? "New expense" : line.expenseType.name());
        name.getStyle().setFontWeight("600");
        var comment = new Span(line.comment == null || line.comment.isBlank()
                ? (line.vatRate == null ? "" : "VAT " + line.vatRate.label()) : line.comment);
        comment.getStyle().setColor("var(--vaadin-text-color-secondary)")
                .setFontSize("var(--aura-font-size-s)");
        var left = new VerticalLayout(name, comment);
        left.setPadding(false);
        left.setSpacing(false);

        var gross = new Span(PrototypeModel.euro(t.gross()));
        gross.getStyle().setFontWeight("700");
        var breakdown = new Span("net " + PrototypeModel.euro(t.net())
                + " · VAT " + PrototypeModel.euro(t.vat())
                + (line.vatRate == null ? "" : " (" + line.vatRate.label() + ")"));
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

        boolean selected = line == current;
        var cardDiv = new Div(body);
        cardDiv.getStyle()
                .set("width", "100%")
                .set("padding", "var(--vaadin-padding)")
                .set("border", selected
                        ? "2px solid var(--aura-accent-color)"
                        : "1px solid var(--vaadin-border-color)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("cursor", "pointer")
                .set("background", selected
                        ? "var(--aura-accent-surface)" : "var(--aura-surface-color)");
        cardDiv.addClickListener(e -> edit(line));
        return cardDiv;
    }

    private VerticalLayout sidePanel() {
        var form = new FormLayout(typeField, amountField, vatField, commentField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        applyButton.addClickListener(e -> applyToCurrent());
        applyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        removeButton.addClickListener(e -> removeCurrent());
        removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        var panel = new VerticalLayout(formHeading, form,
                new HorizontalLayout(applyButton, removeButton));
        panel.setWidth("380px");
        panel.getStyle()
                .set("background", "var(--vaadin-background-container)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("position", "sticky").set("top", "0");
        return panel;
    }

    private void configureForm() {
        typeField.setItems(PrototypeModel.EXPENSE_TYPES);
        typeField.setItemLabelGenerator(ExpenseType::name);
        vatField.setItems(PrototypeModel.VAT_RATES);
        vatField.setItemLabelGenerator(VatRate::label);
        typeField.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                vatField.setValue(e.getValue().defaultRate()); // default, overridable
            }
        });
        binder.forField(typeField).bind(l -> l.expenseType, (l, v) -> l.expenseType = v);
        binder.forField(vatField).bind(l -> l.vatRate, (l, v) -> l.vatRate = v);
        binder.forField(amountField).bind(l -> l.amount, (l, v) -> l.amount = v);
        binder.forField(commentField).bind(l -> l.comment, (l, v) -> l.comment = v);
    }

    private void edit(LineDraft line) {
        current = line;
        binder.readBean(line);
        formHeading.setText(line.expenseType == null ? "New expense" : line.expenseType.name());
        setFormEnabled(true);
        renderCards(); // refresh selection highlight
    }

    private void applyToCurrent() {
        if (current == null) {
            return;
        }
        binder.writeBeanAsDraft(current);
        formHeading.setText(current.expenseType == null ? "New expense" : current.expenseType.name());
        renderCards();
        refreshTotals();
    }

    private void removeCurrent() {
        if (current == null) {
            return;
        }
        report.lines.remove(current);
        current = null;
        binder.readBean(null);
        setFormEnabled(false);
        formHeading.setText("Select a card to edit");
        renderCards();
        refreshTotals();
    }

    private void setFormEnabled(boolean enabled) {
        typeField.setEnabled(enabled);
        vatField.setEnabled(enabled);
        amountField.setEnabled(enabled);
        commentField.setEnabled(enabled);
        applyButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);
    }

    private void refreshTotals() {
        var t = PrototypeModel.reportTotals(report.lines);
        stickyGross.setText(PrototypeModel.euro(t.gross()));
        stickyBreakdown.setText("net " + PrototypeModel.euro(t.net())
                + "  ·  VAT " + PrototypeModel.euro(t.vat()));
    }

    private Button saveReport() {
        var b = new Button("Save");
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return b;
    }

    private Button submit() {
        var b = new Button("Submit", VaadinIcon.PAPERPLANE.create());
        b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        b.setEnabled(!report.lines.isEmpty());
        return b;
    }
}

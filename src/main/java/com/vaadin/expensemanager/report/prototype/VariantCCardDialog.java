package com.vaadin.expensemanager.report.prototype;

import com.vaadin.expensemanager.report.prototype.PrototypeModel.ExpenseType;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.LineDraft;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.ReportDraft;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.VatRate;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

/**
 * VARIANT C — Stacked cards + modal editor. No grid: each line is a tappable
 * card (type, comment, and its own net/VAT/gross breakdown), stacked like a
 * receipt. A sticky totals bar pins the report gross to the top. Adding or
 * editing a line opens a focused modal Dialog form — one field per row, big
 * touch targets. Reads well on a phone and keeps the whole line's maths on the
 * card, at the cost of density.
 */
final class VariantCCardDialog extends VerticalLayout {

    static final String NAME = "Cards + modal editor";

    private final ReportDraft report;
    private final VerticalLayout cardList = new VerticalLayout();
    private final Span stickyGross = new Span();
    private final Span stickyBreakdown = new Span();

    VariantCCardDialog(ReportDraft report) {
        this.report = report;
        setSizeFull();
        setPadding(true);
        setMaxWidth("720px");
        getStyle().set("margin", "0 auto");

        add(header(), stickyTotals(), cardList, addButton());
        cardList.setPadding(false);
        cardList.setSpacing(true);
        renderCards();
        refreshTotals();
    }

    private Div header() {
        var title = new H2("Expense report");
        var date = new DatePicker("Report date");
        date.setValue(report.reportDate);
        var info = new TextField("Additional information");
        info.setValue(report.additionalInformation);
        info.setWidthFull();
        var status = new Span(report.status);
        status.getElement().getThemeList().add("badge");

        var top = new HorizontalLayout(title, status);
        top.setWidthFull();
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        top.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        var wrap = new Div(top, new HorizontalLayout(date, info));
        return wrap;
    }

    private Div stickyTotals() {
        stickyGross.getStyle()
                .setFontWeight("700").setFontSize("var(--aura-font-size-xl)");
        stickyBreakdown.getStyle()
                .setColor("var(--vaadin-text-color-secondary)")
                .setFontSize("var(--aura-font-size-s)");

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
                .set("position", "sticky")
                .set("top", "0")
                .set("z-index", "5")
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

        var del = new Button(VaadinIcon.TRASH.create(), e -> {
            report.lines.remove(line);
            renderCards();
            refreshTotals();
        });
        del.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);

        var body = new HorizontalLayout(left, amounts, del);
        body.setWidthFull();
        body.setAlignItems(FlexComponent.Alignment.CENTER);
        body.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        body.setFlexGrow(1, left);

        var cardDiv = new Div(body);
        cardDiv.getStyle()
                .set("width", "100%")
                .set("padding", "var(--vaadin-padding)")
                .set("border", "1px solid var(--vaadin-border-color)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("cursor", "pointer")
                .set("background", "var(--aura-surface-color)");
        cardDiv.addClickListener(e -> openEditor(line, false));
        return cardDiv;
    }

    private Button addButton() {
        var add = new Button("Add expense", VaadinIcon.PLUS.create(),
                e -> openEditor(new LineDraft(), true));
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.setWidthFull();
        return add;
    }

    private void openEditor(LineDraft line, boolean isNew) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "Add expense" : "Edit expense");
        dialog.setWidth("420px");

        var typeField = new ComboBox<ExpenseType>("Expense type");
        typeField.setItems(PrototypeModel.EXPENSE_TYPES);
        typeField.setItemLabelGenerator(ExpenseType::name);
        var vatField = new ComboBox<VatRate>("VAT rate");
        vatField.setItems(PrototypeModel.VAT_RATES);
        vatField.setItemLabelGenerator(VatRate::label);
        var amountField = new BigDecimalField("Gross amount (paid)");
        var commentField = new TextField("Comment");

        typeField.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                vatField.setValue(e.getValue().defaultRate()); // default, overridable
            }
        });

        var binder = new Binder<LineDraft>();
        binder.forField(typeField).bind(l -> l.expenseType, (l, v) -> l.expenseType = v);
        binder.forField(vatField).bind(l -> l.vatRate, (l, v) -> l.vatRate = v);
        binder.forField(amountField).bind(l -> l.amount, (l, v) -> l.amount = v);
        binder.forField(commentField).bind(l -> l.comment, (l, v) -> l.comment = v);
        binder.readBean(line);

        var form = new FormLayout(typeField, amountField, vatField, commentField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        dialog.add(form);

        var save = new Button("Save", e -> {
            binder.writeBeanAsDraft(line);
            if (isNew) {
                report.lines.add(line);
            }
            renderCards();
            refreshTotals();
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancel, save);

        dialog.open();
    }

    private void refreshTotals() {
        var t = PrototypeModel.reportTotals(report.lines);
        stickyGross.setText(PrototypeModel.euro(t.gross()));
        stickyBreakdown.setText("net " + PrototypeModel.euro(t.net())
                + "  ·  VAT " + PrototypeModel.euro(t.vat()));
    }

    private Button saveReport() {
        var b = new Button("Save");
        b.addThemeVariants(ButtonVariant.TERTIARY);
        return b;
    }

    private Button submit() {
        var b = new Button("Submit", VaadinIcon.PAPERPLANE.create());
        b.addThemeVariants(ButtonVariant.PRIMARY);
        b.setEnabled(!report.lines.isEmpty());
        return b;
    }
}

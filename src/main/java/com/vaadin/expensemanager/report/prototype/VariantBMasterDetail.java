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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

/**
 * VARIANT B — Master–detail. A compact list of lines on the left, a persistent
 * edit form on the right that binds to the selected line. Select a row → its
 * fields load into the form; "Add line" opens a blank form. The running
 * net/VAT/gross lives in a summary card above the form, always visible while
 * you edit. No inline table editing, no modal — the form is the one editor and
 * it never moves.
 */
final class VariantBMasterDetail extends VerticalLayout {

    static final String NAME = "Master–detail form";

    private final ReportDraft report;
    private final Grid<LineDraft> grid = new Grid<>(LineDraft.class, false);
    private final Binder<LineDraft> binder = new Binder<>(LineDraft.class);

    private final ComboBox<ExpenseType> typeField = new ComboBox<>("Expense type");
    private final ComboBox<VatRate> vatField = new ComboBox<>("VAT rate");
    private final BigDecimalField amountField = new BigDecimalField("Gross amount (paid)");
    private final TextArea commentField = new TextArea("Comment");

    private final Span netValue = new Span();
    private final Span vatValue = new Span();
    private final Span grossValue = new Span();
    private final H3 formHeading = new H3("Select or add a line");
    private LineDraft current;

    VariantBMasterDetail(ReportDraft report) {
        this.report = report;
        setSizeFull();
        setPadding(true);

        add(header());
        var master = masterList();
        var split = new HorizontalLayout(master, detailPanel());
        split.setWidthFull();
        split.setFlexGrow(1, master);
        add(split);

        configureForm();
        refreshTotals();
    }

    private HorizontalLayout header() {
        var title = new H2("Expense report");
        var date = new DatePicker("Report date");
        date.setValue(report.reportDate);
        var info = new TextField("Additional information");
        info.setValue(report.additionalInformation);
        info.setWidth("22em");
        var status = new Span(report.status);
        status.getElement().getThemeList().add("badge");
        var bar = new HorizontalLayout(title, date, info, status);
        bar.setAlignItems(FlexComponent.Alignment.BASELINE);
        return bar;
    }

    private VerticalLayout masterList() {
        grid.addColumn(l -> l.expenseType == null ? "New line" : l.expenseType.name())
                .setHeader("Expense type").setFlexGrow(2);
        grid.addColumn(l -> l.vatRate == null ? "—" : l.vatRate.label())
                .setHeader("VAT").setFlexGrow(0).setWidth("6em");
        grid.addColumn(l -> l.amount == null ? "—" : PrototypeModel.euro(l.amount))
                .setHeader("Gross").setFlexGrow(0).setWidth("8em")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.setItems(report.lines);
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                edit(e.getValue());
            }
        });

        var add = new Button("Add line", VaadinIcon.PLUS.create(), e -> {
            var line = new LineDraft();
            report.lines.add(line);
            grid.getDataProvider().refreshAll();
            grid.select(line);
        });
        add.addThemeVariants(ButtonVariant.TERTIARY);

        var box = new VerticalLayout(add, grid);
        box.setPadding(false);
        box.setWidthFull();
        return box;
    }

    private VerticalLayout detailPanel() {
        var totals = totalsCard();

        var form = new FormLayout(typeField, amountField, vatField, commentField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var saveLine = new Button("Apply", e -> applyToCurrent());
        saveLine.addThemeVariants(ButtonVariant.PRIMARY);
        var removeLine = new Button("Remove line", e -> removeCurrent());
        removeLine.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        var actions = new HorizontalLayout(saveLine, removeLine);

        var panel = new VerticalLayout(totals, formHeading, form, actions);
        panel.setWidth("380px");
        panel.getStyle()
                .set("background", "var(--vaadin-background-container)")
                .set("border-radius", "var(--vaadin-radius-l)");
        setFormEnabled(false);
        return panel;
    }

    private VerticalLayout totalsCard() {
        netValue.getStyle().setFontWeight("600");
        vatValue.getStyle().setFontWeight("600");
        grossValue.getStyle().setFontWeight("700").setFontSize("var(--aura-font-size-l)");
        var card = new VerticalLayout(
                row("Net", netValue), row("VAT", vatValue), row("Gross", grossValue));
        card.setPadding(false);
        card.setSpacing(false);
        var footerActions = new HorizontalLayout(
                primary("Save report"), submitBtn());
        card.add(footerActions);
        return card;
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
        formHeading.setText(line.expenseType == null ? "New line" : line.expenseType.name());
        setFormEnabled(true);
    }

    private void applyToCurrent() {
        if (current == null) {
            return;
        }
        binder.writeBeanAsDraft(current);
        grid.getDataProvider().refreshAll();
        refreshTotals();
        formHeading.setText(current.expenseType == null ? "New line" : current.expenseType.name());
    }

    private void removeCurrent() {
        if (current == null) {
            return;
        }
        report.lines.remove(current);
        current = null;
        grid.getDataProvider().refreshAll();
        grid.deselectAll();
        binder.readBean(null);
        setFormEnabled(false);
        refreshTotals();
    }

    private void setFormEnabled(boolean enabled) {
        typeField.setEnabled(enabled);
        vatField.setEnabled(enabled);
        amountField.setEnabled(enabled);
        commentField.setEnabled(enabled);
    }

    private void refreshTotals() {
        var t = PrototypeModel.reportTotals(report.lines);
        netValue.setText(PrototypeModel.euro(t.net()));
        vatValue.setText(PrototypeModel.euro(t.vat()));
        grossValue.setText(PrototypeModel.euro(t.gross()));
    }

    private HorizontalLayout row(String label, Span value) {
        var l = new Span(label);
        l.getStyle().setColor("var(--vaadin-text-color-secondary)");
        var r = new HorizontalLayout(l, value);
        r.setWidthFull();
        r.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return r;
    }

    private Button primary(String text) {
        var b = new Button(text);
        b.addThemeVariants(ButtonVariant.PRIMARY);
        return b;
    }

    private Button submitBtn() {
        var b = new Button("Submit", VaadinIcon.PAPERPLANE.create());
        b.setEnabled(!report.lines.isEmpty());
        return b;
    }
}

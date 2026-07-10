package com.vaadin.expensemanager.report.prototype;

import com.vaadin.expensemanager.report.prototype.PrototypeModel.ExpenseType;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.LineDraft;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.ReportDraft;
import com.vaadin.expensemanager.report.prototype.PrototypeModel.VatRate;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

/**
 * VARIANT A — Inline editable Grid (row editor). The ADR-0015 provisional pick.
 *
 * <p>Every line is a Grid row; pressing Edit turns the row into in-place fields
 * (ComboBox type, BigDecimalField amount, ComboBox VAT), Save/Cancel per row.
 * Live net/VAT/gross totals sit in the Grid footer. One dense table, editing
 * happens where the data already is — no separate form, no dialog.
 */
final class VariantAInlineGrid extends VerticalLayout {

    static final String NAME = "Inline editable grid";

    private final ReportDraft report;
    private final Grid<LineDraft> grid = new Grid<>(LineDraft.class, false);
    private final Binder<LineDraft> binder = new Binder<>(LineDraft.class);
    private Grid.Column<LineDraft> typeCol;
    private Grid.Column<LineDraft> netCol;
    private Grid.Column<LineDraft> vatAmtCol;
    private Grid.Column<LineDraft> actionsCol;

    VariantAInlineGrid(ReportDraft report) {
        this.report = report;
        setSizeFull();
        setPadding(true);

        add(header());
        configureGrid();
        add(grid);
        add(toolbar());
        refreshTotals();
    }

    private HorizontalLayout header() {
        var title = new H2("Expense report");
        var date = new DatePicker("Report date");
        date.setValue(report.reportDate);
        date.addValueChangeListener(e -> report.reportDate = e.getValue());
        var info = new TextField("Additional information");
        info.setValue(report.additionalInformation);
        info.setWidth("22em");
        var status = statusBadge(report.status);

        var bar = new HorizontalLayout(title, date, info, status);
        bar.setAlignItems(Alignment.BASELINE);
        bar.setSpacing(true);
        return bar;
    }

    private void configureGrid() {
        Editor<LineDraft> editor = grid.getEditor();
        editor.setBinder(binder);
        editor.setBuffered(true);
        grid.setItems(report.lines);
        grid.setAllRowsVisible(true);

        // Expense type
        var typeField = new ComboBox<ExpenseType>();
        typeField.setItems(PrototypeModel.EXPENSE_TYPES);
        typeField.setItemLabelGenerator(ExpenseType::name);
        typeField.setWidthFull();
        binder.forField(typeField).bind(l -> l.expenseType, (l, v) -> {
            l.expenseType = v;
            if (v != null) { // pre-fill VAT from type default (overridable)
                l.vatRate = v.defaultRate();
            }
        });
        typeCol = grid.addColumn(l -> l.expenseType == null ? "—" : l.expenseType.name())
                .setHeader("Expense type").setEditorComponent(typeField).setFlexGrow(2);

        // VAT rate (default follows the type, overridable)
        var vatField = new ComboBox<VatRate>();
        vatField.setItems(PrototypeModel.VAT_RATES);
        vatField.setItemLabelGenerator(VatRate::label);
        typeField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                vatField.setValue(e.getValue().defaultRate());
            }
        });
        binder.forField(vatField).bind(l -> l.vatRate, (l, v) -> l.vatRate = v);
        grid.addColumn(l -> l.vatRate == null ? "—" : l.vatRate.label())
                .setHeader("VAT").setEditorComponent(vatField).setWidth("8em").setFlexGrow(0);

        // Gross amount
        var amountField = new BigDecimalField();
        amountField.setWidthFull();
        binder.forField(amountField).bind(l -> l.amount, (l, v) -> l.amount = v);
        grid.addColumn(l -> l.amount == null ? "—" : PrototypeModel.euro(l.amount))
                .setHeader("Gross (paid)").setEditorComponent(amountField)
                .setWidth("9em").setFlexGrow(0);

        // Comment
        var commentField = new TextField();
        commentField.setWidthFull();
        binder.forField(commentField).bind(l -> l.comment, (l, v) -> l.comment = v);
        grid.addColumn(l -> l.comment == null ? "" : l.comment)
                .setHeader("Comment").setEditorComponent(commentField).setFlexGrow(1);

        // Derived net / VAT (read-only display)
        netCol = grid.addColumn(l -> PrototypeModel.euro(PrototypeModel.lineTotals(l).net()))
                .setHeader("Net").setWidth("7em").setFlexGrow(0);
        vatAmtCol = grid.addColumn(l -> PrototypeModel.euro(PrototypeModel.lineTotals(l).vat()))
                .setHeader("VAT €").setWidth("7em").setFlexGrow(0);

        // Actions column: Edit/Delete, or Save/Cancel while editing
        actionsCol = grid.addComponentColumn(line -> rowActions(line, editor))
                .setHeader("").setWidth("11em").setFlexGrow(0);

        editor.addSaveListener(e -> {
            grid.getDataProvider().refreshAll();
            refreshTotals();
        });
    }

    private HorizontalLayout rowActions(LineDraft line, Editor<LineDraft> editor) {
        var edit = new Button(VaadinIcon.PENCIL.create());
        edit.addThemeVariants(ButtonVariant.TERTIARY);
        edit.addClickListener(e -> {
            if (editor.isOpen()) {
                editor.cancel();
            }
            editor.editItem(line);
        });
        var del = new Button(VaadinIcon.TRASH.create());
        del.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
        del.addClickListener(e -> {
            report.lines.remove(line);
            grid.getDataProvider().refreshAll();
            refreshTotals();
        });

        var save = new Button("Save", e -> editor.save());
        save.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
        var cancel = new Button("Cancel", e -> editor.cancel());
        cancel.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);

        var row = new HorizontalLayout();
        row.setSpacing(false);
        // Swap the buttons depending on whether this row is being edited.
        if (editor.isOpen() && editor.getItem() == line) {
            row.add(save, cancel);
        } else {
            row.add(edit, del);
        }
        return row;
    }

    private HorizontalLayout toolbar() {
        var addLine = new Button("Add line", VaadinIcon.PLUS.create(), e -> {
            var line = new LineDraft();
            report.lines.add(line);
            grid.getDataProvider().refreshAll();
            grid.getEditor().editItem(line);
        });
        addLine.addThemeVariants(ButtonVariant.TERTIARY);

        var save = new Button("Save report");
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var submit = new Button("Submit", VaadinIcon.PAPERPLANE.create());
        submit.setEnabled(!report.lines.isEmpty());

        var bar = new HorizontalLayout(addLine, save, submit);
        bar.setSpacing(true);
        return bar;
    }

    private void refreshTotals() {
        var t = PrototypeModel.reportTotals(report.lines);
        if (grid.getFooterRows().isEmpty()) {
            grid.appendFooterRow();
        }
        var footer = grid.getFooterRows().get(0);
        footer.getCell(typeCol).setText("Totals");
        footer.getCell(netCol).setComponent(bold(PrototypeModel.euro(t.net())));
        footer.getCell(vatAmtCol).setComponent(bold(PrototypeModel.euro(t.vat())));
        footer.getCell(actionsCol).setComponent(bold("Gross " + PrototypeModel.euro(t.gross())));
    }

    private static Span bold(String text) {
        var s = new Span(text);
        s.getStyle().setFontWeight("700");
        return s;
    }

    private static Span statusBadge(String status) {
        var badge = new Span(status);
        badge.getElement().getThemeList().add("badge");
        return badge;
    }
}

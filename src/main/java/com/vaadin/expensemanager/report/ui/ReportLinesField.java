package com.vaadin.expensemanager.report.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;

/**
 * The variant-D line editor as a single bindable field (F-004, ADR-0015): its
 * value is the ordered {@code List<ReportLineModel>}, composing the receipt
 * {@link ReportLineCards} and the persistent {@link ReportLineEditorPanel}. A
 * {@code CustomField} makes the whole editor a first-class {@code HasValue} the
 * detail view reads on save and that emits value-change events driving the live
 * totals.
 *
 * <p><strong>Value semantics.</strong> {@link #generateModelValue()} returns a
 * detached deep copy, so the field's value never aliases the working instances
 * the editor mutates — and, because a {@code HasValue} can't observe in-place
 * mutation of a shared instance, each edit's snapshot genuinely differs from the
 * last, so value-change fires (no manual revision counter needed).
 *
 * <p>New lines are offered only active types/rates; the picker options are the
 * active options unioned with any now-deactivated type/rate a loaded line kept
 * (ADR-0018), computed once whenever the value or the active set changes — never
 * while a line is bound.
 */
final class ReportLinesField extends CustomField<List<ReportLineModel>> {

    private final List<ReportLineModel> lines = new ArrayList<>();
    private final ReportLineCards cards = new ReportLineCards();
    private final ReportLineEditorPanel editor = new ReportLineEditorPanel();
    private final Button addLine = new Button("Add expense", VaadinIcon.PLUS.create());

    private transient List<ExpenseTypeDto> activeTypes = List.of();
    private transient List<VatRateDto> activeRates = List.of();
    private transient ReportLineModel selected;

    ReportLinesField() {
        setWidthFull();

        cards.setSelectListener(this::select);
        editor.addChangeListener(this::onLineChanged);
        editor.addRemoveListener(this::removeSelected);
        addLine.addThemeVariants(ButtonVariant.TERTIARY);
        addLine.setWidthFull();
        addLine.addClickListener(event -> addLine());

        var left = new VerticalLayout(cards, addLine);
        left.setPadding(false);
        left.getStyle().set("flex", "1 1 20rem");

        var split = new Div(left, editor);
        split.getStyle()
                .setDisplay(Style.Display.FLEX)
                .setFlexWrap(Style.FlexWrap.WRAP)
                .set("gap", "var(--vaadin-gap, 1rem)")
                .setWidth("100%");
        add(split);
    }

    /** The active reference options for new choices (ADR-0018). */
    void setActiveOptions(List<ExpenseTypeDto> types, List<VatRateDto> rates) {
        this.activeTypes = types;
        this.activeRates = rates;
        refreshOptions();
    }

    @Override
    protected List<ReportLineModel> generateModelValue() {
        return lines.stream().map(ReportLineModel::copy).toList();
    }

    @Override
    protected void setPresentationValue(List<ReportLineModel> value) {
        lines.clear();
        if (value != null) {
            value.forEach(model -> lines.add(model.copy()));
        }
        selected = null;
        refreshOptions();
        editor.clear();
        cards.render(lines, null);
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);
        addLine.setVisible(!readOnly);
        if (readOnly) {
            selected = null;
            editor.clear();
            cards.render(lines, null);
        }
    }

    private void addLine() {
        var model = new ReportLineModel();
        lines.add(model);
        select(model);
        updateValue();
    }

    private void select(ReportLineModel model) {
        if (isReadOnly()) {
            return;
        }
        this.selected = model;
        editor.editLine(model);
        cards.render(lines, model);
    }

    private void onLineChanged() {
        if (selected != null) {
            editor.refreshHeading(selected);
        }
        cards.render(lines, selected);
        updateValue();
    }

    private void removeSelected() {
        if (selected == null) {
            return;
        }
        lines.remove(selected);
        selected = null;
        editor.clear();
        cards.render(lines, null);
        updateValue();
    }

    /**
     * Picker options = active options ∪ any type/rate the current lines carry, so
     * a historical line's now-deactivated value still renders (ADR-0018). Safe to
     * call only while no line is bound (setting items resets a ComboBox value).
     */
    private void refreshOptions() {
        var types = union(activeTypes, ExpenseTypeDto::id,
                lines.stream().map(ReportLineModel::getExpenseType).toList());
        var rates = union(activeRates, VatRateDto::id,
                lines.stream().map(ReportLineModel::getVatRate).toList());
        editor.setOptions(types, rates);
    }

    private static <T> List<T> union(List<T> base, Function<T, Long> idOf,
            List<T> extras) {
        Map<Long, T> byId = new LinkedHashMap<>();
        for (T item : base) {
            byId.put(idOf.apply(item), item);
        }
        for (T extra : extras) {
            if (extra != null) {
                byId.putIfAbsent(idOf.apply(extra), extra);
            }
        }
        return List.copyOf(byId.values());
    }
}

package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.report.domain.LineMoney;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

import jakarta.annotation.security.PermitAll;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatRate;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.statusLabel;

/**
 * Create and edit a single report, line by line (UC-001/UC-005, ADR-0019) —
 * <strong>variant D</strong> of the F-004 line-editor exploration: receipt-style
 * cards on the left, a persistent editor side panel on the right (never a modal),
 * and live net/VAT/gross totals.
 *
 * <p>Two entry points on one route: {@code /report} opens a <strong>transient
 * working copy</strong> (no row is persisted until the first save, ADR-0019) with
 * the date defaulting to today, and {@code /report/{id}} loads an existing report.
 * The first successful save routes from {@code /report} to {@code /report/{id}}.
 *
 * <p>Lines are edited in memory and saved with the whole aggregate: the service
 * reconciles the collection by nullable line id (ADR-0019). Picking an expense
 * type pre-fills its default VAT rate, which stays overridable (ADR-0018); a
 * line filed under a now-deactivated type/rate keeps it even though new lines are
 * offered only active options. Live totals recompute reactively via Signals
 * (ADR-0015).
 *
 * <p><strong>Save is always enabled with a validation error summary on top of the
 * form</strong> (never a disabled submit, project rule / ADR-0020): incomplete
 * lines (missing type / missing or zero amount / missing VAT rate) surface there.
 * <strong>Delete</strong> shows only while the report is a persisted {@code DRAFT}
 * (the aggregate enforces the guard, ADR-0006). Stale writes surface the "reload"
 * message (ADR-0011). {@code @PermitAll}; owner-scoping is enforced in the service.
 */
@Route("report")
@PageTitle("Report")
@PermitAll
public class ReportDetailView extends VerticalLayout
        implements HasUrlParameter<Long> {

    private final transient ExpenseReportService service;
    private final transient ReferenceDataService referenceData;

    // Report-level fields.
    private final Div errorSummary = new Div();
    private final Span statusBadge = new Span();
    private final DatePicker reportDate = new DatePicker("Report date");
    private final TextArea additionalInformation = new TextArea("Additional information");
    private final Binder<ReportFormModel> reportBinder = new Binder<>();
    private final ReportFormModel reportModel = new ReportFormModel();

    // Live totals (Signals, ADR-0015): every line mutation bumps the revision,
    // which the computed total/breakdown text depends on.
    private final ValueSignal<Integer> linesRevision = new ValueSignal<>(0);
    private final Span totalDisplay = new Span();
    private final Span totalsBreakdown = new Span();

    // Line cards + in-memory working lines.
    private final List<LineModel> lines = new ArrayList<>();
    private final VerticalLayout cardList = new VerticalLayout();
    private final Button addLine = new Button("Add expense", VaadinIcon.PLUS.create());

    // Persistent editor side panel bound to the selected line.
    private final H3 panelHeading = new H3("Select an expense to edit");
    private final ComboBox<ExpenseTypeDto> typeField = new ComboBox<>("Expense type");
    private final BigDecimalField amountField = new BigDecimalField("Gross amount (paid)");
    private final ComboBox<VatRateDto> vatField = new ComboBox<>("VAT rate");
    private final TextField commentField = new TextField("Comment");
    private final Button removeLine = new Button("Remove expense");
    private final Binder<LineModel> lineBinder = new Binder<>();

    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");

    /** Active reference options for new choices (ADR-0018); loaded per navigation. */
    private transient List<ExpenseTypeDto> activeTypes = List.of();
    private transient List<VatRateDto> activeRates = List.of();

    /** The current working copy (transient for a new report until first save). */
    private transient ReportDetailDto working;
    /** The line currently loaded in the side panel, or {@code null}. */
    private transient LineModel selected;
    /** Suppresses line-field listeners while the panel is being populated. */
    private boolean populating;

    public ReportDetailView(ExpenseReportService service,
            ReferenceDataService referenceData) {
        this.service = service;
        this.referenceData = referenceData;
        setPadding(true);
        setSpacing(true);
        setWidthFull();
        setMaxWidth("64rem");

        configureErrorSummary();
        configureReportFields();
        configureTotals();
        configureSidePanel();
        configureActions();

        add(headerRow(), errorSummary, reportFields(), totalsBar(), linesSection());
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long id) {
        // Active options power the "new line" choices; a selected historical line
        // may add its own (possibly inactive) type/rate to the pickers on top.
        activeTypes = referenceData.activeExpenseTypes();
        activeRates = referenceData.activeVatRates();
        typeField.setItems(activeTypes);
        vatField.setItems(activeRates);

        if (id == null) {
            load(ReportDetailDto.forNew(LocalDate.now()));
            return;
        }
        try {
            load(service.findMine(id));
        } catch (IllegalArgumentException notFound) {
            // A missing id or someone else's id (owner-scoped) both land here — no
            // information leak: bounce to the owner's list (ADR-0008, ADR-0016).
            Notification.show("Report not found.");
            event.forwardTo(MyReportsView.class);
        }
    }

    /** Populates the form from a working copy and reflects its editability/status. */
    private void load(ReportDetailDto dto) {
        this.working = dto;
        reportModel.setReportDate(dto.reportDate());
        reportModel.setAdditionalInformation(dto.additionalInformation());
        reportBinder.readBean(reportModel);

        lines.clear();
        for (ExpenseLineDto line : dto.lines()) {
            lines.add(LineModel.from(line));
        }
        clearSelection();
        renderCards();
        bumpTotals();

        clearErrors();
        statusBadge.setText(statusLabel(dto.status()));

        boolean editable = dto.status().isEditable();
        reportDate.setReadOnly(!editable);
        additionalInformation.setReadOnly(!editable);
        addLine.setVisible(editable);
        save.setVisible(editable);
        // Delete only while a persisted DRAFT (ADR-0006, glossary).
        delete.setVisible(dto.isPersisted() && dto.status().isDeletable());
    }

    // ------------------------------------------------------------ line editing

    private void onAddLine() {
        var model = new LineModel();
        lines.add(model);
        renderCards();
        edit(model);
        typeField.focus();
        bumpTotals();
    }

    /** Loads a line into the side panel and highlights its card. */
    private void edit(LineModel model) {
        this.selected = model;
        // Make sure the pickers can show this line's (possibly inactive) values.
        typeField.setItems(optionsWith(activeTypes, model.expenseType, ExpenseTypeDto::id));
        vatField.setItems(optionsWith(activeRates, model.vatRate, VatRateDto::id));

        populating = true;
        lineBinder.setBean(model);
        populating = false;

        panelHeading.setText(model.expenseType == null ? "New expense"
                : model.expenseType.name());
        setPanelEnabled(working.status().isEditable());
        renderCards();
    }

    private void onLineFieldChanged() {
        if (populating || selected == null) {
            return;
        }
        panelHeading.setText(selected.expenseType == null ? "New expense"
                : selected.expenseType.name());
        renderCards();
        bumpTotals();
    }

    /** Applies the chosen type's default VAT rate — overridable (ADR-0018). */
    private void applyDefaultVat() {
        if (populating || typeField.getValue() == null) {
            return;
        }
        Long defaultId = typeField.getValue().defaultVatRateId();
        activeRates.stream().filter(r -> r.id().equals(defaultId)).findFirst()
                .ifPresent(vatField::setValue);
    }

    private void onRemoveLine() {
        if (selected == null) {
            return;
        }
        lines.remove(selected);
        clearSelection();
        renderCards();
        bumpTotals();
    }

    private void clearSelection() {
        this.selected = null;
        populating = true;
        lineBinder.readBean(null);
        populating = false;
        panelHeading.setText("Select an expense to edit");
        setPanelEnabled(false);
    }

    // ------------------------------------------------------------------- save

    private void onSave() {
        clearErrors();
        var errors = new ArrayList<String>();
        if (!reportBinder.writeBeanIfValid(reportModel)) {
            reportBinder.validate().getValidationErrors().stream()
                    .map(ValidationResult::getErrorMessage).forEach(errors::add);
        }
        errors.addAll(validateLines());
        if (!errors.isEmpty()) {
            showErrors(errors);
            return;
        }

        var edited = new ReportDetailDto(working.id(), reportModel.getReportDate(),
                reportModel.getAdditionalInformation(), working.status(),
                working.version(), working.total(), toLineDtos());
        try {
            if (!working.isPersisted()) {
                Long newId = service.create(edited);
                Notification.show("Report saved.");
                // First save routes /report → /report/{id} (ADR-0019).
                getUI().ifPresent(ui -> ui.navigate(ReportDetailView.class, newId));
            } else {
                load(service.update(working.id(), edited, working.version()));
                Notification.show("Report saved.");
            }
        } catch (ObjectOptimisticLockingFailureException stale) {
            showErrors(List.of("This report was changed elsewhere. "
                    + "Reload to see the latest version before saving again."));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            showErrors(List.of(invalid.getMessage()));
        }
    }

    /**
     * Per-line invariants surfaced as an error summary (never a disabled Save,
     * ADR-0020). The domain guards in {@code ExpenseLine} are the backstop; this
     * gives the user a readable, line-numbered list before the write.
     */
    private List<String> validateLines() {
        var messages = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            String label = "Expense " + (i + 1);
            if (line.expenseType == null) {
                messages.add(label + ": choose an expense type.");
            }
            if (line.amount == null) {
                messages.add(label + ": enter a gross amount.");
            } else if (line.amount.signum() == 0) {
                messages.add(label + ": amount must not be zero.");
            }
            if (line.vatRate == null) {
                messages.add(label + ": choose a VAT rate.");
            }
        }
        return messages;
    }

    private List<ExpenseLineDto> toLineDtos() {
        return lines.stream().map(l -> new ExpenseLineDto(l.id, l.expenseType,
                l.amount, l.vatRate, l.comment)).toList();
    }

    private void confirmDelete() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete report?");
        dialog.add(new Paragraph(
                "This permanently deletes the draft report. This cannot be undone."));

        var confirm = new Button("Delete report", event -> {
            dialog.close();
            performDelete();
        });
        confirm.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void performDelete() {
        try {
            service.delete(working.id());
            Notification.show("Report deleted.");
            getUI().ifPresent(ui -> ui.navigate(MyReportsView.class));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            showErrors(List.of(ex.getMessage()));
        }
    }

    // --------------------------------------------------------------- building

    private void configureErrorSummary() {
        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.getStyle().setColor("var(--aura-red-text)");
    }

    private void configureReportFields() {
        reportDate.setRequiredIndicatorVisible(true);
        additionalInformation.setMaxLength(2000);
        additionalInformation.setWidthFull();

        reportBinder.forField(reportDate)
                .asRequired("Report date is required")
                .bind(ReportFormModel::getReportDate, ReportFormModel::setReportDate);
        reportBinder.forField(additionalInformation)
                .bind(ReportFormModel::getAdditionalInformation,
                        ReportFormModel::setAdditionalInformation);
    }

    private void configureTotals() {
        // Reactive text: recomputes whenever a line changes (via linesRevision).
        totalDisplay.bindText(Signal.computed(() -> {
            linesRevision.get();
            return formatEur(grossTotal());
        }));
        totalsBreakdown.bindText(Signal.computed(() -> {
            linesRevision.get();
            return "net " + formatEur(netTotal()) + "  ·  VAT " + formatEur(vatTotal());
        }));
    }

    private void configureSidePanel() {
        typeField.setItemLabelGenerator(ExpenseTypeDto::name);
        vatField.setItemLabelGenerator(rate -> formatRate(rate.value()));
        amountField.setWidthFull();
        commentField.setWidthFull();
        commentField.setMaxLength(500);

        typeField.addValueChangeListener(event -> applyDefaultVat());
        lineBinder.forField(typeField)
                .bind(m -> m.expenseType, (m, v) -> m.expenseType = v);
        lineBinder.forField(amountField)
                .bind(m -> m.amount, (m, v) -> m.amount = v);
        lineBinder.forField(vatField)
                .bind(m -> m.vatRate, (m, v) -> m.vatRate = v);
        lineBinder.forField(commentField)
                .bind(m -> m.comment, (m, v) -> m.comment = v);
        lineBinder.addValueChangeListener(event -> onLineFieldChanged());

        removeLine.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        removeLine.addClickListener(event -> onRemoveLine());
    }

    private void configureActions() {
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(event -> onSave());
        delete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        delete.addClickListener(event -> confirmDelete());
        addLine.addThemeVariants(ButtonVariant.TERTIARY);
        addLine.addClickListener(event -> onAddLine());
    }

    private HorizontalLayout headerRow() {
        statusBadge.getElement().getThemeList().add("badge");
        var header = new HorizontalLayout(new H2("Report"), statusBadge);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        header.setSpacing(true);
        return header;
    }

    private FormLayout reportFields() {
        var form = new FormLayout(reportDate, additionalInformation);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("30rem", 2));
        return form;
    }

    private Div totalsBar() {
        totalDisplay.getStyle().setFontWeight("700")
                .setFontSize("var(--aura-font-size-xl)");
        totalsBreakdown.getStyle().setColor("var(--vaadin-text-color-secondary)")
                .setFontSize("var(--aura-font-size-s)");

        var totals = new VerticalLayout(new Span("Report total"), totalDisplay,
                totalsBreakdown);
        totals.setPadding(false);
        totals.setSpacing(false);

        var actions = new HorizontalLayout(save, delete);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        var bar = new HorizontalLayout(totals, actions);
        bar.setWidthFull();
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.getStyle().setFlexWrap(Style.FlexWrap.WRAP);

        var wrap = new Div(bar);
        wrap.getStyle()
                .set("background", "var(--aura-accent-surface)")
                .set("padding", "var(--vaadin-padding)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .setWidth("100%");
        return wrap;
    }

    /** The cards + side-panel split; wraps to a single column at ~360px. */
    private Div linesSection() {
        cardList.setPadding(false);
        cardList.setSpacing(true);
        cardList.setWidthFull();
        addLine.setWidthFull();

        var left = new VerticalLayout(cardList, addLine);
        left.setPadding(false);
        left.getStyle().set("flex", "1 1 20rem");

        var split = new Div(left, sidePanel());
        split.getStyle()
                .setDisplay(Style.Display.FLEX)
                .setFlexWrap(Style.FlexWrap.WRAP)
                .set("gap", "var(--vaadin-gap, 1rem)")
                .setWidth("100%");
        return split;
    }

    private Div sidePanel() {
        var form = new FormLayout(typeField, amountField, vatField, commentField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var panel = new Div(panelHeading, form, removeLine);
        panel.getStyle()
                .set("flex", "1 1 18rem")
                .set("background", "var(--vaadin-background-container)")
                .set("padding", "var(--vaadin-padding)")
                .set("border-radius", "var(--vaadin-radius-l)");
        return panel;
    }

    private void renderCards() {
        cardList.removeAll();
        if (lines.isEmpty()) {
            var empty = new Span("No expenses yet — add your first.");
            empty.getStyle().setColor("var(--vaadin-text-color-secondary)");
            cardList.add(empty);
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            cardList.add(card(lines.get(i), i));
        }
    }

    private Div card(LineModel line, int index) {
        var name = new Span(line.expenseType == null ? "New expense"
                : line.expenseType.name());
        name.getStyle().setFontWeight("600");
        String subtitleText = line.comment != null && !line.comment.isBlank()
                ? line.comment
                : (line.vatRate == null ? "" : "VAT " + formatRate(line.vatRate.value()));
        var subtitle = new Span(subtitleText);
        subtitle.getStyle().setColor("var(--vaadin-text-color-secondary)")
                .setFontSize("var(--aura-font-size-s)");
        var left = new VerticalLayout(name, subtitle);
        left.setPadding(false);
        left.setSpacing(false);

        var gross = new Span(line.amount == null ? "€—" : formatEur(line.grossAmount()));
        gross.getStyle().setFontWeight("700");
        var breakdown = new Span(line.amount == null || line.vatRate == null ? ""
                : "net " + formatEur(line.netAmount()) + " · VAT "
                        + formatEur(line.vatAmount()));
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

        boolean isSelected = line == selected;
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

        // Keyboard-operable card (ADR-0020): a focusable button role that responds
        // to Enter/Space, plus an accessible name describing the line.
        cardDiv.getElement().setAttribute("role", "button");
        cardDiv.getElement().setAttribute("tabindex", "0");
        cardDiv.getElement().setAttribute("aria-label", "Edit " + name.getText()
                + (line.amount == null ? "" : ", " + formatEur(line.grossAmount())));
        cardDiv.addClickListener(event -> edit(line));
        cardDiv.getElement().addEventListener("keydown", event -> edit(line))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        return cardDiv;
    }

    private void setPanelEnabled(boolean enabled) {
        typeField.setEnabled(enabled);
        amountField.setEnabled(enabled);
        vatField.setEnabled(enabled);
        commentField.setEnabled(enabled);
        removeLine.setEnabled(enabled);
    }

    private void clearErrors() {
        errorSummary.removeAll();
        errorSummary.setVisible(false);
    }

    private void showErrors(List<String> messages) {
        errorSummary.removeAll();
        if (messages.isEmpty()) {
            errorSummary.setVisible(false);
            return;
        }
        var heading = new Span("Please fix the following:");
        heading.getStyle().setFontWeight("600");
        var list = new UnorderedList();
        messages.forEach(message -> list.add(new ListItem(message)));
        errorSummary.add(heading, list);
        errorSummary.setVisible(true);
    }

    // ------------------------------------------------------------- totals math

    private void bumpTotals() {
        linesRevision.update(revision -> revision + 1);
    }

    private BigDecimal grossTotal() {
        return lines.stream().filter(l -> l.amount != null)
                .map(LineModel::grossAmount)
                .reduce(LineMoney.zero(), BigDecimal::add);
    }

    private BigDecimal netTotal() {
        return lines.stream().filter(l -> l.amount != null && l.vatRate != null)
                .map(LineModel::netAmount)
                .reduce(LineMoney.zero(), BigDecimal::add);
    }

    private BigDecimal vatTotal() {
        return lines.stream().filter(l -> l.amount != null && l.vatRate != null)
                .map(LineModel::vatAmount)
                .reduce(LineMoney.zero(), BigDecimal::add);
    }

    /**
     * The picker options for a selected line: the active list, plus the line's own
     * type/rate if it is a now-deactivated one that would otherwise be missing —
     * so a historical line keeps its filed value (ADR-0018). Deduped by id.
     */
    private static <T> List<T> optionsWith(List<T> active, T selected,
            java.util.function.Function<T, Long> idOf) {
        if (selected == null) {
            return active;
        }
        Map<Long, T> byId = new LinkedHashMap<>();
        for (T option : active) {
            byId.put(idOf.apply(option), option);
        }
        byId.putIfAbsent(idOf.apply(selected), selected);
        return List.copyOf(byId.values());
    }

    /** Mutable binding model for the report-level fields (Binder needs setters). */
    private static final class ReportFormModel {
        private LocalDate reportDate;
        private String additionalInformation;

        LocalDate getReportDate() {
            return reportDate;
        }

        void setReportDate(LocalDate reportDate) {
            this.reportDate = reportDate;
        }

        String getAdditionalInformation() {
            return additionalInformation;
        }

        void setAdditionalInformation(String additionalInformation) {
            this.additionalInformation = additionalInformation;
        }
    }

    /**
     * Mutable working copy of one line, bound to the side panel. Holds the
     * reference DTOs directly so the pickers can show a now-deactivated value; net
     * and VAT are derived live via {@link LineMoney}.
     */
    private static final class LineModel {
        private Long id;
        private ExpenseTypeDto expenseType;
        private BigDecimal amount;
        private VatRateDto vatRate;
        private String comment;

        static LineModel from(ExpenseLineDto dto) {
            var model = new LineModel();
            model.id = dto.id();
            model.expenseType = dto.expenseType();
            model.amount = dto.amount();
            model.vatRate = dto.vatRate();
            model.comment = dto.comment();
            return model;
        }

        BigDecimal grossAmount() {
            return LineMoney.gross(amount);
        }

        BigDecimal netAmount() {
            return LineMoney.net(amount, vatRate.value());
        }

        BigDecimal vatAmount() {
            return LineMoney.vat(amount, vatRate.value());
        }
    }
}

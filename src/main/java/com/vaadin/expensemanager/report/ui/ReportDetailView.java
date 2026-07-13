package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.report.domain.LineAmounts;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReceiptUpload;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;
import com.vaadin.flow.signals.local.ValueSignal;

import jakarta.annotation.security.PermitAll;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatPercent;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.statusLabel;

/**
 * Create and edit a single report with its expense lines (UC-001/UC-005,
 * ADR-0019) — the variant-C detail editor (issue #24).
 *
 * <p>Two entry points on one route: {@code /report} opens a <strong>transient
 * working copy</strong> (no row is persisted until the first save, ADR-0019)
 * with the date defaulting to today, and {@code /report/{id}} loads an existing
 * report. The first successful save routes from {@code /report} to
 * {@code /report/{id}}.
 *
 * <p>Lines are shown as receipt-style cards; adding or clicking a card opens the
 * focused modal {@link LineEditorDialog}. The report total bar shows live
 * net/VAT/gross that recompute as lines change — driven by Signals (ADR-0015):
 * a {@link ListSignal} of working lines feeds both the bound card list and the
 * computed totals, so an unsaved edit is reflected immediately without manual
 * refresh. The whole aggregate saves at once; the line collection reconciles by
 * nullable id in the service (ADR-0019).
 *
 * <p>Validation follows the project rule (ADR-0020): the report date is required
 * and <strong>Save is always enabled</strong> with a top-of-form error summary;
 * line-field validation lives in the dialog. Report-level fields, the card
 * actions, and Save show only while the report is editable ({@code DRAFT}/
 * {@code REJECTED}); <strong>Submit for approval</strong> (always enabled — a
 * zero-line submit surfaces the reason, never a silent no-op) only for a
 * persisted {@code DRAFT}, moving it to {@code SUBMITTED} and read-only;
 * <strong>Delete</strong> only while a persisted {@code DRAFT} (the aggregate
 * enforces the guard, ADR-0006). Stale writes surface a "reload" affordance,
 * never a silent overwrite (ADR-0011). {@code @PermitAll}; owner-scoping is
 * enforced in the service.
 */
@Route("report")
@PageTitle("Report")
@PermitAll
public class ReportDetailView extends VerticalLayout
        implements HasUrlParameter<Long> {

    private final transient ExpenseReportService service;
    private final transient ReferenceDataService referenceData;

    private final Div errorSummary = new Div();
    private final Span statusBadge = new Span();
    private final DatePicker reportDate = new DatePicker("Report date");
    private final TextArea additionalInformation = new TextArea("Additional information");
    private final Span grossDisplay = new Span();
    private final Span breakdownDisplay = new Span();
    private final Button save = new Button("Save");
    private final Button submit = new Button("Submit for approval");
    private final Button addLine = new Button("Add expense", VaadinIcon.PLUS.create());
    private final Button delete = new Button("Delete");
    private final Binder<ReportFormModel> binder = new Binder<>();
    private final ReportFormModel model = new ReportFormModel();

    /** Working lines, the reactive source for both the cards and live totals. */
    private final transient ListSignal<ExpenseLineDto> lines = new ListSignal<>();

    /**
     * Buffered receipt mutations keyed by their working-line entry (ADR-0021):
     * the bytes live here — off the DTO — until the next save, when they are
     * mapped to line positions and handed to the service. Cleared on (re)load.
     */
    private final transient Map<ValueSignal<ExpenseLineDto>, ReceiptUpload> pendingReceipts =
            new HashMap<>();

    /** The current working copy (transient for a new report until first save). */
    private transient ReportDetailDto working;

    /** Whether the loaded report allows edits (drives card/actions interactivity). */
    private boolean editable = true;

    public ReportDetailView(ExpenseReportService service,
            ReferenceDataService referenceData) {
        this.service = service;
        this.referenceData = referenceData;
        setPadding(true);
        setSpacing(true);
        setMaxWidth("40rem");

        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.getStyle().setColor("var(--aura-red-text)");

        reportDate.setRequiredIndicatorVisible(true);
        additionalInformation.setMaxLength(2000);
        additionalInformation.setWidthFull();

        binder.forField(reportDate)
                .asRequired("Report date is required")
                .bind(ReportFormModel::getReportDate, ReportFormModel::setReportDate);
        binder.forField(additionalInformation)
                .bind(ReportFormModel::getAdditionalInformation,
                        ReportFormModel::setAdditionalInformation);

        save.addClickListener(event -> onSave());
        // Submit is the primary forward action on a draft; Save is the quieter
        // "keep working" action beside it (two primaries would compete).
        submit.addThemeVariants(ButtonVariant.PRIMARY);
        submit.addClickListener(event -> onSubmit());
        delete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        delete.addClickListener(event -> confirmDelete());
        addLine.addThemeVariants(ButtonVariant.TERTIARY);
        addLine.addClickListener(event -> addLine());

        var actions = new HorizontalLayout(save, submit, delete);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        add(headerRow(), errorSummary, reportDate, additionalInformation,
                totalsBar(), linesSection(), actions);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long id) {
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
        model.setReportDate(dto.reportDate());
        model.setAdditionalInformation(dto.additionalInformation());
        binder.readBean(model);

        clearErrors();
        statusBadge.setText(statusLabel(dto.status()));

        // Set editability before repopulating so the card factory builds the
        // right (interactive vs read-only) cards.
        editable = dto.status().isEditable();
        reportDate.setReadOnly(!editable);
        additionalInformation.setReadOnly(!editable);
        save.setVisible(editable);
        addLine.setVisible(editable);
        // Submit only for a persisted DRAFT: a brand-new report must be saved
        // first, and resubmitting a REJECTED report is Phase 5 (out of scope).
        submit.setVisible(dto.isPersisted() && dto.status() == ReportStatus.DRAFT);
        // Delete only while DRAFT and already persisted (ADR-0006, glossary).
        delete.setVisible(dto.isPersisted() && dto.status().isDeletable());

        pendingReceipts.clear();
        lines.clear();
        if (!dto.lines().isEmpty()) {
            lines.insertAllLast(dto.lines());
        }
    }

    private void onSave() {
        clearErrors();
        if (!binder.writeBeanIfValid(model)) {
            showErrors(binder.validate().getValidationErrors().stream()
                    .map(ValidationResult::getErrorMessage).distinct().toList());
            return;
        }
        var edited = new ReportDetailDto(working.id(), model.getReportDate(),
                model.getAdditionalInformation(), working.status(),
                working.version(), currentLines(), working.total(),
                working.netTotal(), working.vatTotal());
        var receipts = pendingReceiptsByLineIndex();
        try {
            if (!working.isPersisted()) {
                Long newId = service.create(edited, receipts);
                Notification.show("Report saved.");
                // First save routes /report → /report/{id} (ADR-0019).
                getUI().ifPresent(ui -> ui.navigate(ReportDetailView.class, newId));
            } else {
                load(service.update(working.id(), edited, working.version(), receipts));
                Notification.show("Report saved.");
            }
        } catch (ObjectOptimisticLockingFailureException stale) {
            showConflict();
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            showErrors(List.of(invalid.getMessage()));
        }
    }

    /**
     * Submits the persisted report for approval (UC-003): {@code DRAFT →
     * SUBMITTED}. The button is always enabled (ADR-0020) — a zero-line report
     * is not silently no-op'd but surfaces the domain reason in the error
     * summary. A stale write surfaces the reload affordance (ADR-0011).
     */
    private void onSubmit() {
        clearErrors();
        try {
            load(service.submit(working.id(), working.version()));
            Notification.show("Report submitted for approval.");
        } catch (ObjectOptimisticLockingFailureException stale) {
            showConflict();
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            showErrors(List.of(invalid.getMessage()));
        }
    }

    /** A snapshot of the working lines, in order, for a save. */
    private List<ExpenseLineDto> currentLines() {
        return lines.peek().stream().map(ValueSignal::peek).toList();
    }

    /**
     * Maps each buffered receipt mutation to its line's position in the save
     * snapshot (ADR-0021). Iterating {@code lines.peek()} shares the order with
     * {@link #currentLines()}, so index {@code i} here addresses the same line
     * the service reconciles at {@code dto.lines().get(i)}.
     */
    private Map<Integer, ReceiptUpload> pendingReceiptsByLineIndex() {
        if (pendingReceipts.isEmpty()) {
            return Map.of();
        }
        var entries = lines.peek();
        Map<Integer, ReceiptUpload> byIndex = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            ReceiptUpload upload = pendingReceipts.get(entries.get(i));
            if (upload != null) {
                byIndex.put(i, upload);
            }
        }
        return byIndex;
    }

    private void addLine() {
        new LineEditorDialog(referenceData.activeExpenseTypes(),
                referenceData.activeVatRates(), null, (dto, receipt) -> {
                    var entry = lines.insertLast(dto);
                    if (receipt != null) {
                        pendingReceipts.put(entry, receipt);
                    }
                }).open();
    }

    private void openEditor(ValueSignal<ExpenseLineDto> entry) {
        new LineEditorDialog(referenceData.activeExpenseTypes(),
                referenceData.activeVatRates(), entry.peek(), (dto, receipt) -> {
                    entry.set(dto);
                    if (receipt != null) {
                        pendingReceipts.put(entry, receipt);
                    }
                }).open();
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

    private HorizontalLayout headerRow() {
        var header = new HorizontalLayout(new H2("Report"), statusBadge);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        header.setSpacing(true);
        return header;
    }

    /** The pinned live-total bar — recomputes net/VAT/gross via Signals. */
    private Div totalsBar() {
        var label = new Span("Report total");
        label.getStyle().setColor("var(--vaadin-text-color-secondary)");
        grossDisplay.getStyle().setFontWeight("700");
        grossDisplay.getStyle().setFontSize("var(--aura-font-size-xl)");
        breakdownDisplay.getStyle().setColor("var(--vaadin-text-color-secondary)");
        breakdownDisplay.getStyle().setFontSize("var(--aura-font-size-s)");

        grossDisplay.bindText(Signal.computed(
                () -> formatEur(currentTotals().gross())));
        breakdownDisplay.bindText(Signal.computed(() -> {
            var totals = currentTotals();
            return "net " + formatEur(totals.net())
                    + "  ·  VAT " + formatEur(totals.vat());
        }));

        var column = new VerticalLayout(label, grossDisplay, breakdownDisplay);
        column.setPadding(false);
        column.setSpacing(false);

        var bar = new Div(column);
        bar.setWidthFull();
        bar.getStyle().set("position", "sticky").set("top", "0").set("z-index", "5")
                .set("padding", "var(--vaadin-padding)")
                .set("background", "var(--aura-accent-surface)")
                .set("border-radius", "var(--vaadin-radius-l)");
        return bar;
    }

    private Div linesSection() {
        var emptyState = new Span("No expenses yet — add your first.");
        emptyState.getStyle().setColor("var(--vaadin-text-color-secondary)");
        emptyState.bindVisible(Signal.computed(() -> lines.get().isEmpty()));

        var cardList = new VerticalLayout();
        cardList.setPadding(false);
        cardList.setSpacing(true);
        cardList.setWidthFull();
        cardList.bindChildren(lines, this::card);

        var section = new Div(emptyState, cardList, addLine);
        section.setWidthFull();
        return section;
    }

    /** One receipt-style card for a working line; clickable to edit when editable. */
    private Component card(ValueSignal<ExpenseLineDto> entry) {
        var name = new Span();
        name.bindText(entry.map(dto -> dto.expenseTypeName() == null
                ? "New expense" : dto.expenseTypeName()));
        name.getStyle().setFontWeight("600");
        var subtitle = new Span();
        subtitle.bindText(entry.map(ReportDetailView::subtitleOf));
        subtitle.getStyle().setColor("var(--vaadin-text-color-secondary)");
        subtitle.getStyle().setFontSize("var(--aura-font-size-s)");
        // Receipt indicator (summary only — the read/preview path is a later
        // slice); shows in every state so a submitted report still reads clearly.
        var receipt = new Span();
        receipt.bindText(entry.map(dto -> dto.hasReceipt()
                ? "📎 " + dto.receiptFilename() : ""));
        receipt.bindVisible(entry.map(ExpenseLineDto::hasReceipt));
        receipt.getStyle().setColor("var(--vaadin-text-color-secondary)");
        receipt.getStyle().setFontSize("var(--aura-font-size-xs)");
        var texts = new VerticalLayout(name, subtitle, receipt);
        texts.setPadding(false);
        texts.setSpacing(false);

        var gross = new Span();
        gross.bindText(entry.map(dto -> formatEur(grossOf(dto))));
        gross.getStyle().setFontWeight("600");
        var breakdown = new Span();
        breakdown.bindText(entry.map(ReportDetailView::breakdownOf));
        breakdown.getStyle().setColor("var(--vaadin-text-color-secondary)");
        breakdown.getStyle().setFontSize("var(--aura-font-size-xs)");
        var amounts = new VerticalLayout(gross, breakdown);
        amounts.setPadding(false);
        amounts.setSpacing(false);
        amounts.setAlignItems(FlexComponent.Alignment.END);

        var body = new HorizontalLayout(texts, amounts);
        body.setWidthFull();
        body.setFlexGrow(1, texts);
        body.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        body.setAlignItems(FlexComponent.Alignment.CENTER);
        if (editable) {
            body.getStyle().setCursor("pointer");
            body.addClickListener(event -> openEditor(entry));
        }

        var card = new HorizontalLayout(body);
        card.setWidthFull();
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setFlexGrow(1, body);
        card.getStyle().set("padding", "var(--vaadin-padding)")
                .set("border", "1px solid var(--vaadin-border-color)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("background", "var(--aura-surface-color)");
        // Trash lives outside the clickable body, so removing a line never also
        // opens the editor (no click-propagation hack needed).
        if (editable) {
            var trash = new Button(VaadinIcon.TRASH.create(), event -> {
                pendingReceipts.remove(entry);
                lines.remove(entry);
            });
            trash.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
            trash.getElement().setAttribute("aria-label", "Remove line");
            card.add(trash);
        }
        return card;
    }

    private LineAmounts currentTotals() {
        return lines.get().stream().map(ValueSignal::get)
                .filter(dto -> dto.amount() != null && dto.vatRatePercent() != null)
                .map(dto -> LineAmounts.of(dto.amount(), dto.vatRatePercent()))
                .reduce(LineAmounts.zero(), LineAmounts::add);
    }

    private static BigDecimal grossOf(ExpenseLineDto dto) {
        return dto.amount() == null ? BigDecimal.ZERO.setScale(2)
                : dto.amount().setScale(2, RoundingMode.HALF_UP);
    }

    private static String subtitleOf(ExpenseLineDto dto) {
        if (dto.comment() != null && !dto.comment().isBlank()) {
            return dto.comment();
        }
        return dto.vatRatePercent() == null ? ""
                : "VAT " + formatPercent(dto.vatRatePercent());
    }

    private static String breakdownOf(ExpenseLineDto dto) {
        if (dto.amount() == null || dto.vatRatePercent() == null) {
            return "";
        }
        var totals = LineAmounts.of(dto.amount(), dto.vatRatePercent());
        return "net " + formatEur(totals.net()) + " · VAT " + formatEur(totals.vat())
                + " (" + formatPercent(dto.vatRatePercent()) + ")";
    }

    private void clearErrors() {
        errorSummary.removeAll();
        errorSummary.setVisible(false);
    }

    /**
     * The optimistic-lock conflict UX (ADR-0011): a never-silent-overwrite
     * message plus a Reload affordance that re-fetches the latest committed
     * version into the form, so the owner can review it before acting again.
     */
    private void showConflict() {
        errorSummary.removeAll();
        var heading = new Span("This report was changed elsewhere.");
        heading.getStyle().setFontWeight("600");
        var detail = new Paragraph(
                "Reload to see the latest version before saving again.");
        var reload = new Button("Reload", event -> reload());
        reload.addThemeVariants(ButtonVariant.TERTIARY);
        errorSummary.add(heading, detail, reload);
        errorSummary.setVisible(true);
    }

    /** Re-fetches the persisted report, discarding the stale working copy. */
    private void reload() {
        if (working.isPersisted()) {
            load(service.findMine(working.id()));
            Notification.show("Reloaded the latest version.");
        }
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
}

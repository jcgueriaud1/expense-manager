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
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.LineAmounts;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.GeneratedLineRef;
import com.vaadin.expensemanager.report.service.GeneratedLineView;
import com.vaadin.expensemanager.report.service.ReceiptUpload;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.expensemanager.report.service.TravelDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
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
    /** Holds the freshly-built status badge; repopulated on each (re)load. */
    private final Div statusBadgeSlot = new Div();
    /** The rejected/approved/submitted status callout; hidden while DRAFT. */
    private final Div statusCallout = new Div();
    /** Header eyebrow: "New report" or "Report #{id}". */
    private final Span headerId = new Span();
    /** Header title: the note, or a generic label. */
    private final Span headerName = new Span();
    private final DatePicker reportDate = new DatePicker("Report date");
    private final TextArea additionalInformation = new TextArea("Additional information");
    private final Span netDisplay = new Span();
    private final Span vatDisplay = new Span();
    private final Span perDiemDisplay = new Span();
    private final Span kilometreDisplay = new Span();
    private final Span mealDisplay = new Span();
    private final Span grossDisplay = new Span();
    private final Button save = new Button("Save");
    private final Button submit = new Button("Submit for approval");
    private final Button addLine = new Button("Add expense", VaadinIcon.PLUS.create());
    private final Button addTravel =
            new Button("Insert travel info", VaadinIcon.AIRPLANE.create());
    private final Button delete = new Button("Delete");
    private final Binder<ReportFormModel> binder = new Binder<>();
    private final ReportFormModel model = new ReportFormModel();

    /** Working lines, the reactive source for both the cards and live totals. */
    private final transient ListSignal<ExpenseLineDto> lines = new ListSignal<>();

    /** Working trips, the reactive source for the trip cards and the per-diem row. */
    private final transient ListSignal<TravelDto> travels = new ListSignal<>();

    /** Whether the loaded report is editable, as a signal so bound UI reacts on load. */
    private final transient ValueSignal<Boolean> editableSignal =
            new ValueSignal<>(Boolean.TRUE);

    /**
     * Buffered receipt mutations keyed by their working-line entry (ADR-0021):
     * the bytes live here — off the DTO — until the next save, when they are
     * mapped to line positions and handed to the service. Cleared on (re)load.
     */
    private final transient Map<ValueSignal<ExpenseLineDto>, ReceiptUpload> pendingReceipts =
            new HashMap<>();

    /**
     * Buffered receipt mutations for a trip's generated lines (Phase 4.3), keyed by
     * the trip's working entry and the line kind. Like {@link #pendingReceipts} the
     * bytes live here until the next save, when they are translated to
     * {@link GeneratedLineRef}s (trip position + kind) for the service. Cleared on
     * (re)load.
     */
    private final transient Map<TravelReceiptKey, ReceiptUpload> pendingTravelReceipts =
            new HashMap<>();

    /** Buffer key: which working trip entry + which generated-line kind. */
    private record TravelReceiptKey(ValueSignal<TravelDto> travel, GeneratedLineKind kind) {
    }

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
        setMaxWidth("46rem");
        addClassName("report-detail");

        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.addClassName("error-summary");

        statusCallout.setWidthFull();
        statusCallout.setVisible(false);

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
        addTravel.addThemeVariants(ButtonVariant.TERTIARY);
        addTravel.addClickListener(event -> addTravel());

        // Submit is the full-width forward action; Save keeps working, Delete is
        // the quiet destructive one — a footer action bar (the mockup's footer).
        var actions = new HorizontalLayout(save, submit, delete);
        actions.setWidthFull();
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.expand(submit);
        actions.addClassName("detail-actions");

        add(headerRow(), errorSummary, statusCallout, reportDate,
                additionalInformation, travelsSection(), linesSection(), totalsCard(),
                actions);
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
        statusBadgeSlot.removeAll();
        statusBadgeSlot.add(ReportViewSupport.statusBadge(dto.status()));
        updateStatusCallout(dto.status());
        headerId.setText(dto.isPersisted() ? "Report #" + dto.id() : "New report");
        headerName.setText(dto.additionalInformation() == null
                || dto.additionalInformation().isBlank()
                ? "Expense report" : dto.additionalInformation());

        // Set editability before repopulating so the card factory builds the
        // right (interactive vs read-only) cards.
        editable = dto.status().isEditable();
        editableSignal.set(editable);
        reportDate.setReadOnly(!editable);
        additionalInformation.setReadOnly(!editable);
        save.setVisible(editable);
        addLine.setVisible(editable);
        addTravel.setVisible(editable);
        // Submit only for a persisted DRAFT: a brand-new report must be saved
        // first, and resubmitting a REJECTED report is Phase 5 (out of scope).
        submit.setVisible(dto.isPersisted() && dto.status() == ReportStatus.DRAFT);
        // Delete only while DRAFT and already persisted (ADR-0006, glossary).
        delete.setVisible(dto.isPersisted() && dto.status().isDeletable());

        pendingReceipts.clear();
        pendingTravelReceipts.clear();
        lines.clear();
        if (!dto.lines().isEmpty()) {
            lines.insertAllLast(dto.lines());
        }
        travels.clear();
        if (!dto.travels().isEmpty()) {
            travels.insertAllLast(dto.travels());
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
                working.version(), currentLines(), currentTravels(), working.total(),
                working.netTotal(), working.vatTotal(), working.perDiemTotal(),
                working.kilometreTotal(), working.mealTotal());
        var receipts = pendingReceiptsByLineIndex();
        var travelReceipts = pendingTravelReceiptsByRef();
        try {
            if (!working.isPersisted()) {
                Long newId = service.create(edited, receipts, travelReceipts);
                Notification.show("Report saved.");
                // First save routes /report → /report/{id} (ADR-0019).
                getUI().ifPresent(ui -> ui.navigate(ReportDetailView.class, newId));
            } else {
                load(service.update(working.id(), edited, working.version(), receipts,
                        travelReceipts));
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

    /** A snapshot of the working trips, in order, for a save. */
    private List<TravelDto> currentTravels() {
        return travels.peek().stream().map(ValueSignal::peek).toList();
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

    /**
     * Translates each buffered generated-line receipt to a {@link GeneratedLineRef}
     * (trip position + kind, ADR-0021). The trip's position in {@code travels.peek()}
     * matches its position in {@link #currentTravels()} — and thus in the saved
     * {@code dto.travels()} the service reconciles — so the service resolves the ref
     * to the right persisted line. A buffered receipt for a trip since removed
     * (entry no longer in the list) is dropped.
     */
    private Map<GeneratedLineRef, ReceiptUpload> pendingTravelReceiptsByRef() {
        if (pendingTravelReceipts.isEmpty()) {
            return Map.of();
        }
        var entries = travels.peek();
        Map<GeneratedLineRef, ReceiptUpload> byRef = new HashMap<>();
        pendingTravelReceipts.forEach((key, upload) -> {
            int index = entries.indexOf(key.travel());
            if (index >= 0) {
                byRef.put(new GeneratedLineRef(index, key.kind()), upload);
            }
        });
        return byRef;
    }

    private void addLine() {
        new LineEditorDialog(referenceData.activeExpenseTypes(),
                referenceData.activeVatRates(), null, service::receiptDownload,
                (dto, receipt) -> {
                    var entry = lines.insertLast(dto);
                    if (receipt != null) {
                        pendingReceipts.put(entry, receipt);
                    }
                }).open();
    }

    /** Opens the trip editor to insert a new trip (glossary: Travel Calculator). */
    private void addTravel() {
        new TravelEditorDialog(null, service::previewTravel,
                service::foreignDestinations, travels::insertLast).open();
    }

    /**
     * Re-opens the trip editor pre-filled; a save recomputes the generated lines.
     * The recomputed preview carries no receipt info, so any receipt already
     * attached (persisted or buffered) is carried across by kind, and buffered
     * receipts for a kind the trip no longer earns are pruned.
     */
    private void openTravelEditor(ValueSignal<TravelDto> entry) {
        var before = entry.peek();
        new TravelEditorDialog(before, service::previewTravel,
                service::foreignDestinations, updated -> {
            entry.set(mergeReceipts(before, updated));
            var kinds = updated.generatedLines().stream()
                    .map(GeneratedLineView::kind).toList();
            pendingTravelReceipts.keySet().removeIf(
                    key -> key.travel().equals(entry) && !kinds.contains(key.kind()));
        }).open();
    }

    /** Carries each still-present kind's receipt summary from {@code before} onto {@code updated}. */
    private static TravelDto mergeReceipts(TravelDto before, TravelDto updated) {
        var merged = updated.generatedLines().stream()
                .map(line -> before.generatedLine(line.kind())
                        .filter(GeneratedLineView::hasReceipt)
                        .map(old -> line.withReceipt(old.receiptId(), old.receiptFilename(),
                                old.receiptContentType(), old.receiptSizeBytes()))
                        .orElse(line))
                .toList();
        return updated.withGeneratedLines(merged);
    }

    /** Opens the receipt editor for one of a trip's generated lines (Phase 4.3). */
    private void openTravelLineReceipt(ValueSignal<TravelDto> entry,
            GeneratedLineView line) {
        new TravelLineReceiptDialog(line, service::receiptDownload, (updated, receipt) -> {
            var trip = entry.peek();
            var newLines = trip.generatedLines().stream()
                    .map(l -> l.kind() == updated.kind() ? updated : l).toList();
            entry.set(trip.withGeneratedLines(newLines));
            if (receipt != null) {
                pendingTravelReceipts.put(new TravelReceiptKey(entry, updated.kind()),
                        receipt);
            }
        }).open();
    }

    private void openEditor(ValueSignal<ExpenseLineDto> entry) {
        new LineEditorDialog(referenceData.activeExpenseTypes(),
                referenceData.activeVatRates(), entry.peek(), service::receiptDownload,
                (dto, receipt) -> {
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
        var back = new Button(VaadinIcon.ARROW_LEFT.create(),
                event -> getUI().ifPresent(ui -> ui.navigate(MyReportsView.class)));
        back.addThemeVariants(ButtonVariant.TERTIARY);
        back.getElement().setAttribute("aria-label", "Back to reports");

        headerId.addClassName("report-detail-eyebrow");
        headerName.addClassName("report-detail-title");
        var titleColumn = new VerticalLayout(headerId, headerName);
        titleColumn.setPadding(false);
        titleColumn.setSpacing(false);

        var header = new HorizontalLayout(back, titleColumn, statusBadgeSlot);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.expand(titleColumn);
        return header;
    }

    /**
     * Sets the coloured status note above the form: a red "changes requested"
     * callout for a rejected report, a green approved note, a neutral
     * "waiting for approval" note once submitted, and nothing while it is a
     * draft. Only the status drives it — the approver identity, comment, and
     * dates are surfaced when the approval flow lands (Phase 5); until then the
     * note states where the report stands without inventing that data.
     */
    private void updateStatusCallout(ReportStatus status) {
        statusCallout.removeAll();
        statusCallout.setClassName("status-callout");
        switch (status) {
            case REJECTED -> {
                statusCallout.addClassName("status-callout--rejected");
                var heading = new Span("Rejected — changes requested");
                heading.addClassName("status-callout-heading");
                statusCallout.add(heading, new Span(
                        "Update this report to address the feedback, then resubmit."));
                statusCallout.setVisible(true);
            }
            case APPROVED -> {
                statusCallout.addClassName("status-callout--approved");
                var heading = new Span("Approved.");
                heading.addClassName("status-callout-heading");
                statusCallout.add(heading,
                        new Span(" This report has been approved for reimbursement."));
                statusCallout.setVisible(true);
            }
            case SUBMITTED -> {
                statusCallout.addClassName("status-callout--submitted");
                statusCallout.add(new Span(
                        "Waiting for approval. You'll see any feedback here."));
                statusCallout.setVisible(true);
            }
            case DRAFT -> statusCallout.setVisible(false);
        }
    }

    /**
     * The totals card (the mockup's summary block) — a net / VAT breakdown above
     * a bold total-to-reimburse line, all recomputing live via Signals as the
     * working lines change.
     */
    private Div totalsCard() {
        // Net/VAT include the VAT-bearing manual lines plus each trip's parking fee
        // (also VAT-bearing); the three tax-free allowances are broken out below.
        netDisplay.bindText(Signal.computed(() -> formatEur(currentTotals().net())));
        vatDisplay.bindText(Signal.computed(() -> formatEur(currentTotals().vat())));
        perDiemDisplay.bindText(Signal.computed(() -> formatEur(currentPerDiem())));
        kilometreDisplay.bindText(Signal.computed(() -> formatEur(currentKilometre())));
        mealDisplay.bindText(Signal.computed(() -> formatEur(currentMeal())));
        grossDisplay.bindText(Signal.computed(() -> formatEur(currentGrandTotal())));
        grossDisplay.addClassName("totals-grand");

        // Each tax-free allowance is its own subtotal row, shown only when a trip
        // earned one (Phase 4.3).
        var perDiemRow = allowanceRow("Per diem allowance", perDiemDisplay,
                this::currentPerDiem);
        var kilometreRow = allowanceRow("Kilometre allowance", kilometreDisplay,
                this::currentKilometre);
        var mealRow = allowanceRow("Meal allowance", mealDisplay, this::currentMeal);

        var totalLabel = new Span("Total to reimburse");
        var totalRow = new HorizontalLayout(totalLabel, grossDisplay);
        totalRow.setWidthFull();
        totalRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        totalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        totalRow.addClassName("totals-total-row");

        var card = new Div(breakdownRow("Net", netDisplay),
                breakdownRow("VAT", vatDisplay), perDiemRow, kilometreRow, mealRow,
                totalRow);
        card.setWidthFull();
        card.addClassName("totals-card");
        return card;
    }

    /** A tax-free allowance subtotal row, shown only when its amount is non-zero. */
    private HorizontalLayout allowanceRow(String label, Span value,
            java.util.function.Supplier<BigDecimal> amount) {
        var row = breakdownRow(label, value);
        row.bindVisible(Signal.computed(() -> amount.get().signum() != 0));
        return row;
    }

    /** One secondary "label … value" row in the totals card. */
    private static HorizontalLayout breakdownRow(String label, Span value) {
        var name = new Span(label);
        var row = new HorizontalLayout(name, value);
        row.setWidthFull();
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        row.addClassName("totals-row");
        return row;
    }

    /**
     * The trips section (Phase 4.2/4.3): the Trip & Allowance cards above the
     * "Insert travel info" action. Both the cards' Edit/Remove affordances and the
     * action are hidden on a read-only report (the {@link #addTravel} visibility is
     * toggled in {@link #load}; the card factory reads {@link #editable}).
     */
    private Div travelsSection() {
        var cardList = new VerticalLayout();
        cardList.setPadding(false);
        cardList.setSpacing("var(--vaadin-gap-m)");
        cardList.setWidthFull();
        cardList.bindChildren(travels, this::travelCard);

        var section = new Div(cardList, addTravel);
        section.setWidthFull();
        return section;
    }

    /** One "Trip & Allowance" card — trip info only (no amounts), with Edit. */
    private Component travelCard(ValueSignal<TravelDto> entry) {
        var title = new Span();
        title.bindText(entry.map(t -> t.purpose() == null || t.purpose().isBlank()
                ? "Trip" : t.purpose()));
        title.addClassName("line-name");
        var where = new Span();
        where.bindText(entry.map(ReportDetailView::tripWhere));
        where.addClassName("muted");
        var when = new Span();
        when.bindText(entry.map(t -> ReportViewSupport.formatTripRange(
                t.departureAt(), t.returnAt())));
        when.addClassName("muted-xs");
        var texts = new VerticalLayout(title, where, when);
        texts.setPadding(false);
        texts.setSpacing(false);

        var icon = VaadinIcon.AIRPLANE.create();
        icon.addClassName("travel-card-icon");

        var body = new HorizontalLayout(icon, texts);
        body.setWidthFull();
        body.setAlignItems(FlexComponent.Alignment.CENTER);
        body.expand(texts);

        var card = new HorizontalLayout(body);
        card.setWidthFull();
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.expand(body);
        card.addClassName("line-card");
        card.addClassName("travel-card");
        if (editable) {
            var edit = new Button("Edit", event -> openTravelEditor(entry));
            edit.addThemeVariants(ButtonVariant.TERTIARY);
            var trash = new Button(VaadinIcon.TRASH.create(), event -> {
                pendingTravelReceipts.keySet().removeIf(k -> k.travel().equals(entry));
                travels.remove(entry);
            });
            trash.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
            trash.getElement().setAttribute("aria-label", "Remove trip");
            card.add(edit, trash);
        }

        // The trip's generated lines (per-diem, kilometre, meal, parking) as
        // read-only rows nested under it; each can carry a receipt (Phase 4.3). The
        // list rebuilds whenever the trip is re-costed or a receipt is attached.
        var generatedList = new Div();
        generatedList.addClassName("travel-lines");
        generatedList.setWidthFull();
        Signal.effect(generatedList, () -> {
            TravelDto trip = entry.get();
            generatedList.removeAll();
            trip.generatedLines()
                    .forEach(line -> generatedList.add(generatedLineRow(entry, line)));
        });

        var group = new VerticalLayout(card, generatedList);
        group.setPadding(false);
        group.setSpacing(false);
        group.setWidthFull();
        group.addClassName("travel-group");
        return group;
    }

    /**
     * One read-only generated-line row nested under a trip: its label, computed
     * amount, and read-only explanation, plus the receipt it carries and (while
     * editable) an attach/edit-receipt affordance (Phase 4.3).
     */
    private Component generatedLineRow(ValueSignal<TravelDto> entry,
            GeneratedLineView line) {
        var name = new Span(ReportViewSupport.generatedLineLabel(line.kind()));
        name.addClassName("line-name");
        var comment = new Span(line.comment() == null ? "" : line.comment());
        comment.addClassName("muted-xs");
        var texts = new VerticalLayout(name, comment);
        texts.setPadding(false);
        texts.setSpacing(false);

        var amount = new Span(formatEur(line.amount()));
        amount.addClassName("line-amount");
        var receipt = new Div();
        receipt.addClassName("line-receipt");
        if (line.hasReceipt() && line.receiptId() != null) {
            Long receiptId = line.receiptId();
            receipt.add(ReceiptPreview.forReceipt(line.receiptFilename(),
                    line.receiptContentType(), () -> service.receiptDownload(receiptId)));
        } else if (line.hasReceipt()) {
            var chip = new Span("📎 " + line.receiptFilename());
            chip.addClassName("muted-xs");
            receipt.add(chip);
        }
        var amounts = new VerticalLayout(amount, receipt);
        amounts.setPadding(false);
        amounts.setSpacing(false);
        amounts.setAlignItems(FlexComponent.Alignment.END);

        var body = new HorizontalLayout(texts, amounts);
        body.setWidthFull();
        body.setAlignItems(FlexComponent.Alignment.CENTER);
        body.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        body.expand(texts);

        var row = new HorizontalLayout(body);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.expand(body);
        row.addClassName("line-card");
        row.addClassName("travel-line-row");
        if (editable) {
            var attach = new Button(line.hasReceipt() ? "Receipt" : "Add receipt",
                    VaadinIcon.PAPERCLIP.create());
            attach.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            attach.addClickListener(event -> openTravelLineReceipt(entry, line));
            attach.getElement().setAttribute("aria-label",
                    (line.hasReceipt() ? "Edit receipt: " : "Add receipt: ")
                            + ReportViewSupport.generatedLineLabel(line.kind()));
            row.add(attach);
        }
        return row;
    }

    /** "destinations, country" for a trip card — the trip's where-line. */
    private static String tripWhere(TravelDto trip) {
        var destinations = trip.destinations() == null ? "" : trip.destinations();
        if (trip.country() == null || trip.country().isBlank()) {
            return destinations;
        }
        return destinations.isBlank() ? trip.country()
                : destinations + ", " + trip.country();
    }

    private Div linesSection() {
        var emptyState = new Span("No expenses yet — add your first.");
        emptyState.addClassName("muted");
        // Only invite adding when the report is editable — a read-only report that
        // carries only a trip (no manual lines) must not prompt an action it can't
        // offer (ADR-0020). Both are signals so the effect always reads at least one
        // and re-runs when the report's editability flips on (re)load.
        emptyState.bindVisible(Signal.computed(
                () -> lines.get().isEmpty() && editableSignal.get()));

        var cardList = new VerticalLayout();
        cardList.setPadding(false);
        cardList.setSpacing("var(--vaadin-gap-m)");
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
        name.addClassName("line-name");
        var subtitle = new Span();
        subtitle.bindText(entry.map(ReportDetailView::subtitleOf));
        subtitle.addClassName("muted");
        // Receipt read affordance (ADR-0021): a saved image shows a thumbnail
        // that enlarges in a dialog, a saved PDF an "open" link — both streaming
        // the bytes on demand via the owner-scoped DownloadHandler, so a submitted
        // (read-only) report can still view its receipt. An unsaved buffered
        // attachment has no stable id yet, so the card shows its filename until
        // the first save (the editor previews the buffered bytes meanwhile).
        var receipt = new Div();
        receipt.addClassName("line-receipt");
        Signal.effect(receipt, () -> {
            ExpenseLineDto dto = entry.get();
            receipt.removeAll();
            if (!dto.hasReceipt()) {
                return;
            }
            if (dto.receiptId() != null) {
                Long receiptId = dto.receiptId();
                receipt.add(ReceiptPreview.forReceipt(dto.receiptFilename(),
                        dto.receiptContentType(),
                        () -> service.receiptDownload(receiptId)));
            } else {
                var chip = new Span("📎 " + dto.receiptFilename());
                chip.addClassName("muted-xs");
                receipt.add(chip);
            }
        });
        var texts = new VerticalLayout(name, subtitle, receipt);
        texts.setPadding(false);
        texts.setSpacing(false);

        var gross = new Span();
        gross.bindText(entry.map(dto -> formatEur(grossOf(dto))));
        gross.addClassName("line-amount");
        var breakdown = new Span();
        breakdown.bindText(entry.map(ReportDetailView::breakdownOf));
        breakdown.addClassName("muted-xs");
        var amounts = new VerticalLayout(gross, breakdown);
        amounts.setPadding(false);
        amounts.setSpacing(false);
        amounts.setAlignItems(FlexComponent.Alignment.END);

        // A small colour swatch per expense type (the mockup's category dot). The
        // dynamic colour is fed to the .category-dot class as a CSS custom
        // property, recomputed if the line's type changes in the editor.
        var dot = new Div();
        dot.addClassName("category-dot");
        Signal.effect(dot, () -> dot.getStyle().set("--category-color",
                ReportViewSupport.categoryColor(entry.get().expenseTypeName())));

        var body = new HorizontalLayout(dot, texts, amounts);
        body.setWidthFull();
        body.setAlignItems(FlexComponent.Alignment.CENTER);
        body.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        body.expand(texts);
        if (editable) {
            body.addClassName("clickable");
            body.addClickListener(event -> openEditor(entry));
        }

        var card = new HorizontalLayout(body);
        card.setWidthFull();
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.expand(body);
        card.addClassName("line-card");
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

    /**
     * The VAT-bearing net/VAT/gross summed live: the working manual lines plus each
     * trip's parking fee (also VAT-bearing, at the parking type's rate). The
     * tax-free allowances are broken out separately (Phase 4.3).
     */
    private LineAmounts currentTotals() {
        var manual = lines.get().stream().map(ValueSignal::get)
                .filter(dto -> dto.amount() != null && dto.vatRatePercent() != null)
                .map(dto -> LineAmounts.of(dto.amount(), dto.vatRatePercent()))
                .reduce(LineAmounts.zero(), LineAmounts::add);
        // Each trip's VAT-bearing generated lines (parking) fold into Net/VAT too.
        return travels.get().stream().map(ValueSignal::get)
                .flatMap(t -> t.generatedLines().stream())
                .filter(line -> !line.isTaxFreeAllowance())
                .map(line -> LineAmounts.of(line.amount(), line.vatRatePercent()))
                .reduce(manual, LineAmounts::add);
    }

    private BigDecimal currentPerDiem() {
        return sumKind(GeneratedLineKind.PER_DIEM);
    }

    private BigDecimal currentKilometre() {
        return sumKind(GeneratedLineKind.KILOMETRE);
    }

    private BigDecimal currentMeal() {
        return sumKind(GeneratedLineKind.MEAL);
    }

    /** Sums one generated-line kind's amount live across the working trips (Phase 4.3). */
    private BigDecimal sumKind(GeneratedLineKind kind) {
        return travels.get().stream().map(ValueSignal::get)
                .map(t -> t.amountOf(kind))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Grand total = VAT-bearing gross (Net + VAT) + the three tax-free allowances. */
    private BigDecimal currentGrandTotal() {
        return currentTotals().gross().add(currentPerDiem()).add(currentKilometre())
                .add(currentMeal());
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
        heading.addClassName("summary-heading");
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
        heading.addClassName("summary-heading");
        var list = new UnorderedList();
        messages.forEach(message -> list.add(new ListItem(message)));
        errorSummary.add(heading, list);
        errorSummary.setVisible(true);
    }
}

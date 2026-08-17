package com.vaadin.expensemanager.report.ui;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.vaadin.expensemanager.approval.service.ApprovalService;
import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.LineAmounts;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.GeneratedLineRef;
import com.vaadin.expensemanager.report.service.GeneratedLineView;
import com.vaadin.expensemanager.report.service.ReceiptUpload;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.expensemanager.report.service.StatusChangeDto;
import com.vaadin.expensemanager.report.service.TravelDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;
import com.vaadin.flow.signals.local.ValueSignal;

import jakarta.annotation.security.PermitAll;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatPercent;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatQuantity;

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
 * {@code REJECTED}); the forward action (always enabled — a
 * zero-line submit surfaces the reason, never a silent no-op) shows on a
 * persisted {@code DRAFT} (<strong>Submit for approval</strong>) and on a
 * {@code REJECTED} report (<strong>Resubmit</strong>, Phase 5.5), both moving it
 * to {@code SUBMITTED};
 * <strong>Delete</strong> only while a persisted {@code DRAFT} (the aggregate
 * enforces the guard, ADR-0006). Stale writes surface a "reload" affordance,
 * never a silent overwrite (ADR-0011). {@code @PermitAll}; owner-scoping is
 * enforced in the service.
 *
 * <p><strong>Admin review mode (Phase 5).</strong> The same view is reached from
 * the approval queue at the {@code /review/{id}} alias to review another user's
 * report. Review mode loads via the non-owner-scoped
 * {@link ApprovalService#findForReview} (any owner), renders everything
 * read-only, and offers <strong>Approve</strong> and <strong>Reject</strong> (the
 * latter via a mandatory-reason dialog) in place of Save/Submit/Delete — both
 * reusing the same {@link #showConflict()}/{@link #reload()} conflict UX
 * (ADR-0011). The rejection reason, rejecter, and date then surface in the owner's
 * status callout, and the full ordered status history is shown to both. The review alias is unreachable except by an admin: navigation is
 * gated in {@link #setParameter} (a non-admin is forwarded away) and, as the real
 * enforcement (ADR-0008), every {@code ApprovalService} method is
 * {@code @RolesAllowed("ADMIN")}.
 */
@Route("report")
@RouteAlias("review")
@PageTitle("Report")
@PermitAll
public class ReportDetailView extends VerticalLayout
        implements HasUrlParameter<Long> {

    /** The URL path segment that enters admin review mode (vs the owner path). */
    private static final String REVIEW_SEGMENT = "review";

    /**
     * The approval-queue route path, referenced as a string rather than the
     * {@code approval.ui} view class so {@code report.ui} does not depend on
     * {@code approval.ui} (which links back here for review — that would be a
     * package cycle).
     */
    private static final String APPROVAL_QUEUE_PATH = "approvals";

    private final transient ExpenseReportService service;
    private final transient ReferenceDataService referenceData;
    private final transient ApprovalService approvalService;
    private final transient CurrentUserProvider currentUserProvider;

    private final ErrorSummary errorSummary = new ErrorSummary();
    /** Holds the freshly-built status badge; repopulated on each (re)load. */
    private final Div statusBadgeSlot = new Div();
    /** The rejected/approved/submitted status callout; hidden while DRAFT. */
    private final Div statusCallout = new Div();
    /** The ordered status-history log; hidden until the first transition is recorded. */
    private final Div statusHistory = new Div();
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
    private final Button approve = new Button("Approve");
    private final Button reject = new Button("Reject");
    private final Button addLine = new Button("Add expense", LucideIcon.PLUS.create());
    private final Button addTravel =
            new Button("Insert travel info", LucideIcon.PLANE.create());
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

    /** Whether the view was entered via the admin {@code /review/{id}} alias. */
    private boolean reviewMode = false;

    public ReportDetailView(ExpenseReportService service,
            ReferenceDataService referenceData, ApprovalService approvalService,
            CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.referenceData = referenceData;
        this.approvalService = approvalService;
        this.currentUserProvider = currentUserProvider;
        setPadding(true);
        setSpacing(true);
        setMaxWidth("46rem");
        addClassName("report-detail");

        statusCallout.setWidthFull();
        statusCallout.setVisible(false);

        reportDate.setRequiredIndicatorVisible(true);
        // Without a bad-input message the field goes invalid with a *blank* message
        // when the user types an unparseable date (e.g. "dsdds"), which surfaced as an
        // empty bullet in the error summary (issue #85). Required stays with the
        // binder's asRequired below.
        reportDate.setI18n(new DatePicker.DatePickerI18n()
                .setBadInputErrorMessage("Enter a valid date"));
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
        // Approve is the admin's forward action in review mode (mirrors Submit).
        approve.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SUCCESS);
        approve.addClickListener(event -> onApprove());
        approve.setVisible(false);
        // Reject is the admin's destructive review action — it opens a dialog for
        // the mandatory reason rather than acting on the click (mirrors Delete).
        reject.addThemeVariants(ButtonVariant.ERROR);
        reject.addClickListener(event -> openRejectDialog());
        reject.setVisible(false);
        delete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        delete.addClickListener(event -> confirmDelete());
        addLine.addThemeVariants(ButtonVariant.TERTIARY);
        addLine.addClickListener(event -> addLine());
        addTravel.addThemeVariants(ButtonVariant.TERTIARY);
        addTravel.addClickListener(event -> addTravel());

        // Submit is the full-width forward action; Save keeps working, Delete is
        // the quiet destructive one — a footer action bar (the mockup's footer).
        var actions = new HorizontalLayout(save, submit, approve, reject, delete);
        actions.setWidthFull();
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.expand(submit);
        actions.addClassName("detail-actions");

        statusHistory.setWidthFull();
        statusHistory.setVisible(false);
        statusHistory.addClassName("status-history");

        add(headerRow(), errorSummary, statusCallout, reportDate,
                additionalInformation, travelsSection(), linesSection(), totalsCard(),
                statusHistory, actions);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long id) {
        reviewMode = REVIEW_SEGMENT.equals(event.getLocation().getFirstSegment());
        if (reviewMode) {
            enterReviewMode(event, id);
            return;
        }
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

    /**
     * Enters admin review mode for {@code /review/{id}} (Phase 5). The alias shares
     * the owner path's {@code @PermitAll}, so admin access is gated here — a
     * non-admin is forwarded away — backed by {@link ApprovalService}'s
     * {@code @RolesAllowed("ADMIN")} as the real enforcement (ADR-0008). Loads the
     * report via the non-owner-scoped {@link ApprovalService#findForReview}.
     */
    private void enterReviewMode(BeforeEvent event, Long id) {
        if (id == null || !currentUserProvider.get()
                .map(user -> user.isAdmin()).orElse(false)) {
            event.forwardTo("");
            return;
        }
        try {
            load(approvalService.findForReview(id));
        } catch (IllegalArgumentException notFound) {
            Notification.show("Report not found.");
            event.forwardTo(APPROVAL_QUEUE_PATH);
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
        // The status callout speaks to the owner ("you'll see feedback here"); in
        // admin review mode the status badge carries the state instead.
        if (reviewMode) {
            statusCallout.setVisible(false);
        } else {
            updateStatusCallout(dto);
        }
        // The ordered audit trail is shown to both the owner and the admin reviewer.
        renderStatusHistory(dto.statusHistory());
        headerId.setText(dto.isPersisted() ? "Report #" + dto.id() : "New report");
        headerName.setText(dto.additionalInformation() == null
                || dto.additionalInformation().isBlank()
                ? "Expense report" : dto.additionalInformation());

        // Set editability before repopulating so the card factory builds the
        // right (interactive vs read-only) cards. An admin reviewing another user's
        // report never edits it — review mode is always read-only, even once a
        // reject moves the report to the (owner-)editable REJECTED state.
        editable = dto.status().isEditable() && !reviewMode;
        editableSignal.set(editable);
        reportDate.setReadOnly(!editable);
        additionalInformation.setReadOnly(!editable);
        save.setVisible(editable);
        addLine.setVisible(editable);
        addTravel.setVisible(editable);
        // The forward action shows on a persisted DRAFT (first submit) and on a
        // persisted REJECTED report (resubmit, Phase 5.5) — a brand-new report must
        // be saved first. Same button, relabelled/rerouted by status; SUBMITTED and
        // APPROVED stay read-only with no forward action.
        boolean rejected = dto.status() == ReportStatus.REJECTED;
        submit.setVisible(!reviewMode && dto.isPersisted()
                && (dto.status() == ReportStatus.DRAFT || rejected));
        submit.setText(rejected ? "Resubmit" : "Submit for approval");
        // Delete only while DRAFT and already persisted (ADR-0006, glossary).
        delete.setVisible(!reviewMode && dto.isPersisted() && dto.status().isDeletable());
        // Approve/Reject are the review-mode actions, only while the report is still
        // reviewable (SUBMITTED); once acted on they drop and the view stays read-only.
        approve.setVisible(reviewMode && dto.status().isReviewable());
        reject.setVisible(reviewMode && dto.status().isReviewable());

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
        if (!validateForm()) {
            return;
        }
        var edited = editedDto();
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
        } catch (RuntimeException ex) {
            surface(ex);
        }
    }

    /**
     * Writes the report-level fields into the model, surfacing the top-of-form
     * error summary (ADR-0020) and returning {@code false} if a required field is
     * blank. Shared by Save and the save-then-submit path.
     */
    private boolean validateForm() {
        if (binder.writeBeanIfValid(model)) {
            return true;
        }
        errorSummary.showValidationErrors(binder.validate());
        return false;
    }

    /** A snapshot of the current working copy — report fields, lines, and trips — for a save. */
    private ReportDetailDto editedDto() {
        return new ReportDetailDto(working.id(), model.getReportDate(),
                model.getAdditionalInformation(), working.status(),
                working.version(), currentLines(), currentTravels(), working.total(),
                working.netTotal(), working.vatTotal(), working.perDiemTotal(),
                working.kilometreTotal(), working.mealTotal());
    }

    /**
     * Sends the persisted report to the admin queue (UC-003, issue #81): a
     * {@code DRAFT} submits, a {@code REJECTED} report resubmits (Phase 5.5) — both
     * land in {@code SUBMITTED}. Because submitting locks the report against further
     * edits, this first <strong>validates and confirms</strong>, then <strong>saves
     * the current working copy</strong> before the transition (they run atomically
     * server-side, {@link ExpenseReportService#saveAndSubmit}) — so the state the
     * user sees is the state that gets submitted, never a stale persisted one.
     * The button is always enabled (ADR-0020) — a zero-line report is not silently
     * no-op'd but surfaces the domain reason in the error summary.
     */
    private void onSubmit() {
        clearErrors();
        // Validate before confirming so we never pop the dialog on an invalid form;
        // this also writes the report-level fields into the model for the save.
        if (!validateForm()) {
            return;
        }
        confirmSubmit();
    }

    /**
     * The submit/resubmit confirmation (issue #81): a warning that the report locks
     * once submitted, above a Cancel / confirm footer. Confirming saves the current
     * working copy and transitions in one atomic call.
     */
    private void confirmSubmit() {
        boolean rejected = working.status() == ReportStatus.REJECTED;
        var dialog = new Dialog();
        dialog.setHeaderTitle(rejected ? "Resubmit for approval?"
                : "Submit for approval?");
        dialog.add(new Paragraph("This will submit the expense report for approval "
                + "and save your latest changes. You won't be able to update it "
                + "while it's waiting for approval."));

        var confirm = new Button(rejected ? "Resubmit report" : "Submit report",
                event -> {
                    dialog.close();
                    performSubmit(rejected);
                });
        confirm.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    /**
     * Saves the working copy and transitions it to {@code SUBMITTED} atomically
     * (issue #81). A stale write surfaces the reload affordance (ADR-0011); a domain
     * violation (e.g. no lines) rolls the save back and surfaces the reason.
     */
    private void performSubmit(boolean rejected) {
        var edited = editedDto();
        var receipts = pendingReceiptsByLineIndex();
        var travelReceipts = pendingTravelReceiptsByRef();
        try {
            // One service call for both actions (the aggregate branches on its
            // origin state); the UI keeps first-submit vs resubmit only in wording.
            load(service.saveAndSubmit(working.id(), edited, working.version(),
                    receipts, travelReceipts));
            Notification.show(rejected ? "Report resubmitted for approval."
                    : "Report submitted for approval.");
        } catch (RuntimeException ex) {
            surface(ex);
        }
    }

    /**
     * Approves the report under review (Phase 5): {@code SUBMITTED → APPROVED}.
     * Admin-only (enforced in {@link ApprovalService}); a stale approve surfaces
     * the same reload affordance as a stale save/submit (ADR-0011), never a silent
     * double-processing. The illegal-transition guard is defensive — the button
     * only shows while the report is reviewable.
     */
    private void onApprove() {
        clearErrors();
        try {
            load(approvalService.approve(working.id(), working.version()));
            Notification.show("Report approved.");
        } catch (RuntimeException ex) {
            surface(ex);
        }
    }

    /**
     * Opens the reject dialog (Phase 5): a mandatory Rejection Comment above a
     * Cancel / Reject footer. Following ADR-0020, the confirm button is
     * <strong>always enabled</strong> — a blank comment does not submit but surfaces
     * the reason-required message in the dialog's own error summary and focuses the
     * field. A non-blank reason rejects the report ({@code SUBMITTED → REJECTED}),
     * recording the reason; a stale reject reuses the same {@link #showConflict()}/
     * {@link #reload()} conflict UX as save/submit/approve (ADR-0011).
     */
    private void openRejectDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Reject report");

        var summary = new ErrorSummary();

        var comment = new TextArea("Rejection comment");
        comment.setWidthFull();
        comment.setRequiredIndicatorVisible(true);
        comment.setMaxLength(2000);

        var body = new VerticalLayout(summary, new Paragraph(
                "Explain what needs to change — the owner will see this reason."),
                comment);
        body.setPadding(false);
        body.setSpacing(false);
        dialog.add(body);

        var confirm = new Button("Reject report", event ->
                submitReject(dialog, summary, comment));
        confirm.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
        comment.focus();
    }

    /**
     * Handles the reject-dialog confirm: blocks a blank comment with the dialog's
     * error summary (never a silent no-op, ADR-0020), otherwise rejects the report
     * and closes the dialog. A stale reject closes the dialog and surfaces the
     * shared reload affordance on the form (ADR-0011).
     */
    private void submitReject(Dialog dialog, ErrorSummary summary, TextArea comment) {
        var reason = comment.getValue();
        if (reason == null || reason.isBlank()) {
            summary.show("A rejection comment is required.");
            comment.focus();
            return;
        }
        try {
            var updated = approvalService.reject(working.id(), reason,
                    working.version());
            dialog.close();
            load(updated);
            Notification.show("Report rejected.");
        } catch (RuntimeException ex) {
            // The reject dialog always closes; the outcome then routes as usual.
            dialog.close();
            surface(ex);
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
                    if (receipt == null || receipt.isRemoval()) {
                        lines.insertLast(dto);
                        return;
                    }
                    // A new line carrying a buffered receipt: insert it receipt-free
                    // first, record the bytes against the fresh entry, then set the
                    // full dto — so the card's effect re-runs with the buffer already
                    // in hand and previews the thumbnail immediately (issue #89),
                    // rather than only after the report is first saved.
                    var entry = lines.insertLast(dto.withoutReceipt());
                    pendingReceipts.put(entry, receipt);
                    entry.set(dto);
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
            // Buffer the bytes before the signal update, so the rebuilt row's
            // preview reads them and shows the thumbnail immediately.
            if (receipt != null) {
                pendingTravelReceipts.put(new TravelReceiptKey(entry, updated.kind()),
                        receipt);
            }
            entry.set(trip.withGeneratedLines(newLines));
        }).open();
    }

    private void openEditor(ValueSignal<ExpenseLineDto> entry) {
        new LineEditorDialog(referenceData.activeExpenseTypes(),
                referenceData.activeVatRates(), entry.peek(), service::receiptDownload,
                (dto, receipt) -> {
                    // Buffer the bytes before the signal update, so the card's
                    // effect sees them when it re-runs and previews immediately.
                    if (receipt != null) {
                        pendingReceipts.put(entry, receipt);
                    }
                    entry.set(dto);
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
        } catch (RuntimeException ex) {
            surface(ex);
        }
    }

    private HorizontalLayout headerRow() {
        // Back returns to the approval queue in review mode, else the owner's list.
        var back = new Button(LucideIcon.ARROW_LEFT.create(), event -> getUI()
                .ifPresent(ui -> {
                    if (reviewMode) {
                        ui.navigate(APPROVAL_QUEUE_PATH);
                    } else {
                        ui.navigate(MyReportsView.class);
                    }
                }));
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
     * callout for a rejected report — now carrying the <strong>real</strong>
     * rejection reason, who rejected it, and when, read from the status history
     * (Phase 5) — a green approved note, a neutral "waiting for approval" note
     * once submitted, and nothing while it is a draft.
     */
    private void updateStatusCallout(ReportDetailDto dto) {
        statusCallout.removeAll();
        statusCallout.setClassName("status-callout");
        switch (dto.status()) {
            case REJECTED -> {
                statusCallout.addClassName("status-callout--rejected");
                var heading = new Span("Rejected — changes requested");
                heading.addClassName("status-callout-heading");
                statusCallout.add(heading);
                lastRejection(dto).ifPresent(change -> {
                    var reason = new Span(change.comment());
                    reason.addClassName("status-callout-reason");
                    var meta = new Span("Rejected by " + change.actorName() + " on "
                            + ReportViewSupport.formatTimestamp(change.changedAt()));
                    meta.addClassName("muted-xs");
                    statusCallout.add(reason, meta);
                });
                statusCallout.add(new Span(
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
     * The most recent {@code → REJECTED} transition in the history, if any — the
     * one whose reason the callout surfaces. A report can be rejected, resubmitted,
     * and rejected again, so the latest entry (history is oldest-first) wins.
     */
    private static java.util.Optional<StatusChangeDto> lastRejection(
            ReportDetailDto dto) {
        return dto.statusHistory().stream()
                .filter(change -> change.toStatus() == ReportStatus.REJECTED)
                .reduce((first, second) -> second);
    }

    /**
     * Renders the ordered status history (glossary: Status History) as an audit
     * trail — one entry per transition with its actor, time, and any comment —
     * visible to both the owner and the admin reviewer. Hidden until the first
     * transition is recorded (a fresh draft has none).
     */
    private void renderStatusHistory(List<StatusChangeDto> history) {
        statusHistory.removeAll();
        if (history.isEmpty()) {
            statusHistory.setVisible(false);
            return;
        }
        var heading = new Span("Status history");
        heading.addClassName("status-history-heading");
        statusHistory.add(heading);
        history.forEach(change -> statusHistory.add(statusHistoryEntry(change)));
        statusHistory.setVisible(true);
    }

    /** One status-history row: the transition label, actor and time, then any comment. */
    private static Component statusHistoryEntry(StatusChangeDto change) {
        var label = new Span(ReportViewSupport.statusLabel(change.toStatus()));
        label.addClassName("status-history-label");
        var meta = new Span("by " + change.actorName() + " · "
                + ReportViewSupport.formatTimestamp(change.changedAt()));
        meta.addClassName("muted-xs");
        var row = new VerticalLayout(label, meta);
        row.setPadding(false);
        row.setSpacing(false);
        row.addClassName("status-history-entry");
        if (change.comment() != null && !change.comment().isBlank()) {
            var comment = new Span(change.comment());
            comment.addClassName("status-history-comment");
            row.add(comment);
        }
        return row;
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

        var icon = LucideIcon.PLANE.create();
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
            var trash = new Button(LucideIcon.TRASH_2.create(), event -> {
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
            suppressedKinds(trip)
                    .forEach(kind -> generatedList.add(suppressedLineRow(entry, kind)));
        });

        var group = new VerticalLayout(card, generatedList);
        group.setPadding(false);
        group.setSpacing(false);
        group.setWidthFull();
        group.addClassName("travel-group");
        return group;
    }

    /**
     * The card preview for a line's effective receipt. A persisted receipt streams
     * from the DB by id; a buffered (not-yet-saved) upload streams from its
     * in-memory bytes so the thumbnail shows the moment the receipt is attached,
     * not only after the report is saved (issue #89). Only when neither is in hand
     * — a buffered attachment whose bytes aren't available here (e.g. a line
     * reopened from a not-yet-saved report) — does it fall back to a filename chip.
     */
    private Component receiptCardPreview(Long receiptId, String filename,
            String contentType, ReceiptUpload buffered) {
        if (receiptId != null) {
            Long id = receiptId;
            return ReceiptPreview.forReceipt(filename, contentType,
                    () -> service.receiptDownload(id));
        }
        if (buffered != null && buffered.data() != null) {
            byte[] data = buffered.data();
            return ReceiptPreview.forReceipt(filename, contentType,
                    () -> DownloadHandler.fromInputStream(event ->
                            new DownloadResponse(new ByteArrayInputStream(data),
                                    filename, contentType, data.length)).inline());
        }
        var chip = new Span("📎 " + filename);
        chip.addClassName("muted-xs");
        return chip;
    }

    /**
     * One read-only generated-line row nested under a trip: its label, computed
     * amount, and read-only explanation, plus the receipt it carries and (while
     * editable) an attach/edit-receipt affordance (Phase 4.3).
     *
     * <p>A line whose count the user corrected (glossary: Quantity Override,
     * ADR-0024) reads differently: an "Overridden" badge beside the label, the reason
     * given, and the statutory baseline the calculator produced, in place of the
     * composed comment — which restates all three and would only repeat the row. The
     * amount and the {@code qty × unit} breakdown are already the effective ones.
     */
    private Component generatedLineRow(ValueSignal<TravelDto> entry,
            GeneratedLineView line) {
        var name = new Span(ReportViewSupport.generatedLineLabel(line.kind()));
        name.addClassName("line-name");
        var heading = new HorizontalLayout(name);
        heading.setPadding(false);
        heading.setSpacing("var(--vaadin-gap-s)");
        heading.setAlignItems(FlexComponent.Alignment.CENTER);
        heading.addClassName("travel-line-heading");
        var texts = new VerticalLayout(heading);
        texts.setPadding(false);
        texts.setSpacing(false);
        if (line.isOverridden()) {
            // Text, never colour alone (ADR-0020) — the badge carries its own label.
            var badge = new Badge("Overridden");
            badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.WARNING);
            heading.add(badge);
            texts.add(mutedXs("Reason: " + line.overrideReason()));
            calculatedBaseline(line).ifPresent(baseline -> texts.add(mutedXs(baseline)));
        } else {
            texts.add(mutedXs(line.comment() == null ? "" : line.comment()));
        }

        var amount = new Span(formatEur(line.amount()));
        amount.addClassName("line-amount");
        var receipt = new Div();
        receipt.addClassName("line-receipt");
        if (line.hasReceipt()) {
            receipt.add(receiptCardPreview(line.receiptId(), line.receiptFilename(),
                    line.receiptContentType(),
                    pendingTravelReceipts.get(new TravelReceiptKey(entry, line.kind()))));
        }
        var amounts = new VerticalLayout(amount);
        // The km line is a multiple, so it reads "12.5 × €0.55 = €6.88" like a
        // multi-unit manual card; the flat kinds are quantity 1 and show nothing
        // extra (ADR-0023).
        if (line.showsQuantity()) {
            var quantity = new Span(quantityBreakdown(line.quantity(), line.unitPrice()));
            quantity.addClassName("muted-xs");
            amounts.add(quantity);
        }
        amounts.add(receipt);
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
                    LucideIcon.PAPERCLIP.create());
            attach.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            attach.addClickListener(event -> openTravelLineReceipt(entry, line));
            attach.getElement().setAttribute("aria-label",
                    (line.hasReceipt() ? "Edit receipt: " : "Add receipt: ")
                            + ReportViewSupport.generatedLineLabel(line.kind()));
            var actions = new HorizontalLayout(attach);
            actions.setPadding(false);
            actions.setSpacing("var(--vaadin-gap-xs)");
            actions.setAlignItems(FlexComponent.Alignment.CENTER);
            actions.addClassName("travel-line-actions");
            // Only the per-diem and meal kinds are correctable: the kilometre
            // distance and the parking fee are trip inputs with a single home, so
            // those numbers are changed on the trip (ADR-0024).
            if (line.kind().isOverridable()) {
                actions.add(overrideAction(entry, line));
                if (line.isOverridden()) {
                    actions.add(resetAction(entry, line.kind()));
                }
            }
            row.add(actions);
        }
        return row;
    }

    /**
     * The kinds this trip's owner dropped with a zero-count override (issue #132), in
     * declaration order. They own no generated line — the earned-line gate removed it —
     * so they are read off the trip's <em>overrides</em>, which is what makes them
     * survive a save and reload just as well as a live preview. A kind whose line the
     * trip stopped earning anyway is included too: the override is still in force, and
     * resetting it is still the way out.
     */
    private static List<GeneratedLineKind> suppressedKinds(TravelDto trip) {
        return trip.quantityOverrides().entrySet().stream()
                .filter(entry -> entry.getValue().isSuppression())
                .map(Map.Entry::getKey)
                .filter(kind -> trip.generatedLine(kind).isEmpty())
                .sorted()
                .toList();
    }

    /**
     * The placeholder a suppressed kind leaves behind: what was dropped, why, and — while
     * the report is editable — the way back. Without it a zero override would become
     * unreachable the instant it took effect, since the row it was made on is exactly
     * what it removed.
     *
     * <p>It shows no amount and no receipt affordance, because there is no line: it
     * contributes nothing to the per-diem subtotal or the report total, and nothing can
     * be attached to it. The only action is {@code Reset to calculated}, which restores
     * the line at its statutory count.
     */
    private Component suppressedLineRow(ValueSignal<TravelDto> entry,
            GeneratedLineKind kind) {
        var name = new Span(ReportViewSupport.generatedLineLabel(kind));
        name.addClassName("line-name");
        // Text, never colour alone (ADR-0020) — the badge carries its own label.
        var badge = new Badge("Removed");
        badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
        var heading = new HorizontalLayout(name, badge);
        heading.setPadding(false);
        heading.setSpacing("var(--vaadin-gap-s)");
        heading.setAlignItems(FlexComponent.Alignment.CENTER);
        heading.addClassName("travel-line-heading");

        var texts = new VerticalLayout(heading);
        texts.setPadding(false);
        texts.setSpacing(false);
        var override = entry.peek().quantityOverrides().get(kind);
        texts.add(mutedXs("Removed from the report. Reason: " + override.reason()));

        var body = new HorizontalLayout(texts);
        body.setWidthFull();
        body.setAlignItems(FlexComponent.Alignment.CENTER);
        body.expand(texts);

        var row = new HorizontalLayout(body);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.expand(body);
        row.addClassName("line-card");
        row.addClassName("travel-line-row");
        row.addClassName("travel-line-removed");
        if (editable) {
            var actions = new HorizontalLayout(resetAction(entry, kind));
            actions.setPadding(false);
            actions.setSpacing("var(--vaadin-gap-xs)");
            actions.setAlignItems(FlexComponent.Alignment.CENTER);
            actions.addClassName("travel-line-actions");
            row.add(actions);
        }
        return row;
    }

    /** Opens the Quantity Override editor for one generated line (ADR-0024). */
    private Button overrideAction(ValueSignal<TravelDto> entry, GeneratedLineView line) {
        var button = new Button(line.isOverridden() ? "Edit override" : "Override");
        button.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        button.addClickListener(event -> new GeneratedLineOverrideDialog(line,
                override -> applyOverride(entry, line.kind(), override)).open());
        button.getElement().setAttribute("aria-label",
                (line.isOverridden() ? "Edit override: " : "Override count: ")
                        + ReportViewSupport.generatedLineLabel(line.kind()));
        return button;
    }

    /**
     * Removes the kind's override outright, returning it to the statutory figure —
     * without touching the trip (ADR-0024, "Reset to calculated"). On a suppressed kind
     * this is what brings the line back, at its calculated count.
     */
    private Button resetAction(ValueSignal<TravelDto> entry, GeneratedLineKind kind) {
        var button = new Button("Reset to calculated");
        button.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        button.addClickListener(event -> applyOverride(entry, kind, null));
        button.getElement().setAttribute("aria-label", "Reset to calculated: "
                + ReportViewSupport.generatedLineLabel(kind));
        return button;
    }

    /**
     * Sets or clears one kind's Quantity Override on the working trip — asking first
     * when a count of {@code 0} would destroy a receipt (issue #132), then
     * {@linkplain #commitOverride committing}.
     */
    private void applyOverride(ValueSignal<TravelDto> entry, GeneratedLineKind kind,
            QuantityOverride override) {
        if (override != null && override.isSuppression()) {
            String doomed = attachedReceiptFilename(entry, kind);
            if (doomed != null) {
                confirmSuppression(entry, kind, override, doomed);
                return;
            }
        }
        commitOverride(entry, kind, override);
    }

    /**
     * Sets or clears one kind's Quantity Override on the working trip and re-costs it
     * <strong>server-side</strong> (the client never computes money), so the row's
     * amount, the per-diem subtotal and the report total follow immediately — before
     * the report is saved. Receipts already attached are carried across by kind, as
     * on the trip-edit path.
     *
     * <p>A suppressing override ({@code 0}) leaves no line for a buffered receipt to
     * be attached to, so that buffer entry is dropped — otherwise a stale
     * {@link GeneratedLineRef} would reach the service on save, naming a kind the
     * trip no longer generates.
     */
    private void commitOverride(ValueSignal<TravelDto> entry, GeneratedLineKind kind,
            QuantityOverride override) {
        clearErrors();
        var before = entry.peek();
        var corrected = override == null ? before.withoutQuantityOverride(kind)
                : before.withQuantityOverride(kind, override);
        try {
            var recosted = service.previewTravel(corrected);
            if (override != null && override.isSuppression()) {
                pendingTravelReceipts.remove(new TravelReceiptKey(entry, kind));
            }
            entry.set(mergeReceipts(before, recosted));
        } catch (RuntimeException ex) {
            surface(ex);
        }
    }

    /**
     * The name of the file a suppression would destroy, or {@code null} if the line
     * carries none. The line's own view is the authority: a buffered attachment shows
     * on it the moment it is uploaded, and a buffered <em>removal</em> clears it — so a
     * receipt already staged for deletion is correctly not worth warning about twice.
     */
    private static String attachedReceiptFilename(ValueSignal<TravelDto> entry,
            GeneratedLineKind kind) {
        return entry.peek().generatedLine(kind)
                .filter(GeneratedLineView::hasReceipt)
                .map(GeneratedLineView::receiptFilename)
                .orElse(null);
    }

    /**
     * Asks before a zero override destroys a receipt (ADR-0024). {@code V6__receipts.sql}
     * declares {@code expense_line_id ... on delete cascade} because
     * {@code ExpenseLine} holds no back-reference to its receipt, so dropping the line
     * takes the uploaded file with it — irrecoverably, and invisibly from anywhere
     * above the database. The dialog therefore <strong>names the file</strong> so the
     * user knows exactly what they are giving up; cancelling keeps both the line and
     * its receipt.
     */
    private void confirmSuppression(ValueSignal<TravelDto> entry,
            GeneratedLineKind kind, QuantityOverride override, String filename) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Remove " + kind.label() + "?");
        // Capped to a readable measure, like the override dialog: unconstrained, this
        // paragraph renders as one ~100-character line on a desktop viewport, which is
        // the worst possible shape for the one sentence the user must actually read.
        dialog.setWidth("30rem");
        dialog.setMaxWidth("100%");
        // The body names the line as well as the file, so it stands on its own — a
        // dialog title is not always what a screen reader reads out first.
        dialog.add(new Paragraph("A count of 0 removes the " + kind.label()
                + " line from the report. The receipt attached to it, " + filename
                + ", is deleted with the line and cannot be recovered."));

        var confirm = new Button("Remove line and receipt", event -> {
            dialog.close();
            commitOverride(entry, kind, override);
        });
        confirm.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    /** A muted extra line under a generated row's label. */
    private static Span mutedXs(String text) {
        var span = new Span(text);
        span.addClassName("muted-xs");
        return span;
    }

    /**
     * "Calculated: 3 × €54.00 = €162.00" — what the rules said, beside what the user
     * claimed. Empty when the baseline could not be recomputed for a loaded report
     * (the badge and reason still show).
     */
    private static java.util.Optional<String> calculatedBaseline(GeneratedLineView line) {
        if (line.calculatedQuantity() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of("Calculated: "
                + quantityBreakdown(line.calculatedQuantity(), line.unitPrice()));
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
        // (read-only) report can still view its receipt. A buffered (not-yet-saved)
        // attachment previews straight from its in-memory bytes, so the thumbnail
        // appears the moment the receipt is attached rather than only after the
        // report is saved (issue #89).
        var receipt = new Div();
        receipt.addClassName("line-receipt");
        Signal.effect(receipt, () -> {
            ExpenseLineDto dto = entry.get();
            receipt.removeAll();
            if (!dto.hasReceipt()) {
                return;
            }
            receipt.add(receiptCardPreview(dto.receiptId(), dto.receiptFilename(),
                    dto.receiptContentType(), pendingReceipts.get(entry)));
        });
        var texts = new VerticalLayout(name, subtitle, receipt);
        texts.setPadding(false);
        texts.setSpacing(false);

        var gross = new Span();
        gross.bindText(entry.map(dto -> formatEur(grossOf(dto))));
        gross.addClassName("line-amount");
        // qty × unit = gross, shown only for a multi-unit line (ADR-0023); a
        // quantity-1 card carries no extra row and reads exactly as before.
        var quantity = new Span();
        quantity.bindText(entry.map(ReportDetailView::quantityBreakdownOf));
        quantity.bindVisible(entry.map(ReportDetailView::showsQuantity));
        quantity.addClassName("muted-xs");
        var breakdown = new Span();
        breakdown.bindText(entry.map(ReportDetailView::breakdownOf));
        breakdown.addClassName("muted-xs");
        var amounts = new VerticalLayout(gross, quantity, breakdown);
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
            var trash = new Button(LucideIcon.TRASH_2.create(), event -> {
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
                .filter(dto -> dto.amount() != null && dto.quantity() != null
                        && dto.vatRatePercent() != null)
                .map(dto -> LineAmounts.ofLine(dto.amount(), dto.quantity(),
                        dto.vatRatePercent()))
                .reduce(LineAmounts.zero(), LineAmounts::add);
        // Each trip's VAT-bearing generated lines (parking) fold into Net/VAT too.
        return travels.get().stream().map(ValueSignal::get)
                .flatMap(t -> t.generatedLines().stream())
                .filter(line -> !line.isTaxFreeAllowance())
                .map(line -> LineAmounts.of(line.amount(), line.vatRatePercent()))
                .reduce(manual, LineAmounts::add);
    }

    /** Both per-diem lines share the subtotal — full days and the partial day (#124). */
    private BigDecimal currentPerDiem() {
        return sumKinds(GeneratedLineKind::isPerDiem);
    }

    private BigDecimal currentKilometre() {
        return sumKinds(kind -> kind == GeneratedLineKind.KILOMETRE);
    }

    private BigDecimal currentMeal() {
        return sumKinds(kind -> kind == GeneratedLineKind.MEAL);
    }

    /**
     * Sums one subtotal row's generated lines live across the working trips (Phase
     * 4.3) — the same derived {@code unit × quantity} gross the persisted lines
     * report, so the live figure and the saved one agree (ADR-0023).
     */
    private BigDecimal sumKinds(Predicate<GeneratedLineKind> matching) {
        return travels.get().stream().map(ValueSignal::get)
                .flatMap(t -> t.generatedLines().stream())
                .filter(line -> matching.test(line.kind()))
                .map(GeneratedLineView::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Grand total = VAT-bearing gross (Net + VAT) + the three tax-free allowances. */
    private BigDecimal currentGrandTotal() {
        return currentTotals().gross().add(currentPerDiem()).add(currentKilometre())
                .add(currentMeal());
    }

    private static BigDecimal grossOf(ExpenseLineDto dto) {
        return ReportViewSupport.lineGross(dto.amount(), dto.quantity());
    }

    /**
     * Whether the card shows the {@code qty × unit = gross} breakdown: only when
     * the quantity is not 1, so a plain single-item line reads exactly as it did
     * before quantity existed (ADR-0023).
     */
    private static boolean showsQuantity(ExpenseLineDto dto) {
        return dto.amount() != null && dto.quantity() != null
                && dto.quantity().compareTo(BigDecimal.ONE) != 0;
    }

    /** The {@code 3 × €12.50 = €37.50} line for a multi-unit card (ADR-0023). */
    private static String quantityBreakdownOf(ExpenseLineDto dto) {
        if (!showsQuantity(dto)) {
            return "";
        }
        return quantityBreakdown(dto.quantity(), dto.amount());
    }

    /**
     * The {@code qty × unit = gross} text a multi-unit card shows — shared by the
     * manual cards and the generated kilometre row so they read identically.
     */
    private static String quantityBreakdown(BigDecimal quantity, BigDecimal unitPrice) {
        return formatQuantity(quantity) + " × " + formatEur(unitPrice) + " = "
                + formatEur(ReportViewSupport.lineGross(unitPrice, quantity));
    }

    private static String subtitleOf(ExpenseLineDto dto) {
        if (dto.comment() != null && !dto.comment().isBlank()) {
            return dto.comment();
        }
        return dto.vatRatePercent() == null ? ""
                : "VAT " + formatPercent(dto.vatRatePercent());
    }

    private static String breakdownOf(ExpenseLineDto dto) {
        if (dto.amount() == null || dto.quantity() == null
                || dto.vatRatePercent() == null) {
            return "";
        }
        var totals = LineAmounts.ofLine(dto.amount(), dto.quantity(),
                dto.vatRatePercent());
        return "net " + formatEur(totals.net()) + " · VAT " + formatEur(totals.vat())
                + " (" + formatPercent(dto.vatRatePercent()) + ")";
    }

    private void clearErrors() {
        errorSummary.clear();
    }

    /**
     * Routes a failed action to its surface (issue #86): a user-actionable
     * {@link DomainRuleException} to the top-of-form summary, an optimistic-lock
     * conflict to the reload affordance (ADR-0011). Anything else is technical, so it
     * is re-thrown and left to the global
     * {@link com.vaadin.expensemanager.base.ui.UiErrorHandler}, which logs it and
     * shows the generic error dialog rather than leaking it into the summary. This is
     * the only form-local error routing the view needs — the technical rendering is
     * no longer hand-wired here.
     */
    private void surface(RuntimeException error) {
        if (error instanceof DomainRuleException) {
            errorSummary.show(error.getMessage());
        } else if (error instanceof ObjectOptimisticLockingFailureException) {
            showConflict();
        } else {
            throw error;
        }
    }

    /**
     * The optimistic-lock conflict UX (ADR-0011): a never-silent-overwrite
     * message plus a Reload affordance that re-fetches the latest committed
     * version into the form, so the owner can review it before acting again.
     */
    private void showConflict() {
        var detail = new Paragraph(
                "Reload to see the latest version before saving again.");
        var reload = new Button("Reload", event -> reload());
        reload.addThemeVariants(ButtonVariant.TERTIARY);
        errorSummary.showCustom("This report was changed elsewhere.", detail, reload);
    }

    /**
     * Re-fetches the persisted report, discarding the stale working copy. In
     * review mode it reloads through the non-owner-scoped review path so an admin
     * sees the latest committed version (ADR-0011), not the owner path.
     */
    private void reload() {
        if (working.isPersisted()) {
            load(reviewMode ? approvalService.findForReview(working.id())
                    : service.findMine(working.id()));
            Notification.show("Reloaded the latest version.");
        }
    }

}

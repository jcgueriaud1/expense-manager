package com.vaadin.expensemanager.report.service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.math.BigDecimal;

import com.vaadin.expensemanager.allowance.AllowanceAmount;
import com.vaadin.expensemanager.allowance.AllowanceCalculator;
import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.allowance.DomesticPerDiemDto;
import com.vaadin.expensemanager.allowance.DomesticPerDiemResult;
import com.vaadin.expensemanager.allowance.ForeignPerDiemDto;
import com.vaadin.expensemanager.allowance.ForeignPerDiemResult;
import com.vaadin.expensemanager.allowance.KilometreAllowance;
import com.vaadin.expensemanager.allowance.KilometreRateDto;
import com.vaadin.expensemanager.allowance.MealAllowanceDto;
import com.vaadin.expensemanager.report.domain.ExpenseLine;
import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.GeneratedLineSpec;
import com.vaadin.expensemanager.report.domain.Receipt;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.domain.ReceiptType;
import com.vaadin.expensemanager.report.domain.ReceiptValidator;
import com.vaadin.expensemanager.report.domain.Travel;
import com.vaadin.expensemanager.report.domain.TravelSpec;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.expensemanager.user.CurrentUser;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;

import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the {@link ExpenseReport} aggregate (ADR-0019,
 * ADR-0006) — thin by design: it owns the transaction boundary, entity↔DTO
 * mapping, and owner-scoping; the invariants (delete guard, edit guard) live in
 * the aggregate.
 *
 * <p><strong>Whole-aggregate save, in-memory until first save (ADR-0019).</strong>
 * {@link #create} INSERTs the whole aggregate on first save; {@link #update}
 * UPDATEs report-level fields and the line collection carrying the
 * {@code @Version} the UI last saw. Lines reconcile by their nullable id
 * (match/insert/orphan-remove) inside the aggregate; this service only resolves
 * the reference-data ids to entities — <strong>unfiltered</strong>, so a line
 * filed under a now-deactivated type/rate still saves (ADR-0018) — and owns the
 * transaction boundary.
 *
 * <p><strong>Owner scoping (ADR-0008, ADR-0016).</strong> Every read and write
 * is bound to the {@link CurrentUserProvider current user}: the list query and
 * every by-id lookup filter on {@code ownerId}, so a user can neither see nor
 * mutate another's report by guessing its sequential id. Method security is
 * {@code @RolesAllowed("USER")} — an admin reaches these through the
 * {@code ADMIN ⊇ USER} hierarchy for their own reports; the admin review of
 * <em>others'</em> reports is a separate Phase 5 surface.
 *
 * <p>Per ADR-0003 entities never escape this class — callers exchange
 * {@link ReportSummaryDto} / {@link ReportDetailDto} records.
 */
@Service
public class ExpenseReportService {

    // The expense types the four generated line kinds are filed under, resolved by
    // name (the accepted coupling — F-034). The three allowances are 0 %-VAT;
    // parking is filed under the VAT-bearing parking type at its own rate.
    private static final String PER_DIEM_EXPENSE_TYPE = "Travel allowance";
    private static final String KILOMETRE_EXPENSE_TYPE = "Kilometre allowance";
    private static final String MEAL_EXPENSE_TYPE = "Meal allowance";
    private static final String PARKING_EXPENSE_TYPE = "Parking/supplies/goods";
    private static final BigDecimal ZERO_VAT = BigDecimal.ZERO.setScale(2);

    private final ExpenseReportRepository reportRepository;
    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final VatRateRepository vatRateRepository;
    private final AllowanceRateService allowanceRateService;
    private final CurrentUserProvider currentUserProvider;
    private final ReportDtoMapper mapper;

    /** Pure, stateless per-diem maths (ADR-0006) — a plain instance, not a bean. */
    private final AllowanceCalculator calculator = new AllowanceCalculator();

    public ExpenseReportService(ExpenseReportRepository reportRepository,
            ReceiptRepository receiptRepository,
            UserRepository userRepository,
            ExpenseTypeRepository expenseTypeRepository,
            VatRateRepository vatRateRepository,
            AllowanceRateService allowanceRateService,
            CurrentUserProvider currentUserProvider,
            ReportDtoMapper mapper) {
        this.reportRepository = reportRepository;
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.expenseTypeRepository = expenseTypeRepository;
        this.vatRateRepository = vatRateRepository;
        this.allowanceRateService = allowanceRateService;
        this.currentUserProvider = currentUserProvider;
        this.mapper = mapper;
    }

    /** The current user's reports, newest report-date first (My Reports, UC-002). */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public List<ReportSummaryDto> listMine() {
        return reportRepository
                .findByOwnerIdOrderByReportDateDescIdDesc(currentUserId()).stream()
                .map(ReportDtoMapper::toSummary)
                .toList();
    }

    /**
     * Loads one of the current user's reports as a working copy.
     *
     * @throws IllegalArgumentException if no such report exists for this user
     *         (a missing id and someone else's id are indistinguishable by
     *         design — no information leak, ADR-0016)
     */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public ReportDetailDto findMine(Long id) {
        return mapper.toDetail(requireOwned(id));
    }

    /**
     * Previews the trip outputs for a trip's inputs without persisting anything
     * (Phase 4.2/4.3, the dialog preview). Amounts are
     * <strong>server-authoritative</strong>: the client sends inputs, this
     * recomputes every output (per-diem, kilometre, meal, parking) from the
     * trip-year rates and returns the trip with its generated-line breakdown filled
     * in. The per-diem is costed domestically for a Finnish trip or against the
     * destination country's rate for a foreign one (from {@link TravelDto#country()}).
     * Invalid input (return before departure, or no rate configured for a requested
     * output's trip year — including a foreign country with no rate for the year)
     * throws with a message the caller surfaces in the error summary (ADR-0020) —
     * never a silent Finnish default.
     *
     * @throws IllegalArgumentException if the inputs are invalid or a rate is missing
     */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public TravelDto previewTravel(TravelDto input) {
        var views = earnedLines(input, resolveGeneratedLineTypes()).stream()
                .map(ExpenseReportService::toView).toList();
        return input.withGeneratedLines(views);
    }

    /**
     * The destination countries that have a foreign per-diem rate configured for a
     * year, in country order (Phase 4.2 picker input). The dialog lists these
     * alongside Finland; a country absent here has no rate for the year, so picking
     * it would surface the missing-rate failure on save rather than silently
     * defaulting to the Finnish per-diem.
     */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public List<String> foreignDestinations(int year) {
        return allowanceRateService.foreignPerDiems(year).stream()
                .map(ForeignPerDiemDto::country).toList();
    }

    /**
     * Resolves one receipt's bytes for the read path (ADR-0021): empty unless the
     * receipt exists <em>and</em> the caller may see it. An ordinary user is
     * owner-scoped (ADR-0008) — only their own receipts; an admin, who reviews
     * any user's report, may fetch any receipt. The bytea is read here and
     * nowhere else — through the dedicated download projection, never the
     * aggregate load path — and copied into a detached {@link ReceiptContent} so
     * nothing lazy is touched while streaming.
     *
     * <p>This is the authorization + fetch seam behind {@link #receiptDownload};
     * exposed on its own so the owner-scoping contract is directly testable
     * (ADR-0012 layer 2) without driving an HTTP download.
     */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public Optional<ReceiptContent> receiptForDownload(Long receiptId) {
        return findDownloadFor(currentUserProvider.require(), receiptId)
                .map(ReceiptContent::from);
    }

    /**
     * A {@link DownloadHandler} that streams one receipt to the browser (ADR-0021,
     * read-path slice). The current user is captured <strong>now</strong>, on the
     * UI thread where the security context is present, and their id/role threaded
     * into the projection — so the later resource request resolves the receipt
     * without depending on the security context being populated on the download
     * thread (ADR-0008). An ordinary user is owner-scoped (only their own
     * receipts); an admin, who reviews any user's report, may stream any receipt.
     *
     * <p>Served <em>inline</em> (images render in an {@code <img>}, PDFs open in
     * the browser viewer) with the stored, magic-byte-verified content type and a
     * {@code Content-Disposition} filename; {@code X-Content-Type-Options: nosniff}
     * stops the browser second-guessing that type. A receipt that is missing or
     * that the caller may not see yields {@code 404} — never another user's bytes.
     */
    @RolesAllowed("USER")
    public DownloadHandler receiptDownload(Long receiptId) {
        CurrentUser user = currentUserProvider.require();
        return DownloadHandler.fromInputStream(event -> {
            Optional<ReceiptContent> content = findDownloadFor(user, receiptId)
                    .map(ReceiptContent::from);
            if (content.isEmpty()) {
                return DownloadResponse.error(404);
            }
            ReceiptContent receipt = content.get();
            event.getResponse().setHeader("X-Content-Type-Options", "nosniff");
            return new DownloadResponse(new ByteArrayInputStream(receipt.data()),
                    receipt.filename(), receipt.contentType(), receipt.data().length);
        }).inline();
    }

    /**
     * First save (ADR-0019): INSERTs a new {@code DRAFT} report owned by the
     * current user from the working copy's report-level fields. Returns the new
     * id so the detail view can route to {@code /report/{id}}.
     */
    @RolesAllowed("USER")
    @Transactional
    public Long create(ReportDetailDto dto) {
        return create(dto, Map.of());
    }

    /**
     * First save (ADR-0019) that also persists buffered receipt uploads
     * (ADR-0021). {@code receipts} maps a position in {@code dto.lines()} to the
     * receipt buffered against that line; the report is flushed first so its new
     * lines have ids, then each upload is validated and written.
     */
    @RolesAllowed("USER")
    @Transactional
    public Long create(ReportDetailDto dto, Map<Integer, ReceiptUpload> receipts) {
        return create(dto, receipts, Map.of());
    }

    /**
     * First save that also persists receipts buffered against a trip's generated
     * lines (Phase 4.3, ADR-0021). {@code travelReceipts} maps a
     * {@link GeneratedLineRef} — a (trip position, {@link
     * com.vaadin.expensemanager.report.domain.GeneratedLineKind}) pair — to an
     * attach or {@link ReceiptUpload#REMOVE}, applied against the generated line
     * once the aggregate is flushed and those lines have ids.
     */
    @RolesAllowed("USER")
    @Transactional
    public Long create(ReportDetailDto dto, Map<Integer, ReceiptUpload> receipts,
            Map<GeneratedLineRef, ReceiptUpload> travelReceipts) {
        User owner = userRepository.findById(currentUserId()).orElseThrow(
                () -> new IllegalStateException("Current user no longer exists"));
        var report = new ExpenseReport(owner, dto.reportDate(),
                dto.additionalInformation());
        report.reconcile(toSpecs(dto.lines()), toTravelSpecs(dto.travels()));
        // Persist then flush (not saveAndFlush → merge): the aggregate stays the
        // managed instance, so its new lines get ids and orphan-removals run
        // before receipts are applied against those lines.
        reportRepository.save(report);
        reportRepository.flush();
        applyReceipts(report, receipts);
        applyTravelReceipts(report, travelReceipts);
        return report.getId();
    }

    /**
     * Whole-aggregate UPDATE of report-level fields (ADR-0019), owner-scoped and
     * version-checked (ADR-0011). The domain enforces the edit guard.
     *
     * @param expectedVersion the {@code @Version} the UI last saw
     * @throws ObjectOptimisticLockingFailureException if the report changed
     *         underneath the editor
     */
    @RolesAllowed("USER")
    @Transactional
    public ReportDetailDto update(Long id, ReportDetailDto dto, long expectedVersion) {
        return update(id, dto, expectedVersion, Map.of());
    }

    /**
     * Whole-aggregate UPDATE that also persists buffered receipt mutations
     * (ADR-0021): {@code receipts} maps a position in {@code dto.lines()} to an
     * attach (overwrite) or {@link ReceiptUpload#REMOVE}. The aggregate is
     * flushed first (so reconciled/new lines have ids and orphan-removes run),
     * then each receipt is applied against its line.
     */
    @RolesAllowed("USER")
    @Transactional
    public ReportDetailDto update(Long id, ReportDetailDto dto, long expectedVersion,
            Map<Integer, ReceiptUpload> receipts) {
        return update(id, dto, expectedVersion, receipts, Map.of());
    }

    /**
     * Whole-aggregate UPDATE that also persists receipts buffered against a trip's
     * generated lines (Phase 4.3, ADR-0021): {@code travelReceipts} keys each
     * upload by its {@link GeneratedLineRef}, applied once the reconciled lines are
     * flushed and have ids.
     */
    @RolesAllowed("USER")
    @Transactional
    public ReportDetailDto update(Long id, ReportDetailDto dto, long expectedVersion,
            Map<Integer, ReceiptUpload> receipts,
            Map<GeneratedLineRef, ReceiptUpload> travelReceipts) {
        return mapper.toDetail(
                applyUpdate(id, dto, expectedVersion, receipts, travelReceipts));
    }

    /**
     * Saves the working copy <strong>and then submits it for approval</strong> in
     * one transaction (issue #81): the whole-aggregate UPDATE ({@link #applyUpdate})
     * persists the current edits, then the report moves to {@code SUBMITTED},
     * appending a {@link com.vaadin.expensemanager.report.domain.StatusChange}.
     *
     * <p>One path serves both UI actions — first submit ({@code DRAFT →
     * SUBMITTED}) and resubmit of a rejected report ({@code REJECTED → SUBMITTED},
     * Phase 5.5): the aggregate already knows its origin state, so this dispatches on
     * it and lets the domain guard the transition. Atomic by design — if the
     * transition's invariants fail (e.g. the report has no lines) the whole save
     * rolls back, so the editor never ends up half-saved.
     *
     * <p>Owner-scoped and version-checked exactly like {@link #update}/{@link
     * #submit}: a stale write surfaces as a conflict before anything is touched.
     *
     * @param expectedVersion the {@code @Version} the UI last saw
     * @throws ObjectOptimisticLockingFailureException if the report changed
     *         underneath the editor
     */
    @RolesAllowed("USER")
    @Transactional
    public ReportDetailDto saveAndSubmit(Long id, ReportDetailDto dto,
            long expectedVersion, Map<Integer, ReceiptUpload> receipts,
            Map<GeneratedLineRef, ReceiptUpload> travelReceipts) {
        var report = applyUpdate(id, dto, expectedVersion, receipts, travelReceipts);
        // First submit vs resubmit is a domain distinction on the origin state, not
        // two service operations — the aggregate picks and guards the transition.
        if (report.getStatus() == ReportStatus.REJECTED) {
            report.resubmit(report.getOwner(), Instant.now());
        } else {
            report.submit(report.getOwner(), Instant.now());
        }
        return mapper.toDetail(report);
    }

    /**
     * The shared whole-aggregate UPDATE used by {@link #update} and the
     * save-and-(re)submit paths (issue #81): version-checks, applies the report-level
     * fields and reconciles the line/trip collections, flushes so reconciled lines
     * have ids, then applies the buffered receipt mutations. Returns the managed
     * aggregate so a caller can chain a state transition in the same transaction.
     */
    private ExpenseReport applyUpdate(Long id, ReportDetailDto dto,
            long expectedVersion, Map<Integer, ReceiptUpload> receipts,
            Map<GeneratedLineRef, ReceiptUpload> travelReceipts) {
        var report = requireOwned(id);
        if (report.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(ExpenseReport.class, id);
        }
        report.updateDetails(dto.reportDate(), dto.additionalInformation());
        report.reconcile(toSpecs(dto.lines()), toTravelSpecs(dto.travels()));
        // The aggregate is already managed; flush (don't merge) so reconciled
        // new lines get ids and orphan-removals execute before receipts apply.
        reportRepository.flush();
        applyReceipts(report, receipts);
        applyTravelReceipts(report, travelReceipts);
        return report;
    }

    /**
     * Submits one of the current user's reports for approval (UC-003, ADR-0006):
     * {@code DRAFT → SUBMITTED}, appending the first {@link
     * com.vaadin.expensemanager.report.domain.StatusChange}. Owner-scoped and
     * version-checked (ADR-0011): the {@code @Version} the UI last saw is
     * verified before the transition so a stale submit surfaces as a conflict
     * rather than acting on an outdated view. The aggregate enforces the
     * "≥1 line, DRAFT-only" invariants.
     *
     * @param expectedVersion the {@code @Version} the UI last saw
     * @throws ObjectOptimisticLockingFailureException if the report changed
     *         underneath the editor
     */
    @RolesAllowed("USER")
    @Transactional
    public ReportDetailDto submit(Long id, long expectedVersion) {
        var report = requireOwned(id);
        if (report.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(ExpenseReport.class, id);
        }
        report.submit(report.getOwner(), Instant.now());
        return mapper.toDetail(report);
    }

    /**
     * Resubmits one of the current user's rejected reports for approval (Phase
     * 5.5, ADR-0006): {@code REJECTED → SUBMITTED}, appending a {@link
     * com.vaadin.expensemanager.report.domain.StatusChange} so it re-enters the
     * admin queue. Owner-scoped ({@code requireOwned}) and version-checked
     * (ADR-0011) exactly like {@link #submit}: a different user — <strong>including
     * an admin</strong> — cannot resubmit another owner's report through this
     * method, and a stale resubmit surfaces as a conflict rather than acting on an
     * outdated view. The aggregate enforces the "≥1 line, REJECTED-only" invariants.
     *
     * @param expectedVersion the {@code @Version} the UI last saw
     * @throws ObjectOptimisticLockingFailureException if the report changed
     *         underneath the editor
     */
    @RolesAllowed("USER")
    @Transactional
    public ReportDetailDto resubmit(Long id, long expectedVersion) {
        var report = requireOwned(id);
        if (report.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(ExpenseReport.class, id);
        }
        report.resubmit(report.getOwner(), Instant.now());
        return mapper.toDetail(report);
    }

    /**
     * Deletes one of the current user's reports. The aggregate rejects the
     * delete unless it is a {@code DRAFT} (ADR-0006); once submit exists (Phase
     * 2.4) this guard is exercised end-to-end.
     */
    @RolesAllowed("USER")
    @Transactional
    public void delete(Long id) {
        var report = requireOwned(id);
        report.assertDeletable();
        reportRepository.delete(report);
    }

    // --------------------------------------------------------------- internals

    /**
     * Applies buffered receipt mutations to the just-flushed aggregate (ADR-0021).
     * The bytes are re-validated server-side and the stored {@code content_type}
     * is the <strong>sniffed</strong> signature, never the browser's claim — so a
     * caller cannot bypass magic-byte validation. Attach overwrites any existing
     * receipt (no history); {@link ReceiptUpload#REMOVE} clears it.
     */
    private void applyReceipts(ExpenseReport report,
            Map<Integer, ReceiptUpload> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return;
        }
        // Receipts map to positions in dto.lines() — the MANUAL lines. The trip's
        // generated lines are addressed separately, by GeneratedLineRef.
        List<ExpenseLine> lines = report.manualLines();
        receipts.forEach((index, upload) -> {
            if (index == null || index < 0 || index >= lines.size()) {
                throw new IllegalArgumentException("No line at index " + index);
            }
            applyReceiptToLine(lines.get(index), upload);
        });
    }

    /**
     * Applies buffered receipt mutations to a trip's generated lines (Phase 4.3,
     * ADR-0021). Each {@link GeneratedLineRef} names a trip by its position in
     * {@code dto.travels()} — the same order the aggregate reconciled them into
     * {@link ExpenseReport#getTravels()} — and a {@link
     * com.vaadin.expensemanager.report.domain.GeneratedLineKind}. A ref whose kind
     * the trip no longer earns (e.g. kilometres cleared before the save) is
     * silently dropped: there is no line to attach to, and the aggregate already
     * removed any prior one (cascading its receipt).
     */
    private void applyTravelReceipts(ExpenseReport report,
            Map<GeneratedLineRef, ReceiptUpload> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return;
        }
        List<Travel> travels = report.getTravels();
        receipts.forEach((ref, upload) -> {
            if (ref.travelIndex() < 0 || ref.travelIndex() >= travels.size()) {
                throw new IllegalArgumentException(
                        "No travel at index " + ref.travelIndex());
            }
            report.generatedLineFor(travels.get(ref.travelIndex()), ref.kind())
                    .ifPresent(line -> applyReceiptToLine(line, upload));
        });
    }

    /** Attaches/replaces or removes one line's receipt, re-sniffing the bytes. */
    private void applyReceiptToLine(ExpenseLine line, ReceiptUpload upload) {
        Long lineId = line.getId();
        if (upload.isRemoval()) {
            receiptRepository.findByExpenseLineId(lineId)
                    .ifPresent(receiptRepository::delete);
            return;
        }
        ReceiptType type = ReceiptValidator.validate(upload.data());
        receiptRepository.findByExpenseLineId(lineId).ifPresentOrElse(
                existing -> existing.replace(upload.data(), upload.filename(),
                        type.contentType()),
                () -> receiptRepository.save(new Receipt(line, upload.data(),
                        upload.filename(), type.contentType())));
    }

    private ExpenseReport requireOwned(Long id) {
        return reportRepository.findByIdAndOwnerId(id, currentUserId()).orElseThrow(
                () -> new IllegalArgumentException("No report with id " + id));
    }

    private Long currentUserId() {
        return currentUserProvider.require().id();
    }

    /**
     * The download projection for one receipt, scoped to what {@code user} may
     * see: unscoped for an admin (reviews any report), owner-scoped for an
     * ordinary user (ADR-0008). Empty when the receipt is missing or off-limits.
     */
    private Optional<ReceiptDownloadView> findDownloadFor(CurrentUser user, Long receiptId) {
        return user.isAdmin()
                ? receiptRepository.findDownloadById(receiptId)
                : receiptRepository.findDownloadByIdAndOwnerId(receiptId, user.id());
    }

    /**
     * Resolves each incoming line DTO's reference-data ids to managed entities
     * (unfiltered — a now-inactive historical type/rate must still resolve,
     * ADR-0018) and pairs them with the nullable line id for reconciliation.
     */
    private List<ExpenseLineSpec> toSpecs(List<ExpenseLineDto> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream().map(this::toSpec).toList();
    }

    private ExpenseLineSpec toSpec(ExpenseLineDto dto) {
        return new ExpenseLineSpec(dto.id(), requireType(dto.expenseTypeId()),
                dto.amount(), dto.quantity(), requireRate(dto.vatRateId()),
                dto.comment());
    }

    private ExpenseType requireType(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Expense type is required");
        }
        return expenseTypeRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("No expense type with id " + id));
    }

    private VatRate requireRate(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("VAT rate is required");
        }
        return vatRateRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("No VAT rate with id " + id));
    }

    /**
     * Resolves each incoming trip DTO to a {@link TravelSpec}, recomputing every
     * output server-side (the client never sends money) and resolving the generated
     * lines' reference data once. Empty when there are no trips — so a report
     * without trips never touches the allowance reference lookups.
     */
    private List<TravelSpec> toTravelSpecs(List<TravelDto> travels) {
        if (travels == null || travels.isEmpty()) {
            return List.of();
        }
        GeneratedLineTypes types = resolveGeneratedLineTypes();
        return travels.stream().map(t -> toTravelSpec(t, types)).toList();
    }

    private TravelSpec toTravelSpec(TravelDto t, GeneratedLineTypes types) {
        String country = (t.country() == null || t.country().isBlank())
                ? TravelDto.DOMESTIC_COUNTRY : t.country();
        return new TravelSpec(t.id(), t.departureAt(), t.returnAt(), t.destinations(),
                t.purpose(), country, t.notEligibleForAllowance(), t.freeLunch(),
                t.chargeToCustomer(), t.kilometres(), t.payMealAllowance(),
                t.parkingFees(), earnedLines(t, types));
    }

    /**
     * The generated lines a trip earns (ADR-0006): one {@link GeneratedLineSpec}
     * per non-zero output — per-diem, kilometre, meal, parking — recomputed
     * server-side from the trip-year rates (the client never sends money). A kind
     * that produced nothing is omitted, so the aggregate removes any prior line of
     * it. A missing rate for a requested output is surfaced (ADR-0020).
     */
    private List<GeneratedLineSpec> earnedLines(TravelDto t, GeneratedLineTypes types) {
        if (t.departureAt() == null || t.returnAt() == null) {
            throw new IllegalArgumentException(
                    "Departure and return date & time are required");
        }
        List<GeneratedLineSpec> lines = new ArrayList<>(4);
        // The destination country decides how the per-diem is costed: a Finnish trip
        // against the domestic full/partial rate, a foreign one against the country's
        // flat per-year rate (never a silent Finnish default). Both file the line
        // under the same tax-free PER_DIEM kind (ADR-0006).
        BigDecimal perDiemAmount;
        String perDiemComment;
        if (isForeign(t.country())) {
            ForeignPerDiemResult foreign = costForeign(t);
            perDiemAmount = foreign.amount();
            perDiemComment = foreign.explanation();
        } else {
            DomesticPerDiemResult perDiem = costDomestic(t);
            perDiemAmount = perDiem.amount();
            perDiemComment = perDiem.explanation();
        }
        addFlatSpec(lines, GeneratedLineKind.PER_DIEM, types.perDiemType(),
                types.zeroVat(), perDiemAmount, perDiemComment);
        // The one genuine multiple (ADR-0023): the line carries the distance as its
        // quantity and the year's €/km rate as its unit price, so its card reads
        // "12.5 × €0.55 = €6.88" and the euros are unchanged.
        KilometreAllowance km = costKilometre(t);
        addSpec(lines, GeneratedLineKind.KILOMETRE, types.kilometreType(),
                types.zeroVat(), km.ratePerKm(), km.kilometres(), km.explanation());
        AllowanceAmount meal = costMeal(t);
        addFlatSpec(lines, GeneratedLineKind.MEAL, types.mealType(), types.zeroVat(),
                meal.amount(), meal.explanation());
        AllowanceAmount parking = calculator.parking(t.parkingFees());
        addFlatSpec(lines, GeneratedLineKind.PARKING, types.parkingType(),
                types.parkingType().getDefaultVatRate(), parking.amount(),
                parking.explanation());
        return lines;
    }

    /** Adds a unit-price × quantity generated line, if the rule earned one. */
    private static void addSpec(List<GeneratedLineSpec> into, GeneratedLineKind kind,
            ExpenseType type, VatRate rate, BigDecimal unitPrice, BigDecimal quantity,
            String comment) {
        var spec = new GeneratedLineSpec(kind, type, rate, unitPrice, quantity, comment);
        if (spec.isEarned()) {
            into.add(spec);
        }
    }

    /** Adds a flat (quantity-1) generated line — per-diem, meal, parking (ADR-0023). */
    private static void addFlatSpec(List<GeneratedLineSpec> into, GeneratedLineKind kind,
            ExpenseType type, VatRate rate, BigDecimal amount, String comment) {
        addSpec(into, kind, type, rate, amount, BigDecimal.ONE, comment);
    }

    /** A preview view of an earned generated line — no id or receipt yet. */
    private static GeneratedLineView toView(GeneratedLineSpec spec) {
        return GeneratedLineView.of(spec.kind(), spec.expenseType().getName(),
                spec.unitPrice(), spec.quantity(), spec.vatRate().getValue(),
                spec.comment(), null);
    }

    /** The resolved reference data the generated lines are filed under, per save. */
    private record GeneratedLineTypes(ExpenseType perDiemType, ExpenseType kilometreType,
            ExpenseType mealType, ExpenseType parkingType, VatRate zeroVat) {
    }

    private GeneratedLineTypes resolveGeneratedLineTypes() {
        return new GeneratedLineTypes(
                allowanceExpenseType(PER_DIEM_EXPENSE_TYPE),
                allowanceExpenseType(KILOMETRE_EXPENSE_TYPE),
                allowanceExpenseType(MEAL_EXPENSE_TYPE),
                allowanceExpenseType(PARKING_EXPENSE_TYPE),
                zeroVatRate());
    }

    /** Server-authoritative domestic per-diem for a trip's inputs (ADR-0006). */
    private DomesticPerDiemResult costDomestic(TravelDto t) {
        int year = t.departureAt().getYear();
        DomesticPerDiemDto rate = allowanceRateService.domesticPerDiem(year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No domestic per-diem rate is configured for " + year));
        return calculator.domesticPerDiem(t.departureAt(), t.returnAt(),
                t.notEligibleForAllowance(), t.freeLunch(), rate);
    }

    /**
     * Server-authoritative foreign per-diem for a trip's inputs (ADR-0006): the
     * destination country's flat per-year rate × the allowance-day count. A country
     * with no rate for the trip year surfaces a clear failure — never a silent
     * Finnish default (the "do better than ProCountor" payoff). The day-count
     * thresholds come from the year's domestic rate (the statutory 10 h / 6 h
     * thresholds shared by every per-diem).
     */
    private ForeignPerDiemResult costForeign(TravelDto t) {
        int year = t.departureAt().getYear();
        ForeignPerDiemDto rate = allowanceRateService.foreignPerDiem(year, t.country())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No foreign per-diem rate is configured for " + t.country()
                                + " in " + year));
        DomesticPerDiemDto thresholds = allowanceRateService.domesticPerDiem(year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No per-diem rate is configured for " + year));
        return calculator.foreignPerDiem(t.departureAt(), t.returnAt(),
                t.notEligibleForAllowance(), rate, thresholds.fullDayMinHours(),
                thresholds.partialDayMinHours());
    }

    /** Whether a trip's country is a foreign destination (not domestic Finland). */
    private static boolean isForeign(String country) {
        return country != null && !country.isBlank()
                && !country.strip().equalsIgnoreCase(TravelDto.DOMESTIC_COUNTRY);
    }

    /** Server-authoritative kilometre allowance; needs a rate only when km &gt; 0. */
    private KilometreAllowance costKilometre(TravelDto t) {
        BigDecimal km = t.kilometres();
        if (km == null || km.signum() <= 0) {
            return calculator.kilometreAllowance(km, null);
        }
        int year = t.departureAt().getYear();
        KilometreRateDto rate = allowanceRateService.kilometreRate(year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No kilometre rate is configured for " + year));
        return calculator.kilometreAllowance(km, rate);
    }

    /**
     * Server-authoritative meal allowance; paid only when the trip both requests it
     * and is not eligible for a per-diem (the two are mutually exclusive, issue #93),
     * so the rate is looked up only when a meal allowance is actually payable.
     */
    private AllowanceAmount costMeal(TravelDto t) {
        boolean payable = t.payMealAllowance() && t.notEligibleForAllowance();
        if (!payable) {
            return calculator.mealAllowance(t.payMealAllowance(),
                    t.notEligibleForAllowance(), null);
        }
        int year = t.departureAt().getYear();
        MealAllowanceDto rate = allowanceRateService.mealAllowance(year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No meal allowance is configured for " + year));
        return calculator.mealAllowance(true, true, rate);
    }

    private ExpenseType allowanceExpenseType(String name) {
        return expenseTypeRepository
                .findFirstByNameIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(name)
                .orElseThrow(() -> new IllegalStateException("No active '" + name
                        + "' expense type is configured for generated travel lines"));
    }

    private VatRate zeroVatRate() {
        return vatRateRepository
                .findFirstByValueAndActiveTrueOrderByDisplayOrderAscIdAsc(ZERO_VAT)
                .orElseThrow(() -> new IllegalStateException(
                        "No active 0% VAT rate is configured for allowance lines"));
    }

}

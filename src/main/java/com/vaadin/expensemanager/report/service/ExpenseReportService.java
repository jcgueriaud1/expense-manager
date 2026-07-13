package com.vaadin.expensemanager.report.service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vaadin.expensemanager.report.domain.ExpenseLine;
import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.Receipt;
import com.vaadin.expensemanager.report.domain.ReceiptType;
import com.vaadin.expensemanager.report.domain.ReceiptValidator;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.security.CurrentUserProvider;
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

    private final ExpenseReportRepository reportRepository;
    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final VatRateRepository vatRateRepository;
    private final CurrentUserProvider currentUserProvider;

    public ExpenseReportService(ExpenseReportRepository reportRepository,
            ReceiptRepository receiptRepository,
            UserRepository userRepository,
            ExpenseTypeRepository expenseTypeRepository,
            VatRateRepository vatRateRepository,
            CurrentUserProvider currentUserProvider) {
        this.reportRepository = reportRepository;
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.expenseTypeRepository = expenseTypeRepository;
        this.vatRateRepository = vatRateRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /** The current user's reports, newest report-date first (My Reports, UC-002). */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public List<ReportSummaryDto> listMine() {
        return reportRepository
                .findByOwnerIdOrderByReportDateDescIdDesc(currentUserId()).stream()
                .map(ExpenseReportService::toSummary)
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
        return toDetail(requireOwned(id));
    }

    /**
     * Resolves one receipt's bytes for the read path (ADR-0021), owner-scoped
     * (ADR-0008): empty unless the receipt exists <em>and</em> belongs to the
     * current user. The bytea is read here and nowhere else — through the
     * dedicated download projection, never the aggregate load path — and copied
     * into a detached {@link ReceiptContent} so nothing lazy is touched while
     * streaming.
     *
     * <p>This is the authorization + fetch seam behind {@link #receiptDownload};
     * exposed on its own so the owner-scoping contract is directly testable
     * (ADR-0012 layer 2) without driving an HTTP download.
     */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public Optional<ReceiptContent> receiptForDownload(Long receiptId) {
        return receiptRepository.findDownloadByIdAndOwnerId(receiptId, currentUserId())
                .map(ReceiptContent::from);
    }

    /**
     * A {@link DownloadHandler} that streams one receipt to the browser (ADR-0021,
     * read-path slice). The current user's id is captured <strong>now</strong>, on
     * the UI thread where the security context is present, and threaded into the
     * owner-scoped projection — so the later resource request streams only a
     * receipt this user owns, without depending on the security context being
     * populated on the download thread (ADR-0008).
     *
     * <p>Served <em>inline</em> (images render in an {@code <img>}, PDFs open in
     * the browser viewer) with the stored, magic-byte-verified content type and a
     * {@code Content-Disposition} filename; {@code X-Content-Type-Options: nosniff}
     * stops the browser second-guessing that type. A receipt that is missing or
     * not the caller's yields {@code 404} — never another user's bytes.
     */
    @RolesAllowed("USER")
    public DownloadHandler receiptDownload(Long receiptId) {
        Long ownerId = currentUserId();
        return DownloadHandler.fromInputStream(event -> {
            Optional<ReceiptContent> content = receiptRepository
                    .findDownloadByIdAndOwnerId(receiptId, ownerId)
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
        User owner = userRepository.findById(currentUserId()).orElseThrow(
                () -> new IllegalStateException("Current user no longer exists"));
        var report = new ExpenseReport(owner, dto.reportDate(),
                dto.additionalInformation());
        report.reconcileLines(toSpecs(dto.lines()));
        // Persist then flush (not saveAndFlush → merge): the aggregate stays the
        // managed instance, so its new lines get ids and orphan-removals run
        // before receipts are applied against those lines.
        reportRepository.save(report);
        reportRepository.flush();
        applyReceipts(report, receipts);
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
        var report = requireOwned(id);
        if (report.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(ExpenseReport.class, id);
        }
        report.updateDetails(dto.reportDate(), dto.additionalInformation());
        report.reconcileLines(toSpecs(dto.lines()));
        // The aggregate is already managed; flush (don't merge) so reconciled
        // new lines get ids and orphan-removals execute before receipts apply.
        reportRepository.flush();
        applyReceipts(report, receipts);
        return toDetail(report);
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
        return toDetail(report);
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
        List<ExpenseLine> lines = report.getLines();
        receipts.forEach((index, upload) -> {
            if (index == null || index < 0 || index >= lines.size()) {
                throw new IllegalArgumentException("No line at index " + index);
            }
            Long lineId = lines.get(index).getId();
            if (upload.isRemoval()) {
                receiptRepository.findByExpenseLineId(lineId)
                        .ifPresent(receiptRepository::delete);
                return;
            }
            ReceiptType type = ReceiptValidator.validate(upload.data());
            receiptRepository.findByExpenseLineId(lineId).ifPresentOrElse(
                    existing -> existing.replace(upload.data(), upload.filename(),
                            type.contentType()),
                    () -> receiptRepository.save(new Receipt(lines.get(index),
                            upload.data(), upload.filename(), type.contentType())));
        });
    }

    private ExpenseReport requireOwned(Long id) {
        return reportRepository.findByIdAndOwnerId(id, currentUserId()).orElseThrow(
                () -> new IllegalArgumentException("No report with id " + id));
    }

    private Long currentUserId() {
        return currentUserProvider.require().id();
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
                dto.amount(), requireRate(dto.vatRateId()), dto.comment());
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

    private static ReportSummaryDto toSummary(ExpenseReport r) {
        return new ReportSummaryDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.total());
    }

    private ReportDetailDto toDetail(ExpenseReport r) {
        var lines = r.getLines();
        var lineIds = lines.stream().map(ExpenseLine::getId)
                .filter(Objects::nonNull).toList();
        // One blob-free projection query for the whole report's receipts — the
        // bytea never rides this (or any) aggregate load (ADR-0021).
        Map<Long, ReceiptSummaryView> byLine = lineIds.isEmpty() ? Map.of()
                : receiptRepository.findSummariesByExpenseLineIdIn(lineIds).stream()
                        .collect(Collectors.toMap(ReceiptSummaryView::getExpenseLineId,
                                Function.identity()));
        var lineDtos = lines.stream()
                .map(line -> toLineDto(line, byLine.get(line.getId()))).toList();
        return new ReportDetailDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.getVersion(), lineDtos,
                r.total(), r.netTotal(), r.vatTotal());
    }

    private static ExpenseLineDto toLineDto(ExpenseLine line,
            ReceiptSummaryView receipt) {
        var type = line.getExpenseType();
        var rate = line.getVatRate();
        var dto = ExpenseLineDto.of(line.getId(), type.getId(), type.getName(),
                rate.getId(), rate.getValue(), line.getAmount(), line.getComment());
        if (receipt == null) {
            return dto;
        }
        return dto.withReceipt(receipt.getId(), receipt.getFilename(),
                receipt.getContentType(), receipt.getSizeBytes());
    }
}

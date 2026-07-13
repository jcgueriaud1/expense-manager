package com.vaadin.expensemanager.report.service;

import java.util.List;

import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.report.domain.ExpenseLine;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.LineInput;
import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;

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
 * UPDATEs report-level fields carrying the {@code @Version} the UI last saw. The
 * line collection and its reconciliation arrive in Phase 2.3.
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
    private final UserRepository userRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final VatRateRepository vatRateRepository;
    private final CurrentUserProvider currentUserProvider;

    public ExpenseReportService(ExpenseReportRepository reportRepository,
            UserRepository userRepository,
            ExpenseTypeRepository expenseTypeRepository,
            VatRateRepository vatRateRepository,
            CurrentUserProvider currentUserProvider) {
        this.reportRepository = reportRepository;
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
     * First save (ADR-0019): INSERTs a new {@code DRAFT} report owned by the
     * current user from the working copy's report-level fields and lines. Returns
     * the new id so the detail view can route to {@code /report/{id}}.
     */
    @RolesAllowed("USER")
    @Transactional
    public Long create(ReportDetailDto dto) {
        User owner = userRepository.findById(currentUserId()).orElseThrow(
                () -> new IllegalStateException("Current user no longer exists"));
        var report = new ExpenseReport(owner, dto.reportDate(),
                dto.additionalInformation());
        report.replaceLines(toLineInputs(dto));
        return reportRepository.save(report).getId();
    }

    /**
     * Whole-aggregate UPDATE (ADR-0019), owner-scoped and version-checked
     * (ADR-0011). Updates the report-level fields and reconciles the line
     * collection by nullable id (insert new / update existing / orphan-remove
     * dropped); the domain enforces the edit guard and the line invariants.
     *
     * @param expectedVersion the {@code @Version} the UI last saw
     * @throws ObjectOptimisticLockingFailureException if the report changed
     *         underneath the editor
     */
    @RolesAllowed("USER")
    @Transactional
    public ReportDetailDto update(Long id, ReportDetailDto dto, long expectedVersion) {
        var report = requireOwned(id);
        if (report.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(ExpenseReport.class, id);
        }
        report.updateDetails(dto.reportDate(), dto.additionalInformation());
        report.replaceLines(toLineInputs(dto));
        // Flush so newly-inserted lines get their ids (and dropped ones are
        // deleted) before we map the returned working copy (ADR-0019).
        reportRepository.flush();
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

    private ExpenseReport requireOwned(Long id) {
        return reportRepository.findByIdAndOwnerId(id, currentUserId()).orElseThrow(
                () -> new IllegalArgumentException("No report with id " + id));
    }

    private Long currentUserId() {
        return currentUserProvider.require().id();
    }

    /**
     * Resolves each line DTO's reference ids to managed entities for the
     * aggregate. Resolution is by id (not by the active-options query), so a
     * historical line keeps a now-deactivated type/rate; a missing reference is
     * rejected. The aggregate enforces the money/type invariants.
     */
    private List<LineInput> toLineInputs(ReportDetailDto dto) {
        var lines = dto.lines();
        if (lines == null) {
            return List.of();
        }
        return lines.stream().map(line -> new LineInput(line.id(),
                requireExpenseType(line.expenseType() == null ? null
                        : line.expenseType().id()),
                line.amount(),
                requireVatRate(line.vatRate() == null ? null : line.vatRate().id()),
                line.comment())).toList();
    }

    private ExpenseType requireExpenseType(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Expense type is required");
        }
        return expenseTypeRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("No expense type with id " + id));
    }

    private VatRate requireVatRate(Long id) {
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

    private static ReportDetailDto toDetail(ExpenseReport r) {
        var lines = r.getLines().stream()
                .map(ExpenseReportService::toLineDto)
                .toList();
        return new ReportDetailDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.getVersion(),
                r.total(), lines);
    }

    private static ExpenseLineDto toLineDto(ExpenseLine line) {
        return new ExpenseLineDto(line.getId(), toDto(line.getExpenseType()),
                line.getAmount(), toDto(line.getVatRate()), line.getComment());
    }

    private static ExpenseTypeDto toDto(ExpenseType t) {
        var rate = t.getDefaultVatRate();
        return new ExpenseTypeDto(t.getId(), t.getName(), t.getDisplayOrder(),
                t.isActive(), rate.getId(), rate.getValue());
    }

    private static VatRateDto toDto(VatRate r) {
        return new VatRateDto(r.getId(), r.getValue(), r.getDisplayOrder(), r.isActive());
    }
}

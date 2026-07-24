package com.vaadin.expensemanager.approval.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.domain.StatusChange;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.expensemanager.report.service.ReportDtoMapper;
import com.vaadin.expensemanager.security.CurrentUserProvider;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the admin approval flow (Phase 5, ADR-0006, ADR-0019).
 *
 * <p>The counterpart to {@link ExpenseReportRepository}'s owner-scoped
 * {@code ExpenseReportService}: every method here is {@code @RolesAllowed("ADMIN")}
 * and <strong>deliberately bypasses owner-scoping</strong> — an admin reviews
 * <em>everyone's</em> submitted reports (ADR-0008). Authorization is the method
 * boundary; the invariants (which transitions are legal) live in the
 * {@link ExpenseReport} aggregate (ADR-0006), and entity↔DTO mapping is shared
 * with the owner path via {@link ReportDtoMapper} (ADR-0003).
 *
 * <p>The approval carries the aggregate {@code @Version} the UI last saw and
 * checks it before the transition (ADR-0011): a stale approve surfaces as an
 * {@link ObjectOptimisticLockingFailureException} the review UX turns into a
 * "reload", never a silent double-processing.
 */
@Service
public class ApprovalService {

    private final ExpenseReportRepository reportRepository;
    private final ReportDtoMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public ApprovalService(ExpenseReportRepository reportRepository,
            ReportDtoMapper mapper, CurrentUserProvider currentUserProvider,
            UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    /**
     * Every {@code SUBMITTED} report awaiting review, across all owners, newest
     * submission first — the approval queue (ADR-0008, non-owner-scoped). Each row
     * carries the submitter's display name and submitted-at so the queue reads
     * without a second lookup.
     */
    @RolesAllowed("ADMIN")
    @Transactional(readOnly = true)
    public List<ReviewSummaryDto> listSubmitted() {
        return reportRepository.findByStatusOrderByIdDesc(ReportStatus.SUBMITTED)
                .stream()
                .map(ApprovalService::toReviewSummary)
                .sorted(Comparator.comparing(ReviewSummaryDto::submittedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Every report in a terminal status ({@code APPROVED} or {@code REJECTED})
     * across all owners, newest <em>decision</em> first — the admin review history
     * (Issue #110, ADR-0008, non-owner-scoped). Where {@link #listSubmitted} keys on
     * submitted-at, this keys on the decision: each row carries the deciding admin
     * and decided-at (the {@code → APPROVED} / {@code → REJECTED} transition), plus
     * the rejection comment when the outcome was a rejection.
     */
    @RolesAllowed("ADMIN")
    @Transactional(readOnly = true)
    public List<ReviewedSummaryDto> listReviewed() {
        return reportRepository
                .findByStatusInOrderByIdDesc(
                        List.of(ReportStatus.APPROVED, ReportStatus.REJECTED))
                .stream()
                .map(ApprovalService::toReviewedSummary)
                .sorted(Comparator.comparing(ReviewedSummaryDto::decidedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Loads any report as a detail working copy for read-only review —
     * <strong>not</strong> owner-scoped (ADR-0008), so an admin can open another
     * user's report. The same {@link ReportDetailDto} the owner path uses, carrying
     * the status history for the audit trail.
     *
     * @throws IllegalArgumentException if no report has that id
     */
    @RolesAllowed("ADMIN")
    @Transactional(readOnly = true)
    public ReportDetailDto findForReview(Long id) {
        return mapper.toDetail(require(id));
    }

    /**
     * Approves a submitted report (glossary: Approve): {@code SUBMITTED →
     * APPROVED}, recording a status change whose actor is the reviewing admin
     * (ADR-0006). Not owner-scoped; version-checked before the transition
     * (ADR-0011). The aggregate rejects approving a non-{@code SUBMITTED} report.
     *
     * @param expectedVersion the {@code @Version} the review last saw
     * @throws ObjectOptimisticLockingFailureException if the report changed
     *         underneath the reviewer
     * @throws IllegalArgumentException if no report has that id
     */
    @RolesAllowed("ADMIN")
    @Transactional
    public ReportDetailDto approve(Long id, long expectedVersion) {
        var report = require(id);
        if (report.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(ExpenseReport.class, id);
        }
        report.approve(currentAdmin(), Instant.now());
        return mapper.toDetail(report);
    }

    /**
     * Rejects a submitted report with a mandatory reason (glossary: Reject):
     * {@code SUBMITTED → REJECTED}, recording a status change whose actor is the
     * reviewing admin and whose comment carries the reason (ADR-0006). Not
     * owner-scoped; version-checked before the transition (ADR-0011). The
     * aggregate rejects a non-{@code SUBMITTED} report and a blank comment.
     *
     * @param comment         the mandatory rejection reason (non-blank)
     * @param expectedVersion the {@code @Version} the review last saw
     * @throws ObjectOptimisticLockingFailureException if the report changed
     *         underneath the reviewer
     * @throws IllegalArgumentException if no report has that id or the comment is blank
     * @throws IllegalStateException    if the report is not {@code SUBMITTED}
     */
    @RolesAllowed("ADMIN")
    @Transactional
    public ReportDetailDto reject(Long id, String comment, long expectedVersion) {
        var report = require(id);
        if (report.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(ExpenseReport.class, id);
        }
        report.reject(currentAdmin(), comment, Instant.now());
        return mapper.toDetail(report);
    }

    private ExpenseReport require(Long id) {
        return reportRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("No report with id " + id));
    }

    private User currentAdmin() {
        return userRepository.findById(currentUserProvider.require().id()).orElseThrow(
                () -> new IllegalStateException("Current user no longer exists"));
    }

    private static ReviewSummaryDto toReviewSummary(ExpenseReport r) {
        return new ReviewSummaryDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.total(),
                r.getOwner().getName(), submittedAt(r));
    }

    /** The timestamp of the report's most recent {@code → SUBMITTED} transition. */
    private static Instant submittedAt(ExpenseReport r) {
        return r.getStatusHistory().stream()
                .filter(change -> change.getToStatus() == ReportStatus.SUBMITTED)
                .map(StatusChange::getChangedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static ReviewedSummaryDto toReviewedSummary(ExpenseReport r) {
        var decision = decisionChange(r);
        return new ReviewedSummaryDto(r.getId(), r.getReportDate(),
                r.getAdditionalInformation(), r.getStatus(), r.total(),
                r.getOwner().getName(),
                decision == null ? null : decision.getActingUser().getName(),
                decision == null ? null : decision.getChangedAt(),
                r.getStatus() == ReportStatus.REJECTED && decision != null
                        ? decision.getComment() : null);
    }

    /**
     * The report's most recent transition <em>into</em> its current terminal
     * status — the decision that put it in the history: the {@code → APPROVED} or
     * {@code → REJECTED} change carrying the deciding admin, timestamp and comment.
     */
    private static StatusChange decisionChange(ExpenseReport r) {
        return r.getStatusHistory().stream()
                .filter(change -> change.getToStatus() == r.getStatus())
                .max(Comparator.comparing(StatusChange::getChangedAt))
                .orElse(null);
    }
}

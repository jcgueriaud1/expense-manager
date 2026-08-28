package com.vaadin.expensemanager.report.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for the {@link ExpenseReport} aggregate (ADR-0003).
 *
 * <p>Stays inside the service layer — the UI never sees it or the entities it
 * returns. Every lookup is <strong>owner-scoped</strong>: a report is always
 * loaded together with its owner id so one user can never read or mutate
 * another's report by guessing the sequential id (ADR-0016 enumeration note,
 * ADR-0008).
 */
public interface ExpenseReportRepository extends JpaRepository<ExpenseReport, Long> {

    /** The owner's reports, newest report-date first (ties broken by newest id). */
    List<ExpenseReport> findByOwnerIdOrderByReportDateDescIdDesc(Long ownerId);

    /**
     * The owner's reports with their <strong>expense lines</strong> already
     * fetched, newest report-date first — the first of the three queries behind
     * {@code ExpenseReportService.listMine()} (issue #147).
     *
     * <p>Same rows and same order as
     * {@link #findByOwnerIdOrderByReportDateDescIdDesc}, but the derived total each
     * summary carries walks every line, so leaving the collection lazy is an N+1
     * across the whole list. The two to-one associations are fetched with it: both
     * are {@code EAGER} on {@link com.vaadin.expensemanager.report.domain.ExpenseLine}
     * (a line's money needs its VAT rate), so not joining them here only moves the
     * per-line selects rather than removing them.
     */
    @Query("""
            select r from ExpenseReport r
            left join fetch r.lines line
            left join fetch line.expenseType
            left join fetch line.vatRate
            where r.owner.id = :ownerId
            order by r.reportDate desc, r.id desc
            """)
    List<ExpenseReport> findByOwnerIdFetchingLines(@Param("ownerId") Long ownerId);

    /**
     * Initialises the {@code travels} collection of every report the owner has, in
     * one query (issue #147). Deliberately a <strong>separate</strong> query rather
     * than a second {@code join fetch} on
     * {@link #findByOwnerIdFetchingLines}: fetching two ordered collections in one
     * statement multiplies the rows and corrupts the {@code @OrderColumn} indices.
     * Run inside the same transaction it populates the persistence context, so the
     * already-loaded reports answer {@code getTravels()} without touching the
     * database.
     *
     * <p>The returned list is the same reports again — callers use it for its
     * effect, not its value.
     */
    @Query("""
            select r from ExpenseReport r
            left join fetch r.travels
            where r.owner.id = :ownerId
            """)
    List<ExpenseReport> fetchTravelsByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * Initialises the {@code statusHistory} of every report the owner has, together
     * with each entry's acting user, in one query (issue #147). The list needs the
     * history twice over — the in-flight wait measured from the latest
     * {@code SUBMITTED} change, and the "Rejected by &lt;actor&gt;" footer — and the
     * actor's name would otherwise be a lazy load per entry. Same one-collection-
     * per-query reasoning as {@link #fetchTravelsByOwnerId}; used for its effect on
     * the persistence context.
     */
    @Query("""
            select r from ExpenseReport r
            left join fetch r.statusHistory change
            left join fetch change.actingUser
            where r.owner.id = :ownerId
            """)
    List<ExpenseReport> fetchStatusHistoryByOwnerId(@Param("ownerId") Long ownerId);

    /** A single report, but only if it belongs to {@code ownerId}. */
    Optional<ExpenseReport> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * Reports in a given status across <strong>all</strong> owners, newest id
     * first — the admin approval queue's non-owner-scoped query (Phase 5,
     * ADR-0008). Not owner-bound by design: only the {@code ADMIN}-guarded
     * {@code ApprovalService} calls it, and it reviews everyone's reports. The
     * caller re-orders by submitted-at (the SUBMITTED status change's timestamp).
     */
    List<ExpenseReport> findByStatusOrderByIdDesc(ReportStatus status);

    /**
     * Reports in any of the given statuses across <strong>all</strong> owners,
     * newest id first — the admin review history's non-owner-scoped query (Issue
     * #110, ADR-0008). Like {@link #findByStatusOrderByIdDesc} it is not
     * owner-bound by design: only the {@code ADMIN}-guarded {@code ApprovalService}
     * calls it. The caller re-orders by decided-at (the terminal status change's
     * timestamp).
     */
    List<ExpenseReport> findByStatusInOrderByIdDesc(Collection<ReportStatus> statuses);
}

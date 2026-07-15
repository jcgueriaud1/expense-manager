package com.vaadin.expensemanager.report.service;

import java.util.List;
import java.util.Optional;

import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;

import org.springframework.data.jpa.repository.JpaRepository;

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
}

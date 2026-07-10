package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vaadin.expensemanager.base.AuditedEntity;
import com.vaadin.expensemanager.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The expense-report aggregate root (ADR-0006) — the core of the application.
 *
 * <p>Owns its report-level fields (a required user-entered {@link #reportDate}
 * defaulting to today in the UI, optional {@link #additionalInformation}, no
 * title), its lifecycle {@link #status} (starting {@link ReportStatus#DRAFT}),
 * and an ordered {@link StatusChange} log. The gross line collection and the
 * money it carries arrive in Phase 2.3; until then {@link #total()} is €0.00 —
 * the {@code // lines: Phase 2.3} comments mark the seam.
 *
 * <p><strong>Invariants live here, not in the service (ADR-0006).</strong> The
 * aggregate owns the delete guard — {@link #assertDeletable()} rejects deleting
 * anything but a {@code DRAFT} — and the edit guard on report-level fields
 * ({@link #updateDetails}). Authorization ("who owns this") stays in the
 * security/service layer; the domain only enforces state validity.
 *
 * <p>Versioned for optimistic locking (ADR-0011): a stale whole-aggregate write
 * throws {@code OptimisticLockException}. Per ADR-0003 the entity never leaves
 * the service layer — the UI exchanges {@code ReportDetailDto} /
 * {@code ReportSummaryDto} records instead.
 */
@Entity
@Table(name = "expense_report")
public class ExpenseReport extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "additional_information")
    private String additionalInformation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status = ReportStatus.DRAFT;

    /**
     * Ordered status-history log, owned by the aggregate (cascade + orphan
     * removal, insertion order persisted via {@link OrderColumn}). Empty until
     * the first transition is recorded (Phase 2.4).
     *
     * <p>The collection is the <strong>owning</strong> side (it maps the
     * {@code report_id} FK and the {@code entry_index} order column); {@link
     * StatusChange}'s back-reference is read-only. Owning the {@code @OrderColumn}
     * here — rather than on a {@code mappedBy} inverse — is what lets Hibernate
     * populate {@code entry_index} on insert (and avoids HHH160246).
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "report_id", nullable = false)
    @OrderColumn(name = "entry_index")
    private List<StatusChange> statusHistory = new ArrayList<>();

    /** JPA constructor. */
    protected ExpenseReport() {
    }

    /**
     * Opens a new report in {@link ReportStatus#DRAFT} for {@code owner}.
     *
     * @param owner                the owning user (required, never reassigned)
     * @param reportDate           the user-entered report date (required)
     * @param additionalInformation optional free-text note ({@code null}/blank ok)
     */
    public ExpenseReport(User owner, LocalDate reportDate, String additionalInformation) {
        this.owner = owner;
        this.reportDate = requireDate(reportDate);
        this.additionalInformation = normalize(additionalInformation);
    }

    /**
     * Updates the report-level fields. Allowed only while the report is editable
     * ({@code DRAFT}/{@code REJECTED}, ADR-0006); a locked report rejects the
     * change rather than silently ignoring it.
     *
     * @throws IllegalStateException if the report is not editable
     */
    public void updateDetails(LocalDate reportDate, String additionalInformation) {
        if (!status.isEditable()) {
            throw new IllegalStateException(
                    "Report " + id + " is " + status + " and cannot be edited");
        }
        this.reportDate = requireDate(reportDate);
        this.additionalInformation = normalize(additionalInformation);
    }

    /**
     * Guards the draft-only delete invariant (ADR-0006, glossary): a report is
     * hard-deletable only while {@code DRAFT}. Submitted/approved/rejected
     * reports are retained for the audit trail.
     *
     * @throws IllegalStateException if the report is not a {@code DRAFT}
     */
    public void assertDeletable() {
        if (!status.isDeletable()) {
            throw new IllegalStateException(
                    "Report " + id + " is " + status + " and cannot be deleted");
        }
    }

    /**
     * Appends a status-history entry (glossary: Status Change). Package-internal
     * seam used by the transition methods (submit/approve/reject) that land from
     * Phase 2.4 — entries are created only here so the aggregate owns ordering.
     */
    void recordStatusChange(ReportStatus fromStatus, ReportStatus toStatus,
            User actingUser, String comment, Instant changedAt) {
        statusHistory.add(new StatusChange(this, fromStatus, toStatus, actingUser,
                comment, changedAt));
    }

    /**
     * The derived report total — never stored (ADR-0010, ADR-0019). Sums the
     * gross line amounts; with no lines yet (Phase 2.3) this is {@code 0.00}.
     */
    public BigDecimal total() {
        // lines: Phase 2.3 — sum ExpenseLine gross amounts here.
        return BigDecimal.ZERO.setScale(2);
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public User getOwner() {
        return owner;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public ReportStatus getStatus() {
        return status;
    }

    /** Unmodifiable view of the status history in insertion order. */
    public List<StatusChange> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    private static LocalDate requireDate(LocalDate reportDate) {
        if (reportDate == null) {
            throw new IllegalArgumentException("Report date is required");
        }
        return reportDate;
    }

    /** Trims and collapses blank optional text to {@code null}. */
    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}

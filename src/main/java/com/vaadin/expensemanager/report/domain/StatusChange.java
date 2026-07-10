package com.vaadin.expensemanager.report.domain;

import java.time.Instant;

import com.vaadin.expensemanager.user.User;

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
import jakarta.persistence.Table;

/**
 * One entry in a report's ordered status-history log (glossary: Status Change).
 *
 * <p>Records a single transition: {@code fromStatus → toStatus}, the acting
 * user, when it happened, and an optional comment (mandatory on reject from
 * Phase 5). Part of the {@link ExpenseReport} aggregate — created only through
 * {@link ExpenseReport#recordStatusChange} and never on its own, so its
 * insertion order and parent link stay under the aggregate's control (ADR-0006).
 *
 * <p>Modelled now for the report spine; the transitions that append entries
 * (submit/approve/reject/resubmit) land from Phase 2.4 onward. Carries its own
 * {@code changedAt} rather than extending {@code AuditedEntity}: the transition
 * time <em>is</em> the domain fact here, not an audit side-channel.
 */
@Entity
@Table(name = "status_change")
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Read-only back-reference: the owning ExpenseReport.statusHistory collection
    // maps report_id (and entry_index), so this side must not also write it.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false, insertable = false, updatable = false)
    private ExpenseReport report;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private ReportStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ReportStatus toStatus;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_user_id", nullable = false)
    private User actingUser;

    @Column(name = "comment")
    private String comment;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    /** JPA constructor. */
    protected StatusChange() {
    }

    StatusChange(ExpenseReport report, ReportStatus fromStatus, ReportStatus toStatus,
            User actingUser, String comment, Instant changedAt) {
        this.report = report;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actingUser = actingUser;
        this.comment = comment;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public ReportStatus getFromStatus() {
        return fromStatus;
    }

    public ReportStatus getToStatus() {
        return toStatus;
    }

    public User getActingUser() {
        return actingUser;
    }

    public String getComment() {
        return comment;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}

package com.vaadin.expensemanager.report.domain;

/**
 * The lifecycle status of an {@link ExpenseReport} (ADR-0006, glossary).
 *
 * <p>A report is created in {@link #DRAFT} and moves through the state machine
 * as the owner submits and an admin approves or rejects it. Only {@link #DRAFT}
 * and {@link #REJECTED} are <em>editable</em> (report-level fields and, from
 * Phase 2.3, lines); only {@link #DRAFT} is <em>deletable</em>. The transitions
 * beyond create/edit (submit/approve/reject/resubmit) wire in from Phase 2.4.
 */
public enum ReportStatus {

    /** Being prepared by the owner. Editable and deletable. */
    DRAFT,

    /** Handed to admins for review. Locked from owner edits. */
    SUBMITTED,

    /** Approved by an admin. Terminal for the owner. */
    APPROVED,

    /** Rejected by an admin with a comment. Editable again, then resubmittable. */
    REJECTED;

    /** Whether report-level fields (and, from 2.3, lines) may be changed. */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /** Whether the report may be hard-deleted — draft-only (ADR-0006, glossary). */
    public boolean isDeletable() {
        return this == DRAFT;
    }
}

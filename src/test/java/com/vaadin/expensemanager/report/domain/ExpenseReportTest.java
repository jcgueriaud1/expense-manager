package com.vaadin.expensemanager.report.domain;

import java.time.LocalDate;
import java.util.Set;

import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.User;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit test (pyramid layer 1, ADR-0012): pure JUnit, no Spring/DB, for
 * the {@link ExpenseReport} aggregate and its {@link ReportStatus} guards.
 *
 * <p>Covers the two invariants this phase owns: a new report starts as an empty
 * {@code DRAFT} with a €0.00 derived total, and the draft-only delete/edit
 * guards. The non-{@code DRAFT} rejection is asserted through the state enum
 * (the guard's single source of truth) because no transition exists yet to
 * produce a submitted report — {@code submit()} and the end-to-end delete
 * rejection wire in from Phase 2.4.
 */
class ExpenseReportTest {

    private static final User OWNER =
            new User("owner@vaadin.com", "Report Owner", Set.of(Role.USER));

    @Test
    void newReportStartsAsEmptyDraftWithZeroTotal() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), "Client visit");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(report.getOwner()).isSameAs(OWNER);
        assertThat(report.getReportDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(report.getAdditionalInformation()).isEqualTo("Client visit");
        assertThat(report.getStatusHistory()).isEmpty();
        assertThat(report.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void constructorRequiresAReportDateAndNormalizesBlankInfoToNull() {
        assertThatThrownBy(() -> new ExpenseReport(OWNER, null, "x"))
                .isInstanceOf(IllegalArgumentException.class);

        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 1, 1), "   ");
        assertThat(report.getAdditionalInformation()).isNull();
    }

    @Test
    void draftIsDeletableAndAssertDeletablePasses() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThat(report.getStatus().isDeletable()).isTrue();
        // No throw for a draft.
        report.assertDeletable();
    }

    @Test
    void updateDetailsChangesFieldsWhileEditable() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), "old");

        report.updateDetails(LocalDate.of(2026, 7, 12), "new note");

        assertThat(report.getReportDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(report.getAdditionalInformation()).isEqualTo("new note");
    }

    @Test
    void deleteGuardIsDraftOnly() {
        // The guard's single source of truth: only DRAFT is deletable, and only
        // DRAFT/REJECTED are editable (the end-to-end rejection path is exercised
        // once submit() exists in Phase 2.4).
        assertThat(ReportStatus.DRAFT.isDeletable()).isTrue();
        assertThat(ReportStatus.SUBMITTED.isDeletable()).isFalse();
        assertThat(ReportStatus.APPROVED.isDeletable()).isFalse();
        assertThat(ReportStatus.REJECTED.isDeletable()).isFalse();

        assertThat(ReportStatus.DRAFT.isEditable()).isTrue();
        assertThat(ReportStatus.REJECTED.isEditable()).isTrue();
        assertThat(ReportStatus.SUBMITTED.isEditable()).isFalse();
        assertThat(ReportStatus.APPROVED.isEditable()).isFalse();
    }
}

package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.VatRate;
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

    private static final VatRate RATE_255 = new VatRate(new BigDecimal("25.50"), 0);
    private static final VatRate RATE_135 = new VatRate(new BigDecimal("13.50"), 1);
    private static final VatRate RATE_0 = new VatRate(new BigDecimal("0.00"), 2);
    private static final ExpenseType TYPE = new ExpenseType("Parking", 0, RATE_255);

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

    @Test
    void reconcileInsertsLinesAndDerivesMixedRateTotals() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileLines(List.of(
                spec(null, "100.00", RATE_255),
                spec(null, "50.00", RATE_135)));

        assertThat(report.getLines()).hasSize(2);
        // Sum-per-line then total (ADR-0010).
        assertThat(report.total()).isEqualByComparingTo("150.00");
        assertThat(report.netTotal()).isEqualByComparingTo("123.73");
        assertThat(report.vatTotal()).isEqualByComparingTo("26.27");
    }

    @Test
    void lineKeepsAnOverriddenRateRatherThanTheTypeDefault() {
        // The type defaults to 25.5% but the line is filed at 13.5% — the domain
        // stores what it is given; the default lives in the UI (ADR-0018).
        assertThat(TYPE.getDefaultVatRate()).isSameAs(RATE_255);
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileLines(List.of(spec(null, "50.00", RATE_135)));

        assertThat(report.getLines().getFirst().getVatRate()).isSameAs(RATE_135);
    }

    @Test
    void reconcileRejectsAZeroAmount() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.reconcileLines(
                List.of(spec(null, "0.00", RATE_255))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeLinesAreAcceptedAndReflectedInTotals() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileLines(List.of(
                spec(null, "100.00", RATE_0),
                spec(null, "-30.00", RATE_0)));

        assertThat(report.total()).isEqualByComparingTo("70.00");
    }

    @Test
    void reconcileReplacesUnmatchedLinesOnRepeatedCall() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));

        // A second reconcile with an entirely new (null-id) set replaces the old
        // lines — the first line has no persistent id to match, so it is dropped.
        report.reconcileLines(List.of(spec(null, "40.00", RATE_255)));

        assertThat(report.getLines()).hasSize(1);
        assertThat(report.total()).isEqualByComparingTo("40.00");
    }

    @Test
    void reconcileWithAnUnknownLineIdIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.reconcileLines(
                List.of(spec(999L, "10.00", RATE_255))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExpenseLineSpec spec(Long id, String amount, VatRate rate) {
        return new ExpenseLineSpec(id, TYPE, new BigDecimal(amount), rate, null);
    }
}

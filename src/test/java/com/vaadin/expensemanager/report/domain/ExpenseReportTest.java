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

    // Reference data mirroring the Finnish 2026 seed (V3); ids are null — the
    // domain never needs them (reconciliation by id is a service-layer concern).
    private static final VatRate VAT_255 = new VatRate(new BigDecimal("25.50"), 0);
    private static final VatRate VAT_10 = new VatRate(new BigDecimal("10.00"), 2);
    private static final VatRate VAT_0 = new VatRate(new BigDecimal("0.00"), 3);
    private static final ExpenseType GOODS = new ExpenseType("Goods", 0, VAT_255);
    private static final ExpenseType PUBLICATIONS = new ExpenseType("Publications", 1, VAT_10);

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

    // ------------------------------------------------------------ line editing

    @Test
    void lineUsesItsFiledRate_defaultOrOverridden() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        // One line at the type's default rate (25.5 %), one overriding it to 10 %.
        report.replaceLines(List.of(
                new LineInput(null, GOODS, new BigDecimal("125.50"), VAT_255, null),
                new LineInput(null, GOODS, new BigDecimal("110.00"), VAT_10, "override")));

        var lines = report.getLines();
        assertThat(lines).hasSize(2);
        // 125.50 @ 25.5 % → net 100.00, VAT 25.50 (clean division).
        assertThat(lines.get(0).net()).isEqualByComparingTo("100.00");
        assertThat(lines.get(0).vat()).isEqualByComparingTo("25.50");
        // 110.00 @ overridden 10 % → net 100.00, VAT 10.00.
        assertThat(lines.get(1).net()).isEqualByComparingTo("100.00");
        assertThat(lines.get(1).vat()).isEqualByComparingTo("10.00");
    }

    @Test
    void perLineDerivationRoundsHalfUpAtScaleTwo() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.replaceLines(List.of(
                new LineInput(null, GOODS, new BigDecimal("48.60"), VAT_255, null)));

        var line = report.getLines().getFirst();
        // 48.60 / 1.255 = 38.7250… → 38.73 (HALF_UP); VAT = 48.60 − 38.73 = 9.87.
        assertThat(line.gross()).isEqualByComparingTo("48.60");
        assertThat(line.net()).isEqualByComparingTo("38.73");
        assertThat(line.vat()).isEqualByComparingTo("9.87");
        assertThat(line.net().add(line.vat())).isEqualByComparingTo(line.gross());
    }

    @Test
    void reportTotalsSumPerLineAcrossMixedRates() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.replaceLines(List.of(
                new LineInput(null, GOODS, new BigDecimal("125.50"), VAT_255, null),
                new LineInput(null, PUBLICATIONS, new BigDecimal("110.00"), VAT_10, null)));

        // net 100.00 + 100.00, VAT 25.50 + 10.00, gross 235.50 (no drift).
        assertThat(report.netTotal()).isEqualByComparingTo("200.00");
        assertThat(report.vatTotal()).isEqualByComparingTo("35.50");
        assertThat(report.total()).isEqualByComparingTo("235.50");
    }

    @Test
    void negativeLineReducesTheTotal() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.replaceLines(List.of(
                new LineInput(null, GOODS, new BigDecimal("125.50"), VAT_255, null),
                new LineInput(null, GOODS, new BigDecimal("-25.50"), VAT_0, "refund")));

        assertThat(report.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void lineAmountMustBePresentAndNonZero() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.replaceLines(List.of(
                new LineInput(null, GOODS, BigDecimal.ZERO, VAT_255, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report.replaceLines(List.of(
                new LineInput(null, GOODS, null, VAT_255, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lineRequiresTypeAndRate() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.replaceLines(List.of(
                new LineInput(null, null, new BigDecimal("10.00"), VAT_255, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report.replaceLines(List.of(
                new LineInput(null, GOODS, new BigDecimal("10.00"), null, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replaceLinesReplacesTheWholeCollectionInOrder() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.replaceLines(List.of(
                new LineInput(null, GOODS, new BigDecimal("10.00"), VAT_255, "a"),
                new LineInput(null, GOODS, new BigDecimal("20.00"), VAT_255, "b")));
        assertThat(report.getLines()).hasSize(2);

        // A fresh input list with no matching ids replaces everything (all new).
        report.replaceLines(List.of(
                new LineInput(null, PUBLICATIONS, new BigDecimal("30.00"), VAT_10, "c")));
        assertThat(report.getLines()).hasSize(1);
        assertThat(report.getLines().getFirst().getComment()).isEqualTo("c");
    }
}

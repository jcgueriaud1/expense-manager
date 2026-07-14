package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Test
    void submitMovesDraftToSubmittedAndAppendsAStatusChange() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        var at = Instant.parse("2026-07-13T09:30:00Z");

        report.submit(OWNER, at);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(report.getStatusHistory()).hasSize(1);
        var change = report.getStatusHistory().getFirst();
        assertThat(change.getFromStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(change.getToStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(change.getActingUser()).isSameAs(OWNER);
        assertThat(change.getChangedAt()).isEqualTo(at);
        assertThat(change.getComment()).isNull();
    }

    @Test
    void submitRequiresAtLeastOneLine() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.submit(OWNER, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one line");
        // Nothing changed: still an empty DRAFT with no history.
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(report.getStatusHistory()).isEmpty();
    }

    @Test
    void submitPermitsANonPositiveTotal() {
        // No total-sign guard (ADR-0006): a report netting to zero still submits.
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(
                spec(null, "100.00", RATE_0),
                spec(null, "-100.00", RATE_0)));
        assertThat(report.total()).isEqualByComparingTo("0.00");

        report.submit(OWNER, Instant.EPOCH);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    void resubmittingAnAlreadySubmittedReportIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.EPOCH);

        assertThatThrownBy(() -> report.submit(OWNER, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
        // The illegal second transition does not append a spurious history entry.
        assertThat(report.getStatusHistory()).hasSize(1);
    }

    @Test
    void aSubmittedReportRejectsLineAndDetailEditsAndDelete() {
        // The read-only-after-submit guards, now exercised end-to-end through a
        // real submit() (Phase 2.4) rather than only via the enum.
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.EPOCH);

        assertThatThrownBy(() -> report.reconcileLines(
                List.of(spec(null, "50.00", RATE_255))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> report.updateDetails(LocalDate.of(2026, 7, 11), "x"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(report::assertDeletable)
                .isInstanceOf(IllegalStateException.class);
    }

    // --- Travel / per-diem generated lines (Phase 4.2/4.3) ---

    private static final ExpenseType TRAVEL_TYPE =
            new ExpenseType("Travel allowance", 0, RATE_0);
    private static final LocalDateTime DEP = LocalDateTime.of(2026, 7, 1, 8, 0);

    @Test
    void reconcilingATripGeneratesAReadOnlyPerDiemLine() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileTravels(List.of(travelSpec(null, "54.00", "one full day")));

        // The trip is tracked and a single generated line carries the per-diem.
        assertThat(report.getTravels()).hasSize(1);
        assertThat(report.getLines()).hasSize(1);
        var line = report.getLines().getFirst();
        assertThat(line.isGenerated()).isTrue();
        assertThat(line.getVatRate()).isSameAs(RATE_0);
        assertThat(line.getComment()).isEqualTo("one full day");
        assertThat(line.getAmount()).isEqualByComparingTo("54.00");
        // Broken out of Net/VAT, surfaced as its own allowance subtotal.
        assertThat(report.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(report.netTotal()).isEqualByComparingTo("0.00");
        assertThat(report.total()).isEqualByComparingTo("54.00");
    }

    @Test
    void editingATripRegeneratesItsLineInPlace() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileTravels(List.of(travelSpec(null, "54.00", "full")));
        // Stamp a persisted id so the edit matches the existing trip (a pure
        // unit test never flushes, so the trip's id would otherwise stay null).
        setId(report.getTravels().getFirst());
        var persistedId = report.getTravels().getFirst().getId();

        report.reconcileTravels(List.of(travelSpec(persistedId, "79.00", "full + partial")));

        assertThat(report.getTravels()).hasSize(1);
        assertThat(report.getLines()).hasSize(1);
        assertThat(report.getLines().getFirst().getAmount()).isEqualByComparingTo("79.00");
        assertThat(report.perDiemTotal()).isEqualByComparingTo("79.00");
    }

    @Test
    void removingATripRemovesItsGeneratedLine() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileTravels(List.of(travelSpec(null, "54.00", "full")));
        assertThat(report.getLines()).hasSize(1);

        report.reconcileTravels(List.of());

        assertThat(report.getTravels()).isEmpty();
        assertThat(report.getLines()).isEmpty();
        assertThat(report.perDiemTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void aTripEarningNoPerDiemGeneratesNoLine() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        // A not-eligible / too-short trip: zero per-diem → tracked trip, no line.
        report.reconcileTravels(List.of(travelSpec(null, "0.00", "not eligible")));

        assertThat(report.getTravels()).hasSize(1);
        assertThat(report.getLines()).isEmpty();
        assertThat(report.perDiemTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void manualAndGeneratedLinesCoexistWithSplitTotals() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcile(
                List.of(spec(null, "100.00", RATE_255)),
                List.of(travelSpec(null, "54.00", "full")));

        assertThat(report.getLines()).hasSize(2);
        // Manual line first, generated per-diem line trailing.
        assertThat(report.manualLines()).hasSize(1);
        assertThat(report.getLines().get(0).isGenerated()).isFalse();
        assertThat(report.getLines().get(1).isGenerated()).isTrue();
        // Net/VAT exclude the tax-free per-diem; Total = Net + VAT + allowance.
        assertThat(report.netTotal()).isEqualByComparingTo("79.68");
        assertThat(report.vatTotal()).isEqualByComparingTo("20.32");
        assertThat(report.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(report.total()).isEqualByComparingTo("154.00");
    }

    @Test
    void reconcileTravelsWithAnUnknownIdIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.reconcileTravels(
                List.of(travelSpec(999L, "54.00", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExpenseLineSpec spec(Long id, String amount, VatRate rate) {
        return new ExpenseLineSpec(id, TYPE, new BigDecimal(amount), rate, null);
    }

    private static TravelSpec travelSpec(Long id, String perDiem, String explanation) {
        return new TravelSpec(id, DEP, DEP.plusHours(11), "Helsinki", "Client visit",
                "Finland", false, false, false, TRAVEL_TYPE, RATE_0,
                new BigDecimal(perDiem), explanation);
    }

    /** Reflectively stamps a generated id on a transient travel, to model persistence. */
    private static void setId(Travel travel) {
        try {
            var field = Travel.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(travel, 1L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

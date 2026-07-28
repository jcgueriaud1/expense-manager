package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vaadin.expensemanager.base.DomainRuleException;
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
    void quantityMultipliesTheUnitPriceIntoTheLineGross() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileLines(List.of(spec(null, "100.00", "3", RATE_255)));

        var line = report.getLines().getFirst();
        assertThat(line.getAmount()).isEqualByComparingTo("100.00");   // unit price
        assertThat(line.getQuantity()).isEqualByComparingTo("3.00");
        assertThat(line.gross()).isEqualByComparingTo("300.00");
        // Net/VAT derive from the *gross*, not from the unit price.
        assertThat(line.net()).isEqualByComparingTo("239.04");
        assertThat(line.vat()).isEqualByComparingTo("60.96");
        assertThat(report.total()).isEqualByComparingTo("300.00");
        assertThat(report.netTotal()).isEqualByComparingTo("239.04");
        assertThat(report.vatTotal()).isEqualByComparingTo("60.96");
    }

    @Test
    void quantityOneLeavesTheLineExactlyAsBefore() {
        // The invisible-until-used guarantee (ADR-0023): default quantity, old maths.
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));

        var line = report.getLines().getFirst();
        assertThat(line.getQuantity()).isEqualByComparingTo("1.00");
        assertThat(line.gross()).isEqualByComparingTo("100.00");
        assertThat(line.net()).isEqualByComparingTo("79.68");
        assertThat(line.vat()).isEqualByComparingTo("20.32");
    }

    @Test
    void aNegativeUnitPriceWithAQuantityCreditsTheWholeGross() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileLines(List.of(
                spec(null, "100.00", "3", RATE_0),
                spec(null, "-30.00", "2", RATE_0)));

        assertThat(report.getLines().get(1).gross()).isEqualByComparingTo("-60.00");
        assertThat(report.total()).isEqualByComparingTo("240.00");
    }

    @Test
    void reconcileRejectsAZeroQuantity() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.reconcileLines(
                List.of(spec(null, "100.00", "0", RATE_255))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("Quantity");
    }

    @Test
    void reconcileRejectsANegativeQuantity() {
        // Credits ride a negative unit price, never a negative quantity (ADR-0023).
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.reconcileLines(
                List.of(spec(null, "100.00", "-2", RATE_255))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("Quantity");
    }

    @Test
    void reconcileRejectsAMissingQuantity() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.reconcileLines(List.of(
                new ExpenseLineSpec(null, TYPE, new BigDecimal("100.00"), null,
                        RATE_255, null))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("Quantity is required");
    }

    @Test
    void reReconcilingWithADifferentQuantityRetotalsTheReport() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", "3", RATE_255)));
        var line = report.getLines().getFirst();

        // No persistent id yet, so the null-id set replaces rather than updates
        // (the in-place update path is covered by the integration test).
        report.reconcileLines(List.of(spec(null, "100.00", "2", RATE_255)));

        assertThat(report.getLines()).hasSize(1).doesNotContain(line);
        assertThat(report.total()).isEqualByComparingTo("200.00");
    }

    @Test
    void generatedTravelLinesArePinnedToQuantityOne() {
        // This slice keeps travel euros identical: the calculator's full computed
        // gross is the unit price × 1 (issue #122).
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileTravels(List.of(travelSpec(null, "54.00", "11 h")));

        var generated = report.getLines().getFirst();
        assertThat(generated.isGenerated()).isTrue();
        assertThat(generated.getQuantity()).isEqualByComparingTo("1.00");
        assertThat(generated.getAmount()).isEqualByComparingTo("54.00");
        assertThat(generated.gross()).isEqualByComparingTo("54.00");
        assertThat(report.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(report.total()).isEqualByComparingTo("54.00");
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
                .isInstanceOf(DomainRuleException.class)
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
    void approveMovesSubmittedToApprovedAndAppendsAStatusChange() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.parse("2026-07-13T09:30:00Z"));
        var admin = new User("admin@vaadin.com", "The Admin", Set.of(Role.ADMIN));
        var at = Instant.parse("2026-07-14T08:00:00Z");

        report.approve(admin, at);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(report.getStatusHistory()).hasSize(2);
        var change = report.getStatusHistory().get(1);
        assertThat(change.getFromStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(change.getToStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(change.getActingUser()).isSameAs(admin);
        assertThat(change.getChangedAt()).isEqualTo(at);
        assertThat(change.getComment()).isNull();
    }

    @Test
    void approvingADraftIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));

        assertThatThrownBy(() -> report.approve(OWNER, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
        // The illegal transition leaves the report untouched, with no history.
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(report.getStatusHistory()).isEmpty();
    }

    @Test
    void approvingAnAlreadyApprovedReportIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.EPOCH);
        report.approve(OWNER, Instant.EPOCH);

        assertThatThrownBy(() -> report.approve(OWNER, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
        // No spurious second APPROVED entry from the rejected transition.
        assertThat(report.getStatusHistory()).hasSize(2);
    }

    @Test
    void rejectMovesSubmittedToRejectedAndAppendsAStatusChangeCarryingTheReason() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.parse("2026-07-13T09:30:00Z"));
        var admin = new User("admin@vaadin.com", "The Admin", Set.of(Role.ADMIN));
        var at = Instant.parse("2026-07-14T08:00:00Z");

        report.reject(admin, "  Please attach the hotel receipt.  ", at);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(report.getStatusHistory()).hasSize(2);
        var change = report.getStatusHistory().get(1);
        assertThat(change.getFromStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(change.getToStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(change.getActingUser()).isSameAs(admin);
        assertThat(change.getChangedAt()).isEqualTo(at);
        // The mandatory reason is carried as the comment, trimmed.
        assertThat(change.getComment()).isEqualTo("Please attach the hotel receipt.");
    }

    @Test
    void rejectingWithABlankCommentIsRejectedAsADomainViolation() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.EPOCH);

        // Null, empty, and whitespace-only are all refused — not just DB nullability.
        assertThatThrownBy(() -> report.reject(OWNER, null, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report.reject(OWNER, "   ", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        // The report stays SUBMITTED and no spurious history entry is appended.
        assertThat(report.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(report.getStatusHistory()).hasSize(1);
    }

    @Test
    void rejectingANonSubmittedReportIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));

        // A DRAFT cannot be rejected, and the guard fires before the comment check.
        assertThatThrownBy(() -> report.reject(OWNER, "no", Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(report.getStatusHistory()).isEmpty();
    }

    @Test
    void rejectingAnAlreadyApprovedReportIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.EPOCH);
        report.approve(OWNER, Instant.EPOCH);

        assertThatThrownBy(() -> report.reject(OWNER, "too late", Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(report.getStatusHistory()).hasSize(2);
    }

    @Test
    void onlySubmittedIsReviewable() {
        assertThat(ReportStatus.DRAFT.isReviewable()).isFalse();
        assertThat(ReportStatus.SUBMITTED.isReviewable()).isTrue();
        assertThat(ReportStatus.APPROVED.isReviewable()).isFalse();
        assertThat(ReportStatus.REJECTED.isReviewable()).isFalse();
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

    // --- Resubmit (Phase 5.5) ---

    /** Creates a REJECTED report with one line — the resubmit tests' fixture. */
    private static ExpenseReport rejectedReport() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.parse("2026-07-11T09:00:00Z"));
        var admin = new User("admin@vaadin.com", "The Admin", Set.of(Role.ADMIN));
        report.reject(admin, "Please attach the receipt.",
                Instant.parse("2026-07-12T09:00:00Z"));
        return report;
    }

    @Test
    void resubmitMovesRejectedToSubmittedAndAppendsAStatusChange() {
        var report = rejectedReport();
        var at = Instant.parse("2026-07-13T09:30:00Z");

        report.resubmit(OWNER, at);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        // Submit (1) + reject (2) + resubmit (3) — the loop is auditable.
        assertThat(report.getStatusHistory()).hasSize(3);
        var change = report.getStatusHistory().getLast();
        assertThat(change.getFromStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(change.getToStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(change.getActingUser()).isSameAs(OWNER);
        assertThat(change.getChangedAt()).isEqualTo(at);
        assertThat(change.getComment()).isNull();
    }

    @Test
    void resubmitRequiresAtLeastOneLine() {
        var report = rejectedReport();
        // Strip the report back to zero lines while REJECTED (still editable).
        report.reconcileLines(List.of());
        assertThat(report.getLines()).isEmpty();

        assertThatThrownBy(() -> report.resubmit(OWNER, Instant.EPOCH))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("at least one line");
        // Nothing changed: still REJECTED with no spurious history entry.
        assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(report.getStatusHistory()).hasSize(2);
    }

    @Test
    void resubmittingADraftIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));

        assertThatThrownBy(() -> report.resubmit(OWNER, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
        // The illegal transition leaves the draft untouched, with no history.
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(report.getStatusHistory()).isEmpty();
    }

    @Test
    void resubmittingAReportAwaitingApprovalIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileLines(List.of(spec(null, "100.00", RATE_255)));
        report.submit(OWNER, Instant.EPOCH);

        // resubmit() only leaves REJECTED — a SUBMITTED report cannot be resubmitted.
        assertThatThrownBy(() -> report.resubmit(OWNER, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(report.getStatusHistory()).hasSize(1);
    }

    // --- Travel / per-diem generated lines (Phase 4.2/4.3) ---

    private static final ExpenseType TRAVEL_TYPE =
            new ExpenseType("Travel allowance", 0, RATE_0);
    private static final ExpenseType KM_TYPE =
            new ExpenseType("Kilometre allowance", 0, RATE_0);
    private static final ExpenseType MEAL_TYPE =
            new ExpenseType("Meal allowance", 0, RATE_0);
    private static final ExpenseType PARKING_TYPE =
            new ExpenseType("Parking/supplies/goods", 0, RATE_255);
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

    // --- Quantity Override invariants (ADR-0024, issues #131 / #132) ---
    //
    // The pair (count, reason) is indivisible and lives in the value object; the
    // rules that depend on WHICH line is corrected — overridable kinds, the
    // partial-day cap — belong to the trip, which alone knows the map key. Every
    // violation is a DomainRuleException like any other trip invariant (ADR-0006).

    @Test
    void anOverrideNeedsAWholeCountAtOrAboveTheFloor() {
        assertThatThrownBy(() -> new QuantityOverride(null, "personal day"))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("count is required");
        assertThatThrownBy(() -> new QuantityOverride(new BigDecimal("1.5"), "half"))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("whole number");
        // The floor is 0 (issue #132) — a negative count is the only one refused.
        assertThatThrownBy(() -> new QuantityOverride(new BigDecimal("-1"), "owe a day"))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("less than 0");

        // A whole count at money scale round-trips.
        assertThat(new QuantityOverride(new BigDecimal("2"), "two days").quantity())
                .isEqualByComparingTo("2.00");
    }

    @Test
    void aZeroCountIsAcceptedForEveryOverridableKindAndSuppressesTheLine() {
        // Issue #132: zero is the answer, not a rejected edge — it drops the line,
        // which is the only way to express "keep the full days, lose the leftover
        // partial one" or "no meal allowance after all".
        for (GeneratedLineKind kind : List.of(GeneratedLineKind.PER_DIEM_FULL,
                GeneratedLineKind.PER_DIEM_PARTIAL, GeneratedLineKind.MEAL)) {
            var override = QuantityOverride.of(kind, BigDecimal.ZERO, "not claimed");
            assertThat(override.quantity()).as(kind.name()).isEqualByComparingTo("0.00");
            assertThat(override.suppresses()).as(kind.name()).isTrue();
        }
        // Any other count rescales rather than removes.
        assertThat(new QuantityOverride(BigDecimal.ONE, "one day").suppresses()).isFalse();
    }

    @Test
    void aTripAcceptsAZeroOverrideAsAnInputLikeAnyOther() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        // No generated lines: a suppressed kind simply has none (the service's earned
        // gate drops it), and the override still round-trips as a trip input.
        report.reconcileTravels(List.of(travelSpecWith(null,
                Map.of(GeneratedLineKind.PER_DIEM_PARTIAL,
                        new QuantityOverride(BigDecimal.ZERO, "left the leftover")),
                List.of())));

        var stored = report.getTravels().getFirst().getQuantityOverrides()
                .get(GeneratedLineKind.PER_DIEM_PARTIAL);
        assertThat(stored.quantity()).isEqualByComparingTo("0.00");
        assertThat(stored.suppresses()).isTrue();
        assertThat(report.getLines()).isEmpty();
    }

    @Test
    void anOverrideNeedsAReasonAndStoresItTrimmed() {
        assertThatThrownBy(() -> new QuantityOverride(BigDecimal.ONE, null))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> new QuantityOverride(BigDecimal.ONE, "   "))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("reason");

        assertThat(new QuantityOverride(BigDecimal.ONE, "  the Wednesday was personal  ")
                .reason()).isEqualTo("the Wednesday was personal");
    }

    @Test
    void onlyThePerDiemAndMealKindsAcceptAnOverride() {
        for (GeneratedLineKind kind : List.of(GeneratedLineKind.PER_DIEM_FULL,
                GeneratedLineKind.PER_DIEM_PARTIAL, GeneratedLineKind.MEAL)) {
            assertThat(kind.isOverridable()).as(kind.name()).isTrue();
        }
        // The distance and the parking fee are trip inputs with a single home.
        assertThat(GeneratedLineKind.KILOMETRE.isOverridable()).isFalse();
        assertThat(GeneratedLineKind.PARKING.isOverridable()).isFalse();
    }

    @Test
    void aTripRejectsAnOverrideForANonOverridableKind() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        var override = new QuantityOverride(new BigDecimal("2"), "drove less");

        // Rejected by the domain, not silently ignored: the client learns why.
        assertThatThrownBy(() -> report.reconcileTravels(List.of(travelSpecWith(null,
                Map.of(GeneratedLineKind.KILOMETRE, override), List.of()))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("Kilometre allowance")
                .hasMessageContaining("on the trip");
        assertThat(report.getTravels()).isEmpty();
    }

    @Test
    void aTripCapsThePartialDayOverrideAtOne() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        // A trip's duration yields at most one leftover day by construction, so
        // "2 partial days" is incoherent rather than merely generous.
        assertThatThrownBy(() -> report.reconcileTravels(List.of(travelSpecWith(null,
                Map.of(GeneratedLineKind.PER_DIEM_PARTIAL,
                        new QuantityOverride(new BigDecimal("2"), "two leftovers")),
                List.of()))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("one partial day");

        // Exactly one is fine.
        report.reconcileTravels(List.of(travelSpecWith(null,
                Map.of(GeneratedLineKind.PER_DIEM_PARTIAL,
                        new QuantityOverride(BigDecimal.ONE, "keep the leftover")),
                List.of())));
        assertThat(report.getTravels().getFirst().getQuantityOverrides())
                .containsOnlyKeys(GeneratedLineKind.PER_DIEM_PARTIAL);
    }

    @Test
    void aTripKeepsAtMostOneOverridePerKindAndReplacesThemOnEdit() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileTravels(List.of(travelSpecWith(null,
                Map.of(GeneratedLineKind.PER_DIEM_FULL,
                        new QuantityOverride(new BigDecimal("2"), "one day was personal")),
                List.of(generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0,
                        "54.00", "2")))));
        setId(report.getTravels().getFirst());
        var travelId = report.getTravels().getFirst().getId();
        assertThat(report.getTravels().getFirst().getQuantityOverrides())
                .hasSize(1);

        // Re-saving the trip without the override clears it (the "Reset to
        // calculated" path); the map is replaced wholesale, never merged.
        report.reconcileTravels(List.of(travelSpecWith(travelId, Map.of(),
                List.of(generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0,
                        "54.00", "3")))));

        assertThat(report.getTravels().getFirst().getQuantityOverrides()).isEmpty();
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
    void aTripGeneratesEachEarnedLineRoutedByKind() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "54.00"),
                generated(GeneratedLineKind.KILOMETRE, KM_TYPE, RATE_0, "70.80"),
                generated(GeneratedLineKind.MEAL, MEAL_TYPE, RATE_0, "13.50"),
                generated(GeneratedLineKind.PARKING, PARKING_TYPE, RATE_255, "12.00")))));

        assertThat(report.getLines()).hasSize(4);
        // Each tax-free allowance is its own subtotal.
        assertThat(report.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(report.kilometreTotal()).isEqualByComparingTo("70.80");
        assertThat(report.mealTotal()).isEqualByComparingTo("13.50");
        // Parking is VAT-bearing → in Net/VAT, not a tax-free subtotal (12.00 @ 25.5%).
        assertThat(report.netTotal()).isEqualByComparingTo("9.56");
        assertThat(report.vatTotal()).isEqualByComparingTo("2.44");
        // Grand total sums everything: parking gross 12.00 + 54 + 70.80 + 13.50.
        assertThat(report.total()).isEqualByComparingTo("150.30");
        // Generated lines trail in kind (declaration) order.
        assertThat(report.getLines().stream().map(ExpenseLine::getGeneratedKind).toList())
                .containsExactly(GeneratedLineKind.PER_DIEM_FULL,
                        GeneratedLineKind.KILOMETRE, GeneratedLineKind.MEAL,
                        GeneratedLineKind.PARKING);
    }

    @Test
    void theKilometreLineIsGeneratedAsDistanceTimesRate() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        // ADR-0023: km is the one generated multiple — quantity = 12.5 km, unit price
        // = €0.55/km. The flat kinds stay at quantity 1 with their amount as the unit
        // price, so their euros are untouched.
        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "54.00"),
                generated(GeneratedLineKind.KILOMETRE, KM_TYPE, RATE_0, "0.55", "12.5"),
                generated(GeneratedLineKind.MEAL, MEAL_TYPE, RATE_0, "13.50"),
                generated(GeneratedLineKind.PARKING, PARKING_TYPE, RATE_255, "12.00")))));

        var km = report.getLines().stream()
                .filter(line -> line.getGeneratedKind() == GeneratedLineKind.KILOMETRE)
                .findFirst().orElseThrow();
        assertThat(km.getQuantity()).isEqualByComparingTo("12.50");
        assertThat(km.getAmount()).isEqualByComparingTo("0.55");
        // 12.5 × 0.55 = 6.875 → €6.88 (HALF_UP), and that is what the subtotal sums.
        assertThat(km.gross()).isEqualByComparingTo("6.88");
        assertThat(report.kilometreTotal()).isEqualByComparingTo("6.88");

        // Every other generated line is still a flat quantity-1 line.
        assertThat(report.getLines().stream()
                .filter(line -> line.getGeneratedKind() != GeneratedLineKind.KILOMETRE)
                .map(ExpenseLine::getQuantity))
                .allSatisfy(q -> assertThat(q).isEqualByComparingTo("1"));
        assertThat(report.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(report.mealTotal()).isEqualByComparingTo("13.50");
    }

    @Test
    void reCostingReplacesTheKilometreLineInPlace() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.KILOMETRE, KM_TYPE, RATE_0, "0.55", "12.5")))));
        setId(report.getTravels().getFirst());
        var id = report.getTravels().getFirst().getId();
        var before = report.getLines().getFirst();

        // Same trip, more kilometres: the one existing line is regenerated, never
        // duplicated or left stale.
        report.reconcileTravels(List.of(travelSpecWith(id, List.of(
                generated(GeneratedLineKind.KILOMETRE, KM_TYPE, RATE_0, "0.55", "120")))));

        assertThat(report.getLines()).hasSize(1);
        assertThat(report.getLines().getFirst()).isSameAs(before);
        assertThat(before.getQuantity()).isEqualByComparingTo("120.00");
        assertThat(report.kilometreTotal()).isEqualByComparingTo("66.00");
    }

    @Test
    void editingATripDropsOnlyTheKindsItNoLongerEarns() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "54.00"),
                generated(GeneratedLineKind.KILOMETRE, KM_TYPE, RATE_0, "70.80")))));
        setId(report.getTravels().getFirst());
        var id = report.getTravels().getFirst().getId();

        // Re-cost: per-diem changes, kilometre drops, parking appears.
        report.reconcileTravels(List.of(travelSpecWith(id, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "27.00"),
                generated(GeneratedLineKind.PARKING, PARKING_TYPE, RATE_255, "12.00")))));

        assertThat(report.getLines()).hasSize(2);
        assertThat(report.perDiemTotal()).isEqualByComparingTo("27.00");
        assertThat(report.kilometreTotal()).isEqualByComparingTo("0.00");
        assertThat(report.netTotal()).isEqualByComparingTo("9.56");
    }

    // --- The split per-diem: two kinds, one subtotal (issue #124, ADR-0023) ---

    @Test
    void bothPerDiemKindsGenerateTheirOwnLineAndShareTheSubtotal() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        // A day-plus trip: 1 full day (€54.00) + 1 partial day (€25.00) — two honest
        // days × rate lines whose gross sums into the single per-diem subtotal.
        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "54.00", "1"),
                generated(GeneratedLineKind.PER_DIEM_PARTIAL, TRAVEL_TYPE, RATE_0, "25.00",
                        "1")))));

        assertThat(report.getLines()).hasSize(2);
        assertThat(report.getLines().stream().map(ExpenseLine::getGeneratedKind))
                .containsExactly(GeneratedLineKind.PER_DIEM_FULL,
                        GeneratedLineKind.PER_DIEM_PARTIAL);
        assertThat(report.perDiemTotal()).isEqualByComparingTo("79.00");
        // Both are tax-free: nothing leaks into Net/VAT, and the total is the subtotal.
        assertThat(report.netTotal()).isEqualByComparingTo("0.00");
        assertThat(report.vatTotal()).isEqualByComparingTo("0.00");
        assertThat(report.total()).isEqualByComparingTo("79.00");
    }

    @Test
    void multiDayPerDiemLinesCarryTheDayCountAsTheirQuantity() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        // 2 full days × €54.00 + 1 partial × €25.00 = €133.00, as quantity × unit.
        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "54.00", "2"),
                generated(GeneratedLineKind.PER_DIEM_PARTIAL, TRAVEL_TYPE, RATE_0, "25.00",
                        "1")))));

        var full = generatedLine(report, GeneratedLineKind.PER_DIEM_FULL);
        assertThat(full.getQuantity()).isEqualByComparingTo("2");
        assertThat(full.getAmount()).isEqualByComparingTo("54.00");
        assertThat(full.gross()).isEqualByComparingTo("108.00");
        assertThat(report.perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void reCostingReconcilesBothPerDiemKindsWithoutLeavingAStaleLine() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);
        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "54.00", "1"),
                generated(GeneratedLineKind.PER_DIEM_PARTIAL, TRAVEL_TYPE, RATE_0, "25.00",
                        "1")))));
        setId(report.getTravels().getFirst());
        var id = report.getTravels().getFirst().getId();
        var fullBefore = generatedLine(report, GeneratedLineKind.PER_DIEM_FULL);

        // Shorten the trip to a whole 24 h: the full-day line is re-costed in place and
        // the partial-day line — no longer earned — is dropped, not left stale.
        report.reconcileTravels(List.of(travelSpecWith(id, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "54.00",
                        "1")))));

        assertThat(report.getLines()).hasSize(1);
        assertThat(report.getLines().getFirst()).isSameAs(fullBefore);
        assertThat(report.perDiemTotal()).isEqualByComparingTo("54.00");
    }

    @Test
    void aFreeMealHalvesTheUnitPriceOfBothPerDiemLines() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        // ADR-0023: halving rides the unit price, so the quantities stay honest day
        // counts — 2 full days at €27.00 + 1 partial at €12.50 = €66.50.
        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE, RATE_0, "27.00", "2"),
                generated(GeneratedLineKind.PER_DIEM_PARTIAL, TRAVEL_TYPE, RATE_0, "12.50",
                        "1")))));

        assertThat(generatedLine(report, GeneratedLineKind.PER_DIEM_FULL).getQuantity())
                .isEqualByComparingTo("2");
        assertThat(generatedLine(report, GeneratedLineKind.PER_DIEM_PARTIAL).getQuantity())
                .isEqualByComparingTo("1");
        assertThat(report.perDiemTotal()).isEqualByComparingTo("66.50");
    }

    @Test
    void aParkingOnlyTripGeneratesNoTaxFreeSubtotal() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        report.reconcileTravels(List.of(travelSpecWith(null, List.of(
                generated(GeneratedLineKind.PARKING, PARKING_TYPE, RATE_255, "20.00")))));

        assertThat(report.getLines()).hasSize(1);
        assertThat(report.perDiemTotal()).isEqualByComparingTo("0.00");
        assertThat(report.total()).isEqualByComparingTo("20.00");
        assertThat(report.netTotal().add(report.vatTotal())).isEqualByComparingTo("20.00");
    }

    @Test
    void reconcileTravelsWithAnUnknownIdIsRejected() {
        var report = new ExpenseReport(OWNER, LocalDate.of(2026, 7, 10), null);

        assertThatThrownBy(() -> report.reconcileTravels(
                List.of(travelSpec(999L, "54.00", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExpenseLineSpec spec(Long id, String amount, VatRate rate) {
        return ExpenseLineSpec.of(id, TYPE, new BigDecimal(amount), rate, null);
    }

    /** A spec with an explicit quantity — {@code amount} is the unit price (ADR-0023). */
    private static ExpenseLineSpec spec(Long id, String unitPrice, String quantity,
            VatRate rate) {
        return new ExpenseLineSpec(id, TYPE, new BigDecimal(unitPrice),
                new BigDecimal(quantity), rate, null);
    }

    private static TravelSpec travelSpec(Long id, String perDiem, String explanation) {
        var lines = new ArrayList<GeneratedLineSpec>();
        if (new BigDecimal(perDiem).signum() != 0) {
            lines.add(GeneratedLineSpec.flat(GeneratedLineKind.PER_DIEM_FULL, TRAVEL_TYPE,
                    RATE_0, new BigDecimal(perDiem), explanation));
        }
        return travelSpecWith(id, lines);
    }

    private static TravelSpec travelSpecWith(Long id, List<GeneratedLineSpec> lines) {
        return travelSpecWith(id, Map.of(), lines);
    }

    /** A trip spec carrying Quantity Overrides as trip inputs (ADR-0024). */
    private static TravelSpec travelSpecWith(Long id,
            Map<GeneratedLineKind, QuantityOverride> overrides,
            List<GeneratedLineSpec> lines) {
        return new TravelSpec(id, DEP, DEP.plusHours(11), "Helsinki", "Client visit",
                "Finland", false, false, false, BigDecimal.ZERO.setScale(2), false,
                BigDecimal.ZERO.setScale(2), overrides, lines);
    }

    private static GeneratedLineSpec generated(GeneratedLineKind kind, ExpenseType type,
            VatRate rate, String amount) {
        return GeneratedLineSpec.flat(kind, type, rate, new BigDecimal(amount),
                kind + " line");
    }

    /** A generated spec that is a real multiple — the kilometre shape (ADR-0023). */
    private static GeneratedLineSpec generated(GeneratedLineKind kind, ExpenseType type,
            VatRate rate, String unitPrice, String quantity) {
        return new GeneratedLineSpec(kind, type, rate, new BigDecimal(unitPrice),
                new BigDecimal(quantity), kind + " line");
    }

    /** The report's single generated line of one kind (fails if the trip earned none). */
    private static ExpenseLine generatedLine(ExpenseReport report,
            GeneratedLineKind kind) {
        return report.getLines().stream()
                .filter(line -> line.getGeneratedKind() == kind)
                .findFirst().orElseThrow();
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

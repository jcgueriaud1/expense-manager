package com.vaadin.expensemanager.approval.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.security.LocalUserDetailsService;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service + method-security integration test (pyramid layer 2, ADR-0008,
 * ADR-0012) for {@link ApprovalService}.
 *
 * <p>Exercises the ADMIN-only, non-owner-scoped approval surface: the queue spans
 * every owner newest-first with the submitter's name, review loads another user's
 * report, and approve moves {@code SUBMITTED → APPROVED} recording the reviewing
 * admin. Method security is proven both ways — a USER is denied, an ADMIN allowed.
 *
 * <p>Authenticates by setting the app's global {@code SecurityContextHolder}
 * (F-020), the same pattern as {@code ExpenseReportServiceIntegrationTest}; the
 * default is the seeded admin, and the denial test re-authenticates as the plain
 * user. Reports owned by <em>other</em> users are seeded straight through the
 * repository + aggregate, bypassing the owner-scoped {@code ExpenseReportService}.
 */
class ApprovalServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@vaadin.com";

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ExpenseReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    private VatRateRepository vatRateRepository;

    @Autowired
    private LocalUserDetailsService userDetailsService;

    @Autowired
    private SecurityContextHolderStrategy securityContextHolderStrategy;

    @AfterEach
    void clearAuthentication() {
        securityContextHolderStrategy.clearContext();
    }

    @Test
    void listSubmittedSpansAllOwnersNewestFirstWithSubmitterName() {
        authenticateAs(ADMIN_EMAIL);
        // Two submitted reports owned by different users, plus a DRAFT that must
        // never appear. The plain user's is submitted later (newer submitted-at).
        var adminReport = seedSubmittedReport(ADMIN_EMAIL, LocalDate.of(2026, 6, 1),
                "admin trip", Instant.parse("2026-07-10T09:00:00Z"));
        var userReport = seedSubmittedReport(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip", Instant.parse("2026-07-12T09:00:00Z"));
        seedDraft(LocalUserSeeder.PLAIN_USER_EMAIL, LocalDate.of(2026, 6, 3), "draft");

        var queue = approvalService.listSubmitted();

        assertThat(queue).extracting(ReviewSummaryDto::id)
                .containsExactly(userReport, adminReport); // newest submission first
        assertThat(queue).allMatch(dto -> dto.status() == ReportStatus.SUBMITTED);
        assertThat(queue).extracting(ReviewSummaryDto::submitterName)
                .containsExactly("Demo User", "Expense Admin");
        assertThat(queue.getFirst().submittedAt())
                .isEqualTo(Instant.parse("2026-07-12T09:00:00Z"));
    }

    @Test
    void listReviewedSpansAllOwnersNewestDecisionFirstCarryingTheDecision() {
        authenticateAs(ADMIN_EMAIL);
        // One approved and one rejected report owned by different users, decided at
        // different times, plus a SUBMITTED and a DRAFT that must never appear.
        var approved = seedApprovedReport(ADMIN_EMAIL, LocalDate.of(2026, 6, 1),
                "admin trip", Instant.parse("2026-07-14T08:00:00Z"));
        var rejected = seedRejectedReport(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip",
                "Please itemise the taxi fares.", Instant.parse("2026-07-16T08:00:00Z"));
        seedSubmittedReport(LocalUserSeeder.PLAIN_USER_EMAIL, LocalDate.of(2026, 6, 3),
                "pending", Instant.parse("2026-07-12T09:00:00Z"));
        seedDraft(LocalUserSeeder.PLAIN_USER_EMAIL, LocalDate.of(2026, 6, 4), "draft");

        var history = approvalService.listReviewed();

        // Only terminal statuses, newest decision first (rejection was later).
        assertThat(history).extracting(ReviewedSummaryDto::id)
                .containsExactly(rejected, approved);
        assertThat(history).extracting(ReviewedSummaryDto::status)
                .containsExactly(ReportStatus.REJECTED, ReportStatus.APPROVED);
        // The decision is carried: deciding admin, decided-at, and the reason (reject only).
        var first = history.getFirst();
        assertThat(first.submitterName()).isEqualTo("Demo User");
        assertThat(first.decidedByName()).isEqualTo("Expense Admin");
        assertThat(first.decidedAt()).isEqualTo(Instant.parse("2026-07-16T08:00:00Z"));
        assertThat(first.rejectionComment()).isEqualTo("Please itemise the taxi fares.");
        // The approved row carries no rejection comment.
        assertThat(history.get(1).decidedByName()).isEqualTo("Expense Admin");
        assertThat(history.get(1).decidedAt()).isEqualTo(Instant.parse("2026-07-14T08:00:00Z"));
        assertThat(history.get(1).rejectionComment()).isNull();
    }

    @Test
    void findForReviewLoadsAnotherUsersReport() {
        authenticateAs(ADMIN_EMAIL);
        var userReport = seedSubmittedReport(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip", Instant.parse("2026-07-12T09:00:00Z"));

        var detail = approvalService.findForReview(userReport);

        assertThat(detail.id()).isEqualTo(userReport);
        assertThat(detail.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(detail.additionalInformation()).isEqualTo("user trip");
        // The status history rides along for the audit trail (StatusChangeDto).
        assertThat(detail.statusHistory()).hasSize(1);
        assertThat(detail.statusHistory().getFirst().toStatus())
                .isEqualTo(ReportStatus.SUBMITTED);
        assertThat(detail.statusHistory().getFirst().actorName()).isEqualTo("Demo User");
    }

    @Test
    void approveMovesSubmittedToApprovedAndRecordsTheAdmin() {
        authenticateAs(ADMIN_EMAIL);
        var userReport = seedSubmittedReport(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip", Instant.parse("2026-07-12T09:00:00Z"));
        var version = approvalService.findForReview(userReport).version();

        var approved = approvalService.approve(userReport, version);

        assertThat(approved.status()).isEqualTo(ReportStatus.APPROVED);
        var reloaded = reportRepository.findById(userReport).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(reloaded.getStatusHistory()).hasSize(2);
        var change = reloaded.getStatusHistory().get(1);
        assertThat(change.getFromStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(change.getToStatus()).isEqualTo(ReportStatus.APPROVED);
        // The acting user is the reviewing ADMIN, not the owner.
        assertThat(change.getActingUser().getEmail()).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void approveWithAStaleVersionThrowsOptimisticLockFailure() {
        authenticateAs(ADMIN_EMAIL);
        var userReport = seedSubmittedReport(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip", Instant.parse("2026-07-12T09:00:00Z"));

        assertThatThrownBy(() -> approvalService.approve(userReport, 999L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(reportRepository.findById(userReport).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    void rejectMovesSubmittedToRejectedAndPersistsTheReasonAndAdmin() {
        authenticateAs(ADMIN_EMAIL);
        var userReport = seedSubmittedReport(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip", Instant.parse("2026-07-12T09:00:00Z"));
        var version = approvalService.findForReview(userReport).version();

        var rejected = approvalService.reject(userReport,
                "Please itemise the taxi fares.", version);

        assertThat(rejected.status()).isEqualTo(ReportStatus.REJECTED);
        var reloaded = reportRepository.findById(userReport).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(reloaded.getStatusHistory()).hasSize(2);
        var change = reloaded.getStatusHistory().get(1);
        assertThat(change.getFromStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(change.getToStatus()).isEqualTo(ReportStatus.REJECTED);
        // The reason is persisted, and the actor is the reviewing ADMIN.
        assertThat(change.getComment()).isEqualTo("Please itemise the taxi fares.");
        assertThat(change.getActingUser().getEmail()).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void rejectWithABlankCommentIsRejectedAndDoesNotTransition() {
        authenticateAs(ADMIN_EMAIL);
        var userReport = seedSubmittedReport(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip", Instant.parse("2026-07-12T09:00:00Z"));
        var version = approvalService.findForReview(userReport).version();

        assertThatThrownBy(() -> approvalService.reject(userReport, "   ", version))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reportRepository.findById(userReport).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    void rejectWithAStaleVersionThrowsOptimisticLockFailure() {
        authenticateAs(ADMIN_EMAIL);
        var userReport = seedSubmittedReport(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip", Instant.parse("2026-07-12T09:00:00Z"));

        assertThatThrownBy(() -> approvalService.reject(userReport, "stale", 999L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(reportRepository.findById(userReport).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    void rejectingANonSubmittedReportIsRejected() {
        authenticateAs(ADMIN_EMAIL);
        var draft = seedDraft(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 3), "draft");
        var version = reportRepository.findById(draft).orElseThrow().getVersion();

        assertThatThrownBy(() -> approvalService.reject(draft, "no", version))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvingANonSubmittedReportIsRejected() {
        authenticateAs(ADMIN_EMAIL);
        var draft = seedDraft(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 3), "draft");
        var version = reportRepository.findById(draft).orElseThrow().getVersion();

        assertThatThrownBy(() -> approvalService.approve(draft, version))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aUserIsDeniedEveryApprovalMethod() {
        authenticateAs(LocalUserSeeder.PLAIN_USER_EMAIL);

        assertThatThrownBy(() -> approvalService.listSubmitted())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> approvalService.listReviewed())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> approvalService.findForReview(1L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> approvalService.approve(1L, 0L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> approvalService.reject(1L, "denied", 0L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- helpers -----------------------------------------------------------

    private void authenticateAs(String email) {
        SecurityContextHolder.setContextHolderStrategy(securityContextHolderStrategy);
        var principal = userDetailsService.loadUserByUsername(email);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", principal.getAuthorities());
        var context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
    }

    /** Seeds a SUBMITTED report owned by {@code email}, submitted at {@code at}. */
    private Long seedSubmittedReport(String email, LocalDate date, String info,
            Instant at) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(new ExpenseLineSpec(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, at);
        return reportRepository.save(report).getId();
    }

    /** Seeds an APPROVED report owned by {@code email}, decided by the admin at {@code at}. */
    private Long seedApprovedReport(String email, LocalDate date, String info, Instant at) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(new ExpenseLineSpec(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, Instant.parse("2026-07-10T09:00:00Z"));
        report.approve(admin, at);
        return reportRepository.save(report).getId();
    }

    /** Seeds a REJECTED report owned by {@code email}, decided by the admin at {@code at}. */
    private Long seedRejectedReport(String email, LocalDate date, String info,
            String reason, Instant at) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(new ExpenseLineSpec(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, Instant.parse("2026-07-10T09:00:00Z"));
        report.reject(admin, reason, at);
        return reportRepository.save(report).getId();
    }

    private Long seedDraft(String email, LocalDate date, String info) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(new ExpenseLineSpec(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        return reportRepository.save(report).getId();
    }

    private ExpenseType firstType() {
        return expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
    }

    private VatRate firstRate() {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().getFirst();
    }
}

package com.vaadin.expensemanager.approval.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.security.LocalUserDetailsService;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Committed-state optimistic-lock test (pyramid layer 2, ADR-0011/ADR-0012) for
 * {@link ApprovalService#approve} — modelled on
 * {@code ExpenseReportOptimisticLockIntegrationTest}.
 *
 * <p>The documented exception to the {@code @Transactional}-rollback default
 * (ADR-0012): {@code @Version} behaviour needs real commits, so this class does
 * <em>not</em> extend {@code AbstractIntegrationTest}. Each service call commits
 * its own transaction and the reports created here are cleaned up explicitly. The
 * container is re-declared (F-008) and attaches to the same singleton via
 * {@code .withReuse(true)}. Authenticates as the seeded admin through the global
 * {@code SecurityContextHolder} (F-020).
 */
@SpringBootTest
@ActiveProfiles("test")
class ApprovalOptimisticLockIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

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

    private final List<Long> createdReportIds = new ArrayList<>();

    @BeforeEach
    void authenticateAsAdmin() {
        SecurityContextHolder.setContextHolderStrategy(securityContextHolderStrategy);
        var principal = userDetailsService.loadUserByUsername("admin@vaadin.com");
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", principal.getAuthorities());
        var context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
    }

    @AfterEach
    void cleanUp() {
        createdReportIds.forEach(id ->
                reportRepository.findById(id).ifPresent(reportRepository::delete));
        createdReportIds.clear();
        securityContextHolderStrategy.clearContext();
    }

    @Test
    void aStaleApproveIsRejectedNotDoubleProcessed() {
        var id = seedSubmittedReport();
        // The reviewer's view captures the version before anyone acts.
        var staleVersion = approvalService.findForReview(id).version();

        // A first, committed approve advances the version in the DB.
        approvalService.approve(id, staleVersion);

        // The stale reviewer still holds the old version — approving again must be
        // rejected on the version pre-flight, never re-processing the report.
        assertThatThrownBy(() -> approvalService.approve(id, staleVersion))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Load through the review path so the status history maps in-session.
        var latest = approvalService.findForReview(id);
        assertThat(latest.status()).isEqualTo(ReportStatus.APPROVED);
        // Exactly one approval transition happened (submit + one approve).
        assertThat(latest.statusHistory()).hasSize(2);
        assertThat(latest.version()).isGreaterThan(staleVersion);
    }

    @Test
    void aStaleRejectIsRejectedNotDoubleProcessed() {
        var id = seedSubmittedReport();
        // The reviewer captures the version before anyone acts.
        var staleVersion = approvalService.findForReview(id).version();

        // A first, committed reject advances the version in the DB.
        approvalService.reject(id, "Please attach receipts.", staleVersion);

        // The stale reviewer still holds the old version — rejecting again must be
        // refused on the version pre-flight, never re-processing the report.
        assertThatThrownBy(() -> approvalService.reject(id, "again", staleVersion))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        var latest = approvalService.findForReview(id);
        assertThat(latest.status()).isEqualTo(ReportStatus.REJECTED);
        // Exactly one rejection transition happened (submit + one reject).
        assertThat(latest.statusHistory()).hasSize(2);
        assertThat(latest.statusHistory().get(1).comment())
                .isEqualTo("Please attach receipts.");
        assertThat(latest.version()).isGreaterThan(staleVersion);
    }

    private Long seedSubmittedReport() {
        var owner = userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL)
                .orElseThrow();
        var type = expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
        var rate = vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().getFirst();
        var report = new ExpenseReport(owner, LocalDate.of(2026, 7, 10), "conflict test");
        report.reconcileLines(List.of(new ExpenseLineSpec(null, type,
                new BigDecimal("100.00"), rate, null)));
        report.submit(owner, Instant.parse("2026-07-12T09:00:00Z"));
        var id = reportRepository.save(report).getId();
        createdReportIds.add(id);
        return id;
    }
}

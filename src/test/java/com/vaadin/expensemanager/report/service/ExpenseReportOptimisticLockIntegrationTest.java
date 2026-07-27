package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.security.LocalUserDetailsService;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.User;
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
 * {@link ExpenseReportService}.
 *
 * <p>This is the <strong>documented exception</strong> to the
 * {@code @Transactional}-rollback default (ADR-0012): {@code @Version} behaviour
 * cannot be exercised inside a single rolled-back transaction, so this class
 * deliberately does <em>not</em> extend {@code AbstractIntegrationTest} (which is
 * {@code @Transactional}). Each service call commits its own transaction, the
 * version actually advances in the DB, and the reports created here are cleaned
 * up explicitly in {@link #cleanUp()} instead of by rollback.
 *
 * <p>The container is re-declared (as in the view tests, F-008) because the
 * transaction semantics rule out inheriting the shared base. With
 * {@code .withReuse(true)} it attaches to the same singleton container.
 *
 * <p>Authenticates through the global {@code SecurityContextHolder} as the
 * seeded plain user, mirroring {@link ExpenseReportServiceIntegrationTest} (the
 * F-020 rationale applies identically).
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpenseReportOptimisticLockIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    private ExpenseReportService service;

    @Autowired
    private ExpenseReportRepository reportRepository;

    @Autowired
    private ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    private VatRateRepository vatRateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocalUserDetailsService userDetailsService;

    @Autowired
    private SecurityContextHolderStrategy securityContextHolderStrategy;

    private final List<Long> createdReportIds = new ArrayList<>();

    @BeforeEach
    void authenticateAsPlainUser() {
        SecurityContextHolder.setContextHolderStrategy(securityContextHolderStrategy);
        var principal = userDetailsService.loadUserByUsername(
                LocalUserSeeder.PLAIN_USER_EMAIL);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", principal.getAuthorities());
        var context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
    }

    @AfterEach
    void cleanUp() {
        // No rollback here — remove the committed reports so the shared container
        // stays clean for other test classes (repository delete bypasses the
        // domain's draft-only guard, which is what we want for teardown).
        createdReportIds.forEach(id ->
                reportRepository.findById(id).ifPresent(reportRepository::delete));
        createdReportIds.clear();
        securityContextHolderStrategy.clearContext();
    }

    @Test
    void aStaleWriteAfterACommittedEditIsRejectedNotOverwritten() {
        var type = expenseTypeRepository
                .findByActiveTrueOrderByDisplayOrderAscIdAsc().getFirst();
        var rate = firstRate();

        // Commit a draft with one line; capture the version the "stale editor" sees.
        var id = service.create(dtoWithLine(type, rate, "100.00"));
        createdReportIds.add(id);
        var staleView = service.findMine(id);

        // A concurrent, committed edit advances the version in the DB.
        service.update(id, new ReportDetailDto(id, LocalDate.of(2026, 7, 20),
                "edited by the other session", ReportStatus.DRAFT,
                staleView.version(), staleView.lines(), staleView.total(),
                staleView.netTotal(), staleView.vatTotal()), staleView.version());

        // The stale editor still holds the old version — its submit must be
        // rejected, never silently overwriting the committed edit (ADR-0011).
        assertThatThrownBy(() -> service.submit(id, staleView.version()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The committed edit survived and the report is still an (unsubmitted) DRAFT.
        var latest = service.findMine(id);
        assertThat(latest.status()).isEqualTo(ReportStatus.DRAFT);
        assertThat(latest.additionalInformation())
                .isEqualTo("edited by the other session");
        assertThat(latest.version()).isGreaterThan(staleView.version());
    }

    @Test
    void aStaleResubmitAfterACommittedEditIsRejectedNotOverwritten() {
        // Seed a committed REJECTED report owned by the plain user (submit → reject
        // driven through the domain, past the owner-scoped service).
        var me = userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL)
                .orElseThrow();
        var id = seedRejectedReport(me);
        createdReportIds.add(id);
        var staleView = service.findMine(id);

        // A concurrent, committed edit (still REJECTED, still editable) advances the
        // version in the DB.
        service.update(id, new ReportDetailDto(id, LocalDate.of(2026, 7, 20),
                "edited by the other session", ReportStatus.REJECTED,
                staleView.version(), staleView.lines(), staleView.total(),
                staleView.netTotal(), staleView.vatTotal()), staleView.version());

        // The stale editor's resubmit must be rejected, never silently overwriting
        // the committed edit (ADR-0011).
        assertThatThrownBy(() -> service.resubmit(id, staleView.version()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The committed edit survived and the report is still REJECTED (not sent back
        // to the queue on a stale view).
        var latest = service.findMine(id);
        assertThat(latest.status()).isEqualTo(ReportStatus.REJECTED);
        assertThat(latest.additionalInformation())
                .isEqualTo("edited by the other session");
        assertThat(latest.version()).isGreaterThan(staleView.version());
    }

    /** Persists a committed {@code REJECTED} report with one line owned by {@code owner}. */
    private Long seedRejectedReport(User owner) {
        var type = expenseTypeRepository
                .findByActiveTrueOrderByDisplayOrderAscIdAsc().getFirst();
        var report = new ExpenseReport(owner, LocalDate.of(2026, 7, 10), "needs work");
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, type,
                new BigDecimal("100.00"), firstRate(), "hotel")));
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        report.submit(owner, Instant.parse("2026-07-11T09:00:00Z"));
        report.reject(admin, "Please attach the receipt.",
                Instant.parse("2026-07-12T09:00:00Z"));
        reportRepository.save(report);
        return report.getId();
    }

    private VatRate firstRate() {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().getFirst();
    }

    private ReportDetailDto dtoWithLine(ExpenseType type, VatRate rate, String amount) {
        var zero = BigDecimal.ZERO.setScale(2);
        var line = ExpenseLineDto.of(null, type.getId(), type.getName(),
                rate.getId(), rate.getValue(), new BigDecimal(amount), "line");
        return new ReportDetailDto(null, LocalDate.of(2026, 7, 10), "conflict test",
                ReportStatus.DRAFT, 0L, List.of(line), zero, zero, zero);
    }
}

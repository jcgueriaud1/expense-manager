package com.vaadin.expensemanager.approval.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.locator.Locators;
import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared base for the approval view tests (pyramid layer 3, ADR-0012) —
 * {@link ApprovalQueueViewUiTest} and {@link ApprovalAccessUiTest}.
 *
 * <p>Modelled on {@code AdminToolsViewUiTest}, <strong>not</strong> the report
 * feature's {@code AbstractReportViewUiTest}: it extends {@link SpringBrowserlessTest}
 * directly and is deliberately <em>not</em> {@code @Transactional}. Rollback
 * isolation interacts badly with browserless security-context ordering once
 * several browserless classes have run (a report opens as {@code LoginView},
 * F-020/F-008 family), whereas the non-transactional {@code @WithUserDetails}
 * browserless pattern is stable. So reports are seeded straight through the
 * repository + aggregate (bypassing the owner-scoped service, to reach any owner
 * and any status) and cleaned up explicitly in {@link #cleanUp()}.
 *
 * <p>The singleton Testcontainers Postgres is re-declared (F-008) and attaches to
 * the same container via {@code .withReuse(true)}.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractApprovalViewUiTest extends SpringBrowserlessTest
        implements Locators {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    protected ExpenseReportRepository reportRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    protected VatRateRepository vatRateRepository;

    private final List<Long> createdReportIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // No rollback here (non-transactional): remove the reports seeded through
        // the repository so the shared container stays clean for other classes.
        createdReportIds.forEach(id ->
                reportRepository.findById(id).ifPresent(reportRepository::delete));
        createdReportIds.clear();
    }

    /**
     * Seeds a SUBMITTED report owned by {@code email} (any user), bypassing the
     * owner-scoped service, so an admin test sees another user's report in the
     * queue. Returns its id.
     */
    protected Long seedSubmittedReportForOwner(String email, LocalDate date,
            String info) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, Instant.parse("2026-07-12T09:00:00Z"));
        return track(reportRepository.save(report).getId());
    }

    /**
     * Seeds an APPROVED report owned by {@code email} (the seeded admin recorded as
     * approver), so an owner test opens its own already-approved report read-only.
     * Returns its id.
     */
    protected Long seedApprovedReportForOwner(String email, LocalDate date,
            String info) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, Instant.parse("2026-07-12T09:00:00Z"));
        report.approve(admin, Instant.parse("2026-07-14T08:00:00Z"));
        return track(reportRepository.save(report).getId());
    }

    /**
     * Seeds a REJECTED report owned by {@code email} (the seeded admin recorded as
     * the rejecting reviewer, with a reason), so a history test sees a rejected
     * outcome. Returns its id.
     */
    protected Long seedRejectedReportForOwner(String email, LocalDate date,
            String info, String reason) {
        var owner = userRepository.findByEmail(email).orElseThrow();
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        report.submit(owner, Instant.parse("2026-07-12T09:00:00Z"));
        report.reject(admin, reason, Instant.parse("2026-07-15T08:00:00Z"));
        return track(reportRepository.save(report).getId());
    }

    private Long track(Long id) {
        createdReportIds.add(id);
        return id;
    }

    private ExpenseType firstType() {
        return expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
    }

    private VatRate firstRate() {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().getFirst();
    }
}

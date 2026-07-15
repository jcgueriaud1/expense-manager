package com.vaadin.expensemanager.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds labelled expense-report fixtures for the {@code local} profile so a
 * manual or Playwright smoke test lands directly on the screen under test with
 * zero setup clicks (issue #68, F-041). Mirrors {@link LocalUserSeeder}: an
 * idempotent {@link ApplicationRunner} that only seeds an empty database.
 *
 * <p>Reports are written straight through the repository + aggregate — the same
 * bypass-the-owner-scoped-service technique the approval UI tests use in
 * {@code AbstractApprovalViewUiTest} — which is the only way to reach an
 * arbitrary owner (the admin) and a pre-{@code APPROVED} state without driving
 * the whole create → line → submit → approve flow through the UI.
 *
 * <p>The fixtures (see {@code docs/manual-verification.md}):
 * <ul>
 *   <li>a {@code DRAFT} owned by the plain user — edit path;</li>
 *   <li>a {@code SUBMITTED} owned by the plain user — queue + review + approve;</li>
 *   <li>a {@code SUBMITTED} owned by the admin — cross-owner queue visibility;</li>
 *   <li>an {@code APPROVED} owned by the plain user — owner-sees-approved;</li>
 *   <li>a {@code REJECTED} owned by the plain user — edit + resubmit (Phase 5.5).</li>
 * </ul>
 *
 * <p><strong>Profile-scoped to {@code local}</strong>: never runs in
 * {@code staging}/{@code prod} (ADR-0007), where reports are real user data, nor
 * in {@code test}, where the browserless suites seed and assert their own
 * fixtures. Ordered after {@link LocalUserSeeder} (via {@link Order}) so the
 * plain user it owns reports for already exists.
 */
@Component
@Profile("local")
@Order(LocalReportSeeder.SEED_ORDER)
public class LocalReportSeeder implements ApplicationRunner {

    /** Runs after {@link LocalUserSeeder} (which seeds the plain user this needs). */
    static final int SEED_ORDER = 100;

    private static final Logger log = LoggerFactory.getLogger(LocalReportSeeder.class);

    private static final Instant SUBMITTED_AT = Instant.parse("2026-07-12T09:00:00Z");
    private static final Instant APPROVED_AT = Instant.parse("2026-07-14T08:00:00Z");
    private static final Instant REJECTED_AT = Instant.parse("2026-07-13T10:30:00Z");

    private final ExpenseReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final VatRateRepository vatRateRepository;
    private final String adminEmail;

    public LocalReportSeeder(ExpenseReportRepository reportRepository,
            UserRepository userRepository, ExpenseTypeRepository expenseTypeRepository,
            VatRateRepository vatRateRepository,
            @Value("${app.admin.email}") String adminEmail) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.expenseTypeRepository = expenseTypeRepository;
        this.vatRateRepository = vatRateRepository;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (reportRepository.count() > 0) {
            return;
        }
        User user = userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL)
                .orElse(null);
        User admin = userRepository.findByEmail(adminEmail).orElse(null);
        if (user == null || admin == null) {
            log.warn("Skipping report seed: expected users {} and {} not present",
                    LocalUserSeeder.PLAIN_USER_EMAIL, adminEmail);
            return;
        }

        seedDraft(user, LocalDate.of(2026, 7, 10),
                "[seed] DRAFT — plain user, edit path");
        seedSubmitted(user, LocalDate.of(2026, 7, 11),
                "[seed] SUBMITTED — plain user, review + approve");
        seedSubmitted(admin, LocalDate.of(2026, 7, 9),
                "[seed] SUBMITTED — admin-owned, cross-owner queue");
        seedApproved(user, admin, LocalDate.of(2026, 7, 8),
                "[seed] APPROVED — plain user, owner-sees-approved");
        seedRejected(user, admin, LocalDate.of(2026, 7, 7),
                "[seed] REJECTED — plain user, edit + resubmit");

        log.info("Seeded 5 local expense-report fixtures (1 DRAFT, 2 SUBMITTED, "
                + "1 APPROVED, 1 REJECTED)");
    }

    private void seedDraft(User owner, LocalDate date, String info) {
        var report = newReportWithLine(owner, date, info);
        reportRepository.save(report);
    }

    private void seedSubmitted(User owner, LocalDate date, String info) {
        var report = newReportWithLine(owner, date, info);
        report.submit(owner, SUBMITTED_AT);
        reportRepository.save(report);
    }

    private void seedApproved(User owner, User admin, LocalDate date, String info) {
        var report = newReportWithLine(owner, date, info);
        report.submit(owner, SUBMITTED_AT);
        report.approve(admin, APPROVED_AT);
        reportRepository.save(report);
    }

    private void seedRejected(User owner, User admin, LocalDate date, String info) {
        var report = newReportWithLine(owner, date, info);
        report.submit(owner, SUBMITTED_AT);
        report.reject(admin, "Please attach the hotel receipt and split the "
                + "restaurant line by VAT rate.", REJECTED_AT);
        reportRepository.save(report);
    }

    private ExpenseReport newReportWithLine(User owner, LocalDate date, String info) {
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(new ExpenseLineSpec(null, firstType(),
                new BigDecimal("100.00"), firstRate(), null)));
        return report;
    }

    private ExpenseType firstType() {
        return expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
    }

    private VatRate firstRate() {
        return vatRateRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
    }
}

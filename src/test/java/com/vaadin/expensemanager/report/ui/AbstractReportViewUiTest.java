package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.locator.Locators;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.expensemanager.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared base for the report view tests (pyramid layer 3, ADR-0012) —
 * {@link MyReportsViewUiTest} and {@link ReportDetailViewUiTest}.
 *
 * <p>Mirrors {@code AbstractReferenceDataViewUiTest}: the singleton
 * Testcontainers Postgres (re-declared because {@link SpringBrowserlessTest}
 * occupies the single inheritance slot, F-008), {@code implements Locators} for
 * the fluent typed locator DSL, and {@code @Transactional} rollback so reports a
 * test seeds through the service roll back and leave the shared container clean.
 * The {@link ExpenseReportService} handle seeds the current user's reports and
 * asserts persisted state behind a UI action.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class AbstractReportViewUiTest extends SpringBrowserlessTest
        implements Locators {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    protected ExpenseReportService service;

    @Autowired
    protected ExpenseReportRepository reportRepository;

    @Autowired
    protected UserRepository userRepository;

    /** Seeds one DRAFT report owned by the current user; returns its id. */
    protected Long seedReport(LocalDate date, String info) {
        return service.create(new ReportDetailDto(null, date, info,
                ReportStatus.DRAFT, 0L, null));
    }

    /**
     * Seeds a report owned by the seeded admin (a <em>different</em> user),
     * bypassing the owner-scoped service, so tests can prove the current user's
     * views never surface it.
     */
    protected Long seedReportForAdmin(LocalDate date, String info) {
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        return reportRepository.save(new ExpenseReport(admin, date, info)).getId();
    }
}

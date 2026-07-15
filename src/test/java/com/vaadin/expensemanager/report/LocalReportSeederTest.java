package com.vaadin.expensemanager.report;

import java.util.List;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link LocalReportSeeder} (pyramid layer 2, ADR-0012).
 *
 * <p>The seeder bean itself is {@code @Profile("local")} so it is absent from
 * the {@code test} context; this drives it directly with the same autowired
 * repositories it would get at runtime, on the transactional integration base
 * (rolled back per method). The plain user is present via {@link LocalUserSeeder}
 * and the admin via the V2 migration, exactly as in a real {@code local} boot.
 */
class LocalReportSeederTest extends AbstractIntegrationTest {

    @Autowired
    private ExpenseReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    private VatRateRepository vatRateRepository;

    @Value("${app.admin.email}")
    private String adminEmail;

    private LocalReportSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new LocalReportSeeder(reportRepository, userRepository,
                expenseTypeRepository, vatRateRepository, adminEmail);
        // Start from a known-empty report table (rolled back with the test tx).
        reportRepository.deleteAll();
    }

    @Test
    void seedsTheFourLabelledFixturesOnEmptyDb() {
        seeder.run(null);

        List<ExpenseReport> reports = reportRepository.findAll();
        assertThat(reports).hasSize(4);

        String user = LocalUserSeeder.PLAIN_USER_EMAIL;
        assertThat(reports)
                .filteredOn(r -> r.getStatus() == ReportStatus.DRAFT)
                .singleElement()
                .satisfies(r -> assertThat(r.getOwner().getEmail()).isEqualTo(user));

        assertThat(reports)
                .filteredOn(r -> r.getStatus() == ReportStatus.SUBMITTED)
                .hasSize(2)
                .extracting(r -> r.getOwner().getEmail())
                .containsExactlyInAnyOrder(user, adminEmail);

        assertThat(reports)
                .filteredOn(r -> r.getStatus() == ReportStatus.APPROVED)
                .singleElement()
                .satisfies(r -> assertThat(r.getOwner().getEmail()).isEqualTo(user));
    }

    @Test
    void everySeededReportHasAtLeastOneLine() {
        seeder.run(null);

        assertThat(reportRepository.findAll())
                .allSatisfy(r -> assertThat(r.getLines()).isNotEmpty());
    }

    @Test
    void isIdempotentOnANonEmptyDb() {
        seeder.run(null);
        long afterFirst = reportRepository.count();

        seeder.run(null);

        assertThat(reportRepository.count()).isEqualTo(afterFirst).isEqualTo(4);
    }
}

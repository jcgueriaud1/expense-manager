package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test (pyramid layer 2, ADR-0012) for the data the redesigned report
 * list needs (issue #147): the trips and audit meta on {@link ReportSummaryDto},
 * the fetch plan that keeps them off the N+1 path, and
 * {@link ExpenseReportService#myMetrics()}.
 *
 * <p>Runs as the seeded plain user, who owns no reports in the {@code test}
 * profile — so every fixture here is one this test made, and the empty case is
 * simply "assert before creating anything".
 *
 * <p>Authenticates through the app's <strong>global</strong>
 * {@code SecurityContextHolder} rather than {@code @WithUserDetails}, for the
 * reason recorded in finding F-020 (and in
 * {@link ExpenseReportServiceIntegrationTest}).
 */
class ReportListDataIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ExpenseReportService service;

    @Autowired
    private ExpenseReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    private VatRateRepository vatRateRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private LocalUserDetailsService userDetailsService;

    @Autowired
    private SecurityContextHolderStrategy securityContextHolderStrategy;

    /** A trip year the allowance rates are seeded for. */
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 7, 1, 8, 0);

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    @BeforeEach
    void authenticateAsPlainUser() {
        SecurityContextHolder.setContextHolderStrategy(securityContextHolderStrategy);
        authenticateAs(LocalUserSeeder.PLAIN_USER_EMAIL);
    }

    @AfterEach
    void clearAuthentication() {
        securityContextHolderStrategy.clearContext();
    }

    // --- The summary projection (AC 1, plus the survey's three additions) ---

    @Test
    void eachSummaryCarriesItsTripsRouteAndDateRange() {
        service.create(dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                trip("Turku → Helsinki", DEPARTURE, DEPARTURE.plusHours(11)),
                trip("Copenhagen → Aarhus", DEPARTURE.plusDays(3),
                        DEPARTURE.plusDays(4)))));

        var trips = service.listMine().getFirst().trips();

        // One row per Travel, in insertion order, passed through verbatim: the
        // arrows are the user's own text, not a route the app composed.
        assertThat(trips).extracting(TripSummaryDto::destinations)
                .containsExactly("Turku → Helsinki", "Copenhagen → Aarhus");
        assertThat(trips.getFirst().departureAt()).isEqualTo(DEPARTURE);
        assertThat(trips.getFirst().returnAt()).isEqualTo(DEPARTURE.plusHours(11));
        assertThat(trips.getLast().returnAt()).isEqualTo(DEPARTURE.plusDays(4));
    }

    @Test
    void aReportWithoutTravelCarriesNoTripsRatherThanNull() {
        service.create(draftDto(LocalDate.of(2026, 7, 10), "desk supplies"));

        assertThat(service.listMine().getFirst().trips()).isEmpty();
    }

    @Test
    void theSummaryCarriesCreatedAtAlongsideTheUserEnteredReportDate() {
        var before = Instant.now();
        service.create(draftDto(LocalDate.of(2026, 7, 10), "client visit"));

        var summary = service.listMine().getFirst();

        // Two different facts: the footer renders createdAt, the list sorts and
        // filters on the user-entered reportDate.
        assertThat(summary.reportDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(summary.createdAt()).isNotNull()
                .isAfterOrEqualTo(before.minusSeconds(1));
    }

    @Test
    void onlyARejectedReportCarriesWhoRejectedItAndWhen() {
        var me = plainUser();
        var rejectedAt = Instant.parse("2026-07-12T09:00:00Z");
        seedReport(me, LocalDate.of(2026, 7, 10), "needs work",
                report -> {
                    report.submit(me, Instant.parse("2026-07-11T09:00:00Z"));
                    report.reject(admin(), "Please attach the receipt.", rejectedAt);
                });
        seedReport(me, LocalDate.of(2026, 7, 9), "fine as is",
                report -> report.submit(me, Instant.parse("2026-07-11T09:00:00Z")));

        var byInfo = service.listMine();
        var rejected = byInfo.stream()
                .filter(r -> "needs work".equals(r.additionalInformation()))
                .findFirst().orElseThrow();
        var submitted = byInfo.stream()
                .filter(r -> "fine as is".equals(r.additionalInformation()))
                .findFirst().orElseThrow();

        assertThat(rejected.rejectedByName()).isEqualTo("Expense Admin");
        assertThat(rejected.rejectedAt()).isEqualTo(rejectedAt);
        assertThat(submitted.rejectedByName()).isNull();
        assertThat(submitted.rejectedAt()).isNull();
    }

    @Test
    void aResubmittedReportDropsItsRejectionMeta() {
        var me = plainUser();
        var id = seedReport(me, LocalDate.of(2026, 7, 10), "reworked", report -> {
            report.submit(me, Instant.parse("2026-07-11T09:00:00Z"));
            report.reject(admin(), "Missing receipt.",
                    Instant.parse("2026-07-12T09:00:00Z"));
            report.resubmit(me, Instant.parse("2026-07-13T09:00:00Z"));
        });

        var summary = service.listMine().getFirst();
        assertThat(summary.id()).isEqualTo(id);
        // The footer entry describes the present state, not the report's past.
        assertThat(summary.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(summary.rejectedByName()).isNull();
    }

    // --- The fetch plan (AC 2) ---

    @Test
    void listMineIssuesTheSameNumberOfQueriesHoweverManyReportsThereAre() {
        seedReportWithTripAndHistory(LocalDate.of(2026, 7, 1));
        long forOneReport = statementsIssuedBy(service::listMine);

        for (int i = 2; i <= 5; i++) {
            seedReportWithTripAndHistory(LocalDate.of(2026, 7, i));
        }
        long forFiveReports = statementsIssuedBy(service::listMine);

        // The whole point: constant, not linear. Asserted on the statement count
        // rather than inferred from the mapping being correct.
        //
        // Five statements, and here is every one of them: the three batched
        // collection fetches (lines, status history, travels) plus the eagerly
        // mapped role set of each of the two distinct users who acted on these
        // reports (the owner submitted, the admin rejected). That second pair is
        // bounded by how many people have touched the list, not by how long it is —
        // which is why the count holds at five for five reports as it did for one.
        assertThat(forFiveReports).isEqualTo(forOneReport).isEqualTo(5);

        // …and the batched fetch really did populate every row.
        var reports = service.listMine();
        assertThat(reports).hasSize(5);
        assertThat(reports).allSatisfy(r -> {
            assertThat(r.trips()).hasSize(1);
            assertThat(r.total()).isGreaterThan(ZERO);
        });
    }

    // --- The metrics (AC 3–6, 8, 9) ---

    @Test
    void aUserWithNoReportsGetsZeroesNotNullsOrAnException() {
        var metrics = service.myMetrics();

        assertThat(metrics.needsYouCount()).isZero();
        assertThat(metrics.needsYouTotal()).isEqualByComparingTo(ZERO);
        assertThat(metrics.rejectedCount()).isZero();
        assertThat(metrics.inFlightCount()).isZero();
        assertThat(metrics.inFlightWaitDays()).isZero();
        assertThat(metrics.reimbursedTotal()).isEqualByComparingTo(ZERO);
        assertThat(metrics.approvedCount()).isZero();
        // Even with nothing to show, the caption still has a year.
        assertThat(metrics.reimbursedYear()).isEqualTo(LocalDate.now().getYear());
    }

    @Test
    void needsYouCountsDraftsAndRejectedTogetherWithRejectedBrokenOut() {
        var me = plainUser();
        seedReportWithLine(me, LocalDate.of(2026, 7, 1), "draft one", "100.00");
        seedReportWithLine(me, LocalDate.of(2026, 7, 2), "draft two", "200.00");
        seedReport(me, LocalDate.of(2026, 7, 3), "rejected", "300.00", report -> {
            report.submit(me, Instant.parse("2026-07-04T09:00:00Z"));
            report.reject(admin(), "No.", Instant.parse("2026-07-05T09:00:00Z"));
        });
        // Neither of these awaits the owner, so neither may reach the figure.
        seedReport(me, LocalDate.of(2026, 7, 6), "submitted", "400.00",
                report -> report.submit(me, Instant.parse("2026-07-07T09:00:00Z")));
        seedReport(me, LocalDate.of(2026, 7, 8), "approved", "500.00", report -> {
            report.submit(me, Instant.parse("2026-07-09T09:00:00Z"));
            report.approve(admin(), Instant.parse("2026-07-10T09:00:00Z"));
        });

        var metrics = service.myMetrics();

        // 2 drafts + 1 rejected = 3, with the rejected one counted out of the same
        // figure rather than added to it.
        assertThat(metrics.needsYouCount()).isEqualTo(3);
        assertThat(metrics.rejectedCount()).isEqualTo(1);
        assertThat(metrics.needsYouTotal()).isEqualByComparingTo("600.00");
    }

    @Test
    void inFlightWaitIsMeasuredFromTheLatestSubmittedChangeNotTheReportDate() {
        var me = plainUser();
        var now = Instant.now();
        // A report dated long ago but submitted yesterday has waited one day…
        seedReport(me, LocalDate.of(2026, 1, 5), "old date, fresh submit", "100.00",
                report -> report.submit(me, now.minus(1, ChronoUnit.DAYS)));
        // …and this one, submitted 36 days ago, is the queue's real age.
        seedReport(me, LocalDate.of(2026, 8, 1), "waiting", "100.00",
                report -> report.submit(me, now.minus(36, ChronoUnit.DAYS)));

        var metrics = service.myMetrics();

        assertThat(metrics.inFlightCount()).isEqualTo(2);
        assertThat(metrics.inFlightWaitDays()).isEqualTo(36);
    }

    @Test
    void aResubmitRestartsTheInFlightWaitFromTheLatestSubmittedChange() {
        var me = plainUser();
        var now = Instant.now();
        seedReport(me, LocalDate.of(2026, 7, 1), "round two", "100.00", report -> {
            report.submit(me, now.minus(40, ChronoUnit.DAYS));
            report.reject(admin(), "Fix it.", now.minus(30, ChronoUnit.DAYS));
            report.resubmit(me, now.minus(4, ChronoUnit.DAYS));
        });

        assertThat(service.myMetrics().inFlightWaitDays()).isEqualTo(4);
    }

    @Test
    void reimbursedCoversTheCurrentCalendarYearAndReturnsTheYearItUsed() {
        var me = plainUser();
        int year = LocalDate.now().getYear();
        seedApproved(me, "this year", "150.00", atStartOfDay(year, 3, 15));
        seedApproved(me, "also this year", "50.00", atStartOfDay(year, 4, 20));
        // Approved in the previous year — same owner, different calendar year.
        seedApproved(me, "last year", "999.00", atStartOfDay(year - 1, 11, 20));

        var metrics = service.myMetrics();

        assertThat(metrics.reimbursedYear()).isEqualTo(year);
        assertThat(metrics.approvedCount()).isEqualTo(2);
        assertThat(metrics.reimbursedTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void anotherUsersReportsNeverReachAnyMetric() {
        var me = plainUser();
        var other = admin();
        int year = LocalDate.now().getYear();
        seedReportWithLine(me, LocalDate.of(2026, 7, 1), "mine", "100.00");

        // The same three shapes, owned by someone else.
        seedReportWithLine(other, LocalDate.of(2026, 7, 2), "theirs", "1000.00");
        seedReport(other, LocalDate.of(2026, 7, 3), "theirs, in flight", "2000.00",
                report -> report.submit(other, Instant.now().minus(90,
                        ChronoUnit.DAYS)));
        seedApproved(other, "theirs, approved", "3000.00", atStartOfDay(year, 5, 1));

        var metrics = service.myMetrics();

        assertThat(metrics.needsYouCount()).isEqualTo(1);
        assertThat(metrics.needsYouTotal()).isEqualByComparingTo("100.00");
        assertThat(metrics.inFlightCount()).isZero();
        assertThat(metrics.inFlightWaitDays()).isZero();
        assertThat(metrics.approvedCount()).isZero();
        assertThat(metrics.reimbursedTotal()).isEqualByComparingTo(ZERO);
    }

    @Test
    void listMineStillShowsOnlyTheCurrentUsersReports() {
        seedReportWithLine(plainUser(), LocalDate.of(2026, 7, 1), "mine", "100.00");
        seedReportWithLine(admin(), LocalDate.of(2026, 8, 1), "theirs", "100.00");

        assertThat(service.listMine())
                .extracting(ReportSummaryDto::additionalInformation)
                .containsExactly("mine");
    }

    // ------------------------------------------------------------- fixtures

    /**
     * How many JDBC statements {@code work} issues, from a cleared persistence
     * context — so nothing already in memory hides a query that production would
     * make.
     */
    private long statementsIssuedBy(Runnable work) {
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        work.run();
        return stats.getPrepareStatementCount();
    }

    private void authenticateAs(String email) {
        var principal = userDetailsService.loadUserByUsername(email);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", principal.getAuthorities());
        var context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
    }

    private User plainUser() {
        return userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL)
                .orElseThrow();
    }

    private User admin() {
        return userRepository.findByEmail("admin@vaadin.com").orElseThrow();
    }

    private static Instant atStartOfDay(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault())
                .toInstant();
    }

    /** A one-line report of {@code amount}, owned by {@code owner}, left a draft. */
    private Long seedReportWithLine(User owner, LocalDate date, String info,
            String amount) {
        return seedReport(owner, date, info, amount, report -> {
        });
    }

    /** A report approved by the admin at {@code approvedAt}. */
    private Long seedApproved(User owner, String info, String amount,
            Instant approvedAt) {
        return seedReport(owner, LocalDate.of(2026, 1, 2), info, amount, report -> {
            report.submit(owner, approvedAt.minus(1, ChronoUnit.DAYS));
            report.approve(admin(), approvedAt);
        });
    }

    private Long seedReport(User owner, LocalDate date, String info,
            java.util.function.Consumer<ExpenseReport> transitions) {
        return seedReport(owner, date, info, "100.00", transitions);
    }

    /**
     * Persists a report with one €{@code amount} line, then drives the real domain
     * transitions on it — so the status history under test is the one the app
     * writes, not a hand-built fixture.
     */
    private Long seedReport(User owner, LocalDate date, String info, String amount,
            java.util.function.Consumer<ExpenseReport> transitions) {
        var report = new ExpenseReport(owner, date, info);
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstActiveType(),
                new BigDecimal(amount), zeroRate(), "line")));
        transitions.accept(report);
        reportRepository.save(report);
        reportRepository.flush();
        return report.getId();
    }

    /** A submitted, once-rejected report with a trip — every collection populated. */
    private void seedReportWithTripAndHistory(LocalDate date) {
        var me = plainUser();
        var id = service.create(dtoWithTravels(date,
                List.of(trip("Turku → Helsinki", DEPARTURE, DEPARTURE.plusHours(11)))));
        var report = reportRepository.findById(id).orElseThrow();
        report.submit(me, Instant.parse("2026-07-20T09:00:00Z"));
        report.reject(admin(), "Redo.", Instant.parse("2026-07-21T09:00:00Z"));
        reportRepository.flush();
    }

    private static TravelDto trip(String destinations, LocalDateTime departure,
            LocalDateTime returnAt) {
        return TravelDto.domestic(null, departure, returnAt, destinations,
                "Client visit", false, false, false, ZERO, false, ZERO);
    }

    private static ReportDetailDto dtoWithTravels(LocalDate date,
            List<TravelDto> travels) {
        return new ReportDetailDto(null, date, "trip", ReportStatus.DRAFT, 0L,
                List.of(), travels, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    private static ReportDetailDto draftDto(LocalDate date, String info) {
        return new ReportDetailDto(null, date, info, ReportStatus.DRAFT, 0L,
                List.of(), ZERO, ZERO, ZERO);
    }

    private ExpenseType firstActiveType() {
        return expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
    }

    /** The 0 % rate, so a seeded total is exactly the amount the test names. */
    private VatRate zeroRate() {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(r -> r.getValue().compareTo(BigDecimal.ZERO) == 0)
                .findFirst().orElseThrow();
    }
}

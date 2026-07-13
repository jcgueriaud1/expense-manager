package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.security.LocalUserDetailsService;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service + owner-scoping integration test (pyramid layer 2, ADR-0012) for
 * {@link ExpenseReportService}, on Testcontainers Postgres.
 *
 * <p>Runs as the seeded plain user (a real {@code AppUserPrincipal}, so
 * {@code CurrentUserProvider} resolves the owner). Covers the DTO-boundary
 * behaviour the detail/list views drive — create → id, owner-scoped newest-first
 * listing, whole-aggregate update, the optimistic-lock version check (ADR-0011),
 * and the draft-only delete guard (ADR-0006) — plus the owner-scoping contract:
 * another user's report is invisible and untouchable.
 *
 * <p>Authenticates as the seeded plain user by setting the context on the app's
 * <strong>global</strong> {@code SecurityContextHolder} in {@link BeforeEach}
 * rather than via {@code @WithUserDetails}. The service resolves the owner
 * through {@code CurrentUserProvider} → Vaadin's {@code AuthenticationContext},
 * which reads the global strategy; {@code @WithUserDetails} writes to
 * {@code TestSecurityContextHolder}'s own strategy instance, which — once a
 * browserless test context has installed Vaadin's session-aware strategy
 * globally — is no longer the one the app reads, so the principal is invisible
 * to the service (finding F-020). Writing and reading through the same global
 * holder here is order-independent.
 */
class ExpenseReportServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ExpenseReportService service;

    @Autowired
    private ReferenceDataService referenceData;

    @Autowired
    private VatRateRepository vatRateRepository;

    @Autowired
    private ExpenseReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocalUserDetailsService userDetailsService;

    @Autowired
    private SecurityContextHolderStrategy securityContextHolderStrategy;

    @BeforeEach
    void authenticateAsPlainUser() {
        // Method security reads THIS context's SecurityContextHolderStrategy bean;
        // AuthenticationContext reads the global static holder. In the multi-context
        // test JVM the two can be different instances (a later browserless context
        // overwrites the static), so pin the static to this context's bean, then
        // write the authentication through it — both readers now agree (F-020).
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
    void clearAuthentication() {
        securityContextHolderStrategy.clearContext();
    }

    @Test
    void createReturnsIdAndLoadsBackAsDraft() {
        var id = service.create(draftDto(LocalDate.of(2026, 7, 10), "Client visit"));

        var loaded = service.findMine(id);
        assertThat(loaded.id()).isEqualTo(id);
        assertThat(loaded.status()).isEqualTo(ReportStatus.DRAFT);
        assertThat(loaded.reportDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(loaded.additionalInformation()).isEqualTo("Client visit");
        assertThat(loaded.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void listMineIsOwnerScopedAndNewestReportDateFirst() {
        var older = service.create(draftDto(LocalDate.of(2026, 6, 1), "older"));
        var newer = service.create(draftDto(LocalDate.of(2026, 7, 1), "newer"));

        // A report owned by a different user must never appear in the list.
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        reportRepository.save(
                new ExpenseReport(admin, LocalDate.of(2026, 8, 1), "admin's report"));

        var mine = service.listMine();
        assertThat(mine).extracting(ReportSummaryDto::id)
                .containsExactly(newer, older);
        assertThat(mine).noneMatch(r -> "admin's report".equals(r.additionalInformation()));
    }

    @Test
    void updateChangesReportLevelFields() {
        var id = service.create(draftDto(LocalDate.of(2026, 7, 10), "before"));
        var loaded = service.findMine(id);

        service.update(id, new ReportDetailDto(id, LocalDate.of(2026, 7, 20), "after",
                loaded.status(), loaded.version(), loaded.total(), List.of()),
                loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.reportDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(reloaded.additionalInformation()).isEqualTo("after");
    }

    @Test
    void updateWithStaleVersionThrowsOptimisticLockFailure() {
        var id = service.create(draftDto(LocalDate.of(2026, 7, 10), "x"));

        assertThatThrownBy(() -> service.update(id,
                draftDto(LocalDate.of(2026, 7, 11), "y"), 999L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void deleteRemovesADraft() {
        var id = service.create(draftDto(LocalDate.of(2026, 7, 10), "scratch"));

        service.delete(id);

        assertThatThrownBy(() -> service.findMine(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anotherUsersReportIsInvisibleAndUntouchable() {
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        var foreign = reportRepository.save(
                new ExpenseReport(admin, LocalDate.of(2026, 8, 1), "admin's")).getId();

        // Owner-scoped: a missing id and someone else's id are indistinguishable.
        assertThatThrownBy(() -> service.findMine(foreign))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.delete(foreign))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(foreign,
                draftDto(LocalDate.of(2026, 8, 2), "hijack"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------ line reconciliation

    @Test
    void createPersistsLinesWithDerivedTotals() {
        var goods = expenseTypeNamed("Parking/supplies/goods"); // default 25.5 %
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 10),
                "trip", ReportStatus.DRAFT, 0L, null, List.of(
                        line(goods, "125.50"), line(goods, "110.00"))));

        var loaded = service.findMine(id);
        assertThat(loaded.lines()).hasSize(2);
        assertThat(loaded.lines()).allSatisfy(l -> assertThat(l.id()).isNotNull());
        // gross 125.50 + 110.00 = 235.50 (sum-per-line, ADR-0019).
        assertThat(loaded.total()).isEqualByComparingTo("235.50");
    }

    @Test
    void updateReconcilesLinesByNullableId_insertUpdateOrphanRemove() {
        var goods = expenseTypeNamed("Parking/supplies/goods");
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 10),
                "trip", ReportStatus.DRAFT, 0L, null, List.of(
                        line(goods, "10.00"), line(goods, "20.00"))));
        var loaded = service.findMine(id);
        var keptLine = loaded.lines().getFirst();  // will be updated in place
        var droppedId = loaded.lines().get(1).id(); // will be orphan-removed

        // Keep+modify line 0, drop line 1, insert a brand-new line 2 (null id).
        var updated = service.update(id, new ReportDetailDto(id, loaded.reportDate(),
                loaded.additionalInformation(), loaded.status(), loaded.version(),
                loaded.total(), List.of(
                        new ExpenseLineDto(keptLine.id(), keptLine.expenseType(),
                                new BigDecimal("15.00"), keptLine.vatRate(), "bumped"),
                        line(goods, "30.00"))), loaded.version());

        assertThat(updated.lines()).hasSize(2);
        // Matched id updated in place (same id, new amount); dropped id is gone.
        assertThat(updated.lines().getFirst().id()).isEqualTo(keptLine.id());
        assertThat(updated.lines().getFirst().amount()).isEqualByComparingTo("15.00");
        assertThat(updated.lines()).noneMatch(l -> l.id().equals(droppedId));
        // The inserted line has a fresh id and its amount.
        assertThat(updated.lines().get(1).id()).isNotNull().isNotEqualTo(droppedId);
        assertThat(updated.lines().get(1).amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void negativeLinePersistsAndReducesTotal() {
        var goods = expenseTypeNamed("Parking/supplies/goods");
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 10),
                "trip", ReportStatus.DRAFT, 0L, null, List.of(
                        line(goods, "125.50"), line(goods, "-25.50"))));

        var loaded = service.findMine(id);
        assertThat(loaded.lines()).hasSize(2);
        assertThat(loaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void zeroAmountLineIsRejectedByTheDomainGuard() {
        var goods = expenseTypeNamed("Parking/supplies/goods");

        assertThatThrownBy(() -> service.create(new ReportDetailDto(null,
                LocalDate.of(2026, 7, 10), null, ReportStatus.DRAFT, 0L, null,
                List.of(line(goods, "0.00")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLineKeepsItsRateThroughASaveEvenAfterThatRateIsDeactivated() {
        var goods = expenseTypeNamed("Parking/supplies/goods"); // default 25.5 %
        var id = service.create(new ReportDetailDto(null, LocalDate.of(2026, 7, 10),
                "trip", ReportStatus.DRAFT, 0L, null, List.of(line(goods, "125.50"))));
        var loaded = service.findMine(id);
        var rateId = loaded.lines().getFirst().vatRate().id();

        // The rate is deactivated (an admin would; here straight through the repo,
        // bypassing method security). New lines would no longer offer it...
        var rate = vatRateRepository.findById(rateId).orElseThrow();
        rate.setActive(false);
        vatRateRepository.saveAndFlush(rate);
        assertThat(referenceData.activeVatRates())
                .noneMatch(r -> r.id().equals(rateId));

        // ...but re-saving the whole report keeps the filed rate on the line
        // (resolution is by id, not the active-options query — ADR-0018).
        var updated = service.update(id, new ReportDetailDto(id, loaded.reportDate(),
                "still here", loaded.status(), loaded.version(), loaded.total(),
                loaded.lines()), loaded.version());

        assertThat(updated.lines()).hasSize(1);
        assertThat(updated.lines().getFirst().vatRate().id()).isEqualTo(rateId);
        assertThat(updated.lines().getFirst().vatRate().active()).isFalse();
        assertThat(updated.total()).isEqualByComparingTo("125.50");
    }

    private ExpenseTypeDto expenseTypeNamed(String name) {
        return referenceData.activeExpenseTypes().stream()
                .filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    /** A line filed under {@code type} at its default VAT rate. */
    private ExpenseLineDto line(ExpenseTypeDto type, String amount) {
        var rate = referenceData.activeVatRates().stream()
                .filter(r -> r.id().equals(type.defaultVatRateId()))
                .findFirst().orElseThrow();
        return new ExpenseLineDto(null, type, new BigDecimal(amount), rate, null);
    }

    private static ReportDetailDto draftDto(LocalDate date, String info) {
        return new ReportDetailDto(null, date, info, ReportStatus.DRAFT, 0L, null,
                List.of());
    }
}

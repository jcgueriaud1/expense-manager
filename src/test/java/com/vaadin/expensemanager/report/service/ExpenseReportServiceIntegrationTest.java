package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
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
                loaded.status(), loaded.version(), List.of(), loaded.total(),
                loaded.netTotal(), loaded.vatTotal()), loaded.version());

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

    @Test
    void createPersistsLinesAndDerivesReportTotals() {
        var type = firstActiveType();
        var rate255 = rateByValue("25.5");
        var rate135 = rateByValue("13.5");

        var id = service.create(dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate255, "100.00", "parking"),
                        newLine(type, rate135, "50.00", "lunch"))));

        var loaded = service.findMine(id);
        assertThat(loaded.lines()).hasSize(2);
        // Sum-per-line then total (ADR-0010): net 79.68 + 44.05, VAT 20.32 + 5.95.
        assertThat(loaded.total()).isEqualByComparingTo("150.00");
        assertThat(loaded.netTotal()).isEqualByComparingTo("123.73");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("26.27");
    }

    @Test
    void updateReconcilesLinesByNullableIdInsertUpdateOrphanRemove() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");

        var id = service.create(dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "10.00", "A"),
                        newLine(type, rate, "20.00", "B"),
                        newLine(type, rate, "30.00", "C"))));
        var loaded = service.findMine(id);
        var lineA = loaded.lines().get(0);
        var lineB = loaded.lines().get(1);
        var lineC = loaded.lines().get(2);

        // Keep A (edit amount, same id), drop B (orphan-remove the middle line),
        // keep C, add a brand-new line (null id → insert).
        var edited = List.of(
                new ExpenseLineDto(lineA.id(), type.getId(), type.getName(),
                        rate.getId(), rate.getValue(), new BigDecimal("11.00"), "A2"),
                new ExpenseLineDto(lineC.id(), type.getId(), type.getName(),
                        rate.getId(), rate.getValue(), lineC.amount(), lineC.comment()),
                newLine(type, rate, "40.00", "D"));
        service.update(id, dtoWithLines(id, LocalDate.of(2026, 7, 10),
                loaded.version(), edited), loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.lines()).hasSize(3);
        // A kept its identity and was updated in place; B is gone.
        assertThat(reloaded.lines().get(0).id()).isEqualTo(lineA.id());
        assertThat(reloaded.lines().get(0).amount()).isEqualByComparingTo("11.00");
        assertThat(reloaded.lines()).noneMatch(l -> l.id().equals(lineB.id()));
        // Order follows the submitted list: A2, C, D.
        assertThat(reloaded.lines()).extracting(ExpenseLineDto::comment)
                .containsExactly("A2", "C", "D");
    }

    @Test
    void aLineFiledUnderANowDeactivatedRateStillResolvesOnSave() {
        var type = firstActiveType();
        // Deactivate a rate after a line references it (ADR-0018 history rule).
        var rate = rateByValue("10");
        var id = service.create(dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "22.00", "publication"))));
        rate.setActive(false);
        vatRateRepository.saveAndFlush(rate);

        // The line round-trips: its rate id still resolves unfiltered on update.
        var loaded = service.findMine(id);
        var line = loaded.lines().getFirst();
        service.update(id, dtoWithLines(id, LocalDate.of(2026, 7, 10),
                loaded.version(),
                List.of(new ExpenseLineDto(line.id(), type.getId(), type.getName(),
                        rate.getId(), rate.getValue(), new BigDecimal("23.00"), "publication"))),
                loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.lines().getFirst().amount()).isEqualByComparingTo("23.00");
        assertThat(reloaded.lines().getFirst().vatRateId()).isEqualTo(rate.getId());
    }

    @Test
    void savingAZeroAmountLineIsRejected() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");

        assertThatThrownBy(() -> service.create(dtoWithLines(null,
                LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "0.00", "nope")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ExpenseType firstActiveType() {
        return expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .getFirst();
    }

    private VatRate rateByValue(String value) {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(r -> r.getValue().compareTo(new BigDecimal(value)) == 0)
                .findFirst().orElseThrow();
    }

    private static ExpenseLineDto newLine(ExpenseType type, VatRate rate,
            String amount, String comment) {
        return new ExpenseLineDto(null, type.getId(), type.getName(), rate.getId(),
                rate.getValue(), new BigDecimal(amount), comment);
    }

    private static ReportDetailDto draftDto(LocalDate date, String info) {
        var zero = BigDecimal.ZERO.setScale(2);
        return new ReportDetailDto(null, date, info, ReportStatus.DRAFT, 0L,
                List.of(), zero, zero, zero);
    }

    private static ReportDetailDto dtoWithLines(Long id, LocalDate date, long version,
            List<ExpenseLineDto> lines) {
        var zero = BigDecimal.ZERO.setScale(2);
        return new ReportDetailDto(id, date, "report", ReportStatus.DRAFT, version,
                lines, zero, zero, zero);
    }
}

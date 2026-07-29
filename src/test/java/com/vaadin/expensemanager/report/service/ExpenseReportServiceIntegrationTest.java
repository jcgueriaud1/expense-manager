package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
import com.vaadin.expensemanager.report.domain.Receipt;
import com.vaadin.expensemanager.report.domain.ReceiptRejectedException;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
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

    /** Raw SQL access, for asserting/replaying the generated-kind migration (V13). */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReceiptRepository receiptRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
        authenticateAs(LocalUserSeeder.PLAIN_USER_EMAIL);
    }

    /** Re-authenticate the current context as the given seeded user (F-020). */
    private void authenticateAs(String email) {
        var principal = userDetailsService.loadUserByUsername(email);
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
                ExpenseLineDto.of(lineA.id(), type.getId(), type.getName(),
                        rate.getId(), rate.getValue(), new BigDecimal("11.00"), "A2"),
                ExpenseLineDto.of(lineC.id(), type.getId(), type.getName(),
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
    void aQuantityRoundTripsAndTotalsFromTheMultipliedGross() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");

        var id = service.create(dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "100.00", "3", "3 nights"))));

        entityManager.clear();
        var loaded = service.findMine(id);
        var line = loaded.lines().getFirst();
        // The stored amount is the unit price; the gross is unit × quantity.
        assertThat(line.amount()).isEqualByComparingTo("100.00");
        assertThat(line.quantity()).isEqualByComparingTo("3.00");
        assertThat(loaded.total()).isEqualByComparingTo("300.00");
        assertThat(loaded.netTotal()).isEqualByComparingTo("239.04");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("60.96");
    }

    @Test
    void updatingAPersistedLinesQuantityKeepsItsIdentityAndRetotals() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "100.00", "3 nights"))));
        var loaded = service.findMine(id);
        var line = loaded.lines().getFirst();
        entityManager.clear();

        service.update(id, dtoWithLines(id, LocalDate.of(2026, 7, 10),
                loaded.version(),
                List.of(ExpenseLineDto.of(line.id(), type.getId(), type.getName(),
                        rate.getId(), rate.getValue(), new BigDecimal("100.00"),
                        new BigDecimal("3"), "3 nights"))),
                loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.lines().getFirst().id()).isEqualTo(line.id());
        assertThat(reloaded.lines().getFirst().quantity()).isEqualByComparingTo("3.00");
        assertThat(reloaded.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void aPreMigrationLineBackfillsToQuantityOneAndTotalsAsBefore() {
        // A row written the way every line was written before ADR-0023: no quantity
        // column in the insert, so V12's `default 1` backfills it. The stored amount
        // must be untouched and the report must total exactly as it did.
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(draftDto(LocalDate.of(2026, 7, 10), "legacy"));

        entityManager.createNativeQuery("""
                insert into expense_line (report_id, line_index, expense_type_id,
                        vat_rate_id, amount, comment, created_at, updated_at)
                values (?1, 0, ?2, ?3, 100.00, 'legacy', now(), now())
                """)
                .setParameter(1, id)
                .setParameter(2, type.getId())
                .setParameter(3, rate.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        var loaded = service.findMine(id);
        var line = loaded.lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("100.00");
        assertThat(line.quantity()).isEqualByComparingTo("1.00");
        assertThat(loaded.total()).isEqualByComparingTo("100.00");
        assertThat(loaded.netTotal()).isEqualByComparingTo("79.68");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("20.32");
    }

    @Test
    void aZeroQuantityIsRejectedOnSave() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");

        assertThatThrownBy(() -> service.create(dtoWithLines(null,
                LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "100.00", "0", "nothing")))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("Quantity");
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
                List.of(ExpenseLineDto.of(line.id(), type.getId(), type.getName(),
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

    @Test
    void submitMovesDraftToSubmittedAndRecordsAStatusChange() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "100.00", "hotel"))));
        var loaded = service.findMine(id);

        var submitted = service.submit(id, loaded.version());

        assertThat(submitted.status()).isEqualTo(ReportStatus.SUBMITTED);
        // Round-trips through the DB as SUBMITTED with one status-change row.
        var reloaded = reportRepository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.getStatusHistory()).hasSize(1);
        var change = reloaded.getStatusHistory().getFirst();
        assertThat(change.getFromStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(change.getToStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(change.getActingUser().getEmail())
                .isEqualTo(LocalUserSeeder.PLAIN_USER_EMAIL);
    }

    @Test
    void submittingAReportWithNoLinesIsRejected() {
        var id = service.create(draftDto(LocalDate.of(2026, 7, 10), "empty"));
        var loaded = service.findMine(id);

        assertThatThrownBy(() -> service.submit(id, loaded.version()))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
    }

    @Test
    void aSubmittedReportCannotBeDeleted() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "100.00", "hotel"))));
        service.submit(id, service.findMine(id).version());

        // The draft-only delete guard (ADR-0006) now exercised end-to-end: a
        // SUBMITTED report rejects delete rather than being removed.
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    void submitWithAStaleVersionThrowsOptimisticLockFailure() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                List.of(newLine(type, rate, "100.00", "hotel"))));

        assertThatThrownBy(() -> service.submit(id, 999L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // --- Save-and-submit / save-and-resubmit the current state (issue #81) ---

    @Test
    void saveAndSubmitPersistsTheWorkingEditsThenSubmits() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        // A draft saved with no lines — the line and the edited info arrive only
        // with the submit, standing in for edits the user never clicked Save on.
        var id = service.create(draftDto(LocalDate.of(2026, 7, 10), "before"));
        var loaded = service.findMine(id);

        var edited = dtoWithLines(id, LocalDate.of(2026, 7, 12), loaded.version(),
                List.of(newLine(type, rate, "100.00", "hotel")));
        var submitted = service.saveAndSubmit(id, edited, loaded.version(),
                Map.of(), Map.of());

        assertThat(submitted.status()).isEqualTo(ReportStatus.SUBMITTED);
        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        // The line and the report-level edits were saved as part of the submit.
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.lines().getFirst().amount()).isEqualByComparingTo("100.00");
        assertThat(reloaded.reportDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(reloaded.additionalInformation()).isEqualTo("report");
    }

    @Test
    void saveAndSubmitWithAStaleVersionThrowsBeforeTouchingAnything() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(draftDto(LocalDate.of(2026, 7, 10), "x"));

        var edited = dtoWithLines(id, LocalDate.of(2026, 7, 10), 999L,
                List.of(newLine(type, rate, "50.00", "taxi")));
        assertThatThrownBy(() -> service.saveAndSubmit(id, edited, 999L,
                Map.of(), Map.of()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void saveAndSubmitResubmitsARejectedReportAfterSavingTheEdits() {
        var me = userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL)
                .orElseThrow();
        var id = seedRejectedReport(me);
        var loaded = service.findMine(id);
        var type = firstActiveType();
        var rate = rateByValue("25.5");

        // A REJECTED report takes the resubmit branch of the same save-and-submit
        // method: address the feedback (change info, keep a line) in one call.
        var edited = new ReportDetailDto(id, loaded.reportDate(), "fixed it",
                loaded.status(), loaded.version(),
                List.of(newLine(type, rate, "100.00", "hotel")),
                loaded.total(), loaded.netTotal(), loaded.vatTotal());
        var resubmitted = service.saveAndSubmit(id, edited, loaded.version(),
                Map.of(), Map.of());

        assertThat(resubmitted.status()).isEqualTo(ReportStatus.SUBMITTED);
        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("fixed it");
    }

    // --- Resubmit (Phase 5.5, ADR-0006) ---

    @Test
    void resubmitMovesRejectedToSubmittedAndReappearsInTheAdminQueue() {
        var me = userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL)
                .orElseThrow();
        var id = seedRejectedReport(me);
        var loaded = service.findMine(id);

        var resubmitted = service.resubmit(id, loaded.version());

        assertThat(resubmitted.status()).isEqualTo(ReportStatus.SUBMITTED);
        // Round-trips as SUBMITTED with the loop's third status-change row, and so
        // reappears in the admin queue (findByStatus, what listSubmitted reads).
        var reloaded = reportRepository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.getStatusHistory()).hasSize(3);
        var change = reloaded.getStatusHistory().getLast();
        assertThat(change.getFromStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(change.getToStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(change.getActingUser().getEmail())
                .isEqualTo(LocalUserSeeder.PLAIN_USER_EMAIL);
        assertThat(reportRepository.findByStatusOrderByIdDesc(ReportStatus.SUBMITTED))
                .extracting(ExpenseReport::getId).contains(id);
    }

    @Test
    void resubmittingAnotherUsersRejectedReportIsRejected() {
        // A rejected report owned by the admin — the plain user is authenticated.
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        var foreign = seedRejectedReport(admin);

        // Owner-scoped exactly like submit: requireOwned hides it, so even though the
        // report really is REJECTED, this user cannot resubmit it (ADR-0008).
        assertThatThrownBy(() -> service.resubmit(foreign, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reportRepository.findById(foreign).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.REJECTED);
    }

    @Test
    void resubmitWithAStaleVersionThrowsOptimisticLockFailure() {
        var me = userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL)
                .orElseThrow();
        var id = seedRejectedReport(me);

        assertThatThrownBy(() -> service.resubmit(id, 999L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    /**
     * Persists a {@code REJECTED} report with one line owned by {@code owner},
     * driving the real domain transitions (submit → reject) past the owner-scoped
     * service so a rejected fixture exists to resubmit. The admin is the rejecter.
     */
    private Long seedRejectedReport(User owner) {
        var report = new ExpenseReport(owner, LocalDate.of(2026, 7, 10), "needs work");
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstActiveType(),
                new BigDecimal("100.00"), rateByValue("25.5"), "hotel")));
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        report.submit(owner, Instant.parse("2026-07-11T09:00:00Z"));
        report.reject(admin, "Please attach the receipt.",
                Instant.parse("2026-07-12T09:00:00Z"));
        reportRepository.save(report);
        reportRepository.flush();
        return report.getId();
    }

    // --- Receipts (Phase 3.1, ADR-0021) ---

    // Minimal magic-byte prefixes; padded so they read as small real files.
    private static final byte[] JPEG = pad((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    private static final byte[] PNG = pad((byte) 0x89, (byte) 0x50, (byte) 0x4E,
            (byte) 0x47);

    @Test
    void attachingAReceiptOnSavePersistsItAndReloadsASummaryWithoutBytes() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(
                dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                        List.of(newLine(type, rate, "40.00", "taxi"))),
                Map.of(0, new ReceiptUpload(JPEG, "taxi.jpg")));

        var loaded = service.findMine(id);
        var line = loaded.lines().getFirst();
        assertThat(line.hasReceipt()).isTrue();
        assertThat(line.receiptFilename()).isEqualTo("taxi.jpg");
        // Stored content type is the SNIFFED signature, not a browser claim.
        assertThat(line.receiptContentType()).isEqualTo("image/jpeg");
        assertThat(line.receiptSizeBytes()).isEqualTo(JPEG.length);
        assertThat(line.receiptId()).isNotNull();

        // The bytes round-trip in the DB even though no DTO ever carried them.
        var stored = receiptRepository.findByExpenseLineId(line.id()).orElseThrow();
        assertThat(stored.getData()).isEqualTo(JPEG);
    }

    @Test
    void replacingAReceiptOverwritesInPlaceWithNoHistory() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(
                dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                        List.of(newLine(type, rate, "40.00", "taxi"))),
                Map.of(0, new ReceiptUpload(JPEG, "taxi.jpg")));
        var loaded = service.findMine(id);
        var line = loaded.lines().getFirst();
        var originalReceiptId = line.receiptId();

        // Upload a PNG over the JPEG: same single receipt row, new bytes/type.
        service.update(id, dtoWithLines(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of(line)), loaded.version(),
                Map.of(0, new ReceiptUpload(PNG, "taxi.png")));

        var reloaded = service.findMine(id).lines().getFirst();
        assertThat(reloaded.receiptContentType()).isEqualTo("image/png");
        assertThat(reloaded.receiptFilename()).isEqualTo("taxi.png");
        // No history: exactly one receipt for the line, and it is the same row.
        assertThat(receiptRepository.findByExpenseLineId(reloaded.id()).orElseThrow()
                .getId()).isEqualTo(originalReceiptId);
    }

    @Test
    void removingAReceiptClearsIt() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(
                dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                        List.of(newLine(type, rate, "40.00", "taxi"))),
                Map.of(0, new ReceiptUpload(JPEG, "taxi.jpg")));
        var loaded = service.findMine(id);
        var line = loaded.lines().getFirst();

        service.update(id, dtoWithLines(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of(line)), loaded.version(),
                Map.of(0, ReceiptUpload.REMOVE));

        assertThat(service.findMine(id).lines().getFirst().hasReceipt()).isFalse();
        assertThat(receiptRepository.findByExpenseLineId(line.id())).isEmpty();
    }

    @Test
    void orphanRemovingALineAlsoRemovesItsReceipt() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(
                dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                        List.of(newLine(type, rate, "40.00", "taxi"))),
                Map.of(0, new ReceiptUpload(JPEG, "taxi.jpg")));
        var loaded = service.findMine(id);
        var lineId = loaded.lines().getFirst().id();
        assertThat(receiptRepository.findByExpenseLineId(lineId)).isPresent();

        // Detach everything so the update runs against a clean persistence context,
        // as it would across a real request/transaction boundary (the rollback
        // test otherwise keeps create()'s receipt managed — cf. F-024).
        entityManager.clear();

        // Reconcile the line away (empty line set); the FK cascade removes the
        // receipt with its line — no dangling blob.
        service.update(id, dtoWithLines(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of()), loaded.version());

        assertThat(service.findMine(id).lines()).isEmpty();
        assertThat(receiptRepository.findByExpenseLineId(lineId)).isEmpty();
    }

    @Test
    void aMislabeledFileIsRejectedAtTheService() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        byte[] notAnImage = "totally not a jpeg".getBytes();

        // Even bypassing the UI, the service re-sniffs and rejects: the stored
        // type can only ever be a verified signature (ADR-0021).
        assertThatThrownBy(() -> service.create(
                dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                        List.of(newLine(type, rate, "40.00", "taxi"))),
                Map.of(0, new ReceiptUpload(notAnImage, "taxi.jpg"))))
                .isInstanceOf(ReceiptRejectedException.class);
    }

    // --- Receipt read path (Phase 3.2, ADR-0021 / ADR-0008) ---

    @Test
    void receiptForDownloadReturnsTheBytesForTheOwner() {
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(
                dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                        List.of(newLine(type, rate, "40.00", "taxi"))),
                Map.of(0, new ReceiptUpload(JPEG, "taxi.jpg")));
        var receiptId = service.findMine(id).lines().getFirst().receiptId();

        var content = service.receiptForDownload(receiptId);

        // The dedicated download projection materializes the bytea (the one query
        // that does), with the stored sniffed content type and filename.
        assertThat(content).isPresent();
        assertThat(content.get().data()).isEqualTo(JPEG);
        assertThat(content.get().filename()).isEqualTo("taxi.jpg");
        assertThat(content.get().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void receiptForDownloadDeniesANonOwner() {
        // A receipt on another user's report, seeded past the owner-scoped service.
        var admin = userRepository.findByEmail("admin@vaadin.com").orElseThrow();
        var report = new ExpenseReport(admin, LocalDate.of(2026, 8, 1), "admin's");
        report.reconcileLines(List.of(ExpenseLineSpec.of(null, firstActiveType(),
                new BigDecimal("10.00"), rateByValue("25.5"), "x")));
        reportRepository.save(report);
        reportRepository.flush();
        var adminLine = report.getLines().getFirst();
        var adminReceiptId = receiptRepository
                .save(new Receipt(adminLine, JPEG, "admin.jpg", "image/jpeg")).getId();
        receiptRepository.flush();

        // The current user is the plain user: the owner check in the query returns
        // nothing — a non-owner's receipt is never streamed (ADR-0008).
        assertThat(service.receiptForDownload(adminReceiptId)).isEmpty();
    }

    @Test
    void receiptForDownloadLetsAnAdminSeeAnotherUsersReceipt() {
        // A receipt on the plain user's report, created through the owner-scoped
        // service while authenticated as that user.
        var type = firstActiveType();
        var rate = rateByValue("25.5");
        var id = service.create(
                dtoWithLines(null, LocalDate.of(2026, 7, 10), 0L,
                        List.of(newLine(type, rate, "40.00", "taxi"))),
                Map.of(0, new ReceiptUpload(JPEG, "taxi.jpg")));
        var receiptId = service.findMine(id).lines().getFirst().receiptId();

        // An admin reviews any user's report (Phase 5), so the read path is not
        // owner-scoped for them: the plain user's receipt streams to the admin.
        authenticateAs("admin@vaadin.com");
        var content = service.receiptForDownload(receiptId);

        assertThat(content).isPresent();
        assertThat(content.get().data()).isEqualTo(JPEG);
        assertThat(content.get().filename()).isEqualTo("taxi.jpg");
    }

    @Test
    void receiptForDownloadIsEmptyForAMissingId() {
        assertThat(service.receiptForDownload(-1L)).isEmpty();
    }

    // --- Travel / domestic per-diem (Phase 4.2/4.3) ---

    private static final LocalDateTime DEP = LocalDateTime.of(2026, 7, 1, 8, 0);

    @Test
    void previewComputesTheServerAuthoritativePerDiemWithoutPersisting() {
        // 11 h (> 10 h) → one full day at the seeded 2026 rate (€54.00).
        var preview = service.previewTravel(
                domesticTravel(null, DEP, DEP.plusHours(11), false, false));

        assertThat(preview.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("54.00");
        assertThat(preview.generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow()
                .comment()).contains("full day");
        // Nothing was persisted by a preview.
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void createWithATripPersistsTheTravelAndItsGeneratedPerDiemLine() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11), false, false))));

        var loaded = service.findMine(id);
        // The trip round-trips; the manual line list stays empty (no cards).
        assertThat(loaded.travels()).hasSize(1);
        assertThat(loaded.lines()).isEmpty();
        var trip = loaded.travels().getFirst();
        assertThat(trip.destinations()).isEqualTo("Helsinki");
        assertThat(trip.country()).isEqualTo("Finland");
        assertThat(trip.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("54.00");
        // The per-diem is broken out of Net/VAT and into its own subtotal.
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.netTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.total()).isEqualByComparingTo("54.00");
    }

    @Test
    void editingATripRecostsAndRegeneratesItsLine() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11), false, false))));
        var loaded = service.findMine(id);
        var trip = loaded.travels().getFirst();

        // Free lunch now halves it: €54.00 → €27.00.
        var edited = domesticTravel(trip.id(), DEP, DEP.plusHours(11), false, true);
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of(edited)), loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.travels()).hasSize(1);
        assertThat(reloaded.travels().getFirst().amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("27.00");
        assertThat(reloaded.perDiemTotal()).isEqualByComparingTo("27.00");
    }

    @Test
    void deletingATripRemovesItsGeneratedLine() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11), false, false))));
        var loaded = service.findMine(id);

        entityManager.clear();
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of()), loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.travels()).isEmpty();
        assertThat(reloaded.perDiemTotal()).isEqualByComparingTo("0.00");
        // No dangling generated line left behind on the aggregate.
        assertThat(reportRepository.findById(id).orElseThrow().getLines()).isEmpty();
    }

    @Test
    void aNotEligibleTripPersistsTheTravelButGeneratesNoLine() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11), true, false))));

        var loaded = service.findMine(id);
        assertThat(loaded.travels()).hasSize(1);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("0.00");
        assertThat(reportRepository.findById(id).orElseThrow().getLines()).isEmpty();
    }

    @Test
    void aTripAndAManualLineCoexistWithSplitTotals() {
        var type = firstActiveType();
        var rate255 = rateByValue("25.5");
        var dto = new ReportDetailDto(null, LocalDate.of(2026, 7, 10), "trip",
                ReportStatus.DRAFT, 0L, List.of(newLine(type, rate255, "100.00", "hotel")),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11), false, false)),
                ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);

        var id = service.create(dto);

        var loaded = service.findMine(id);
        assertThat(loaded.lines()).hasSize(1);
        assertThat(loaded.travels()).hasSize(1);
        // Net/VAT from the VAT-bearing line; per-diem broken out; Total sums all.
        assertThat(loaded.netTotal()).isEqualByComparingTo("79.68");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("20.32");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.total()).isEqualByComparingTo("154.00");
    }

    @Test
    void savingATripWithReturnBeforeDepartureIsRejected() {
        assertThatThrownBy(() -> service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.minusHours(1), false, false)))))
                .isInstanceOf(IllegalArgumentException.class);
        // Rejected before persistence — no partial report left behind.
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void previewPaysAMealAllowanceInsteadOfAPerDiemWhenNotEligible() {
        // Issue #93: a meal allowance and a per-diem are mutually exclusive. A
        // not-eligible trip earns the meal allowance (€13.50) and no per-diem, while
        // km (120 × €0.55 = €66.00) and parking (€12.00, VAT-bearing at 25.5 %) are
        // unaffected.
        var preview = service.previewTravel(domesticTravel(null, DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"),
                true));

        assertThat(preview.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("0.00");
        assertThat(preview.amountOf(GeneratedLineKind.KILOMETRE))
                .isEqualByComparingTo("66.00");
        assertThat(preview.amountOf(GeneratedLineKind.MEAL))
                .isEqualByComparingTo("13.50");
        assertThat(preview.amountOf(GeneratedLineKind.PARKING))
                .isEqualByComparingTo("12.00");
        assertThat(preview.generatedLine(GeneratedLineKind.PARKING).orElseThrow()
                .vatRatePercent()).isEqualByComparingTo("25.50");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void previewSuppressesTheMealAllowanceWhenEligibleForAPerDiem() {
        // The mirror case (issue #93): an eligible trip earns the per-diem (€54.00)
        // and the meal flag is ignored server-side even when set — never both, so
        // the server holds the invariant whatever flag combination reaches it.
        var preview = service.previewTravel(domesticTravel(null, DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"),
                false));

        assertThat(preview.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("54.00");
        assertThat(preview.amountOf(GeneratedLineKind.MEAL))
                .isEqualByComparingTo("0.00");
        assertThat(preview.amountOf(GeneratedLineKind.KILOMETRE))
                .isEqualByComparingTo("66.00");
        assertThat(preview.amountOf(GeneratedLineKind.PARKING))
                .isEqualByComparingTo("12.00");
    }

    @Test
    void createWithKmMealParkingPersistsEachGeneratedLineRoutedCorrectly() {
        // A not-eligible trip: the meal allowance stands in for the per-diem (issue
        // #93, mutually exclusive), so km / meal / parking each persist under their
        // own kind and no per-diem line is written.
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11),
                        new BigDecimal("120"), true, new BigDecimal("12.00"), true))));

        var loaded = service.findMine(id);
        // No per-diem; the two tax-free allowances each land in their own subtotal…
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("66.00");
        assertThat(loaded.mealTotal()).isEqualByComparingTo("13.50");
        // …parking is VAT-bearing → Net/VAT (12.00 @ 25.5 %), not a subtotal.
        assertThat(loaded.netTotal()).isEqualByComparingTo("9.56");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("2.44");
        assertThat(loaded.total()).isEqualByComparingTo("91.50");
        // Three generated lines are persisted; none appear as editable manual cards.
        assertThat(loaded.lines()).isEmpty();
        assertThat(reportRepository.findById(id).orElseThrow().getLines()).hasSize(3);

        // The working copy round-trips its inputs and per-kind amounts.
        var trip = loaded.travels().getFirst();
        assertThat(trip.kilometres()).isEqualByComparingTo("120.00");
        assertThat(trip.payMealAllowance()).isTrue();
        assertThat(trip.notEligibleForAllowance()).isTrue();
        assertThat(trip.parkingFees()).isEqualByComparingTo("12.00");
        assertThat(trip.generatedLine(GeneratedLineKind.PARKING).orElseThrow()
                .vatRatePercent()).isEqualByComparingTo("25.50");
    }

    @Test
    void theGeneratedKilometreLineRoundTripsAsDistanceTimesRate() {
        // ADR-0023: a fractional distance is exactly why km is the quantity — 12.5 km
        // × the seeded 2026 rate (€0.550/km) = €6.875 → €6.88, the same euros as
        // before the shape changed.
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11),
                        new BigDecimal("12.5"), false, ZERO, false))));

        var loaded = service.findMine(id);
        var km = loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.KILOMETRE).orElseThrow();
        assertThat(km.quantity()).isEqualByComparingTo("12.50");
        assertThat(km.unitPrice()).isEqualByComparingTo("0.55");
        assertThat(km.amount()).isEqualByComparingTo("6.88");
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("6.88");

        // The persisted line carries the two factors, not a pre-multiplied lump.
        var persisted = reportRepository.findById(id).orElseThrow().getLines().stream()
                .filter(line -> line.getGeneratedKind() == GeneratedLineKind.KILOMETRE)
                .findFirst().orElseThrow();
        assertThat(persisted.getQuantity()).isEqualByComparingTo("12.50");
        assertThat(persisted.getAmount()).isEqualByComparingTo("0.55");
        assertThat(persisted.gross()).isEqualByComparingTo("6.88");

        // The other kinds stay flat: the per-diem is one unit at its full amount.
        var perDiem = loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(perDiem.quantity()).isEqualByComparingTo("1");
        assertThat(perDiem.unitPrice()).isEqualByComparingTo("54.00");
    }

    @Test
    void reCostingATripReplacesItsKilometreLineWithoutDuplicating() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11),
                        new BigDecimal("12.5"), false, ZERO, false))));
        var loaded = service.findMine(id);
        var trip = loaded.travels().getFirst();
        var lineIdBefore = trip.generatedLine(GeneratedLineKind.KILOMETRE).orElseThrow()
                .lineId();

        // Same trip, 120 km instead of 12.5 — the one km line is re-costed in place.
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10), loaded.version(),
                List.of(domesticTravel(trip.id(), DEP, DEP.plusHours(11),
                        new BigDecimal("120"), false, ZERO, false))), loaded.version());

        var reloaded = service.findMine(id);
        var km = reloaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.KILOMETRE).orElseThrow();
        assertThat(km.lineId()).isEqualTo(lineIdBefore);
        assertThat(km.quantity()).isEqualByComparingTo("120.00");
        assertThat(km.amount()).isEqualByComparingTo("66.00");
        assertThat(reloaded.kilometreTotal()).isEqualByComparingTo("66.00");
        // Exactly two generated lines survive (per-diem + the single km line).
        assertThat(reportRepository.findById(id).orElseThrow().getLines()).hasSize(2);
    }

    @Test
    void editingDropsTheKindsATripNoLongerEarns() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11),
                        new BigDecimal("120"), true, new BigDecimal("12.00"), true))));
        var loaded = service.findMine(id);
        var trip = loaded.travels().getFirst();

        // Re-cost: make the trip eligible again and keep only the per-diem (clear
        // km / meal / parking) — the meal allowance gives way to the per-diem.
        var edited = domesticTravel(trip.id(), DEP, DEP.plusHours(11), ZERO, false, ZERO,
                false);
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of(edited)), loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(reloaded.kilometreTotal()).isEqualByComparingTo("0.00");
        assertThat(reloaded.mealTotal()).isEqualByComparingTo("0.00");
        assertThat(reloaded.netTotal()).isEqualByComparingTo("0.00");
        // Only the per-diem line survives.
        assertThat(reportRepository.findById(id).orElseThrow().getLines()).hasSize(1);
    }

    @Test
    void attachingAReceiptToAGeneratedLinePersistsAndRoundTrips() {
        var id = service.create(
                dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                        domesticTravel(null, DEP, DEP.plusHours(11), false, false))),
                Map.of(),
                Map.of(new GeneratedLineRef(0, GeneratedLineKind.PER_DIEM_FULL),
                        new ReceiptUpload(JPEG, "perdiem.jpg")));

        var perDiem = service.findMine(id).travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(perDiem.hasReceipt()).isTrue();
        assertThat(perDiem.receiptId()).isNotNull();
        assertThat(perDiem.receiptFilename()).isEqualTo("perdiem.jpg");
        // The stored content type is the sniffed signature, not the browser's claim.
        assertThat(perDiem.receiptContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void aGeneratedLineReceiptSurvivesReCostingTheTrip() {
        var id = service.create(
                dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                        domesticTravel(null, DEP, DEP.plusHours(11), false, false))),
                Map.of(),
                Map.of(new GeneratedLineRef(0, GeneratedLineKind.PER_DIEM_FULL),
                        new ReceiptUpload(JPEG, "perdiem.jpg")));
        var loaded = service.findMine(id);
        var tripId = loaded.travels().getFirst().id();

        // Re-cost with a free lunch (54.00 → 27.00): the per-diem line updates in
        // place (same id), so its receipt rides along.
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of(
                        domesticTravel(tripId, DEP, DEP.plusHours(11), false, true))),
                loaded.version());

        var perDiem = service.findMine(id).travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(perDiem.amount()).isEqualByComparingTo("27.00");
        assertThat(perDiem.receiptFilename()).isEqualTo("perdiem.jpg");
    }

    @Test
    void removingAGeneratedLineKindCascadesItsReceipt() {
        var id = service.create(
                dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                        domesticTravel(null, DEP, DEP.plusHours(11), ZERO, false,
                                new BigDecimal("12.00"), false))),
                Map.of(),
                Map.of(new GeneratedLineRef(0, GeneratedLineKind.PARKING),
                        new ReceiptUpload(JPEG, "parking.jpg")));
        var loaded = service.findMine(id);
        var tripId = loaded.travels().getFirst().id();
        Long parkingLineId = loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PARKING).orElseThrow().lineId();
        assertThat(receiptRepository.findByExpenseLineId(parkingLineId)).isPresent();

        // Detach the session so the update runs against a fresh load (as a real
        // request would), not one holding the just-created receipt.
        entityManager.clear();
        // Clear the parking fee → the parking line is orphan-removed, and its
        // receipt is cascade-deleted at the DB (V6 ON DELETE CASCADE).
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of(
                        domesticTravel(tripId, DEP, DEP.plusHours(11), ZERO, false, ZERO,
                                false))),
                loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PARKING)).isEmpty();
        assertThat(receiptRepository.findByExpenseLineId(parkingLineId)).isEmpty();
    }

    // --- The split per-diem: PER_DIEM_FULL + PER_DIEM_PARTIAL (issue #124) ---

    @Test
    void aTripEarningAPartialDaySplitsIntoTwoPerDiemLines() {
        // 31 h = one whole 24 h period + a 7 h leftover (over the strict 6 h threshold)
        // → a full-day line (1 × €54.00) and a partial-day line (1 × €25.00), each an
        // honest days × per-day rate (ADR-0023).
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(31), false, false))));

        var trip = service.findMine(id).travels().getFirst();
        var full = trip.generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(full.quantity()).isEqualByComparingTo("1");
        assertThat(full.unitPrice()).isEqualByComparingTo("54.00");
        assertThat(full.comment()).contains("full day");
        var partial = trip.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL).orElseThrow();
        assertThat(partial.quantity()).isEqualByComparingTo("1");
        assertThat(partial.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(partial.comment()).contains("partial day");
        // Both are 0 %-VAT tax-free lines sharing the one per-diem subtotal.
        assertThat(full.vatRatePercent()).isEqualByComparingTo("0.00");
        assertThat(partial.vatRatePercent()).isEqualByComparingTo("0.00");
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("79.00");
        assertThat(service.findMine(id).netTotal()).isEqualByComparingTo("0.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("79.00");
    }

    @Test
    void multiDayPerDiemLinesCarryTheDayCountsAsQuantities() {
        // 55 h → 2 full days + 1 partial: 2 × €54.00 + 1 × €25.00 = €133.00.
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(55), false, false))));

        var trip = service.findMine(id).travels().getFirst();
        assertThat(trip.generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow()
                .quantity()).isEqualByComparingTo("2");
        assertThat(trip.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("108.00");
        assertThat(trip.amountOf(GeneratedLineKind.PER_DIEM_PARTIAL))
                .isEqualByComparingTo("25.00");
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void theSplitKeepsTheDayMixAndEurosOfRepresentativeTrips() {
        // The AC's three representative trips, previewed: the mix per trip and the
        // total euros are exactly what the single-line per-diem produced.
        var full24h = service.previewTravel(
                domesticTravel(null, DEP, DEP.plusHours(24), false, false));
        assertThat(full24h.generatedLine(GeneratedLineKind.PER_DIEM_FULL)).isPresent();
        assertThat(full24h.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL)).isEmpty();
        assertThat(full24h.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("54.00");

        // A day-plus trip (the AC's "30 h" case; 31 h clears the strict 6 h leftover
        // threshold) → a full-day line plus a partial-day line, €79.00 all told.
        var dayPlus = service.previewTravel(
                domesticTravel(null, DEP, DEP.plusHours(31), false, false));
        assertThat(dayPlus.generatedLine(GeneratedLineKind.PER_DIEM_FULL)).isPresent();
        assertThat(dayPlus.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL)).isPresent();
        assertThat(dayPlus.amountOf(GeneratedLineKind.PER_DIEM_FULL)
                .add(dayPlus.amountOf(GeneratedLineKind.PER_DIEM_PARTIAL)))
                .isEqualByComparingTo("79.00");

        // Under the 6 h partial threshold: neither kind is generated.
        var tooShort = service.previewTravel(
                domesticTravel(null, DEP, DEP.plusHours(5), false, false));
        assertThat(tooShort.generatedLines()).isEmpty();
    }

    @Test
    void aFreeMealHalvesTheUnitPriceOfBothPerDiemLinesAndKeepsTheDaysHonest() {
        // ADR-0023: halving rides the unit price. 55 h with a free lunch → 2 days at
        // €27.00 + 1 partial at €12.50 = €66.50 (the pre-split €133.00 / 2).
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(55), false, true))));

        var trip = service.findMine(id).travels().getFirst();
        var full = trip.generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(full.quantity()).isEqualByComparingTo("2");
        assertThat(full.unitPrice()).isEqualByComparingTo("27.00");
        var partial = trip.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL).orElseThrow();
        assertThat(partial.quantity()).isEqualByComparingTo("1");
        assertThat(partial.unitPrice()).isEqualByComparingTo("12.50");
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("66.50");
    }

    @Test
    void reCostingATripReconcilesBothPerDiemKindsWithoutDuplicating() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(31), false, false))));
        var loaded = service.findMine(id);
        var trip = loaded.travels().getFirst();
        Long fullLineId = trip.generatedLine(GeneratedLineKind.PER_DIEM_FULL)
                .orElseThrow().lineId();

        // Shorten the trip to a whole 24 h: the full-day line is re-costed in place
        // (same id) and the partial-day line it no longer earns is removed.
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10), loaded.version(),
                List.of(domesticTravel(trip.id(), DEP, DEP.plusHours(24), false, false))),
                loaded.version());

        var reloaded = service.findMine(id);
        var reTrip = reloaded.travels().getFirst();
        assertThat(reTrip.generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow()
                .lineId()).isEqualTo(fullLineId);
        assertThat(reTrip.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL)).isEmpty();
        assertThat(reTrip.generatedLines()).hasSize(1);
        assertThat(reloaded.perDiemTotal()).isEqualByComparingTo("54.00");
        // Exactly one generated line survives on the aggregate — nothing stale.
        assertThat(reportRepository.findById(id).orElseThrow().getLines()).hasSize(1);
    }

    @Test
    void theMigrationReclassifiesALegacyPerDiemRowToTheFullDayKind() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11), false, false))));
        Long lineId = service.findMine(id).travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow().lineId();
        // Put the row back into its pre-split shape (a V12-era `PER_DIEM` value), which
        // no longer maps to any enum constant.
        jdbcTemplate.update(
                "update expense_line set generated_kind = 'PER_DIEM' where id = ?", lineId);
        assertThat(generatedKindOf(lineId)).isEqualTo("PER_DIEM");

        // Re-run the real V13 script — the statement Flyway applies on a live database.
        jdbcTemplate.execute((Connection connection) -> {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V13__per_diem_full_partial.sql"));
            return null;
        });

        assertThat(generatedKindOf(lineId)).isEqualTo("PER_DIEM_FULL");
        // The reclassified line loads as a full-day per-diem with its euros unchanged —
        // still the stored amount at quantity 1, until a travel edit re-splits it.
        entityManager.clear();
        var reloaded = service.findMine(id);
        var perDiem = reloaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(perDiem.quantity()).isEqualByComparingTo("1");
        assertThat(perDiem.unitPrice()).isEqualByComparingTo("54.00");
        assertThat(reloaded.perDiemTotal()).isEqualByComparingTo("54.00");
    }

    /** The persisted discriminator of one expense line, as raw SQL sees it. */
    private String generatedKindOf(Long lineId) {
        return jdbcTemplate.queryForObject(
                "select generated_kind from expense_line where id = ?", String.class,
                lineId);
    }

    // --- Foreign per-diem via the destination-country picker (Phase 4.2) ---

    @Test
    void foreignDestinationsListsTheCountriesWithARateForTheYearNotFinland() {
        var destinations = service.foreignDestinations(2026);
        // The 12 seeded 2026 countries, in country order; Finland is not among them
        // (it is the domestic option, added separately by the dialog).
        assertThat(destinations).hasSize(12).contains("Germany", "Sweden")
                .doesNotContain("Finland").isSorted();
    }

    @Test
    void previewCostsAForeignTripAgainstTheDestinationCountryRate() {
        // Germany 2026 = €71.00/day; 11 h → 1 allowance day → €71.00 (not the
        // domestic €54.00 — the ProCountor payoff).
        var preview = service.previewTravel(
                foreignTravel(null, DEP, DEP.plusHours(11), "Germany", false));

        assertThat(preview.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("71.00");
        assertThat(preview.generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow()
                .comment()).contains("Germany");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void createWithAForeignTripPersistsTheCountryAndForeignPerDiem() {
        // 24 h + 7 h leftover → 2 allowance days × €71.00 = €142.00.
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(foreignTravel(null, DEP, DEP.plusHours(31), "Germany", false))));

        var loaded = service.findMine(id);
        var trip = loaded.travels().getFirst();
        assertThat(trip.country()).isEqualTo("Germany");
        assertThat(trip.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("142.00");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("142.00");
        assertThat(loaded.total()).isEqualByComparingTo("142.00");
    }

    @Test
    void aForeignCountryWithNoRateForTheYearFailsClearlyNotSilentlyDomestic() {
        // Japan has no seeded 2026 rate: the trip must fail with a clear message,
        // never fall back to the Finnish per-diem.
        assertThatThrownBy(() -> service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(foreignTravel(null, DEP, DEP.plusHours(11), "Japan", false)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Japan")
                .hasMessageContaining("2026");
        // Nothing was persisted — no silent Finnish default slipped through.
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void aDomesticTripStillUsesTheFinnishPerDiemAlongsideForeignSupport() {
        // Same 11 h trip, domestic → €54.00 (the Finnish rate), proving the branch.
        var preview = service.previewTravel(
                domesticTravel(null, DEP, DEP.plusHours(11), false, false));
        assertThat(preview.amountOf(GeneratedLineKind.PER_DIEM_FULL))
                .isEqualByComparingTo("54.00");
    }

    // --- Quantity Override (ADR-0024, issue #131) ---
    //
    // The four facts the view test cannot observe: the persisted comment string, the
    // previewTravel contract, that an override can never conjure a line, and that a
    // foreign trip's per-diem is overridable through the same PER_DIEM_FULL kind.

    @Test
    void previewAppliesTheOverrideAndAnOverridesStrippedCopyShowsTheBaseline() {
        // 55 h → 2 full days (€54.00 each) + 1 partial; claim only 1 full day.
        var trip = domesticTravel(null, DEP, DEP.plusHours(55), false, false)
                .withQuantityOverride(GeneratedLineKind.PER_DIEM_FULL,
                        new QuantityOverride(BigDecimal.ONE,
                                "the Wednesday was personal"));

        var preview = service.previewTravel(trip);

        var full = preview.generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(full.quantity()).isEqualByComparingTo("1.00");
        assertThat(full.unitPrice()).isEqualByComparingTo("54.00"); // still statutory
        assertThat(full.amount()).isEqualByComparingTo("54.00");
        // The view carries the override's substance, so the row needs no parsing.
        assertThat(full.isOverridden()).isTrue();
        assertThat(full.overrideReason()).isEqualTo("the Wednesday was personal");
        assertThat(full.calculatedQuantity()).isEqualByComparingTo("2.00");
        assertThat(full.comment()).isEqualTo("Per diem allowance (full day): "
                + "1 × €54.00 = €54.00 — overridden from 2 days: "
                + "the Wednesday was personal");
        // The untouched partial-day line is unaffected.
        assertThat(preview.amountOf(GeneratedLineKind.PER_DIEM_PARTIAL))
                .isEqualByComparingTo("25.00");

        // The same call on an overrides-stripped copy is the calculated baseline —
        // no second service method.
        var calculated = service.previewTravel(trip.withoutQuantityOverrides());
        var calculatedFull = calculated
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(calculatedFull.quantity()).isEqualByComparingTo("2.00");
        assertThat(calculatedFull.isOverridden()).isFalse();
        assertThat(calculatedFull.amount()).isEqualByComparingTo("108.00");
    }

    @Test
    void anOverriddenLinePersistsItsEffectiveQuantityGrossAndSelfDescribingComment() {
        var trip = domesticTravel(null, DEP, DEP.plusHours(55), false, false)
                .withQuantityOverride(GeneratedLineKind.PER_DIEM_FULL,
                        new QuantityOverride(BigDecimal.ONE, "  personal day  "));
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(trip)));

        // Read the row itself: quantity, gross and comment must agree in the database,
        // so an export or an audit reads the same story the screen shows.
        entityManager.flush();
        var row = jdbcTemplate.queryForMap(
                "select amount, quantity, comment from expense_line "
                        + "where generated_kind = 'PER_DIEM_FULL' and travel_id = "
                        + "(select id from travel where report_id = ?)", id);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("54.00");
        assertThat((BigDecimal) row.get("quantity")).isEqualByComparingTo("1.00");
        assertThat((String) row.get("comment")).isEqualTo(
                "Per diem allowance (full day): 1 × €54.00 = €54.00 "
                        + "— overridden from 2 days: personal day");

        // …and the override itself round-trips as a keyed child of the trip, its
        // reason trimmed, with the subtotals following the effective figures.
        var loaded = service.findMine(id);
        var override = loaded.travels().getFirst().quantityOverrides()
                .get(GeneratedLineKind.PER_DIEM_FULL);
        assertThat(override.quantity()).isEqualByComparingTo("1.00");
        assertThat(override.reason()).isEqualTo("personal day");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("79.00"); // 54 + 25
        assertThat(loaded.total()).isEqualByComparingTo("79.00");
        // The loaded row still knows what the rules said.
        var full = loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(full.isOverridden()).isTrue();
        assertThat(full.calculatedQuantity()).isEqualByComparingTo("2.00");
    }

    @Test
    void anOverrideCanRescaleAnEarnedLineButNeverConjureOne() {
        // A not-eligible trip earning only a meal allowance: overriding the per-diem
        // it was never awarded must do nothing, or the report would carry BOTH a
        // per-diem and a meal allowance — which the Finnish rule forbids.
        var trip = domesticTravel(null, DEP, DEP.plusHours(11), ZERO, true, ZERO, true)
                .withQuantityOverride(GeneratedLineKind.PER_DIEM_FULL,
                        new QuantityOverride(new BigDecimal("3"), "give me the days"))
                .withQuantityOverride(GeneratedLineKind.MEAL,
                        new QuantityOverride(new BigDecimal("2"), "two meals taken"));

        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(trip)));

        var loaded = service.findMine(id);
        // No per-diem was conjured…
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL)).isEmpty();
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("0.00");
        // …while the meal allowance the trip DID earn rescaled: 2 × €13.50.
        assertThat(loaded.mealTotal()).isEqualByComparingTo("27.00");
        assertThat(loaded.total()).isEqualByComparingTo("27.00");
    }

    @Test
    void aForeignTripsPerDiemIsOverridableThroughTheFullDayKind() {
        // 31 h in Germany → 2 allowance days × €71.00, generated as PER_DIEM_FULL
        // (asserted, not assumed) — so a foreign trip inherits the override.
        var calculated = service.previewTravel(
                foreignTravel(null, DEP, DEP.plusHours(31), "Germany", false));
        assertThat(calculated.generatedLine(GeneratedLineKind.PER_DIEM_FULL))
                .isPresent();
        assertThat(calculated.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL))
                .isEmpty();

        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                foreignTravel(null, DEP, DEP.plusHours(31), "Germany", false)
                        .withQuantityOverride(GeneratedLineKind.PER_DIEM_FULL,
                                new QuantityOverride(BigDecimal.ONE,
                                        "only one day was business")))));

        var loaded = service.findMine(id);
        // The country rate is untouched; only the day count moved.
        var full = loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow();
        assertThat(full.unitPrice()).isEqualByComparingTo("71.00");
        assertThat(full.quantity()).isEqualByComparingTo("1.00");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("71.00");
    }

    @Test
    void anOverrideForKilometreIsRejectedByTheDomainNotSilentlyIgnored() {
        var trip = domesticTravel(null, DEP, DEP.plusHours(11), new BigDecimal("120"),
                false, ZERO, false)
                .withQuantityOverride(GeneratedLineKind.KILOMETRE,
                        new QuantityOverride(new BigDecimal("50"), "drove less"));

        assertThatThrownBy(() -> service.create(
                dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(trip))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("Kilometre allowance");

        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void anOverrideSurvivesATripEditAndIsClearedByResetToCalculated() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                domesticTravel(null, DEP, DEP.plusHours(55), false, false)
                        .withQuantityOverride(GeneratedLineKind.PER_DIEM_FULL,
                                new QuantityOverride(BigDecimal.ONE, "personal day")))));
        var loaded = service.findMine(id);
        var trip = loaded.travels().getFirst();

        // Editing an unrelated trip input keeps the correction in force (clearing it
        // when the calculated count moves is issue #133).
        var edited = domesticTravel(trip.id(), DEP, DEP.plusHours(55), false, false)
                .withQuantityOverrides(trip.quantityOverrides());
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 11),
                loaded.version(), List.of(edited)), loaded.version());
        var afterEdit = service.findMine(id);
        assertThat(afterEdit.perDiemTotal()).isEqualByComparingTo("79.00");

        // Reset to calculated drops the override outright, without touching the trip.
        var reset = domesticTravel(trip.id(), DEP, DEP.plusHours(55), false, false);
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 11),
                afterEdit.version(), List.of(reset)), afterEdit.version());

        var afterReset = service.findMine(id);
        assertThat(afterReset.travels().getFirst().quantityOverrides()).isEmpty();
        assertThat(afterReset.perDiemTotal()).isEqualByComparingTo("133.00");
        assertThat(afterReset.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL).orElseThrow()
                .isOverridden()).isFalse();
    }

    // --- Zero suppresses the line (ADR-0024, issue #132) ---
    //
    // A count of 0 drops the generated line altogether. What only this layer can
    // observe: that no expense_line row survives, that the subtotals follow, and that
    // the line's Receipt goes with it — a database-level ON DELETE CASCADE that is
    // invisible from any Java assertion about the DTOs.

    @Test
    void aZeroOverrideDropsTheGeneratedLineAndTheSubtotalsFollow() {
        // 55 h → 2 full days (€108.00) + 1 partial day (€25.00). Drop the leftover
        // partial day and keep the full days — the correction the trip inputs cannot
        // express, since moving the return time would move the full-day count too.
        var trip = domesticTravel(null, DEP, DEP.plusHours(55), false, false)
                .withQuantityOverride(GeneratedLineKind.PER_DIEM_PARTIAL,
                        new QuantityOverride(BigDecimal.ZERO,
                                "the leftover day was personal"));

        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(trip)));

        var loaded = service.findMine(id);
        var saved = loaded.travels().getFirst();
        assertThat(saved.generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL)).isEmpty();
        assertThat(saved.generatedLine(GeneratedLineKind.PER_DIEM_FULL)).isPresent();
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("108.00");
        assertThat(loaded.total()).isEqualByComparingTo("108.00");
        // No row was written at quantity 0 either: the line does not exist.
        entityManager.flush();
        assertThat(countLines("PER_DIEM_PARTIAL", id)).isZero();
        // The override itself persists, so the suppression survives a reload and the
        // owner can still see — and undo — what they dropped.
        assertThat(saved.quantityOverrides().get(GeneratedLineKind.PER_DIEM_PARTIAL)
                .isSuppression()).isTrue();
    }

    @Test
    void suppressingALineDeletesItsReceiptRowFromTheDatabase() {
        // The meal allowance is exactly where a receipt is plausible, so this is the
        // real case, not a contrived one. V6__receipts.sql declares
        // "expense_line_id ... on delete cascade" because ExpenseLine holds no
        // back-reference — so the blob goes when the line goes, irrecoverably.
        var id = service.create(
                dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                        domesticTravel(null, DEP, DEP.plusHours(11), ZERO, true, ZERO,
                                true))),
                Map.of(),
                Map.of(new GeneratedLineRef(0, GeneratedLineKind.MEAL),
                        new ReceiptUpload(JPEG, "lunch.jpg")));
        var loaded = service.findMine(id);
        var tripId = loaded.travels().getFirst().id();
        Long mealLineId = loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.MEAL).orElseThrow().lineId();
        assertThat(receiptRepository.findByExpenseLineId(mealLineId)).isPresent();

        // Detach the session so the update runs against a fresh load, as a real
        // request would.
        entityManager.clear();
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of(
                        domesticTravel(tripId, DEP, DEP.plusHours(11), ZERO, true, ZERO,
                                true).withQuantityOverride(GeneratedLineKind.MEAL,
                                        new QuantityOverride(BigDecimal.ZERO,
                                                "no meal was taken")))),
                loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.travels().getFirst().generatedLine(GeneratedLineKind.MEAL))
                .isEmpty();
        assertThat(reloaded.mealTotal()).isEqualByComparingTo("0.00");
        assertThat(reloaded.total()).isEqualByComparingTo("0.00");
        // The point of this test: the receipt is gone from the database, not merely
        // unreferenced.
        assertThat(receiptRepository.findByExpenseLineId(mealLineId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from receipt where expense_line_id = ?", Integer.class,
                mealLineId)).isZero();
    }

    @Test
    void resettingASuppressedKindBringsTheLineBackAtItsStatutoryCount() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                domesticTravel(null, DEP, DEP.plusHours(55), false, false)
                        .withQuantityOverride(GeneratedLineKind.PER_DIEM_PARTIAL,
                                new QuantityOverride(BigDecimal.ZERO, "personal")))));
        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("108.00");

        // Reset to calculated = save the trip without that kind's override. The line is
        // regenerated from the trip inputs, at the count the rules award.
        service.update(id, dtoWithTravels(id, LocalDate.of(2026, 7, 10),
                loaded.version(), List.of(domesticTravel(
                        loaded.travels().getFirst().id(), DEP, DEP.plusHours(55), false,
                        false))), loaded.version());

        var afterReset = service.findMine(id);
        var partial = afterReset.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_PARTIAL).orElseThrow();
        assertThat(partial.quantity()).isEqualByComparingTo("1.00");
        assertThat(partial.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(partial.isOverridden()).isFalse();
        assertThat(afterReset.perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void aZeroOverrideOnAKindTheTripNeverEarnedChangesNothing() {
        // Suppression rides the same earned-line gate as everything else, so it cannot
        // "remove" a line that was never there — and cannot disturb the one the trip
        // did earn.
        var trip = domesticTravel(null, DEP, DEP.plusHours(11), ZERO, true, ZERO, true)
                .withQuantityOverride(GeneratedLineKind.PER_DIEM_FULL,
                        new QuantityOverride(BigDecimal.ZERO, "no per-diem anyway"));

        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(trip)));

        var loaded = service.findMine(id);
        assertThat(loaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM_FULL)).isEmpty();
        assertThat(loaded.mealTotal()).isEqualByComparingTo("13.50");
        assertThat(loaded.total()).isEqualByComparingTo("13.50");
    }

    /** How many {@code expense_line} rows of one generated kind a report's trip owns. */
    private Integer countLines(String generatedKind, Long reportId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from expense_line where generated_kind = ? "
                        + "and travel_id = (select id from travel where report_id = ?)",
                Integer.class, generatedKind, reportId);
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private static TravelDto domesticTravel(Long id, LocalDateTime departure,
            LocalDateTime returnAt, boolean notEligible, boolean freeLunch) {
        return TravelDto.domestic(id, departure, returnAt, "Helsinki", "Client visit",
                notEligible, freeLunch, false, ZERO, false, ZERO);
    }

    /** A foreign trip to the given destination country (no km/meal/parking). */
    private static TravelDto foreignTravel(Long id, LocalDateTime departure,
            LocalDateTime returnAt, String country, boolean notEligible) {
        return TravelDto.of(id, departure, returnAt, "Berlin", "Conference", country,
                notEligible, false, false, ZERO, false, ZERO);
    }

    /** A domestic trip with the Phase 4.3 km / meal / parking inputs set. */
    private static TravelDto domesticTravel(Long id, LocalDateTime departure,
            LocalDateTime returnAt, BigDecimal kilometres, boolean payMeal,
            BigDecimal parkingFees, boolean notEligible) {
        return TravelDto.domestic(id, departure, returnAt, "Helsinki", "Client visit",
                notEligible, false, false, kilometres, payMeal, parkingFees);
    }

    private static ReportDetailDto dtoWithTravels(LocalDate date, List<TravelDto> travels) {
        return dtoWithTravels(null, date, 0L, travels);
    }

    private static ReportDetailDto dtoWithTravels(Long id, LocalDate date, long version,
            List<TravelDto> travels) {
        return new ReportDetailDto(id, date, "trip", ReportStatus.DRAFT, version,
                List.of(), travels, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    private static byte[] pad(byte... magic) {
        byte[] file = new byte[magic.length + 6];
        System.arraycopy(magic, 0, file, 0, magic.length);
        return file;
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
        return ExpenseLineDto.of(null, type.getId(), type.getName(), rate.getId(),
                rate.getValue(), new BigDecimal(amount), comment);
    }

    /** A new line with an explicit quantity — {@code unitPrice} is per item. */
    private static ExpenseLineDto newLine(ExpenseType type, VatRate rate,
            String unitPrice, String quantity, String comment) {
        return ExpenseLineDto.of(null, type.getId(), type.getName(), rate.getId(),
                rate.getValue(), new BigDecimal(unitPrice), new BigDecimal(quantity),
                comment);
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

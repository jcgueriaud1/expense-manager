package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.report.domain.ExpenseLineSpec;
import com.vaadin.expensemanager.report.domain.ExpenseReport;
import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.Receipt;
import com.vaadin.expensemanager.report.domain.ReceiptRejectedException;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.ExpenseTypeRepository;
import com.vaadin.expensemanager.reference.VatRate;
import com.vaadin.expensemanager.reference.VatRateRepository;
import com.vaadin.expensemanager.security.LocalUserDetailsService;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.expensemanager.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
                .isInstanceOf(IllegalStateException.class)
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
        report.reconcileLines(List.of(new ExpenseLineSpec(null, firstActiveType(),
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
    void receiptForDownloadIsEmptyForAMissingId() {
        assertThat(service.receiptForDownload(-1L)).isEmpty();
    }

    // --- Travel / domestic per-diem (Phase 4.2/4.3) ---

    private static final LocalDateTime DEP = LocalDateTime.of(2026, 7, 1, 8, 0);

    @Test
    void previewComputesTheServerAuthoritativePerDiemWithoutPersisting() {
        // 11 h (> 10 h) → one full day at the seeded 2026 rate (€54.00).
        var preview = service.previewDomesticTravel(
                domesticTravel(null, DEP, DEP.plusHours(11), false, false));

        assertThat(preview.amountOf(GeneratedLineKind.PER_DIEM))
                .isEqualByComparingTo("54.00");
        assertThat(preview.generatedLine(GeneratedLineKind.PER_DIEM).orElseThrow()
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
        assertThat(trip.amountOf(GeneratedLineKind.PER_DIEM))
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
        assertThat(reloaded.travels().getFirst().amountOf(GeneratedLineKind.PER_DIEM))
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
    void previewComputesEveryTripOutputServerSide() {
        // 11 h → €54.00 per-diem; 120 km × €0.59 = €70.80; meal €13.50;
        // parking €12.00 (VAT-bearing, at the parking type's 25.5 %).
        var preview = service.previewDomesticTravel(domesticTravel(null, DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00")));

        assertThat(preview.amountOf(GeneratedLineKind.PER_DIEM))
                .isEqualByComparingTo("54.00");
        assertThat(preview.amountOf(GeneratedLineKind.KILOMETRE))
                .isEqualByComparingTo("70.80");
        assertThat(preview.amountOf(GeneratedLineKind.MEAL))
                .isEqualByComparingTo("13.50");
        assertThat(preview.amountOf(GeneratedLineKind.PARKING))
                .isEqualByComparingTo("12.00");
        assertThat(preview.generatedLine(GeneratedLineKind.PARKING).orElseThrow()
                .vatRatePercent()).isEqualByComparingTo("25.50");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void createWithKmMealParkingPersistsEachGeneratedLineRoutedCorrectly() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11),
                        new BigDecimal("120"), true, new BigDecimal("12.00")))));

        var loaded = service.findMine(id);
        // The three tax-free allowances each land in their own subtotal…
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("70.80");
        assertThat(loaded.mealTotal()).isEqualByComparingTo("13.50");
        // …parking is VAT-bearing → Net/VAT (12.00 @ 25.5 %), not a subtotal.
        assertThat(loaded.netTotal()).isEqualByComparingTo("9.56");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("2.44");
        assertThat(loaded.total()).isEqualByComparingTo("150.30");
        // Four generated lines are persisted; none appear as editable manual cards.
        assertThat(loaded.lines()).isEmpty();
        assertThat(reportRepository.findById(id).orElseThrow().getLines()).hasSize(4);

        // The working copy round-trips its inputs and per-kind amounts.
        var trip = loaded.travels().getFirst();
        assertThat(trip.kilometres()).isEqualByComparingTo("120.00");
        assertThat(trip.payMealAllowance()).isTrue();
        assertThat(trip.parkingFees()).isEqualByComparingTo("12.00");
        assertThat(trip.generatedLine(GeneratedLineKind.PARKING).orElseThrow()
                .vatRatePercent()).isEqualByComparingTo("25.50");
    }

    @Test
    void editingDropsTheKindsATripNoLongerEarns() {
        var id = service.create(dtoWithTravels(LocalDate.of(2026, 7, 10),
                List.of(domesticTravel(null, DEP, DEP.plusHours(11),
                        new BigDecimal("120"), true, new BigDecimal("12.00")))));
        var loaded = service.findMine(id);
        var trip = loaded.travels().getFirst();

        // Re-cost: keep only the per-diem (clear km / meal / parking).
        var edited = domesticTravel(trip.id(), DEP, DEP.plusHours(11), ZERO, false, ZERO);
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
                Map.of(new GeneratedLineRef(0, GeneratedLineKind.PER_DIEM),
                        new ReceiptUpload(JPEG, "perdiem.jpg")));

        var perDiem = service.findMine(id).travels().getFirst()
                .generatedLine(GeneratedLineKind.PER_DIEM).orElseThrow();
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
                Map.of(new GeneratedLineRef(0, GeneratedLineKind.PER_DIEM),
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
                .generatedLine(GeneratedLineKind.PER_DIEM).orElseThrow();
        assertThat(perDiem.amount()).isEqualByComparingTo("27.00");
        assertThat(perDiem.receiptFilename()).isEqualTo("perdiem.jpg");
    }

    @Test
    void removingAGeneratedLineKindCascadesItsReceipt() {
        var id = service.create(
                dtoWithTravels(LocalDate.of(2026, 7, 10), List.of(
                        domesticTravel(null, DEP, DEP.plusHours(11), ZERO, false,
                                new BigDecimal("12.00")))),
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
                        domesticTravel(tripId, DEP, DEP.plusHours(11), ZERO, false, ZERO))),
                loaded.version());

        var reloaded = service.findMine(id);
        assertThat(reloaded.travels().getFirst()
                .generatedLine(GeneratedLineKind.PARKING)).isEmpty();
        assertThat(receiptRepository.findByExpenseLineId(parkingLineId)).isEmpty();
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private static TravelDto domesticTravel(Long id, LocalDateTime departure,
            LocalDateTime returnAt, boolean notEligible, boolean freeLunch) {
        return TravelDto.domestic(id, departure, returnAt, "Helsinki", "Client visit",
                notEligible, freeLunch, false, ZERO, false, ZERO);
    }

    /** A domestic trip with the Phase 4.3 km / meal / parking inputs set. */
    private static TravelDto domesticTravel(Long id, LocalDateTime departure,
            LocalDateTime returnAt, BigDecimal kilometres, boolean payMeal,
            BigDecimal parkingFees) {
        return TravelDto.domestic(id, departure, returnAt, "Helsinki", "Client visit",
                false, false, false, kilometres, payMeal, parkingFees);
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

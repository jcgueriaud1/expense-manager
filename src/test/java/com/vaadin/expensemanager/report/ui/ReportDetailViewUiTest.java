package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Image;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link ReportDetailView}
 * (UC-001/UC-005, ADR-0019). Runs as the seeded plain user via
 * {@link WithUserDetails}.
 *
 * <p>Covers the create path (transient working copy, date defaulting to today,
 * first save persisting a DRAFT and routing to {@code /report/{id}}), the
 * always-enabled-Save + validation-error-summary rule (no disabled submit,
 * ADR-0020), report-level editing, and the draft-only delete returning to the
 * list. Persisted state is asserted through {@link #service}.
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ReportDetailViewUiTest extends AbstractReportViewUiTest {

    @Test
    void newReportSaveWithDefaultsPersistsTodayDatedDraftAndRoutesToId() {
        navigate(ReportDetailView.class);

        // No field touched: the date defaults to today; first save persists.
        findButton().withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().reportDate()).isEqualTo(LocalDate.now());
        // Still on the detail view — now the persisted /report/{id} form.
        assertThat(getCurrentView()).isInstanceOf(ReportDetailView.class);
    }

    @Test
    void missingRequiredDateShowsErrorSummaryAndPersistsNothing() {
        navigate(ReportDetailView.class);

        // The DatePicker tester refuses to *set* an invalid value (null on a
        // required field), so clear the value straight on the component to model
        // a user emptying the field, then save (finding F-021).
        $(DatePicker.class).first().clear();
        findButton().withText("Save").click();

        // Always-enabled Save + top-of-form error summary (ADR-0020).
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Report date is required");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void editingExistingReportUpdatesItsFields() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "before");
        navigate(ReportDetailView.class, id);

        findTextArea().withLabel("Additional information").setValue("after");
        findDatePicker().withLabel("Report date").setValue(LocalDate.of(2026, 7, 20));
        findButton().withText("Save").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.additionalInformation()).isEqualTo("after");
        assertThat(reloaded.reportDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void deletingADraftRemovesItAndReturnsToTheList() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "scratch");
        navigate(ReportDetailView.class, id);

        findButton().withText("Delete").click();          // opens the confirm dialog
        findButton().withText("Delete report").click();   // confirms

        assertThat(getCurrentView()).isInstanceOf(MyReportsView.class);
        assertThat(service.listMine()).noneMatch(r -> r.id().equals(id));
    }

    @Test
    void newReportDoesNotOfferDelete() {
        navigate(ReportDetailView.class);

        // Delete is hidden until the report is a persisted DRAFT.
        assertThat(findButton().withText("Delete").exists()).isFalse();
    }

    @Test
    void addingALineViaTheDialogUpdatesLiveTotalsAndPersistsOnSave() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().setValue(new BigDecimal("100"));
        findButton().withText("Save expense").click();

        // Live total bar reflects the added line before the report is saved.
        assertThat(getCurrentView().getElement().getTextRecursively()).contains("€100.00");

        findButton().withText("Save").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.lines().getFirst().amount()).isEqualByComparingTo("100.00");
        assertThat(reloaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void lineEditorKeepsSaveEnabledAndBlocksAnEmptyLine() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withText("Add expense").click();
        // Always-enabled Save (ADR-0020): clicking it with nothing filled must
        // not add a line — validation blocks it and the dialog stays open.
        findButton().withText("Save expense").click();

        // Dialog is still open (validation blocked the save) …
        assertThat(findButton().withText("Save expense").exists()).isTrue();
        // … and no line was added: the empty state still shows, nothing persisted.
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("No expenses yet");
        assertThat(service.findMine(id).lines()).isEmpty();
    }

    @Test
    void submittingADraftWithALineLocksItReadOnly() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        findButton().withText("Submit for approval").click();

        // The report is now SUBMITTED and read-only to the owner: the editing
        // affordances are gone and the status is shown as text (ADR-0020).
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Submitted");
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
        assertThat(findButton().withText("Add expense").exists()).isFalse();
        assertThat(findButton().withText("Delete").exists()).isFalse();
    }

    @Test
    void submittingAnEmptyDraftShowsTheReasonAndStaysEnabled() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "empty");
        navigate(ReportDetailView.class, id);

        // Always-enabled Submit (ADR-0020): a zero-line submit is blocked with the
        // reason in the top-of-form error summary, not a silent no-op.
        findButton().withText("Submit for approval").click();

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        // Submit button is still there (never disabled) so the owner can retry.
        assertThat(findButton().withText("Submit for approval").exists()).isTrue();
    }

    @Test
    void anAlreadySubmittedReportOpensReadOnly() {
        var id = seedSubmittedReport(LocalDate.of(2026, 7, 1), "80.00");
        navigate(ReportDetailView.class, id);

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Submitted");
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
        assertThat(findButton().withText("Delete").exists()).isFalse();
        assertThat(findButton().withText("Add expense").exists()).isFalse();
        // The line is shown but its remove affordance is not offered.
        assertThat(findButton().withAriaLabel("Remove line").exists()).isFalse();
    }

    @Test
    void uploadingAReceiptOnAnUnsavedReportPersistsItOnFirstSave() {
        // Brand-new, never-saved report: the receipt attaches at any time, no
        // "save first" gate (ADR-0021 overrides ADR-0019's save-before-attach).
        navigate(ReportDetailView.class);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().setValue(new BigDecimal("100"));
        // Drive the real UploadHandler: bytes are validated server-side by magic
        // bytes and buffered in memory (the browser mime is not trusted).
        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());
        findButton().withText("Save expense").click();

        // The card reflects the buffered receipt before the report is even saved.
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("taxi.jpg");

        findButton().withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        var line = service.findMine(mine.getFirst().id()).lines().getFirst();
        assertThat(line.hasReceipt()).isTrue();
        assertThat(line.receiptFilename()).isEqualTo("taxi.jpg");
        assertThat(line.receiptContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void aMislabeledUploadIsRejectedAndNothingIsBuffered() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().setValue(new BigDecimal("100"));
        // A text file renamed to .jpg: magic-byte check rejects it at upload; the
        // control is never disabled, the reason is surfaced instead (ADR-0020).
        findUpload().upload("fake.jpg", "image/jpeg", "not an image".getBytes());
        findButton().withText("Save expense").click();
        findButton().withText("Save").click();

        // The line saved, but with no receipt — the rejected bytes were dropped.
        assertThat(service.findMine(id).lines().getFirst().hasReceipt()).isFalse();
    }

    @Test
    void aSubmittedReportShowsTheReceiptSummaryButOffersNoUpload() {
        var id = seedSubmittedReportWithReceipt(LocalDate.of(2026, 7, 1), "50.00",
                "hotel.jpg");
        navigate(ReportDetailView.class, id);

        // The receipt is viewable (read path, ADR-0021): its image preview shows
        // even on a read-only report. But every mutation surface is gone — no Add
        // expense to open the editor, so the receipt cannot be changed.
        assertThat(findButton().withAriaLabel("Preview receipt: hotel.jpg").exists())
                .isTrue();
        assertThat(findButton().withText("Add expense").exists()).isFalse();
        assertThat(findButton().withAriaLabel("Remove line").exists()).isFalse();
    }

    @Test
    void aSavedImageReceiptRendersAnEnlargeablePreview() {
        // A persisted image receipt: the card shows a keyboard-operable thumbnail
        // button (accessible name) that streams from the DB and enlarges in a
        // dialog (ADR-0021 / ADR-0020) — visible even on a read-only report.
        var id = seedSubmittedReportWithReceipt(LocalDate.of(2026, 7, 1), "50.00",
                "hotel.jpg");
        navigate(ReportDetailView.class, id);

        assertThat(findButton().withAriaLabel("Preview receipt: hotel.jpg").exists())
                .isTrue();
        assertThat($(Image.class).exists()).isTrue();
    }

    @Test
    void aSavedPdfReceiptOffersAnOpenAffordance() {
        var id = seedReportWithReceipt(LocalDate.of(2026, 7, 1), "70.00",
                "invoice.pdf", pdfBytes());
        navigate(ReportDetailView.class, id);

        // A PDF is not thumbnailed — it offers an "open" link (browser viewer).
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Open invoice.pdf");
    }

    @Test
    void anUnsavedImageAttachmentPreviewsFromBufferedBytes() {
        // Brand-new report, receipt attached but nothing saved yet: the editor
        // previews the buffered bytes directly (no DB round-trip, no receipt id).
        navigate(ReportDetailView.class);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().setValue(new BigDecimal("100"));
        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());

        // The preview affordance appears in the still-open dialog, before any save.
        assertThat(findButton().withAriaLabel("Preview receipt: taxi.jpg").exists())
                .isTrue();
    }

    // --- Travel Calculator / domestic per-diem (Phase 4.2/4.3) ---

    private static final LocalDateTime DEP = LocalDateTime.of(2026, 7, 1, 8, 0);

    @Test
    void insertingADomesticTripPreviewsGeneratesAndPersistsThePerDiem() {
        navigate(ReportDetailView.class);

        findButton().withText("Insert travel info").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");

        // The per-diem previews live as the dates are filled — no separate compute
        // step (11 h → one full day €54). The preview lives in the dialog overlay
        // (attached to the UI, not the view), so assert against the whole UI tree.
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Per diem: €54.00");

        findButton().withText("Save trip").click();

        // The trip card + the live "Per diem allowance" subtotal row appear before
        // the report is even saved.
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Client visit", "Helsinki, Finland",
                "Per diem allowance", "€54.00");

        findButton().withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        var loaded = service.findMine(mine.getFirst().id());
        assertThat(loaded.travels()).hasSize(1);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.total()).isEqualByComparingTo("54.00");
    }

    @Test
    void editingATripWithFreeLunchRegeneratesAHalvedPerDiem() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        findButton().withText("Edit").click();          // the trip card's Edit
        findCheckbox().withLabel("Free lunch provided?").click();
        findButton().withText("Save trip").click();

        // Live per-diem halves to €27.00; a save regenerates the line.
        assertThat(getCurrentView().getElement().getTextRecursively()).contains("€27.00");
        findButton().withText("Save").click();

        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("27.00");
    }

    @Test
    void removingATripRemovesItsPerDiemOnSave() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Per diem allowance");

        findButton().withAriaLabel("Remove trip").click();
        findButton().withText("Save").click();

        assertThat(service.findMine(id).travels()).isEmpty();
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void choosingADepartureConstrainsTheReturnPickerRangeAndViceVersa() {
        navigate(ReportDetailView.class);

        findButton().withText("Insert travel info").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));

        // The overlay can no longer offer an invalid range: the return can't go
        // before the departure, nor the departure after the return.
        var ret = (DateTimePicker) findDateTimePicker().withLabel("Return")
                .getComponent();
        var dep = (DateTimePicker) findDateTimePicker().withLabel("Departure")
                .getComponent();
        assertThat(ret.getMin()).isEqualTo(DEP);
        assertThat(dep.getMax()).isEqualTo(DEP.plusHours(11));
    }

    @Test
    void anIncompleteTripShowsTheErrorSummaryAndGeneratesNothing() {
        // The reciprocal min/max on the pickers means an invalid *range* can't be
        // produced through the UI (see the constraint test above); the return-
        // before-departure guard itself is covered at the domain/service layers.
        // Here we prove the always-enabled Save + error-summary rule (ADR-0020) on
        // the reachable invalid case: missing required fields.
        navigate(ReportDetailView.class);

        findButton().withText("Insert travel info").click();
        findButton().withText("Save trip").click();

        // The dialog overlay carries the reasons and stays open; nothing generated.
        assertThat(UI.getCurrent().getElement().getTextRecursively()).contains(
                "Departure date & time is required", "Destinations are required");
        assertThat(findButton().withText("Save trip").exists()).isTrue();
        assertThat(findSpan().withText("Per diem allowance").exists()).isFalse();
    }

    @Test
    void aSubmittedReportShowsTheTripButHidesEveryTripAction() {
        var id = seedSubmittedReportWithTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        // The trip info and its per-diem are still visible (read path)…
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Client visit", "Per diem allowance", "€54.00");
        // …a trip-only read-only report must not prompt "add your first": the empty
        // state is hidden, so no visible span carries it (ADR-0020)…
        assertThat(findSpan().withText("No expenses yet — add your first.").exists())
                .isFalse();
        // …and every mutation surface is gone on a read-only report.
        assertThat(findButton().withText("Insert travel info").exists()).isFalse();
        assertThat(findButton().withText("Edit").exists()).isFalse();
        assertThat(findButton().withAriaLabel("Remove trip").exists()).isFalse();
    }

    @Test
    void removingALinePersistsOnSave() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        assertThat(getCurrentView().getElement().getTextRecursively()).contains("€60.00");

        findButton().withAriaLabel("Remove line").click();
        findButton().withText("Save").click();

        assertThat(service.findMine(id).lines()).isEmpty();
    }
}

package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
    void reportDateCarriesABadInputErrorMessage() {
        // Issue #85: typing an unparseable date (e.g. "dsdds") makes the picker
        // invalid; without a bad-input message the error reaches the summary blank.
        // The tester can't type an unparseable value, so we assert the message that
        // prevents the blank error is configured on the field.
        navigate(ReportDetailView.class);

        var picker = (DatePicker) $(DatePicker.class).first();
        assertThat(picker.getI18n()).isNotNull();
        assertThat(picker.getI18n().getBadInputErrorMessage()).isNotBlank();
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
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("100"));
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
    void lineEditorDefaultsQuantityToOneAndShowsTheLineTotalLive() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withText("Add expense").click();

        // Quantity starts at 1, so the line total is the unit price itself.
        assertThat(findBigDecimalField().withLabel("Quantity").getComponent().getValue())
                .isEqualByComparingTo("1");
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("12.50"));
        assertThat(findDialog().getComponent().getElement().getTextRecursively())
                .contains("Line total").contains("€12.50");

        // Raising the quantity recomputes the total before anything is saved.
        findBigDecimalField().withLabel("Quantity").setValue(new BigDecimal("3"));
        assertThat(findDialog().getComponent().getElement().getTextRecursively()).contains("€37.50");
    }

    @Test
    void aQuantityLinePersistsItsUnitPriceAndTotalsTheProduct() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("100"));
        findBigDecimalField().withLabel("Quantity").setValue(new BigDecimal("3"));
        findButton().withText("Save expense").click();

        // The card shows the qty × unit = gross breakdown and the live total bar
        // reflects the product, both before the report is saved.
        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("3 × €100.00 = €300.00").contains("€300.00");

        findButton().withText("Save").click();

        var line = service.findMine(id).lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("100.00");     // unit price
        assertThat(line.quantity()).isEqualByComparingTo("3.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("300.00");
    }

    @Test
    void aQuantityOneCardShowsNoQuantityBreakdown() {
        // The feature is invisible until used (ADR-0023).
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "100.00");
        navigate(ReportDetailView.class, id);

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("€100.00")
                .doesNotContain("1 × €100.00");
    }

    @Test
    void aNonPositiveQuantityIsBlockedWithAnErrorSummary() {
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("100"));
        findBigDecimalField().withLabel("Quantity").setValue(BigDecimal.ZERO);
        // Always-enabled Save (ADR-0020): the click is allowed, the reason shows.
        findButton().withText("Save expense").click();

        assertThat(findDialog().getComponent().getElement().getTextRecursively())
                .contains("Quantity must be greater than zero");
        assertThat(service.findMine(id).lines()).isEmpty();
    }

    @Test
    void editingALineLoadsAndUpdatesItsQuantity() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "50.00");
        navigate(ReportDetailView.class, id);

        // Open the existing (quantity-1) line and give it a quantity. The card body
        // itself carries the click listener (no Edit button), so click that.
        openLineCardEditor();
        assertThat(findBigDecimalField().withLabel("Quantity").getComponent().getValue())
                .isEqualByComparingTo("1");
        findBigDecimalField().withLabel("Quantity").setValue(new BigDecimal("4"));
        findButton().withText("Save expense").click();
        findButton().withText("Save").click();

        var line = service.findMine(id).lines().getFirst();
        assertThat(line.amount()).isEqualByComparingTo("50.00");
        assertThat(line.quantity()).isEqualByComparingTo("4.00");
        assertThat(service.findMine(id).total()).isEqualByComparingTo("200.00");
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

        findButton().withText("Submit for approval").click(); // opens the confirm dialog
        findButton().withText("Submit report").click();       // confirms

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
        // reason in the top-of-form error summary, not a silent no-op. The report
        // date is valid, so the confirm dialog opens; the domain guard fires on
        // confirm and the atomic save+submit rolls back (issue #81).
        findButton().withText("Submit for approval").click();
        findButton().withText("Submit report").click();

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        // Submit button is still there (never disabled) so the owner can retry.
        assertThat(findButton().withText("Submit for approval").exists()).isTrue();
    }

    // --- Submit saves the current state + confirmation dialog (issue #81) ---

    @Test
    void submittingPersistsAReportLevelEditThatWasNotSavedFirst() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        // Edit a report-level field but never click Save — the edit lives only in
        // the working copy until the submit persists it (issue #81).
        findTextArea().withLabel("Additional information")
                .setValue("edited but not saved");

        findButton().withText("Submit for approval").click(); // opens the confirm dialog
        findButton().withText("Submit report").click();       // confirms

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("edited but not saved");
    }

    @Test
    void submittingPersistsALineAddedButNotSavedFirst() {
        // A seed with no lines: before issue #81 a line added in the dialog but not
        // Saved was lost on submit, so the submit failed the "≥1 line" guard. Now
        // the submit saves the pending line first, so it succeeds.
        var id = seedReport(LocalDate.of(2026, 7, 1), "trip");
        navigate(ReportDetailView.class, id);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("100"));
        findButton().withText("Save expense").click();

        findButton().withText("Submit for approval").click();
        findButton().withText("Submit report").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.lines()).hasSize(1);
        assertThat(reloaded.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void cancellingTheSubmitConfirmationLeavesItAnEditableDraft() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        findButton().withText("Submit for approval").click(); // opens the dialog
        findButton().withText("Cancel").click();              // backs out

        // Nothing submitted: still an editable DRAFT with both actions offered.
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
        assertThat(findButton().withText("Submit for approval").exists()).isTrue();
        assertThat(findButton().withText("Save").exists()).isTrue();
    }

    @Test
    void submittingWithNoReportDateBlocksBeforeConfirming() {
        var id = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, id);

        // Clearing the required date must block the submit at validation — the
        // confirm dialog never opens and nothing transitions.
        $(DatePicker.class).first().clear();
        findButton().withText("Submit for approval").click();

        assertThat(findButton().withText("Submit report").exists()).isFalse();
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Report date is required");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.DRAFT);
    }

    @Test
    void aRejectedReportShowsTheRealReasonRejecterAndDateToTheOwner() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100",
                "Please attach the hotel receipt.");
        navigate(ReportDetailView.class, id);

        // The owner's callout carries the real reason, who rejected it (the seeded
        // admin), and when — replacing the placeholder note. The reason and the
        // rejecter also appear in the status-history audit trail below.
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Rejected — changes requested",
                "Please attach the hotel receipt.", "Expense Admin", "14 Jul 2026");
        assertThat(text).contains("Status history");
        // A rejected report is editable again (owner can resubmit), so Save shows.
        assertThat(findButton().withText("Save").exists()).isTrue();
    }

    // --- Resubmit a rejected report (Phase 5.5) ---

    @Test
    void aRejectedReportOffersResubmitNotSubmit() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Fix the total.");
        navigate(ReportDetailView.class, id);

        // The forward action is relabelled "Resubmit" on a REJECTED report; the
        // first-submit label is gone (same button, flipped by status).
        assertThat(findButton().withText("Resubmit").exists()).isTrue();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
    }

    @Test
    void resubmitIsNotOfferedOnDraftSubmittedOrApproved() {
        // DRAFT offers the first-submit label, never "Resubmit".
        var draft = seedReportWithLine(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, draft);
        assertThat(findButton().withText("Submit for approval").exists()).isTrue();
        assertThat(findButton().withText("Resubmit").exists()).isFalse();

        // SUBMITTED is read-only — no forward action of either label.
        var submitted = seedSubmittedReport(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, submitted);
        assertThat(findButton().withText("Resubmit").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();

        // APPROVED is terminal and read-only — likewise no forward action.
        var approved = seedApprovedReport(LocalDate.of(2026, 7, 1), "60.00");
        navigate(ReportDetailView.class, approved);
        assertThat(findButton().withText("Resubmit").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
    }

    @Test
    void resubmittingARejectedReportSendsItBackToSubmittedAndLocksItReadOnly() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Fix the total.");
        navigate(ReportDetailView.class, id);

        findButton().withText("Resubmit").click();         // opens the confirm dialog
        findButton().withText("Resubmit report").click();  // confirms

        // Back through the queue: REJECTED → SUBMITTED, and read-only to the owner
        // again (the editing affordances and the forward action are gone).
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Submitted");
        assertThat(findButton().withText("Resubmit").exists()).isFalse();
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Add expense").exists()).isFalse();
    }

    @Test
    void resubmittingPersistsAnEditThatWasNotSavedFirst() {
        var id = seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "Add detail.");
        navigate(ReportDetailView.class, id);

        // Address the feedback but don't click Save — the resubmit must save it
        // as part of the transition (issue #81).
        findTextArea().withLabel("Additional information")
                .setValue("addressed the feedback");

        findButton().withText("Resubmit").click();
        findButton().withText("Resubmit report").click();

        var reloaded = service.findMine(id);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reloaded.additionalInformation()).isEqualTo("addressed the feedback");
    }

    @Test
    void resubmittingAnEmptyRejectedReportShowsTheReasonAndStaysEnabled() {
        var id = seedEmptyRejectedReport(LocalDate.of(2026, 7, 1), "Fix the total.");
        navigate(ReportDetailView.class, id);

        // Always-enabled Resubmit (ADR-0020): a zero-line resubmit is blocked with
        // the reason in the top-of-form error summary, not a silent no-op. The
        // confirm dialog opens (the date is valid); the domain guard fires on
        // confirm and the atomic save+resubmit rolls back (issue #81).
        findButton().withText("Resubmit").click();
        findButton().withText("Resubmit report").click();

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("at least one line");
        assertThat(service.findMine(id).status()).isEqualTo(ReportStatus.REJECTED);
        // The button is still there (never disabled) so the owner can add a line and retry.
        assertThat(findButton().withText("Resubmit").exists()).isTrue();
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
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("100"));
        // Drive the real UploadHandler: bytes are validated server-side by magic
        // bytes and buffered in memory (the browser mime is not trusted).
        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());
        findButton().withText("Save expense").click();

        // The card reflects the buffered receipt before the report is even saved —
        // as an image thumbnail (its filename is the accessible name), matching how
        // a persisted receipt renders (issue #89).
        assertThat(findButton().withAriaLabel("Preview receipt: taxi.jpg").exists())
                .isTrue();

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
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("100"));
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
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("100"));
        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());

        // The preview affordance appears in the still-open dialog, before any save.
        assertThat(findButton().withAriaLabel("Preview receipt: taxi.jpg").exists())
                .isTrue();
    }

    @Test
    void anUnsavedImageAttachmentPreviewsOnTheCardBeforeSave() {
        // Issue #89: after the line editor closes, the card must show the image
        // thumbnail from the buffered bytes right away — not only after the report
        // is saved (previously the card showed just a "📎 filename" chip until the
        // receipt was persisted and had an id).
        navigate(ReportDetailView.class);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods");
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().withLabel("Unit price (gross, each)")
                .setValue(new BigDecimal("100"));
        findUpload().upload("taxi.jpg", "image/jpeg", jpegBytes());
        findButton().withText("Save expense").click();

        // Dialog is closed; the preview button now belongs to the line card, and it
        // renders before the report has ever been saved (no receipt id yet).
        assertThat(findButton().withText("Save expense").exists()).isFalse();
        assertThat(findButton().withAriaLabel("Preview receipt: taxi.jpg").exists())
                .isTrue();
        assertThat($(Image.class).exists()).isTrue();
    }

    // --- Travel Calculator / domestic per-diem (Phase 4.2/4.3) ---

    private static final LocalDateTime DEP = LocalDateTime.of(2026, 7, 1, 8, 0);

    @Test
    void insertingADomesticTripPreviewsGeneratesAndPersistsThePerDiem() {
        navigate(ReportDetailView.class);

        findButton().withText("Insert travel info").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        findComboBox(String.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");

        // The per-diem previews live as the dates are filled — no separate compute
        // step (11 h → one full day €54). The preview lives in the dialog overlay
        // (attached to the UI, not the view), so assert against the whole UI tree.
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Per diem allowance (full day): €54.00");

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
    void insertingAForeignTripCostsThePerDiemAgainstTheDestinationCountry() {
        navigate(ReportDetailView.class);

        findButton().withText("Insert travel info").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        // The picker lists the seeded foreign countries alongside Finland; pick one.
        var country = findComboBox(String.class).withLabel("Destination country");
        assertThat(country.getSuggestions())
                .contains("Finland (domestic)", "Germany", "Sweden");
        country.selectItem("Germany");
        findTextField().withLabel("Destinations").setValue("Berlin");
        findTextField().withLabel("Travel purpose").setValue("Conference");

        // Costed against Germany's €71.00/day, not the Finnish €54.00.
        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Per diem allowance (full day): €71.00", "Germany");

        findButton().withText("Save trip").click();
        // The chosen country shows on the Trip & Allowance card ("destinations, country").
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Berlin, Germany", "€71.00");

        findButton().withText("Save").click();

        var loaded = service.findMine(service.listMine().getFirst().id());
        assertThat(loaded.travels().getFirst().country()).isEqualTo("Germany");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("71.00");
    }

    @Test
    void aTripCannotBeSavedWithNoDestinationCountryChosen() {
        navigate(ReportDetailView.class);

        findButton().withText("Insert travel info").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");
        // Country left unchosen: Save surfaces a clear message and generates nothing.
        findButton().withText("Save trip").click();

        assertThat(UI.getCurrent().getElement().getTextRecursively())
                .contains("Destination country is required");
        assertThat(findButton().withText("Save trip").exists()).isTrue();
        assertThat(findSpan().withText("Per diem allowance").exists()).isFalse();
    }

    @Test
    void insertingATripWithKmMealParkingShowsEachSubtotalAndFoldsParkingIntoNetVat() {
        navigate(ReportDetailView.class);

        findButton().withText("Insert travel info").click();
        findDateTimePicker().withLabel("Departure").setValue(DEP);
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(11));
        findComboBox(String.class).withLabel("Destination country")
                .selectItem("Finland (domestic)");
        findTextField().withLabel("Destinations").setValue("Helsinki");
        findTextField().withLabel("Travel purpose").setValue("Client visit");
        findBigDecimalField().withLabel("Kilometre allowance (km)")
                .setValue(new BigDecimal("120"));
        // A meal allowance is only paid when no per-diem applies (issue #93), so
        // clicking it marks the trip not eligible — the per-diem line drops out and
        // the meal-allowance line takes its place.
        findCheckbox().withLabel("Pay meal allowance?").click();
        findBigDecimalField().withLabel("Parking fees (€)")
                .setValue(new BigDecimal("12.00"));

        // The remaining outputs preview live in the dialog (120 km × €0.55 = €66.00,
        // etc.); no per-diem, since the trip is now not eligible for one.
        var previewText = UI.getCurrent().getElement().getTextRecursively();
        assertThat(previewText).contains("Kilometre allowance: €66.00",
                "Meal allowance: €13.50", "Parking: €12.00");
        assertThat(previewText).doesNotContain("Per diem allowance");

        findButton().withText("Save trip").click();

        // The two new tax-free subtotal rows are visible; parking is not a subtotal.
        assertThat(findSpan().withText("Kilometre allowance").exists()).isTrue();
        assertThat(findSpan().withText("Meal allowance").exists()).isTrue();
        assertThat(findSpan().withText("Per diem allowance").exists()).isFalse();
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("€66.00", "€13.50");

        findButton().withText("Save").click();

        var loaded = service.findMine(service.listMine().getFirst().id());
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("66.00");
        assertThat(loaded.mealTotal()).isEqualByComparingTo("13.50");
        // Parking folds into Net/VAT; grand total sums it all.
        assertThat(loaded.netTotal()).isEqualByComparingTo("9.56");
        assertThat(loaded.total()).isEqualByComparingTo("91.50");
    }

    @Test
    void theKilometreLineCardShowsTheKmTimesRateBreakdown() {
        // 12.5 km × the seeded 2026 rate (€0.550/km) = €6.88 — the km line reads like
        // a multi-unit manual card (ADR-0023), and the euros are unchanged.
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("12.5"), false, BigDecimal.ZERO);
        navigate(ReportDetailView.class, id);

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("12.5 × €0.55 = €6.88");
        // The flat per-diem line on the same trip carries no breakdown (quantity 1).
        assertThat(shown).contains("€54.00").doesNotContain("1 × €54.00");

        // Totals are untouched: the km subtotal is the product, the grand total sums.
        var loaded = service.findMine(id);
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("6.88");
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.total()).isEqualByComparingTo("60.88");
        assertThat(loaded.netTotal()).isEqualByComparingTo("0.00");
        assertThat(loaded.vatTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void reCostingATripUpdatesItsKilometreCardWithoutLeavingAStaleOne() {
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("12.5"), false, BigDecimal.ZERO);
        navigate(ReportDetailView.class, id);

        // Re-cost the trip through its editor: 12.5 km → 120 km.
        findButton().withText("Edit").click();
        findBigDecimalField().withLabel("Kilometre allowance (km)")
                .setValue(new BigDecimal("120"));
        findButton().withText("Save trip").click();

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("120 × €0.55 = €66.00")
                .doesNotContain("12.5 × €0.55");

        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.kilometreTotal()).isEqualByComparingTo("66.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(2);
    }

    @Test
    void aTripWithoutKmOrMealHidesThoseSubtotalRows() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(11));
        navigate(ReportDetailView.class, id);

        // Per-diem-only trip: its row shows, but the km/meal rows stay hidden.
        assertThat(findSpan().withText("Per diem allowance").exists()).isTrue();
        assertThat(findSpan().withText("Kilometre allowance").exists()).isFalse();
        assertThat(findSpan().withText("Meal allowance").exists()).isFalse();
    }

    @Test
    void aTripListsItsGeneratedLinesAndAReceiptCanBeAttachedToOne() {
        var id = seedReportWithFullTravel(LocalDate.of(2026, 7, 10), DEP,
                DEP.plusHours(11), new BigDecimal("120"), true, new BigDecimal("12.00"));
        navigate(ReportDetailView.class, id);

        // Each earned line is listed under the trip with an attach affordance.
        assertThat(findButton().withAriaLabel("Add receipt: Per diem allowance (full day)")
                .exists()).isTrue();
        assertThat(findButton().withAriaLabel("Add receipt: Parking").exists()).isTrue();

        // Attach a receipt to the parking line via its focused editor.
        findButton().withAriaLabel("Add receipt: Parking").click();
        findUpload().upload("parking.jpg", "image/jpeg", jpegBytes());
        findButton().withText("Save receipt").click();

        // Issue #89: the buffered receipt previews as a thumbnail on the row right
        // away — before the report is even saved — not just as a filename chip.
        assertThat(findButton().withAriaLabel("Preview receipt: parking.jpg").exists())
                .isTrue();
        assertThat($(Image.class).exists()).isTrue();

        findButton().withText("Save").click();

        var parking = service.findMine(id).travels().getFirst()
                .generatedLine(com.vaadin.expensemanager.report.domain.GeneratedLineKind
                        .PARKING).orElseThrow();
        assertThat(parking.hasReceipt()).isTrue();
        assertThat(parking.receiptFilename()).isEqualTo("parking.jpg");
        assertThat(parking.receiptId()).isNotNull();
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
    void aTripEarningAPartialDayListsTwoPerDiemCardsUnderOneSubtotal() {
        // Issue #124: 31 h → a full-day card (1 × €54.00) and a partial-day card
        // (1 × €25.00), each labelled by the day it prices, both folded into the single
        // "Per diem allowance" subtotal row (€79.00).
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        navigate(ReportDetailView.class, id);

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Per diem allowance (full day)", "€54.00",
                "Per diem allowance (partial day)", "€25.00");
        // One subtotal row for both lines, and it is the sum.
        assertThat(findSpan().withText("Per diem allowance").exists()).isTrue();
        assertThat(shown).contains("€79.00");

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("79.00");
        assertThat(loaded.total()).isEqualByComparingTo("79.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(2);
    }

    @Test
    void aMultiDayPerDiemCardShowsTheDaysTimesRateBreakdown() {
        // 55 h → 2 full days + 1 partial: the full-day card reads like an invoice line
        // (ADR-0023), the partial-day card stays a plain quantity-1 card.
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(55));
        navigate(ReportDetailView.class, id);

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("2 × €54.00 = €108.00")
                .doesNotContain("1 × €25.00");
        assertThat(service.findMine(id).perDiemTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    void reCostingATripDropsThePerDiemCardItNoLongerEarns() {
        var id = seedReportWithTravel(LocalDate.of(2026, 7, 10), DEP, DEP.plusHours(31));
        navigate(ReportDetailView.class, id);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Per diem allowance (partial day)");

        // Shorten the trip to a whole 24 h through its editor: the partial-day card
        // disappears, the full-day one stays, and the subtotal follows live.
        findButton().withText("Edit").click();
        findDateTimePicker().withLabel("Return").setValue(DEP.plusHours(24));
        findButton().withText("Save trip").click();

        var shown = getCurrentView().getElement().getTextRecursively();
        assertThat(shown).contains("Per diem allowance (full day)")
                .doesNotContain("Per diem allowance (partial day)");

        findButton().withText("Save").click();

        var loaded = service.findMine(id);
        assertThat(loaded.perDiemTotal()).isEqualByComparingTo("54.00");
        assertThat(loaded.travels().getFirst().generatedLines()).hasSize(1);
    }

    // --- Meal-allowance / eligibility checkbox coupling (issue #93) ---
    //
    // A meal allowance is paid only when no per-diem applies, and the free-meal
    // reduction only halves a per-diem — so "Pay meal allowance", "Free lunch" and
    // "not eligible" must stay consistent: payMeal ⟹ not-eligible, freeLunch ⟹
    // eligible. The dialog auto-corrects the checkboxes to hold that invariant.

    @Test
    void checkingPayMealAllowanceMarksTheTripNotEligibleAndClearsFreeLunch() {
        navigate(ReportDetailView.class);
        findButton().withText("Insert travel info").click();
        // Start eligible, with a free lunch selected.
        findCheckbox().withLabel("Free lunch provided?").click();
        assertThat(checkboxValue("Free lunch provided?")).isTrue();

        findCheckbox().withLabel("Pay meal allowance?").click();

        // Meal allowance needs "no per-diem": not-eligible is auto-checked and the
        // now-meaningless free lunch is cleared.
        assertThat(checkboxValue("Pay meal allowance?")).isTrue();
        assertThat(checkboxValue("Trip not eligible for daily allowance")).isTrue();
        assertThat(checkboxValue("Free lunch provided?")).isFalse();
    }

    @Test
    void unselectingNotEligibleClearsPayMealAllowance() {
        navigate(ReportDetailView.class);
        findButton().withText("Insert travel info").click();
        // Pay-meal turned it on; turning not-eligible back off must clear pay-meal,
        // since a meal allowance can't stand without "no per-diem".
        findCheckbox().withLabel("Pay meal allowance?").click();
        assertThat(checkboxValue("Trip not eligible for daily allowance")).isTrue();

        findCheckbox().withLabel("Trip not eligible for daily allowance").click();

        assertThat(checkboxValue("Trip not eligible for daily allowance")).isFalse();
        assertThat(checkboxValue("Pay meal allowance?")).isFalse();
    }

    @Test
    void checkingFreeLunchClearsNotEligibleAndPayMealAllowance() {
        navigate(ReportDetailView.class);
        findButton().withText("Insert travel info").click();
        // Get into the not-eligible + pay-meal world first.
        findCheckbox().withLabel("Pay meal allowance?").click();
        assertThat(checkboxValue("Trip not eligible for daily allowance")).isTrue();

        // A free lunch only halves a per-diem → the trip must become eligible again,
        // which in turn clears the meal allowance.
        findCheckbox().withLabel("Free lunch provided?").click();

        assertThat(checkboxValue("Free lunch provided?")).isTrue();
        assertThat(checkboxValue("Trip not eligible for daily allowance")).isFalse();
        assertThat(checkboxValue("Pay meal allowance?")).isFalse();
    }

    @Test
    void checkingNotEligibleClearsFreeLunch() {
        navigate(ReportDetailView.class);
        findButton().withText("Insert travel info").click();
        findCheckbox().withLabel("Free lunch provided?").click();
        assertThat(checkboxValue("Free lunch provided?")).isTrue();

        // No per-diem to halve once not eligible → free lunch is cleared.
        findCheckbox().withLabel("Trip not eligible for daily allowance").click();

        assertThat(checkboxValue("Trip not eligible for daily allowance")).isTrue();
        assertThat(checkboxValue("Free lunch provided?")).isFalse();
    }

    /** The current value of the dialog checkbox carrying the given label. */
    private boolean checkboxValue(String label) {
        return ((Checkbox) findCheckbox().withLabel(label).getComponent()).getValue();
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
    void travelPickersCarryIncompleteAndBadInputErrorMessages() {
        // Issue #85: entering only the date (not the time) in a departure/return
        // picker makes it invalid; without the incomplete-input message that error
        // reached the summary blank. The tester can't produce partial input, so we
        // assert the messages that prevent a blank error are configured.
        navigate(ReportDetailView.class);
        findButton().withText("Insert travel info").click();

        for (String label : new String[] { "Departure", "Return" }) {
            var picker = (DateTimePicker) findDateTimePicker().withLabel(label)
                    .getComponent();
            assertThat(picker.getI18n()).as(label + " i18n").isNotNull();
            assertThat(picker.getI18n().getIncompleteInputErrorMessage())
                    .as(label + " incomplete-input message").isNotBlank();
            assertThat(picker.getI18n().getBadInputErrorMessage())
                    .as(label + " bad-input message").isNotBlank();
        }
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

    /**
     * Opens the line editor for the report's first line card. The card has no Edit
     * button — the whole card body is the click target ({@code .clickable}) — so the
     * click goes to that layout rather than to a locator-findable control.
     */
    private void openLineCardEditor() {
        test($(HorizontalLayout.class).withClassName("clickable").first()).click();
    }
}

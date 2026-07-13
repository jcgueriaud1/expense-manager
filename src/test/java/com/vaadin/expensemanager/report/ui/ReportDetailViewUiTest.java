package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.datepicker.DatePicker;
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

    // ---------------------------------------------------------- line editing

    @Test
    void addingFillingAndSavingALinePersistsTheAggregateWithDerivedTotal() {
        navigate(ReportDetailView.class);

        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods"); // default 25.5 %
        findBigDecimalField().setValue(new BigDecimal("125.50"));
        findButton().withText("Save").click();

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().total()).isEqualByComparingTo("125.50");
        var detail = service.findMine(mine.getFirst().id());
        assertThat(detail.lines()).hasSize(1);
        assertThat(detail.lines().getFirst().amount()).isEqualByComparingTo("125.50");
        assertThat(getCurrentView()).isInstanceOf(ReportDetailView.class);
    }

    @Test
    void pickingAnExpenseTypePrefillsItsDefaultVatRateWhichIsOverridable() {
        navigate(ReportDetailView.class);
        findButton().withText("Add expense").click();

        // Publications defaults to 10 % — prefilled from the type.
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Publications");
        assertThat(findComboBox(VatRateDto.class).withLabel("VAT rate")
                .getSelected().value()).isEqualByComparingTo("10.00");

        // Override it, then save; the override is what persists.
        findComboBox(VatRateDto.class).withLabel("VAT rate").selectItem("25.5 %");
        findBigDecimalField().setValue(new BigDecimal("125.50"));
        findButton().withText("Save").click();

        var detail = service.findMine(service.listMine().getFirst().id());
        assertThat(detail.lines().getFirst().vatRate().value())
                .isEqualByComparingTo("25.50");
    }

    @Test
    void liveTotalsUpdateAsALineIsFilledIn() {
        navigate(ReportDetailView.class);
        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Parking/supplies/goods"); // 25.5 %
        findBigDecimalField().setValue(new BigDecimal("125.50"));

        // Live net/VAT/gross reflect the still-unsaved line (Signals, ADR-0015).
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("€125.50");        // gross
        assertThat(text).contains("€100.00");         // net
        assertThat(text).contains("€25.50");          // VAT
    }

    @Test
    void incompleteLineShowsErrorSummaryAndPersistsNothing() {
        navigate(ReportDetailView.class);
        findButton().withText("Add expense").click();
        // Nothing chosen: type, amount and rate are all missing.
        findButton().withText("Save").click();

        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("choose an expense type");
        assertThat(text).contains("enter a gross amount");
        assertThat(text).contains("choose a VAT rate");
        assertThat(service.listMine()).isEmpty();
    }

    @Test
    void negativeAmountIsAcceptedAndReflectedInTheTotal() {
        navigate(ReportDetailView.class);
        findButton().withText("Add expense").click();
        findComboBox(ExpenseTypeDto.class).withLabel("Expense type")
                .selectItem("Travel allowance"); // 0 %
        findBigDecimalField().setValue(new BigDecimal("-50.00"));
        findButton().withText("Save").click();

        var detail = service.findMine(service.listMine().getFirst().id());
        assertThat(detail.total()).isEqualByComparingTo("-50.00");
    }
}

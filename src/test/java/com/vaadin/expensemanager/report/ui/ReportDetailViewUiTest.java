package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;

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
        // a user emptying the field, then save (finding F-020).
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
}

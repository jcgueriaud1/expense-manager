package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.report.service.ReportSummaryDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link MyReportsView}
 * (UC-002). Runs as the seeded plain user via {@link WithUserDetails}.
 *
 * <p>Covers the acceptance criteria: owner scoping (only the current user's
 * reports render), newest-report-date-first ordering, the status and month/year
 * period filters narrowing the grid, the empty state when there are none, and
 * the "New report" action routing to the detail view. Grid cells are read with
 * {@code getCellText} (revised F-018).
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class MyReportsViewUiTest extends AbstractReportViewUiTest {

    private static final int DATE_COL = 0;
    private static final int INFO_COL = 1;
    private static final int STATUS_COL = 2;
    private static final int TOTAL_COL = 3;

    @Test
    void emptyStateRendersWhenOwnerHasNoReports() {
        navigate(MyReportsView.class);

        assertThat(findGrid(ReportSummaryDto.class).exists()).isFalse();
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("No expense reports yet");
    }

    @Test
    void listsOwnReportsNewestReportDateFirstWithAllColumns() {
        seedReport(LocalDate.of(2026, 6, 1), "older trip");
        seedReport(LocalDate.of(2026, 7, 1), "newer trip");

        navigate(MyReportsView.class);
        var grid = findGrid(ReportSummaryDto.class);

        assertThat(grid.size()).isEqualTo(2);
        // Newest report date first.
        assertThat(grid.getCellText(0, DATE_COL)).isEqualTo("2026-07-01");
        assertThat(grid.getCellText(1, DATE_COL)).isEqualTo("2026-06-01");
        assertThat(grid.getCellText(0, INFO_COL)).isEqualTo("newer trip");
        assertThat(grid.getCellText(0, STATUS_COL)).isEqualTo("Draft");
        // Derived total is €0.00 until lines arrive (Phase 2.3).
        assertThat(grid.getCellText(0, TOTAL_COL)).isEqualTo("€0.00");
    }

    @Test
    void statusFilterNarrowsTheGrid() {
        seedReport(LocalDate.of(2026, 7, 1), "a");
        seedReport(LocalDate.of(2026, 7, 2), "b");
        navigate(MyReportsView.class);

        // No report is SUBMITTED yet (submit lands in 2.4) → filter empties it.
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withLabel("Status").selectItem("Submitted");
        assertThat(findGrid(ReportSummaryDto.class).size()).isZero();

        // Filtering by DRAFT brings both back.
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withLabel("Status").selectItem("Draft");
        assertThat(findGrid(ReportSummaryDto.class).size()).isEqualTo(2);
    }

    @Test
    void monthAndYearFiltersNarrowTheGrid() {
        seedReport(LocalDate.of(2026, 1, 15), "january");
        seedReport(LocalDate.of(2026, 7, 15), "july");
        seedReport(LocalDate.of(2025, 7, 15), "last july");
        navigate(MyReportsView.class);

        findComboBox(java.time.Month.class).withLabel("Month").selectItem("July");
        assertThat(findGrid(ReportSummaryDto.class).size()).isEqualTo(2);

        findComboBox(Integer.class).withLabel("Year").selectItem("2026");
        // July + 2026 leaves just the one report.
        var grid = findGrid(ReportSummaryDto.class);
        assertThat(grid.size()).isEqualTo(1);
        assertThat(grid.getCellText(0, INFO_COL)).isEqualTo("july");
    }

    @Test
    void gridShowsOnlyTheCurrentUsersReports() {
        // Another user's report seeded directly must not leak into this list.
        seedReportForAdmin(LocalDate.of(2026, 7, 1), "admin's report");
        seedReport(LocalDate.of(2026, 7, 2), "mine");

        navigate(MyReportsView.class);
        var grid = findGrid(ReportSummaryDto.class);

        assertThat(grid.size()).isEqualTo(1);
        assertThat(grid.getCellText(0, INFO_COL)).isEqualTo("mine");
    }

    @Test
    void newReportButtonNavigatesToDetailView() {
        navigate(MyReportsView.class);

        findButton().withText("New report").click();

        assertThat(getCurrentView()).isInstanceOf(ReportDetailView.class);
    }
}

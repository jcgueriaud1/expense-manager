package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.user.LocalUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link MyReportsView}
 * (UC-002). Runs as the seeded plain user via {@link WithUserDetails}.
 *
 * <p>Covers the acceptance criteria against the card layout: owner scoping (only
 * the current user's reports render), newest-report-date-first ordering, the
 * grouping into "Needs your attention" (draft/rejected) vs "Submitted &amp;
 * closed" (submitted/approved), the status and month/year period filters
 * narrowing the sections, the empty state when there are none, and the
 * "New report" action routing to the detail view. Assertions read the rendered
 * text of the view (the cards are plain links, not a Grid).
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class MyReportsViewUiTest extends AbstractReportViewUiTest {

    private String renderedText() {
        return getCurrentView().getElement().getTextRecursively();
    }

    @Test
    void emptyStateRendersWhenOwnerHasNoReports() {
        navigate(MyReportsView.class);

        assertThat(renderedText()).contains("No expense reports yet");
    }

    @Test
    void listsOwnReportsNewestReportDateFirstWithStatusAndTotal() {
        seedReport(LocalDate.of(2026, 6, 1), "older trip");
        seedReport(LocalDate.of(2026, 7, 1), "newer trip");

        navigate(MyReportsView.class);
        var text = renderedText();

        // Both reports, their status badge, and the derived €0.00 total render.
        assertThat(text).contains("older trip").contains("newer trip")
                .contains("Draft").contains("€0.00");
        // Newest report date first: the newer card appears above the older one.
        assertThat(text.indexOf("newer trip")).isLessThan(text.indexOf("older trip"));
    }

    @Test
    void statusFilterSplitsDraftFromSubmitted() {
        seedReport(LocalDate.of(2026, 7, 1), "still a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        navigate(MyReportsView.class);

        // Both sections show before any filter is applied.
        assertThat(renderedText()).contains("Needs your attention")
                .contains("Submitted & closed");

        // Filtering by SUBMITTED leaves only the "Submitted & closed" section.
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withLabel("Status").selectItem("Submitted");
        assertThat(renderedText()).contains("Submitted & closed")
                .doesNotContain("Needs your attention")
                .doesNotContain("still a draft");

        // Filtering by DRAFT leaves only the "Needs your attention" section.
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withLabel("Status").selectItem("Draft");
        assertThat(renderedText()).contains("Needs your attention")
                .contains("still a draft")
                .doesNotContain("Submitted & closed");
    }

    @Test
    void monthAndYearFiltersNarrowTheList() {
        seedReport(LocalDate.of(2026, 1, 15), "january");
        seedReport(LocalDate.of(2026, 7, 15), "july");
        seedReport(LocalDate.of(2025, 7, 15), "last july");
        navigate(MyReportsView.class);

        findComboBox(java.time.Month.class).withLabel("Month").selectItem("July");
        // Both Julys remain; January is filtered out.
        assertThat(renderedText()).contains("last july").doesNotContain("january");

        findComboBox(Integer.class).withLabel("Year").selectItem("2026");
        // July + 2026 leaves just the one report — the 2025 July drops out.
        assertThat(renderedText()).contains("july")
                .doesNotContain("last july").doesNotContain("january");
    }

    @Test
    void listShowsOnlyTheCurrentUsersReports() {
        // Another user's report seeded directly must not leak into this list.
        seedReportForAdmin(LocalDate.of(2026, 7, 1), "admin's report");
        seedReport(LocalDate.of(2026, 7, 2), "mine");

        navigate(MyReportsView.class);

        assertThat(renderedText()).contains("mine").doesNotContain("admin's report");
    }

    @Test
    void newReportButtonNavigatesToDetailView() {
        navigate(MyReportsView.class);

        findButton().withText("New report").click();

        assertThat(getCurrentView()).isInstanceOf(ReportDetailView.class);
    }
}

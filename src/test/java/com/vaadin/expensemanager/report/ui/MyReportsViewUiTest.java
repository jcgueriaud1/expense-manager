package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextField;
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
 * closed" (submitted/approved), the search / report-date range / status filters
 * narrowing the sections, the travel summary on a trip report's card, the empty
 * state when there are none, and the "New" action routing to the detail view. Assertions read the rendered
 * text of the view (the cards are plain links, not a Grid).
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class MyReportsViewUiTest extends AbstractReportViewUiTest {

    /** Picks a status — by aria-label, since the filter row uses placeholders. */
    private void selectStatus(String label) {
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withAriaLabel("Status").selectItem(label);
    }

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
        selectStatus("Submitted");
        assertThat(renderedText()).contains("Submitted & closed")
                .doesNotContain("Needs your attention")
                .doesNotContain("still a draft");

        // Filtering by DRAFT leaves only the "Needs your attention" section.
        selectStatus("Draft");
        assertThat(renderedText()).contains("Needs your attention")
                .contains("still a draft")
                .doesNotContain("Submitted & closed");
    }

    /**
     * The From/To range bounds the report date, inclusively at both ends — a
     * report dated exactly on a bound stays in, which is what a user picking "the
     * 15th" means.
     */
    @Test
    void reportDateRangeNarrowsTheList() {
        seedReport(LocalDate.of(2026, 1, 15), "january");
        seedReport(LocalDate.of(2026, 7, 15), "july");
        seedReport(LocalDate.of(2025, 7, 15), "last july");
        navigate(MyReportsView.class);

        var from = $(DatePicker.class).withAriaLabel("Report date from").first();
        var to = $(DatePicker.class).withAriaLabel("Report date to").first();

        from.setValue(LocalDate.of(2026, 1, 1));
        // 2026 onwards: the 2025 July drops out, both 2026 reports remain.
        assertThat(renderedText()).contains("january").contains("july")
                .doesNotContain("last july");

        to.setValue(LocalDate.of(2026, 1, 15));
        // Narrowed to a range ending on January's own date — it survives the bound.
        assertThat(renderedText()).contains("january")
                .doesNotContain("last july");
    }

    /**
     * Search matches the note and, for a travel report, where it went — so a
     * destination finds the report even though nobody typed it into the note.
     */
    @Test
    void searchMatchesTheNoteAndTheTravelDestination() {
        seedReport(LocalDate.of(2026, 7, 1), "office supplies");
        seedReportWithTravel(LocalDate.of(2026, 7, 2), "conference",
                LocalDateTime.of(2026, 7, 2, 8, 0),
                LocalDateTime.of(2026, 7, 4, 19, 0));
        navigate(MyReportsView.class);

        var search = $(TextField.class).withAriaLabel("Search reports").first();

        search.setValue("supplies");
        assertThat(renderedText()).contains("office supplies")
                .doesNotContain("conference");

        // "Helsinki" is the seeded trip's destination, not part of any note.
        search.setValue("Helsinki");
        assertThat(renderedText()).contains("conference")
                .doesNotContain("office supplies");
    }

    /** A travel report says where and when on its card; a plain one has no such row. */
    @Test
    void travelReportShowsItsDestinationAndDatesOnTheCard() {
        seedReportWithTravel(LocalDate.of(2026, 7, 2), "conference",
                LocalDateTime.of(2026, 7, 2, 8, 0),
                LocalDateTime.of(2026, 7, 4, 19, 0));
        navigate(MyReportsView.class);

        assertThat(renderedText()).contains("conference").contains("Helsinki");
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

        findButton().withText("New").click();

        assertThat(getCurrentView()).isInstanceOf(ReportDetailView.class);
    }
}

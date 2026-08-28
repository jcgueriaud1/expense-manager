package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.vaadin.expensemanager.base.ui.MetricCard;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link MyReportsView}
 * (UC-002, redesigned in issue #162). Runs as the seeded plain user via
 * {@link WithUserDetails}.
 *
 * <p>Covers the acceptance criteria the eye cannot check: owner scoping (only the
 * current user's reports render), newest-report-date-first ordering, the grouping
 * into "Needs your attention" (draft/rejected) vs "Closed" (submitted/approved),
 * the status and month/year period filters narrowing the sections, the empty
 * state, and the "New report" action routing to the detail view. On top of those,
 * the redesign's own contracts: the metric band's figures coming from the service
 * (year included) and rendering as zeroes for a user with nothing, the metric
 * cards being neither focusable nor clickable, per-section counts replacing the
 * page-header count, the sections being collapsible with the count surviving the
 * collapse, and each card's trip rows, created-on footer and rejection meta.
 *
 * <p>Assertions read the rendered text of the view or locate components by their
 * design class name (the cards are plain links, not a Grid).
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class MyReportsViewUiTest extends AbstractReportViewUiTest {

    private String renderedText() {
        return getCurrentView().getElement().getTextRecursively();
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root),
                root.getChildren().flatMap(MyReportsViewUiTest::descendants));
    }

    private List<MetricCard> metricCards() {
        return descendants((Component) getCurrentView())
                .filter(MetricCard.class::isInstance)
                .map(MetricCard.class::cast)
                .toList();
    }

    private List<String> sectionCounts() {
        return findSpan().withClassName("report-list-section-count").components()
                .stream().map(Span::getText).toList();
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
        assertThat(renderedText()).contains("Needs your attention").contains("Closed");

        // Filtering by SUBMITTED leaves only the "Closed" section.
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withLabel("Status").selectItem("Submitted");
        assertThat(renderedText()).contains("Closed")
                .doesNotContain("Needs your attention")
                .doesNotContain("still a draft");

        // Filtering by DRAFT leaves only the "Needs your attention" section.
        findComboBox(com.vaadin.expensemanager.report.domain.ReportStatus.class)
                .withLabel("Status").selectItem("Draft");
        assertThat(renderedText()).contains("Needs your attention")
                .contains("still a draft")
                .doesNotContain("Closed");
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

    // ---- Metric band (docs/design/components/metric-card.md) ----

    @Test
    void metricBandShowsZeroesRatherThanBlanksForAUserWithNoReports() {
        navigate(MyReportsView.class);
        var text = renderedText();

        // The three cards render alongside the empty state, never instead of it.
        assertThat(metricCards()).hasSize(3);
        assertThat(text).contains("No expense reports yet")
                .contains("Needs you").contains("€0.00")
                .contains("In flight").contains("waiting 0 days")
                .contains("Reimbursed ").contains("0 approved");
    }

    @Test
    void metricFiguresComeFromTheServiceIncludingTheReimbursedYear() {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        var metrics = service.myMetrics();

        navigate(MyReportsView.class);
        var text = renderedText();

        // The caption's year is the service's, never LocalDate.now() in the view.
        assertThat(text).contains("Reimbursed " + metrics.reimbursedYear());
        assertThat(findSpan().withClassName("metric-card-value").components())
                .extracting(Span::getText)
                .containsExactly(String.valueOf(metrics.needsYouCount()),
                        String.valueOf(metrics.inFlightCount()),
                        ReportViewSupport.formatEur(metrics.reimbursedTotal()));
    }

    @Test
    void onlyTheRejectedFragmentOfTheSubLineIsCalledOut() {
        seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "missing receipt");
        navigate(MyReportsView.class);

        // The alert span holds the fragment alone — the "·" before it stays in the
        // sub-line's own (secondary) colour.
        var alert = findSpan().withClassName("metric-card-sub-alert").component();
        assertThat(alert.getText()).isEqualTo("1 rejected");
        assertThat(alert.getParent()).get()
                .satisfies(parent -> assertThat(parent.getElement()
                        .getTextRecursively()).startsWith("€").contains(" · "));
    }

    @Test
    void aSubLineWithNothingRejectedCarriesNoAlertFragment() {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        navigate(MyReportsView.class);

        assertThat(findSpan().withClassName("metric-card-sub-alert").exists())
                .isFalse();
    }

    @Test
    void metricCardsAreNeitherFocusableNorClickable() {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        navigate(MyReportsView.class);

        assertThat(metricCards()).isNotEmpty().allSatisfy(card ->
                assertThat(descendants(card)).allSatisfy(part -> {
                    assertThat(part).isNotInstanceOfAny(RouterLink.class,
                            Anchor.class, Button.class);
                    assertThat(part.getElement().getAttribute("tabindex")).isNull();
                }));
    }

    // ---- Sections (docs/design/components/report-list-section.md) ----

    @Test
    void eachSectionCarriesItsOwnCountAndThePageHeaderCarriesNone() {
        seedReport(LocalDate.of(2026, 7, 1), "draft one");
        seedReport(LocalDate.of(2026, 7, 2), "draft two");
        seedSubmittedReport(LocalDate.of(2026, 7, 3), "50.00");

        navigate(MyReportsView.class);

        // Singular below two, and the total that used to sit by the page title is
        // gone — two counts and a third saying "3 reports" would contradict.
        assertThat(sectionCounts()).containsExactly("2 reports", "1 report");
        assertThat(renderedText()).doesNotContain("3 reports");
    }

    @Test
    void bothSectionsStartExpandedAndCollapsingOneKeepsItsCountVisible() {
        seedReport(LocalDate.of(2026, 7, 1), "a draft");
        seedSubmittedReport(LocalDate.of(2026, 7, 2), "50.00");
        navigate(MyReportsView.class);

        var sections = findDetails().withClassName("report-list-section").components();
        assertThat(sections).hasSize(2).allSatisfy(
                section -> assertThat(section.isOpened()).isTrue());

        // Locator indices are 1-based.
        findDetails().withClassName("report-list-section").atIndex(1).closeDetails();

        assertThat(sections.get(0).isOpened()).isFalse();
        // The count is in the summary, so it survives the collapse — it is then
        // the only thing left saying what is inside.
        assertThat(sectionCounts()).containsExactly("1 report", "1 report");
    }

    // ---- Report card (docs/design/components/report-card.md) ----

    @Test
    void aCardListsOneRowPerTripWithItsOwnDateRange() {
        seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 27, 19, 0));
        navigate(MyReportsView.class);

        assertThat(renderedText()).contains("Helsinki")
                .contains("25 Aug 2026 – 27 Aug 2026");
    }

    @Test
    void aSingleDayTripRepeatsTheDateRatherThanCollapsingTheRange() {
        seedReportWithTravel(LocalDate.of(2026, 8, 25),
                LocalDateTime.of(2026, 8, 25, 8, 0),
                LocalDateTime.of(2026, 8, 25, 19, 0));
        navigate(MyReportsView.class);

        assertThat(renderedText()).contains("25 Aug 2026 – 25 Aug 2026");
    }

    @Test
    void aReportWithoutTripsRendersNoTripList() {
        seedReport(LocalDate.of(2026, 7, 1), "no travel here");
        navigate(MyReportsView.class);

        assertThat(renderedText()).contains("no travel here");
        assertThat(findSpan().withClassName("report-card-trip-dates").exists())
                .isFalse();
    }

    @Test
    void theFooterDatesTheReportsCreationRatherThanItsReportDate() {
        // A report date deliberately far from today, so "Created on" cannot be
        // passing by coincidence.
        seedReport(LocalDate.of(2020, 1, 2), "an old report date");
        navigate(MyReportsView.class);

        assertThat(renderedText()).contains("Created on " + LocalDate.now())
                .doesNotContain("Created on 2020-01-02");
    }

    @Test
    void aRejectedCardNamesWhoRejectedItAndWhen() {
        seedRejectedReport(LocalDate.of(2026, 7, 1), "100", "missing receipt");
        navigate(MyReportsView.class);

        assertThat(renderedText())
                .contains("Rejected by Expense Admin on 2026-07-14");
    }

    @Test
    void anUnrejectedCardCarriesNoRejectionMeta() {
        seedSubmittedReport(LocalDate.of(2026, 7, 1), "50.00");
        navigate(MyReportsView.class);

        assertThat(renderedText()).contains("Created on")
                .doesNotContain("Rejected by");
    }
}

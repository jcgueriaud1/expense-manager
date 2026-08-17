package com.vaadin.expensemanager.report.ui;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.vaadin.expensemanager.base.ui.EmptyState;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReportSummaryDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import jakarta.annotation.security.PermitAll;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.statusBadge;

/**
 * The report owner's list of their own reports (UC-002, ADR-0017) — the
 * mockup's mobile-first card list.
 *
 * <p>Owner-scoped by construction: it renders only {@link ExpenseReportService}'s
 * {@code listMine()} (the service filters on the current user, ADR-0008), newest
 * report-date first. Reports are grouped into two sections — <strong>Needs your
 * attention</strong> ({@code DRAFT}/{@code REJECTED}, the reports the owner can
 * still act on) and <strong>Submitted &amp; closed</strong>
 * ({@code SUBMITTED}/{@code APPROVED}) — each a stack of cards showing the note,
 * report date, a status badge, and the derived total. The card is a
 * {@link RouterLink} (a real, keyboard-operable link, not a click-only row) to
 * the detail view. A <strong>status filter</strong> and a <strong>month + year
 * period filter</strong> on the report date narrow the sections in memory; when
 * the owner has no reports at all, the {@link EmptyState} from {@code base/}
 * renders instead.
 *
 * <p>{@code @PermitAll} — any authenticated user manages their own reports;
 * there is no admin/user split here. Accessible and usable at ~360px (ADR-0020):
 * the whole view is a single centred column that scrolls, the filters wrap
 * rather than overflow, and status is shown as a text badge (never colour alone).
 */
@Route("reports")
@PageTitle("My reports")
@Menu(title = "My reports", order = 1, icon = "icons/lucide/file-text.svg")
@PermitAll
public class MyReportsView extends VerticalLayout {

    private static final Locale LABEL_LOCALE = Locale.ENGLISH;

    private final transient List<ReportSummaryDto> reports;

    private final ComboBox<ReportStatus> statusFilter = new ComboBox<>("Status");
    private final ComboBox<Month> monthFilter = new ComboBox<>("Month");
    private final ComboBox<Integer> yearFilter = new ComboBox<>("Year");

    /** Repopulated on every filter change with the two grouped sections. */
    private final VerticalLayout sections = new VerticalLayout();

    public MyReportsView(ExpenseReportService service) {
        this.reports = service.listMine();
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        // One centred, mobile-first column (the mockup's single-column shell).
        setAlignItems(FlexComponent.Alignment.CENTER);

        var content = new VerticalLayout(header());
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();
        content.setMaxWidth("46rem");

        if (reports.isEmpty()) {
            content.add(new EmptyState(LucideIcon.FILE_TEXT, "No expense reports yet",
                    "Create your first report to start tracking expenses."));
            add(content);
            return;
        }

        sections.setWidthFull();
        sections.setPadding(false);
        sections.setSpacing("var(--vaadin-gap-l)");
        content.add(filterBar(), sections);
        add(content);
        applyFilters();
    }

    /** Report count + the primary "New report" action. */
    private Component header() {
        // The screen's title lives in the navbar (MainLayout), not here.
        var count = new Span(reports.size() + (reports.size() == 1
                ? " report" : " reports"));
        count.addClassName("muted");

        var newReport = new Button("New report", LucideIcon.PLUS.create(),
                event -> getUI().ifPresent(ui -> ui.navigate(ReportDetailView.class)));
        newReport.addThemeVariants(ButtonVariant.PRIMARY);

        var bar = new HorizontalLayout(count, newReport);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return bar;
    }

    private HorizontalLayout filterBar() {
        statusFilter.setItems(ReportStatus.values());
        statusFilter.setItemLabelGenerator(ReportViewSupport::statusLabel);
        statusFilter.setClearButtonVisible(true);
        statusFilter.setPlaceholder("All statuses");
        statusFilter.addValueChangeListener(event -> applyFilters());

        monthFilter.setItems(Month.values());
        monthFilter.setItemLabelGenerator(
                month -> month.getDisplayName(TextStyle.FULL, LABEL_LOCALE));
        monthFilter.setClearButtonVisible(true);
        monthFilter.setPlaceholder("All months");
        monthFilter.addValueChangeListener(event -> applyFilters());

        yearFilter.setItems(distinctYears());
        yearFilter.setClearButtonVisible(true);
        yearFilter.setPlaceholder("All years");
        yearFilter.addValueChangeListener(event -> applyFilters());

        var bar = new HorizontalLayout(statusFilter, monthFilter, yearFilter);
        bar.setAlignItems(FlexComponent.Alignment.END);
        // Wrap so the three filters stack rather than overflow at ~360px (ADR-0020).
        bar.addClassName("reports-filter-bar");
        return bar;
    }

    /**
     * Rebuilds the two grouped sections from the current filter selection. Each
     * filter is independent; an unset filter matches all. Empty groups are
     * omitted, and a filter combination that matches nothing shows a hint rather
     * than a blank column.
     */
    private void applyFilters() {
        var status = statusFilter.getValue();
        var month = monthFilter.getValue();
        var year = yearFilter.getValue();
        var filtered = reports.stream()
                .filter(dto -> (status == null || dto.status() == status)
                        && (month == null || dto.reportDate().getMonth() == month)
                        && (year == null || dto.reportDate().getYear() == year))
                .toList();

        var actionable = filtered.stream()
                .filter(dto -> dto.status().isEditable())
                .toList();
        var done = filtered.stream()
                .filter(dto -> !dto.status().isEditable())
                .toList();

        sections.removeAll();
        if (!actionable.isEmpty()) {
            sections.add(section("Needs your attention", actionable, true));
        }
        if (!done.isEmpty()) {
            sections.add(section("Submitted & closed", done, false));
        }
        if (filtered.isEmpty()) {
            var none = new Span("No reports match these filters.");
            none.addClassName("no-results");
            sections.add(none);
        }
    }

    /** One titled section: an uppercase label above a stack of report cards. */
    private Component section(String title, List<ReportSummaryDto> rows,
            boolean actionable) {
        var label = new Span(title);
        label.addClassName("section-label");

        var stack = new VerticalLayout();
        stack.setPadding(false);
        stack.setSpacing("var(--vaadin-gap-m)");
        rows.forEach(dto -> stack.add(reportCard(dto, actionable)));

        var wrapper = new VerticalLayout(label, stack);
        wrapper.setPadding(false);
        wrapper.setSpacing("var(--vaadin-gap-s)");
        return wrapper;
    }

    /**
     * One clickable report card, rendered as a {@link RouterLink} so it is a
     * real keyboard-operable link to the detail view (ADR-0020), not a click-only
     * row. Actionable (draft/rejected) cards get a raised surface; submitted and
     * closed reports read as recessed. The card's flex column lives in the
     * {@code report-card} CSS class — a RouterLink is not a Vaadin layout.
     */
    private Component reportCard(ReportSummaryDto dto, boolean actionable) {
        var title = new Span(dto.additionalInformation() == null
                || dto.additionalInformation().isBlank()
                ? "Expense report" : dto.additionalInformation());
        title.addClassName("report-card-title");

        var topRow = new HorizontalLayout(title, statusBadge(dto.status()));
        topRow.setWidthFull();
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topRow.expand(title);

        var date = new Span(dto.reportDate().toString());
        date.addClassName("muted");
        var total = new Span(formatEur(dto.total()));
        total.addClassName("report-card-total");

        var bottomRow = new HorizontalLayout(date, total);
        bottomRow.setWidthFull();
        bottomRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        bottomRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        bottomRow.addClassName("report-card-footer");

        var card = new RouterLink();
        card.setRoute(ReportDetailView.class, dto.id());
        card.add(topRow, bottomRow);
        card.addClassName("report-card");
        if (actionable) {
            card.addClassName("report-card--actionable");
        }
        return card;
    }

    private List<Integer> distinctYears() {
        return reports.stream()
                .map(dto -> dto.reportDate().getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}

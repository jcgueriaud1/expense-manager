package com.vaadin.expensemanager.report.ui;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.MainLayout;
import com.vaadin.expensemanager.base.ui.StatCard;
import com.vaadin.expensemanager.base.ui.ViewHeader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.vaadin.expensemanager.base.ui.EmptyState;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReportSummaryDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
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
// The landing page (ADR-0026): "" is this screen, and /reports still resolves
// here so older links and the verification docs keep working.
@Route("")
@RouteAlias("reports")
@PageTitle("My reports")
@Menu(title = "My reports", order = 0, icon = "icons/lucide/file-text.svg")
@PermitAll
public class MyReportsView extends VerticalLayout {

    private static final Locale LABEL_LOCALE = Locale.ENGLISH;

    private final transient List<ReportSummaryDto> reports;

    private final TextField search = new TextField();
    private final DatePicker fromFilter = new DatePicker();
    private final DatePicker toFilter = new DatePicker();
    private final ComboBox<ReportStatus> statusFilter = new ComboBox<>();

    /** Repopulated on every filter change with the two grouped sections. */
    private final VerticalLayout sections = new VerticalLayout();

    public MyReportsView(ExpenseReportService service) {
        this.reports = service.listMine();
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        // One centred, mobile-first column (the mockup's single-column shell).
        setAlignItems(FlexComponent.Alignment.CENTER);

        // Spacing steps down with the tree: the two sections sit furthest apart
        // (1.5 × xl), then the page's regions (xl), then cards within a section (l),
        // then a section's label nearly touching its cards (s). Even gaps would
        // leave the eye no grouping to read.
        var content = new VerticalLayout(header());
        content.setPadding(false);
        content.setSpacing("var(--vaadin-gap-xl)");
        content.setWidthFull();
        content.setMaxWidth(MainLayout.CONTENT_MAX_WIDTH);

        if (reports.isEmpty()) {
            content.add(new EmptyState(LucideIcon.FILE_TEXT, "No expense reports yet",
                    "Create your first report to start tracking expenses."));
            add(content);
            return;
        }

        sections.setWidthFull();
        sections.setPadding(false);
        // The two sections are the page's coarsest division, so they sit further
        // apart than the regions above them — 1.5x the largest gap token, since the
        // scale stops at xl.
        sections.setSpacing("calc(var(--vaadin-gap-xl) * 1.5)");
        content.add(statCards(), filterBar(), sections);
        add(content);
        applyFilters();
    }

    /** The screen's heading and its primary action. */
    private Component header() {
        var newReport = new Button("New", LucideIcon.PLUS.create(),
                event -> getUI().ifPresent(ui -> ui.navigate(ReportDetailView.class)));
        newReport.addThemeVariants(ButtonVariant.PRIMARY);
        return new ViewHeader(this, newReport);
    }

    /**
     * The three numbers this screen can answer about itself, above the list they
     * describe (ADR-0026).
     *
     * <p>Every figure comes from {@code reports}, already in memory — no extra
     * query, which is the reason these live here rather than on a dashboard. They
     * report; the filter row below is what narrows the list.
     */
    private Component statCards() {
        var actionable = reports.stream().filter(dto -> dto.status().isEditable()).toList();
        var inFlight = reports.stream()
                .filter(dto -> dto.status() == ReportStatus.SUBMITTED).toList();
        var approvedThisYear = reports.stream()
                .filter(dto -> dto.status() == ReportStatus.APPROVED)
                .filter(dto -> dto.reportDate().getYear() == LocalDate.now().getYear())
                .toList();

        var needsYou = new StatCard("Needs you", String.valueOf(actionable.size()),
                actionable.isEmpty() ? "nothing waiting on you"
                        : formatEur(sum(actionable)) + rejectedSuffix(actionable));

        var inFlightCard = new StatCard("In flight", String.valueOf(inFlight.size()),
                waitingText(inFlight));

        var reimbursed = new StatCard("Reimbursed " + LocalDate.now().getYear(),
                formatEur(sum(approvedThisYear)),
                approvedThisYear.size() + " approved");

        var row = new HorizontalLayout(needsYou, inFlightCard, reimbursed);
        row.setWidthFull();
        row.addClassName("stat-cards");
        return row;
    }

    /** "€234 · 1 rejected" — the rejected part only when there is one. */
    private static String rejectedSuffix(List<ReportSummaryDto> actionable) {
        var rejected = actionable.stream()
                .filter(dto -> dto.status() == ReportStatus.REJECTED).count();
        return rejected == 0 ? "" : " · " + rejected + " rejected";
    }

    /**
     * How long the oldest submitted report has been waiting — the one thing an
     * owner cannot tell from the list itself, and the reason the summary carries
     * {@code submittedAt}.
     */
    private static String waitingText(List<ReportSummaryDto> inFlight) {
        return inFlight.stream()
                .map(ReportSummaryDto::submittedAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .map(oldest -> {
                    var days = Duration.between(oldest, Instant.now()).toDays();
                    return days == 0 ? "submitted today"
                            : "waiting " + days + (days == 1 ? " day" : " days");
                })
                .orElse("nothing submitted");
    }

    private static BigDecimal sum(List<ReportSummaryDto> rows) {
        return rows.stream().map(ReportSummaryDto::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    /**
     * Search, a report-date range, and status — the four controls in one row.
     *
     * <p>Status sits beside the search box — the two coarsest ways to narrow the
     * list — with the date range after them. All four narrow the already-loaded
     * list in memory; none re-queries. The
     * range bounds the <strong>report date</strong>, which is also what the list
     * sorts and groups by and what each card shows, so what you filter is what
     * you see.
     */
    private HorizontalLayout filterBar() {
        search.setPlaceholder("Search");
        search.setAriaLabel("Search reports");
        search.setPrefixComponent(LucideIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        // Filter as the user types rather than on blur/Enter.
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> applyFilters());
        search.setMinWidth("12rem");

        // The four controls carry placeholders, not labels — a label would put a
        // caption above only some of them and break the row's baseline. The
        // accessible name comes from setAriaLabel instead, which is also what the
        // view tests locate them by.
        fromFilter.setPlaceholder("From");
        fromFilter.setAriaLabel("Report date from");
        fromFilter.setClearButtonVisible(true);
        fromFilter.setWidth("9rem");
        fromFilter.addValueChangeListener(event -> applyFilters());

        toFilter.setPlaceholder("To");
        toFilter.setAriaLabel("Report date to");
        toFilter.setClearButtonVisible(true);
        toFilter.setWidth("9rem");
        toFilter.addValueChangeListener(event -> applyFilters());

        statusFilter.setItems(ReportStatus.values());
        statusFilter.setItemLabelGenerator(ReportViewSupport::statusLabel);
        statusFilter.setClearButtonVisible(true);
        statusFilter.setPlaceholder("Status");
        statusFilter.setAriaLabel("Status");
        statusFilter.setWidth("10rem");
        statusFilter.addValueChangeListener(event -> applyFilters());

        var bar = new HorizontalLayout(search, statusFilter, fromFilter, toFilter);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.END);
        bar.expand(search);
        // Stack rather than overflow at ~360px (ADR-0020) — setWrap, not CSS.
        bar.setWrap(true);
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
        var from = fromFilter.getValue();
        var to = toFilter.getValue();
        var query = search.getValue() == null ? ""
                : search.getValue().strip().toLowerCase(LABEL_LOCALE);
        var filtered = reports.stream()
                .filter(dto -> (status == null || dto.status() == status)
                        && (from == null || !dto.reportDate().isBefore(from))
                        && (to == null || !dto.reportDate().isAfter(to))
                        && matchesSearch(dto, query))
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


    /**
     * Free-text match over what the card actually shows: the note and, for a
     * travel report, where it went — so searching "Helsinki" finds the trip even
     * though no one typed it into the note.
     */
    private static boolean matchesSearch(ReportSummaryDto dto, String query) {
        if (query.isEmpty()) {
            return true;
        }
        var haystack = new StringBuilder();
        if (dto.additionalInformation() != null) {
            haystack.append(dto.additionalInformation());
        }
        if (dto.travel() != null) {
            haystack.append(' ').append(dto.travel().destination());
        }
        return haystack.toString().toLowerCase(LABEL_LOCALE).contains(query);
    }

    /**
     * One titled section: its label and its count on one row, above the stack of
     * cards they describe. The count sits at the trailing edge — a number is
     * easier to compare between sections when they line up.
     */
    private Component section(String title, List<ReportSummaryDto> rows,
            boolean actionable) {
        var label = new Span(title);
        label.addClassName("section-label");

        var count = new Span(rows.size() + (rows.size() == 1 ? " report" : " reports"));
        count.addClassName("muted-xs");

        var labelRow = new HorizontalLayout(label, count);
        labelRow.setWidthFull();
        labelRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        labelRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        var stack = new VerticalLayout();
        stack.setPadding(false);
        stack.setSpacing("var(--vaadin-gap-l)");
        // RouterLink is not HasSize, so the card links take their width from the
        // stack's cross-axis alignment rather than setting it themselves.
        stack.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        rows.forEach(dto -> stack.add(reportCard(dto, actionable)));

        // The label row belongs to the stack it heads, so it sits closest of all.
        var wrapper = new VerticalLayout(labelRow, stack);
        wrapper.setPadding(false);
        wrapper.setSpacing("var(--vaadin-gap-s)");
        return wrapper;
    }

    /**
     * One clickable report card: Vaadin's {@link Card} for the surface, padding,
     * radius and border, wrapped in a {@link RouterLink} so the whole card is a
     * real keyboard-operable link to the detail view (ADR-0020) rather than a
     * click-only row. Actionable (draft/rejected) cards lift with a shadow;
     * submitted and closed reports lie flat.
     */
    private Component reportCard(ReportSummaryDto dto, boolean actionable) {
        var title = new Span(dto.additionalInformation() == null
                || dto.additionalInformation().isBlank()
                ? "Expense report" : dto.additionalInformation());
        title.addClassName("report-card-title");

        var topRow = new HorizontalLayout(statusBadge(dto.status()), title);
        topRow.setWidthFull();
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.setSpacing("var(--vaadin-gap-s)");
        topRow.expand(title);

        var date = new Span("Report date " + dto.reportDate());
        date.addClassName("muted-xs");
        var total = new Span(formatEur(dto.total()));
        total.addClassName("report-card-total");

        var bottomRow = new HorizontalLayout(date, total);
        bottomRow.setWidthFull();
        bottomRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        bottomRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        bottomRow.addClassName("report-card-footer");

        var card = new Card();
        card.addThemeVariants(CardVariant.OUTLINED);
        card.setWidthFull();
        card.addClassName("report-card");
        if (actionable) {
            card.addClassName("report-card--actionable");
        }
        // Card sets role="region" in ready() unless a role is already present, and a
        // region landmark inside a link is noise for a screen reader. Note that
        // passing null would NOT suppress it — only an explicit value does.
        card.setAriaRole("presentation");
        card.add(topRow);
        // A travel report says where and when on the card, so the trip is legible
        // without opening the report. No trips, no row.
        if (dto.travel() != null) {
            card.add(travelRow(dto.travel()));
        }
        card.add(bottomRow);

        // The anchor stays on the outside: a Card cannot be a link, and wrapping it
        // keeps real keyboard focus, Enter, and open-in-new-tab, which a click
        // listener on the card would lose.
        var link = new RouterLink();
        link.setRoute(ReportDetailView.class, dto.id());
        link.add(card);
        link.addClassName("card-link");
        return link;
    }

    /** "Helsinki · 8 May – 12 May 2026", with a trip count when there are several. */
    private Component travelRow(ReportSummaryDto.TravelSummary travel) {
        var where = new Span(travel.destination());
        where.addClassName("report-card-travel-where");

        var when = new Span(ReportViewSupport.formatDateRange(travel.start(),
                travel.end()));
        when.addClassName("muted-xs");

        var row = new HorizontalLayout(LucideIcon.PLANE.create(), where, when);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setSpacing("var(--vaadin-gap-s)");
        row.addClassName("report-card-travel");
        if (travel.tripCount() > 1) {
            var trips = new Span(travel.tripCount() + " trips");
            trips.addClassName("muted-xs");
            row.add(trips);
        }
        return row;
    }

}

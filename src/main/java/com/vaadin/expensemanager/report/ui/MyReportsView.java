package com.vaadin.expensemanager.report.ui;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.vaadin.expensemanager.base.ui.EmptyState;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReportSummaryDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import jakarta.annotation.security.PermitAll;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.statusLabel;

/**
 * The report owner's list of their own reports (UC-002, ADR-0017).
 *
 * <p>Owner-scoped by construction: it renders only {@link ExpenseReportService}'s
 * {@code listMine()} (the service filters on the current user, ADR-0008), newest
 * report-date first, with the four columns date / additional info / status /
 * total. A <strong>status filter</strong> and a <strong>month + year period
 * filter</strong> on the report date narrow the grid in memory; the report date
 * links to the detail view. When the owner has no reports at all, the
 * {@link EmptyState} from {@code base/} renders instead of an empty grid.
 *
 * <p>{@code @PermitAll} — any authenticated user manages their own reports;
 * there is no admin/user split here. Accessible and usable at ~360px (ADR-0020):
 * the grid scrolls within its own container and the report date is a real link
 * (keyboard-operable), not a click-only row.
 */
@Route("reports")
@PageTitle("My reports")
@Menu(title = "My reports", order = 1, icon = "vaadin:file-text-o")
@PermitAll
public class MyReportsView extends VerticalLayout {

    private static final Locale LABEL_LOCALE = Locale.ENGLISH;

    private final transient List<ReportSummaryDto> reports;

    private final ComboBox<ReportStatus> statusFilter = new ComboBox<>("Status");
    private final ComboBox<Month> monthFilter = new ComboBox<>("Month");
    private final ComboBox<Integer> yearFilter = new ComboBox<>("Year");
    private final Grid<ReportSummaryDto> grid = new Grid<>();
    private ListDataProvider<ReportSummaryDto> dataProvider;

    public MyReportsView(ExpenseReportService service) {
        this.reports = service.listMine();
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(header());

        if (reports.isEmpty()) {
            add(new EmptyState("vaadin:file-text-o", "No expense reports yet",
                    "Create your first report to start tracking expenses."));
            return;
        }

        add(filterBar(), configureGrid());
        applyFilters();
    }

    private HorizontalLayout header() {
        var newReport = new Button("New report", new Icon(VaadinIcon.PLUS),
                event -> getUI().ifPresent(ui -> ui.navigate(ReportDetailView.class)));
        newReport.addThemeVariants(ButtonVariant.PRIMARY);

        var header = new HorizontalLayout(new H2("My reports"), newReport);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return header;
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
        bar.getStyle().setFlexWrap(com.vaadin.flow.dom.Style.FlexWrap.WRAP);
        return bar;
    }

    private Grid<ReportSummaryDto> configureGrid() {
        grid.addColumn(new com.vaadin.flow.data.renderer.ComponentRenderer<>(dto ->
                        new RouterLink(dto.reportDate().toString(),
                                ReportDetailView.class, dto.id())))
                .setHeader("Report date")
                .setComparator(Comparator.comparing(ReportSummaryDto::reportDate))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> dto.additionalInformation() == null
                        ? "" : dto.additionalInformation())
                .setHeader("Additional information").setFlexGrow(1);
        grid.addColumn(dto -> statusLabel(dto.status()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> formatEur(dto.total()))
                .setHeader("Total").setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);

        this.dataProvider = new ListDataProvider<>(reports);
        grid.setItems(dataProvider);
        return grid;
    }

    /**
     * Re-applies the combined status + month + year predicate to the in-memory
     * data provider. Each filter is independent; an unset filter matches all.
     */
    private void applyFilters() {
        if (dataProvider == null) {
            return;
        }
        var status = statusFilter.getValue();
        var month = monthFilter.getValue();
        var year = yearFilter.getValue();
        dataProvider.setFilter(dto ->
                (status == null || dto.status() == status)
                && (month == null || dto.reportDate().getMonth() == month)
                && (year == null || dto.reportDate().getYear() == year));
    }

    private List<Integer> distinctYears() {
        return reports.stream()
                .map(dto -> dto.reportDate().getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}

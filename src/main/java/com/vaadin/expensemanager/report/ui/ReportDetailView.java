package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.statusLabel;
import static java.util.stream.Collectors.toCollection;

/**
 * Create and edit a single report, line by line (UC-001/UC-005, ADR-0019) —
 * <strong>variant D</strong> of the F-004 line-editor exploration. This view is
 * thin orchestration: it owns the report-level fields (a {@link Binder}) and
 * wires three components — the bindable {@link ReportLinesField} (receipt cards +
 * persistent side panel), the {@link ReportTotalsBar} (live net/VAT/gross via
 * Signals + Save/Delete), and the error summary — to the {@link ExpenseReportService}.
 *
 * <p>Two entry points on one route: {@code /report} opens a <strong>transient
 * working copy</strong> (no row is persisted until the first save, ADR-0019) with
 * the date defaulting to today, and {@code /report/{id}} loads an existing report;
 * the first successful save routes {@code /report → /report/{id}}. Lines are
 * edited in memory and saved with the whole aggregate, reconciled by nullable id.
 *
 * <p><strong>Save is always enabled with a validation error summary on top of the
 * form</strong> (never a disabled submit, ADR-0020). <strong>Delete</strong> shows
 * only while the report is a persisted {@code DRAFT} (ADR-0006). Stale writes
 * surface the "reload" message (ADR-0011). {@code @PermitAll}; owner-scoping is
 * enforced in the service.
 */
@Route("report")
@PageTitle("Report")
@PermitAll
public class ReportDetailView extends VerticalLayout
        implements HasUrlParameter<Long> {

    private final transient ExpenseReportService service;
    private final transient ReferenceDataService referenceData;

    private final Div errorSummary = new Div();
    private final Span statusBadge = new Span();
    private final DatePicker reportDate = new DatePicker("Report date");
    private final TextArea additionalInformation = new TextArea("Additional information");
    private final Binder<ReportFormModel> reportBinder = new Binder<>();
    private final ReportFormModel reportModel = new ReportFormModel();

    private final ReportLinesField linesField = new ReportLinesField();
    private final ReportTotalsBar totalsBar = new ReportTotalsBar(this::onSave, this::confirmDelete);

    /** The current working copy (transient for a new report until first save). */
    private transient ReportDetailDto working;

    public ReportDetailView(ExpenseReportService service,
            ReferenceDataService referenceData) {
        this.service = service;
        this.referenceData = referenceData;
        setPadding(true);
        setSpacing(true);
        setWidthFull();
        setMaxWidth("64rem");

        configureErrorSummary();
        configureReportFields();
        // Live totals ride the line field's value-change (ADR-0015).
        linesField.addValueChangeListener(event -> totalsBar.setLines(event.getValue()));
        add(headerRow(), errorSummary, reportFields(), totalsBar, linesField);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long id) {
        linesField.setActiveOptions(referenceData.activeExpenseTypes(),
                referenceData.activeVatRates());
        if (id == null) {
            load(ReportDetailDto.forNew(LocalDate.now()));
            return;
        }
        try {
            load(service.findMine(id));
        } catch (IllegalArgumentException notFound) {
            // A missing id or someone else's id (owner-scoped) both land here — no
            // information leak: bounce to the owner's list (ADR-0008, ADR-0016).
            Notification.show("Report not found.");
            event.forwardTo(MyReportsView.class);
        }
    }

    /** Populates the view from a working copy and reflects its editability/status. */
    private void load(ReportDetailDto dto) {
        this.working = dto;
        reportModel.setReportDate(dto.reportDate());
        reportModel.setAdditionalInformation(dto.additionalInformation());
        reportModel.setLines(dto.lines().stream()
                .map(ReportLineModel::from).collect(toCollection(ArrayList::new)));
        // readBean populates every bound field, including the lines field; its
        // value-change then refreshes the totals bar.
        reportBinder.readBean(reportModel);
        totalsBar.setLines(reportModel.getLines());

        clearErrors();
        statusBadge.setText(statusLabel(dto.status()));

        boolean editable = dto.status().isEditable();
        reportBinder.setReadOnly(!editable);
        totalsBar.setSaveVisible(editable);
        // Delete only while a persisted DRAFT (ADR-0006, glossary).
        totalsBar.setDeleteVisible(dto.isPersisted() && dto.status().isDeletable());
    }

    private void onSave() {
        clearErrors();
        // One Binder write validates the report-level fields AND the line
        // collection; failures surface through the same error summary (ADR-0020).
        if (!reportBinder.writeBeanIfValid(reportModel)) {
            showErrors(reportBinder.validate().getValidationErrors().stream()
                    .map(ValidationResult::getErrorMessage).toList());
            return;
        }

        var lineDtos = reportModel.getLines().stream()
                .map(ReportLineModel::toDto).toList();
        var edited = new ReportDetailDto(working.id(), reportModel.getReportDate(),
                reportModel.getAdditionalInformation(), working.status(),
                working.version(), working.total(), lineDtos);
        try {
            if (!working.isPersisted()) {
                Long newId = service.create(edited);
                Notification.show("Report saved.");
                // First save routes /report → /report/{id} (ADR-0019).
                getUI().ifPresent(ui -> ui.navigate(ReportDetailView.class, newId));
            } else {
                load(service.update(working.id(), edited, working.version()));
                Notification.show("Report saved.");
            }
        } catch (ObjectOptimisticLockingFailureException stale) {
            showErrors(List.of("This report was changed elsewhere. "
                    + "Reload to see the latest version before saving again."));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            showErrors(List.of(invalid.getMessage()));
        }
    }

    private void confirmDelete() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete report?");
        dialog.add(new Paragraph(
                "This permanently deletes the draft report. This cannot be undone."));

        var confirm = new Button("Delete report", event -> {
            dialog.close();
            performDelete();
        });
        confirm.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void performDelete() {
        try {
            service.delete(working.id());
            Notification.show("Report deleted.");
            getUI().ifPresent(ui -> ui.navigate(MyReportsView.class));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            showErrors(List.of(ex.getMessage()));
        }
    }

    private void configureErrorSummary() {
        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.getStyle().setColor("var(--aura-red-text)");
    }

    private void configureReportFields() {
        reportDate.setRequiredIndicatorVisible(true);
        additionalInformation.setMaxLength(2000);
        additionalInformation.setWidthFull();

        reportBinder.forField(reportDate)
                .asRequired("Report date is required")
                .bind(ReportFormModel::getReportDate, ReportFormModel::setReportDate);
        reportBinder.forField(additionalInformation)
                .bind(ReportFormModel::getAdditionalInformation,
                        ReportFormModel::setAdditionalInformation);
        // The whole line collection is a bound field (ReportLinesField is a
        // CustomField<List<ReportLineModel>>), so the Binder owns its value and
        // its validation — the completeness check is a Binder Validator, surfaced
        // through the same error summary as the report-level fields (ADR-0015/0020).
        reportBinder.forField(linesField)
                .withValidator(ReportDetailView::validateLines)
                .bind(ReportFormModel::getLines, ReportFormModel::setLines);
    }

    /**
     * Binder validation for the line collection: every line needs a type, a
     * non-zero amount, and a VAT rate (the domain guards in {@code ExpenseLine}
     * are the backstop). An empty report is valid — a draft may have no lines yet.
     */
    private static ValidationResult validateLines(List<ReportLineModel> lines,
            com.vaadin.flow.data.binder.ValueContext context) {
        var problems = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var issues = new ArrayList<String>();
            if (line.getExpenseType() == null) {
                issues.add("choose an expense type");
            }
            if (line.getAmount() == null) {
                issues.add("enter a gross amount");
            } else if (line.getAmount().signum() == 0) {
                issues.add("amount must not be zero");
            }
            if (line.getVatRate() == null) {
                issues.add("choose a VAT rate");
            }
            if (!issues.isEmpty()) {
                problems.add("Expense " + (i + 1) + ": " + String.join(", ", issues) + ".");
            }
        }
        return problems.isEmpty() ? ValidationResult.ok()
                : ValidationResult.error(String.join(" ", problems));
    }

    private HorizontalLayout headerRow() {
        statusBadge.getElement().getThemeList().add("badge");
        var header = new HorizontalLayout(new H2("Report"), statusBadge);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        header.setSpacing(true);
        return header;
    }

    private FormLayout reportFields() {
        var form = new FormLayout(reportDate, additionalInformation);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("30rem", 2));
        return form;
    }

    private void clearErrors() {
        errorSummary.removeAll();
        errorSummary.setVisible(false);
    }

    private void showErrors(List<String> messages) {
        errorSummary.removeAll();
        if (messages.isEmpty()) {
            errorSummary.setVisible(false);
            return;
        }
        var heading = new Span("Please fix the following:");
        heading.getStyle().setFontWeight("600");
        var list = new UnorderedList();
        messages.forEach(message -> list.add(new ListItem(message)));
        errorSummary.add(heading, list);
        errorSummary.setVisible(true);
    }
}

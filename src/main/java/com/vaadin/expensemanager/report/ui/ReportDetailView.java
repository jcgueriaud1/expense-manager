package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;
import java.util.List;

import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.report.service.ReportDetailDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
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

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.statusLabel;

/**
 * Create and edit a single report (UC-001/UC-005, ADR-0019).
 *
 * <p>Two entry points on one route: {@code /report} opens a <strong>transient
 * working copy</strong> (no row is persisted until the first save, ADR-0019)
 * with the date defaulting to today, and {@code /report/{id}} loads an existing
 * report. The first successful save routes from {@code /report} to
 * {@code /report/{id}}.
 *
 * <p>Report-level fields only for now — date and optional additional
 * information. <strong>Save is always enabled with a validation error summary on
 * top of the form</strong> (never a disabled submit, project rule / ADR-0020);
 * <strong>Delete</strong> shows only while the report is a {@code DRAFT} (the
 * aggregate enforces the guard, ADR-0006). Stale writes surface the
 * "reload" message (ADR-0011).
 *
 * <p>The line editor and live net/VAT/gross totals land in Phase 2.3 — the
 * {@code linesSeam} container and the {@code €0.00} total below mark where they
 * plug in. {@code @PermitAll}; owner-scoping is enforced in the service.
 */
@Route("report")
@PageTitle("Report")
@PermitAll
public class ReportDetailView extends VerticalLayout
        implements HasUrlParameter<Long> {

    private final transient ExpenseReportService service;

    private final Div errorSummary = new Div();
    private final Span statusBadge = new Span();
    private final DatePicker reportDate = new DatePicker("Report date");
    private final TextArea additionalInformation = new TextArea("Additional information");
    private final Span totalDisplay = new Span();
    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Binder<FormModel> binder = new Binder<>();
    private final FormModel model = new FormModel();

    /** The current working copy (transient for a new report until first save). */
    private transient ReportDetailDto working;

    public ReportDetailView(ExpenseReportService service) {
        this.service = service;
        setPadding(true);
        setSpacing(true);
        setMaxWidth("40rem");

        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.getStyle().setColor("var(--aura-red-text)");

        reportDate.setRequiredIndicatorVisible(true);
        additionalInformation.setMaxLength(2000);
        additionalInformation.setWidthFull();

        binder.forField(reportDate)
                .asRequired("Report date is required")
                .bind(FormModel::getReportDate, FormModel::setReportDate);
        binder.forField(additionalInformation)
                .bind(FormModel::getAdditionalInformation,
                        FormModel::setAdditionalInformation);

        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(event -> onSave());
        delete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        delete.addClickListener(event -> confirmDelete());

        var actions = new HorizontalLayout(save, delete);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        add(headerRow(), errorSummary, reportDate, additionalInformation,
                linesSeam(), totalRow(), actions);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long id) {
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

    /** Populates the form from a working copy and reflects its editability/status. */
    private void load(ReportDetailDto dto) {
        this.working = dto;
        model.setReportDate(dto.reportDate());
        model.setAdditionalInformation(dto.additionalInformation());
        binder.readBean(model);

        clearErrors();
        statusBadge.setText(statusLabel(dto.status()));
        totalDisplay.setText(formatEur(dto.total()));

        boolean editable = dto.status().isEditable();
        reportDate.setReadOnly(!editable);
        additionalInformation.setReadOnly(!editable);
        save.setVisible(editable);
        // Delete only while DRAFT and already persisted (ADR-0006, glossary).
        delete.setVisible(dto.isPersisted() && dto.status().isDeletable());
    }

    private void onSave() {
        clearErrors();
        if (!binder.writeBeanIfValid(model)) {
            showErrors(binder.validate().getValidationErrors().stream()
                    .map(ValidationResult::getErrorMessage).toList());
            return;
        }
        var edited = new ReportDetailDto(working.id(), model.getReportDate(),
                model.getAdditionalInformation(), working.status(),
                working.version(), working.total());
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

    private HorizontalLayout headerRow() {
        var header = new HorizontalLayout(new H2("Report"), statusBadge);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        header.setSpacing(true);
        return header;
    }

    /**
     * Seam for the Phase 2.3 line editor: the inline Grid row editor + ComboBox
     * (expense type / VAT rate) drop in here, above the total.
     */
    private Div linesSeam() {
        var placeholder = new Div(new Paragraph(
                "Expense lines are added in a later phase. A saved report starts "
                        + "with no lines and a total of €0.00."));
        placeholder.getStyle().setColor("var(--vaadin-text-color-secondary)");
        return placeholder;
    }

    private HorizontalLayout totalRow() {
        var label = new Span("Total");
        label.getStyle().setFontWeight("600");
        totalDisplay.getStyle().setFontWeight("600");
        var row = new HorizontalLayout(label, totalDisplay);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        return row;
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

    /** Mutable binding model for the report-level fields (Binder needs setters). */
    private static final class FormModel {
        private LocalDate reportDate;
        private String additionalInformation;

        LocalDate getReportDate() {
            return reportDate;
        }

        void setReportDate(LocalDate reportDate) {
            this.reportDate = reportDate;
        }

        String getAdditionalInformation() {
            return additionalInformation;
        }

        void setAdditionalInformation(String additionalInformation) {
            this.additionalInformation = additionalInformation;
        }
    }
}

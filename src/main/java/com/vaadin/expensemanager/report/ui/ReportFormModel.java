package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@link com.vaadin.flow.data.binder.Binder Binder} model for the report
 * detail view — the whole editable report: the {@link #reportDate}, the optional
 * {@link #additionalInformation}, and the ordered {@link #lines}.
 *
 * <p>A <strong>top-level</strong> class, not an inner class of the view
 * (ADR-0022): a view's binding model is a real domain-shaped object that the
 * Binder reads/writes and validates, and keeping it out of the view file keeps
 * both readable and lets the model be reused and unit-tested on its own. The
 * lines are bound through {@link ReportLinesField} (a
 * {@code CustomField<List<ReportLineModel>>}), so the Binder owns the collection
 * value and its validation just like any other field (ADR-0015).
 */
final class ReportFormModel {

    private LocalDate reportDate;
    private String additionalInformation;
    private List<ReportLineModel> lines = new ArrayList<>();

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

    List<ReportLineModel> getLines() {
        return lines;
    }

    void setLines(List<ReportLineModel> lines) {
        this.lines = lines == null ? new ArrayList<>() : lines;
    }
}

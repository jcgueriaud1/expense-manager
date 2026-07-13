package com.vaadin.expensemanager.report.ui;

import java.time.LocalDate;

/**
 * Mutable binding model for the report-level fields of {@link ReportDetailView}
 * (report date + additional information).
 *
 * <p>A top-level class, not a view inner class (ADR-0022): {@code Binder} needs a
 * plain mutable bean with getters/setters, and keeping it out of the view leaves
 * the view focused on layout/wiring and lets the model be exercised on its own.
 */
final class ReportFormModel {

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

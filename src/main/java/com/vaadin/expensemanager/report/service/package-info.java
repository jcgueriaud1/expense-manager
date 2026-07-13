/**
 * Application services and DTOs for the expense-report aggregate (ADR-0019,
 * ADR-0003). {@code ExpenseReportService} owns the transaction boundary,
 * owner-scoping, and entity↔DTO mapping; the UI exchanges {@code ReportSummaryDto}
 * / {@code ReportDetailDto} records and never sees the domain entities or the
 * repository.
 */
package com.vaadin.expensemanager.report.service;

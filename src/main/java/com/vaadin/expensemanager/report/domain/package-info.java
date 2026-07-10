/**
 * The expense-report domain model (ADR-0006): the {@code ExpenseReport}
 * aggregate root, its lifecycle {@code ReportStatus}, and the ordered
 * {@code StatusChange} log it owns. Invariants (delete guard, edit guard) live
 * here; the {@code service} sub-package wraps these in transactions and DTO
 * mapping (ADR-0003), and entities never leave it.
 */
package com.vaadin.expensemanager.report.domain;

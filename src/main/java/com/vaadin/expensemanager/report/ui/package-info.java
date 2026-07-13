/**
 * Report-owner views (UC-001/UC-002/UC-005, ADR-0017): the owner-scoped
 * {@code MyReportsView} list and the {@code ReportDetailView} create/edit form.
 * Both are {@code @PermitAll} (any authenticated user); the real owner-scoping
 * and authorization live in {@code report.service} (ADR-0008).
 */
package com.vaadin.expensemanager.report.ui;

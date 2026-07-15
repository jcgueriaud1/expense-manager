/**
 * UI layer for the approval workflow (ADR-0002).
 *
 * <p>Holds the {@code ADMIN}-only approval queue. The read-only review of a single
 * report reuses the {@code report} feature's detail view via its
 * {@code /review/{id}} route alias, so this package links into it rather than
 * re-implementing the report rendering.
 */
package com.vaadin.expensemanager.approval.ui;

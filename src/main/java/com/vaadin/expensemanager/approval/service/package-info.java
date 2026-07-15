/**
 * Application-service layer for the approval workflow (ADR-0002, ADR-0019).
 *
 * <p>Holds the {@code ADMIN}-only {@code ApprovalService} — the admin's window
 * onto <em>everyone's</em> submitted reports, deliberately bypassing the
 * owner-scoping the {@code report} service enforces (ADR-0008) — and the
 * review-queue DTOs it hands the UI. Reuses the {@code report} feature's
 * aggregate, repository, and detail DTO/mapper rather than duplicating them.
 */
package com.vaadin.expensemanager.approval.service;

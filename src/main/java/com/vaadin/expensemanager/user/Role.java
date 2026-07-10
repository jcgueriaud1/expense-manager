package com.vaadin.expensemanager.user;

/**
 * The two application roles (ADR-0007, ADR-0008).
 *
 * <p>{@code ADMIN} subsumes {@code USER} — but subsumption is <em>not</em>
 * stored: an admin row holds only {@code {ADMIN}}. The {@code ADMIN > USER}
 * relation is expressed once as a Spring {@code RoleHierarchy} bean (ADR-0008),
 * so an admin gains USER access at runtime without every seed/provisioning site
 * having to grant both roles.
 *
 * <p>Only these two roles exist in V1 — no separate finance role; the admin
 * performs finance/export duties (ADR-0007).
 */
public enum Role {
    USER,
    ADMIN
}

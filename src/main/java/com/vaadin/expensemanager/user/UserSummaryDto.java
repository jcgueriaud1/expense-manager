package com.vaadin.expensemanager.user;

/**
 * Immutable read model of a {@link User} for the ADMIN users list (ADR-0003,
 * issue #64).
 *
 * <p>Carries the single <strong>effective</strong> role rather than the stored
 * role set: an admin row stores only {@code {ADMIN}} (ADR-0008), so the summary
 * collapses the set to {@code roles.contains(ADMIN) ? ADMIN : USER} for a
 * one-column display. The live JPA entity never reaches the UI.
 *
 * @param id      persistent id
 * @param email   the login identifier (unique)
 * @param name    display name
 * @param role    the effective role (ADMIN if the stored set contains ADMIN,
 *                otherwise USER)
 * @param enabled whether the account is enabled (revoked when false)
 */
public record UserSummaryDto(Long id, String email, String name, Role role,
        boolean enabled) {
}

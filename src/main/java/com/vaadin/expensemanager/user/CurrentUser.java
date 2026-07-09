package com.vaadin.expensemanager.user;

import java.util.Set;

/**
 * Immutable read model of the logged-in user handed to the UI (ADR-0003,
 * ADR-0008).
 *
 * <p>Built by the {@code CurrentUserProvider} accessor from the authenticated
 * principal with <strong>no per-call DB query</strong> — the id/email/name/roles
 * are captured into the principal at login and simply adapted here. The live
 * JPA {@link User} entity never reaches the UI.
 *
 * <p>{@link #roles()} are the <em>stored</em> roles (an admin holds only
 * {@code {ADMIN}}); the effective {@code ADMIN ⊇ USER} expansion is a runtime
 * authorization concern handled by the {@code RoleHierarchy} bean, not baked
 * into this record.
 */
public record CurrentUser(Long id, String email, String name, Set<Role> roles) {

    public CurrentUser {
        roles = Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }
}

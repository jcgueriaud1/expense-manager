package com.vaadin.expensemanager.security;

import java.util.Set;

import com.vaadin.expensemanager.user.Role;

/**
 * The contract both login paths expose to the rest of the app (ADR-0008).
 *
 * <p>The local form-stub principal (ADR-0012) and the future Google OIDC
 * principal (ADR-0007, Phase 1.2) each implement this, carrying the
 * <em>local</em> user's id, email, name and stored roles. That lets
 * {@code CurrentUserProvider} adapt either principal into a
 * {@link com.vaadin.expensemanager.user.CurrentUser} record without a DB query
 * and without caring which authentication mechanism produced it.
 */
public interface AppUserPrincipal {

    Long getId();

    String getEmail();

    String getName();

    Set<Role> getRoles();
}

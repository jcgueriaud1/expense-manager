package com.vaadin.expensemanager.security;

import java.util.Collection;
import java.util.Set;

import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.User;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The local form-stub principal (ADR-0012): a Spring Security {@link UserDetails}
 * that is also an {@link AppUserPrincipal}.
 *
 * <p>Authorities are derived from the <em>local</em> stored roles (mapped to
 * {@code ROLE_USER} / {@code ROLE_ADMIN}), so enforcement is identical to
 * production — the {@code RoleHierarchy} bean supplies the {@code ADMIN ⊇ USER}
 * expansion at check time (ADR-0008). The password is the shared dev password
 * (already encoded), not a per-user secret; login is by email + that one
 * password.
 */
public final class AppUserDetails implements UserDetails, AppUserPrincipal {

    private final Long id;
    private final String email;
    private final String name;
    private final Set<Role> roles;
    private final boolean enabled;
    private final String encodedPassword;

    public AppUserDetails(User user, String encodedPassword) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.roles = user.getRoles();
        this.enabled = user.isEnabled();
        this.encodedPassword = encodedPassword;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<Role> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return encodedPassword;
    }

    /** The login identifier is the email address (ADR-0012). */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

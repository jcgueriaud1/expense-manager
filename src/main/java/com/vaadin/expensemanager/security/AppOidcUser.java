package com.vaadin.expensemanager.security;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.User;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * The Google OIDC principal (ADR-0007, Phase 1.3): an {@link OidcUser} that is
 * also an {@link AppUserPrincipal}, carrying the <em>local</em> user's
 * id/email/name/stored roles.
 *
 * <p>Authorities come from the local {@link Role}s (mapped to
 * {@code ROLE_USER} / {@code ROLE_ADMIN}), <strong>never</strong> from Google —
 * so enforcement is identical to the form-stub path (ADR-0012) and the
 * {@code RoleHierarchy} bean supplies {@code ADMIN ⊇ USER} at check time
 * (ADR-0008). The OIDC-specific surface ({@link #getClaims()},
 * {@link #getIdToken()}, {@link #getUserInfo()}, {@link #getAttributes()}) is
 * delegated to the raw Google-issued {@code OidcUser}.
 *
 * <p>Because {@link OidcUser#getName()} and {@link AppUserPrincipal#getName()}
 * collide on one signature, {@link #getName()} returns the <em>display</em>
 * name (the {@code AppUserPrincipal} contract that {@code CurrentUser} relies
 * on), not the OIDC subject; the stable subject is still reachable via
 * {@link #getSubject()} / {@link #getClaims()}.
 */
public final class AppOidcUser implements OidcUser, AppUserPrincipal {

    private final OidcUser delegate;
    private final Long id;
    private final String email;
    private final String name;
    private final Set<Role> roles;

    public AppOidcUser(User user, OidcUser delegate) {
        this.delegate = delegate;
        this.id = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.roles = user.getRoles();
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getEmail() {
        return email;
    }

    /** The local display name — the {@link AppUserPrincipal} contract. */
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
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }
}

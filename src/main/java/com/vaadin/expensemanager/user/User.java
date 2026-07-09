package com.vaadin.expensemanager.user;

import java.util.EnumSet;
import java.util.Set;

import com.vaadin.expensemanager.base.AuditedEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A local user record linked to a Google identity by {@code sub} (ADR-0007).
 *
 * <p>The entity is the source of truth for authorization — {@code roles} and
 * {@code enabled} are managed locally and never overwritten by Google claims.
 * {@code email} and {@code name} are <strong>set once</strong> at
 * provision/claim time; {@code sub} is <strong>nullable until claimed</strong>
 * (the bootstrap-admin seed carries a {@code null} sub until its first Google
 * login) and <strong>unique when set</strong>.
 *
 * <p>Per ADR-0003 this entity never leaves the service layer: the UI exchanges
 * the immutable {@link CurrentUser} record instead. There is deliberately no
 * password column — the local form-stub (ADR-0012) authenticates every seeded
 * user against a single shared dev password from configuration, not a per-user
 * hash.
 */
@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uq_app_user_email", columnNames = "email")
})
public class User extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Google's stable subject claim. Null until the user first logs in with
     * Google and the row is "claimed" (ADR-0007). Unique when set — enforced by
     * a partial unique index (see the users migration), which JPA cannot
     * express, so there is no {@code @UniqueConstraint} for it here.
     */
    @Column(name = "sub")
    private String sub;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    /** JPA constructor. */
    protected User() {
    }

    public User(String email, String name, Set<Role> roles) {
        this.email = email;
        this.name = name;
        this.roles = EnumSet.copyOf(roles);
    }

    public Long getId() {
        return id;
    }

    public String getSub() {
        return sub;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns an unmodifiable snapshot of the locally-managed roles. Callers
     * that need to change roles must go through {@link #setRoles(Set)} — the
     * effective authorities (with {@code ADMIN ⊇ USER}) are resolved by the
     * {@code RoleHierarchy} bean, not stored here (ADR-0008).
     */
    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public void setRoles(Set<Role> roles) {
        this.roles = EnumSet.copyOf(roles);
    }
}

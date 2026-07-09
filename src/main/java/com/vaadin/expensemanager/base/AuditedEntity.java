package com.vaadin.expensemanager.base;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Shared audit baseline for all persistent entities (ADR-0016).
 *
 * <p>Carries app-side, Hibernate-managed audit timestamps: {@code createdAt} is
 * set once on first persist ({@link CreationTimestamp}) and {@code updatedAt} on
 * every flush ({@link UpdateTimestamp}). There are deliberately <em>no</em> DB
 * triggers and <em>no</em> created-by / modified-by user tracking — attribution
 * that the domain needs (report owner, approver) is modelled as explicit domain
 * fields, not audit infrastructure.
 *
 * <p>Concrete entities declare their own {@code bigint} identity primary key
 * (the ADR-0016 convention) — this superclass owns only the timestamps so the
 * id type/name stays visible on each aggregate.
 */
@MappedSuperclass
public abstract class AuditedEntity {

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

package com.vaadin.expensemanager.reference;

import com.vaadin.expensemanager.base.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Admin-editable expense-type reference config (ADR-0018, Phase 2.1).
 *
 * <p>Classifies an expense line: a {@code name}, a display {@code order}, an
 * {@code active} flag, and a <strong>required</strong> {@code defaultVatRate} FK
 * — the rate a new line pre-fills when this type is chosen (the line may still
 * override it, Phase 2.3). Seeded via Flyway (V3).
 *
 * <p>As with {@link VatRate}, deactivating hides a type from <em>new</em> lines
 * but never deletes it, so historical lines keep their classification (ADR-0018).
 * Per ADR-0003 the entity stays in the service layer; the UI sees the immutable
 * {@link ExpenseTypeDto} record.
 */
@Entity
@Table(name = "expense_type")
public class ExpenseType extends AuditedEntity implements Ordered {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * The rate a new line defaults to for this type (ADR-0018). Required (NOT
     * NULL FK); eagerly fetched because the admin grid always renders it and the
     * set is tiny.
     */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "default_vat_rate_id", nullable = false)
    private VatRate defaultVatRate;

    /** JPA constructor. */
    protected ExpenseType() {
    }

    public ExpenseType(String name, int displayOrder, VatRate defaultVatRate) {
        this.name = name;
        this.displayOrder = displayOrder;
        this.defaultVatRate = defaultVatRate;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public VatRate getDefaultVatRate() {
        return defaultVatRate;
    }

    public void setDefaultVatRate(VatRate defaultVatRate) {
        this.defaultVatRate = defaultVatRate;
    }
}

package com.vaadin.expensemanager.reference;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Admin-editable VAT rate reference config (ADR-0018, Phase 2.1).
 *
 * <p>A percent {@code value} (Postgres {@code numeric}, ADR-0010) with a display
 * {@code order} and an {@code active} flag. Seeded from the Finnish
 * (Verohallinto) rates via Flyway (V3).
 *
 * <p><strong>Rate history is preserved by the {@code active} flag, never by
 * deletion.</strong> When the law changes, an admin deactivates the old row and
 * adds the new one; deactivating hides a rate from <em>new</em> line choices but
 * leaves the row intact, so an {@code ExpenseLine} filed against it keeps its
 * original rate. Nothing here ever deletes a rate (ADR-0018).
 *
 * <p>Per ADR-0003 this entity never leaves the service layer — the UI exchanges
 * the immutable {@link VatRateDto} record instead.
 */
@Entity
@Table(name = "vat_rate")
public class VatRate extends AuditedEntity implements Ordered {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The rate as a percent, e.g. {@code 25.50}. Non-negative. */
    @Column(name = "value", nullable = false, precision = 5, scale = 2)
    private BigDecimal value;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** JPA constructor. */
    protected VatRate() {
    }

    public VatRate(BigDecimal value, int displayOrder) {
        this.value = value;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
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
}

package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Kilometre compensation rate (€/km) for a single {@code year} (PRD 4.1,
 * Phase 4.1).
 *
 * <p>A per-km rate carries sub-cent precision ({@code numeric(6,3)}), unlike
 * the money amounts (ADR-0010). Seeded from the provisional Verohallinto 2026
 * decision via Flyway (V7). History is preserved by {@code year} (see
 * {@link DomesticPerDiemRate}); the UI sees the immutable {@link KilometreRateDto}.
 */
@Entity
@Table(name = "kilometre_rate")
public class KilometreRate extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false, unique = true)
    private int year;

    @Column(name = "amount_per_km", nullable = false, precision = 6, scale = 3)
    private BigDecimal amountPerKm;

    /** JPA constructor. */
    protected KilometreRate() {
    }

    public KilometreRate(int year, BigDecimal amountPerKm) {
        this.year = year;
        this.amountPerKm = amountPerKm;
    }

    public Long getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getAmountPerKm() {
        return amountPerKm;
    }

    public void setAmountPerKm(BigDecimal amountPerKm) {
        this.amountPerKm = amountPerKm;
    }
}

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
 * Domestic per-diem rate for a single {@code year} (PRD 4.1, Phase 4.1).
 *
 * <p>Carries the full-day and partial-day amounts (money, ADR-0010) and the
 * hour thresholds a trip must exceed to qualify (full over
 * {@code fullDayMinHours}, partial over the lower {@code partialDayMinHours}).
 * Seeded from the provisional Verohallinto 2026 decision via Flyway (V7).
 *
 * <p><strong>History is preserved by {@code year}, never by an {@code active}
 * flag</strong> — the deliberate contrast with the VAT/expense-type reference
 * tables (ADR-0018). Each year is its own row; adding a new year never mutates
 * a prior one. Per ADR-0003 the entity never leaves the service layer — the UI
 * exchanges the immutable {@link DomesticPerDiemDto} record.
 */
@Entity
@Table(name = "domestic_per_diem_rate")
public class DomesticPerDiemRate extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false, unique = true)
    private int year;

    @Column(name = "full_day_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal fullDayAmount;

    @Column(name = "partial_day_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal partialDayAmount;

    @Column(name = "full_day_min_hours", nullable = false)
    private int fullDayMinHours;

    @Column(name = "partial_day_min_hours", nullable = false)
    private int partialDayMinHours;

    /** JPA constructor. */
    protected DomesticPerDiemRate() {
    }

    public DomesticPerDiemRate(int year, BigDecimal fullDayAmount, BigDecimal partialDayAmount,
            int fullDayMinHours, int partialDayMinHours) {
        this.year = year;
        this.fullDayAmount = fullDayAmount;
        this.partialDayAmount = partialDayAmount;
        this.fullDayMinHours = fullDayMinHours;
        this.partialDayMinHours = partialDayMinHours;
    }

    public Long getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getFullDayAmount() {
        return fullDayAmount;
    }

    public void setFullDayAmount(BigDecimal fullDayAmount) {
        this.fullDayAmount = fullDayAmount;
    }

    public BigDecimal getPartialDayAmount() {
        return partialDayAmount;
    }

    public void setPartialDayAmount(BigDecimal partialDayAmount) {
        this.partialDayAmount = partialDayAmount;
    }

    public int getFullDayMinHours() {
        return fullDayMinHours;
    }

    public void setFullDayMinHours(int fullDayMinHours) {
        this.fullDayMinHours = fullDayMinHours;
    }

    public int getPartialDayMinHours() {
        return partialDayMinHours;
    }

    public void setPartialDayMinHours(int partialDayMinHours) {
        this.partialDayMinHours = partialDayMinHours;
    }
}

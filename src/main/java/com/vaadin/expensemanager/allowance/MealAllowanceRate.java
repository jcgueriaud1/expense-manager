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
 * Meal allowance (ateriakorvaus) amount for a single {@code year} (PRD 4.1,
 * Phase 4.1).
 *
 * <p>Money (ADR-0010), seeded from the provisional Verohallinto 2026 decision
 * via Flyway (V7). History is preserved by {@code year} (see
 * {@link DomesticPerDiemRate}); the UI sees the immutable
 * {@link MealAllowanceDto}.
 */
@Entity
@Table(name = "meal_allowance_rate")
public class MealAllowanceRate extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false, unique = true)
    private int year;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** JPA constructor. */
    protected MealAllowanceRate() {
    }

    public MealAllowanceRate(int year, BigDecimal amount) {
        this.year = year;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}

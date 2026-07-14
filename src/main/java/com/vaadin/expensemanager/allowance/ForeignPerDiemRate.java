package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Foreign per-diem amount for a single ({@code year}, {@code country}) pair
 * (PRD 4.1, Phase 4.1).
 *
 * <p>Money (ADR-0010) keyed by year and free-text country name, unique within a
 * year (DB constraint {@code uq_foreign_per_diem_year_country}). Seeded from the
 * provisional Verohallinto 2026 starter set via Flyway (V7). History is
 * preserved by {@code year} (see {@link DomesticPerDiemRate}); the UI sees the
 * immutable {@link ForeignPerDiemDto}.
 */
@Entity
@Table(name = "foreign_per_diem_rate",
        uniqueConstraints = @UniqueConstraint(name = "uq_foreign_per_diem_year_country",
                columnNames = {"year", "country"}))
public class ForeignPerDiemRate extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** JPA constructor. */
    protected ForeignPerDiemRate() {
    }

    public ForeignPerDiemRate(int year, String country, BigDecimal amount) {
        this.year = year;
        this.country = country;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public String getCountry() {
        return country;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}

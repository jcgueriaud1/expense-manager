package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.vaadin.expensemanager.base.AuditedEntity;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.VatRate;

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
 * A single expense on a report (glossary: Expense Line) — part of the
 * {@link ExpenseReport} aggregate, never a root of its own (ADR-0006).
 *
 * <p>Carries a required {@link #expenseType}, a gross {@link #amount} (required
 * and <strong>non-zero</strong>; negatives are allowed for credits/corrections),
 * a required {@link #vatRate} that <em>defaults from the chosen type but is
 * overridable</em> (ADR-0018), and an optional {@link #comment}. There is
 * deliberately <strong>no business date and no description</strong> (glossary,
 * Phase 2.3 spec).
 *
 * <p>The net and VAT figures are <strong>derived, never stored</strong>
 * (ADR-0010): the amount is the gross and {@link #amounts()} works net/VAT
 * backward from it via {@link LineAmounts#of}. The VAT rate is referenced (not
 * copied): a rate the admin later deactivates is retained, so a historical line
 * keeps its filed rate (ADR-0018).
 *
 * <p>Created and mutated only through the aggregate
 * ({@link ExpenseReport#reconcileLines}); the constructor and the
 * {@link #update} seam are package-visible so the invariants stay under the
 * aggregate's control (ADR-0006).
 */
@Entity
@Table(name = "expense_line")
public class ExpenseLine extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "expense_type_id", nullable = false)
    private ExpenseType expenseType;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "vat_rate_id", nullable = false)
    private VatRate vatRate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "comment")
    private String comment;

    /** JPA constructor. */
    protected ExpenseLine() {
    }

    ExpenseLine(ExpenseType expenseType, BigDecimal amount, VatRate vatRate,
            String comment) {
        this.expenseType = requireType(expenseType);
        this.vatRate = requireRate(vatRate);
        this.amount = requireNonZeroAmount(amount);
        this.comment = normalize(comment);
    }

    /** Applies edited values to an existing line (aggregate reconciliation seam). */
    void update(ExpenseType expenseType, BigDecimal amount, VatRate vatRate,
            String comment) {
        this.expenseType = requireType(expenseType);
        this.vatRate = requireRate(vatRate);
        this.amount = requireNonZeroAmount(amount);
        this.comment = normalize(comment);
    }

    /** The derived net/VAT/gross figures of this line (ADR-0010). */
    public LineAmounts amounts() {
        return LineAmounts.of(amount, vatRate.getValue());
    }

    /** The gross amount as entered, scale 2. */
    public BigDecimal gross() {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** The derived net amount (gross excluding VAT), scale 2. */
    public BigDecimal net() {
        return amounts().net();
    }

    /** The derived VAT amount, scale 2. */
    public BigDecimal vat() {
        return amounts().vat();
    }

    public Long getId() {
        return id;
    }

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public VatRate getVatRate() {
        return vatRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getComment() {
        return comment;
    }

    private static ExpenseType requireType(ExpenseType expenseType) {
        if (expenseType == null) {
            throw new IllegalArgumentException("Expense type is required");
        }
        return expenseType;
    }

    private static VatRate requireRate(VatRate vatRate) {
        if (vatRate == null) {
            throw new IllegalArgumentException("VAT rate is required");
        }
        return vatRate;
    }

    private static BigDecimal requireNonZeroAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() == 0) {
            throw new IllegalArgumentException("Amount must not be zero");
        }
        return scaled;
    }

    /** Trims and collapses blank optional text to {@code null}. */
    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}

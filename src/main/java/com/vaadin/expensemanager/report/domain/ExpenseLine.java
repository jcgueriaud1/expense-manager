package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;

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
 * One line of an expense report — a single reimbursable expense (glossary:
 * Expense Line). Part of the {@link ExpenseReport} aggregate (ADR-0006): created
 * and mutated only through the report's {@link ExpenseReport#replaceLines
 * replaceLines}, never on its own, so its parent link and insertion order stay
 * under the aggregate's control.
 *
 * <p>Carries a required {@link ExpenseType} classification, a required
 * {@link VatRate} (defaulted from the type but overridable — the default is a UI
 * concern, ADR-0018), the gross {@link #amount} the user paid, and an optional
 * {@link #comment}. There is deliberately <strong>no business date and no
 * description</strong> (glossary). Net and VAT are derived via {@link LineMoney},
 * never stored (ADR-0010).
 *
 * <p><strong>Invariant (ADR-0006):</strong> the amount is required and
 * <strong>non-zero</strong> — negatives are allowed for credits/corrections, but
 * a €0.00 line is meaningless and rejected here. Type and rate are required. Per
 * ADR-0003 the entity never leaves the service layer.
 */
@Entity
@Table(name = "expense_line")
public class ExpenseLine extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Read-only back-reference: the owning ExpenseReport.lines collection maps
    // report_id (and line_index), so this side must not also write it (mirrors
    // StatusChange).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false, insertable = false, updatable = false)
    private ExpenseReport report;

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

    ExpenseLine(ExpenseReport report, ExpenseType expenseType, BigDecimal amount,
            VatRate vatRate, String comment) {
        this.report = report;
        update(expenseType, amount, vatRate, comment);
    }

    /**
     * Applies edited field values, enforcing the line invariants (ADR-0006).
     *
     * @throws IllegalArgumentException if the type or rate is missing, or the
     *         amount is missing or zero
     */
    void update(ExpenseType expenseType, BigDecimal amount, VatRate vatRate,
            String comment) {
        this.expenseType = requireType(expenseType);
        this.vatRate = requireRate(vatRate);
        this.amount = requireNonZeroAmount(amount);
        this.comment = normalize(comment);
    }

    /** Gross amount at scale 2 (what the user paid). */
    public BigDecimal gross() {
        return LineMoney.gross(amount);
    }

    /** Derived net, from the gross amount and this line's rate (ADR-0010). */
    public BigDecimal net() {
        return LineMoney.net(amount, vatRate.getValue());
    }

    /** Derived VAT = gross − net (ADR-0010). */
    public BigDecimal vat() {
        return LineMoney.vat(amount, vatRate.getValue());
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

    private static ExpenseType requireType(ExpenseType type) {
        if (type == null) {
            throw new IllegalArgumentException("Expense type is required");
        }
        return type;
    }

    private static VatRate requireRate(VatRate rate) {
        if (rate == null) {
            throw new IllegalArgumentException("VAT rate is required");
        }
        return rate;
    }

    private static BigDecimal requireNonZeroAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("Amount must not be zero");
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Trims and collapses blank optional text to {@code null}. */
    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}

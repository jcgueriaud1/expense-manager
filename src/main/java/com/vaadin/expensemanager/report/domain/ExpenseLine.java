package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.vaadin.expensemanager.base.AuditedEntity;
import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.reference.ExpenseType;
import com.vaadin.expensemanager.reference.VatRate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * <p>Carries a required {@link #expenseType}, a gross <strong>unit price</strong>
 * {@link #amount} (required and <strong>non-zero</strong>; negatives are allowed
 * for credits/corrections), a {@link #quantity} (required, strictly {@code > 0},
 * default {@code 1}), a required {@link #vatRate} that <em>defaults from the
 * chosen type but is overridable</em> (ADR-0018), and an optional
 * {@link #comment}. There is deliberately <strong>no business date and no
 * description</strong> (glossary, Phase 2.3 spec).
 *
 * <p>The gross, net and VAT figures are <strong>derived, never stored</strong>
 * (ADR-0010, ADR-0023): the gross is {@code amount × quantity} (HALF_UP scale 2)
 * and {@link #amounts()} works net/VAT backward from <em>that</em> via
 * {@link LineAmounts#ofLine}. A quantity-1 line therefore behaves exactly as
 * before this shape existed. The VAT rate is referenced (not copied): a rate the
 * admin later deactivates is retained, so a historical line keeps its filed rate
 * (ADR-0018).
 *
 * <p>A line may be <strong>manual</strong> (user-entered, {@link #travel}
 * {@code null}) or <strong>generated</strong> — a read-only per-diem line linked
 * to the {@link Travel} that produced it (Phase 4.2/4.3). A generated line is
 * created via {@link #generated} and regenerated in place via
 * {@link #updateGenerated}; it is never edited or removed on its own, only
 * through its travel.
 *
 * <p>Created and mutated only through the aggregate
 * ({@link ExpenseReport#reconcile}); the constructor and the {@link #update}
 * seam are package-visible so the invariants stay under the aggregate's control
 * (ADR-0006).
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

    /** The gross unit price (each) — see {@link #gross()} (ADR-0023). */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * How many units the line covers (glossary: Quantity) — strictly positive,
     * {@code 1} for a plain single-item line and for the flat generated lines
     * (per-diem, meal, parking); the kilometres driven for a generated kilometre
     * line (ADR-0023).
     */
    @Column(name = "quantity", nullable = false, precision = 19, scale = 2)
    private BigDecimal quantity;

    @Column(name = "comment")
    private String comment;

    /**
     * The travel that generated this line, or {@code null} for a manual line
     * (glossary: Travel Calculator). A non-null value marks the line as a
     * read-only generated per-diem line owned by its {@link Travel} (Phase 4.2).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_id")
    private Travel travel;

    /**
     * Which generated line this is (Phase 4.3, F-034) — {@code null} for a manual
     * line, non-null for a travel-generated one. A single {@link Travel} owns at
     * most one line of each {@link GeneratedLineKind}; the kind routes the line in
     * the report totals (tax-free subtotal vs Net/VAT).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "generated_kind")
    private GeneratedLineKind generatedKind;

    /** JPA constructor. */
    protected ExpenseLine() {
    }

    ExpenseLine(ExpenseType expenseType, BigDecimal amount, BigDecimal quantity,
            VatRate vatRate, String comment) {
        this.expenseType = requireType(expenseType);
        this.vatRate = requireRate(vatRate);
        this.amount = requireNonZeroAmount(amount);
        this.quantity = requirePositiveQuantity(quantity);
        this.comment = normalize(comment);
    }

    /**
     * Creates a read-only generated line of the given {@code kind} linked to
     * {@code travel} (Phase 4.2/4.3). The unit price must be non-zero and the
     * quantity positive — the aggregate only generates a line when the rule produced
     * something.
     *
     * <p>A generated line carries the same unit price × quantity shape as a manual
     * one (ADR-0023): the kilometre line is a real multiple
     * ({@code quantity = kilometres}, {@code unitPrice = €/km rate}), while the
     * per-diem, meal, and parking lines are flat and arrive at quantity {@code 1}
     * with their computed amount as the unit price.
     */
    static ExpenseLine generated(Travel travel, GeneratedLineKind kind,
            ExpenseType expenseType, BigDecimal unitPrice, BigDecimal quantity,
            VatRate vatRate, String comment) {
        var line = new ExpenseLine(expenseType, unitPrice, quantity, vatRate, comment);
        line.travel = travel;
        line.generatedKind = requireKind(kind);
        return line;
    }

    /** Applies edited values to an existing line (aggregate reconciliation seam). */
    void update(ExpenseType expenseType, BigDecimal amount, BigDecimal quantity,
            VatRate vatRate, String comment) {
        this.expenseType = requireType(expenseType);
        this.vatRate = requireRate(vatRate);
        this.amount = requireNonZeroAmount(amount);
        this.quantity = requirePositiveQuantity(quantity);
        this.comment = normalize(comment);
    }

    /** Regenerates this line's figures from its (re-costed) travel. */
    void updateGenerated(Travel travel, GeneratedLineKind kind, ExpenseType expenseType,
            BigDecimal unitPrice, BigDecimal quantity, VatRate vatRate, String comment) {
        update(expenseType, unitPrice, quantity, vatRate, comment);
        this.travel = travel;
        this.generatedKind = requireKind(kind);
    }

    /** The travel that generated this line, or {@code null} if it is manual. */
    public Travel getTravel() {
        return travel;
    }

    /** Whether this is a read-only generated line owned by a travel (ADR-0006). */
    public boolean isGenerated() {
        return travel != null;
    }

    /** Which generated line this is, or {@code null} for a manual line (Phase 4.3). */
    public GeneratedLineKind getGeneratedKind() {
        return generatedKind;
    }

    /**
     * Whether this line counts toward Net/VAT: every manual line, plus a generated
     * line that is <em>not</em> a tax-free allowance (i.e. parking). The tax-free
     * allowances (per-diem/kilometre/meal) are broken out into their own subtotals.
     */
    boolean countsInNetVat() {
        return generatedKind == null || !generatedKind.isTaxFreeAllowance();
    }

    /** The derived net/VAT/gross figures of this line (ADR-0010, ADR-0023). */
    public LineAmounts amounts() {
        return LineAmounts.ofLine(amount, quantity, vatRate.getValue());
    }

    /** The derived gross — unit price × quantity, HALF_UP scale 2 (ADR-0023). */
    public BigDecimal gross() {
        return LineAmounts.grossOf(amount, quantity);
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

    /** The gross unit price (each), scale 2 (ADR-0023). */
    public BigDecimal getAmount() {
        return amount;
    }

    /** The line quantity, scale 2 and strictly positive (ADR-0023). */
    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getComment() {
        return comment;
    }

    private static ExpenseType requireType(ExpenseType expenseType) {
        if (expenseType == null) {
            throw new DomainRuleException("Expense type is required");
        }
        return expenseType;
    }

    private static GeneratedLineKind requireKind(GeneratedLineKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("Generated line kind is required");
        }
        return kind;
    }

    private static VatRate requireRate(VatRate vatRate) {
        if (vatRate == null) {
            throw new DomainRuleException("VAT rate is required");
        }
        return vatRate;
    }

    private static BigDecimal requireNonZeroAmount(BigDecimal amount) {
        if (amount == null) {
            throw new DomainRuleException("Amount is required");
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() == 0) {
            throw new DomainRuleException("Amount must not be zero");
        }
        return scaled;
    }

    /**
     * Quantity is required and <strong>strictly positive</strong> (ADR-0023) —
     * zero would make the gross zero and a negative one would express a credit
     * twice over; credits ride a negative unit price instead. Rounded HALF_UP to
     * scale 2 first, so a sub-cent quantity that rounds to zero is rejected rather
     * than silently zeroing the line.
     */
    private static BigDecimal requirePositiveQuantity(BigDecimal quantity) {
        if (quantity == null) {
            throw new DomainRuleException("Quantity is required");
        }
        BigDecimal scaled = quantity.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() <= 0) {
            throw new DomainRuleException("Quantity must be greater than zero");
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

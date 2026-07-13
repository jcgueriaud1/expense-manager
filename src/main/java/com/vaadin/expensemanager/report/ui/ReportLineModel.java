package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.util.Objects;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.report.domain.LineMoney;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;

/**
 * Mutable working copy of one expense line, edited in the {@link ReportLineEditorPanel}
 * and carried as the value of {@link ReportLinesField}.
 *
 * <p>Holds the reference DTOs directly (not just their ids) so the editor's
 * pickers can show a now-deactivated value a historical line kept (ADR-0018);
 * net/VAT/gross are derived live via {@link LineMoney}. It is a Binder-friendly
 * bean (getters/setters) but carries <strong>value-based {@link #equals}</strong>
 * so {@link ReportLinesField} can detect real changes and emit value-change
 * events from snapshot copies (a {@code HasValue} can't observe in-place mutation
 * of a shared instance).
 */
final class ReportLineModel {

    private Long id;
    private ExpenseTypeDto expenseType;
    private BigDecimal amount;
    private VatRateDto vatRate;
    private String comment;

    ReportLineModel() {
    }

    static ReportLineModel from(ExpenseLineDto dto) {
        var model = new ReportLineModel();
        model.id = dto.id();
        model.expenseType = dto.expenseType();
        model.amount = dto.amount();
        model.vatRate = dto.vatRate();
        model.comment = dto.comment();
        return model;
    }

    /** A detached snapshot, so the field's value never aliases the working instance. */
    ReportLineModel copy() {
        var copy = new ReportLineModel();
        copy.id = id;
        copy.expenseType = expenseType;
        copy.amount = amount;
        copy.vatRate = vatRate;
        copy.comment = comment;
        return copy;
    }

    ExpenseLineDto toDto() {
        return new ExpenseLineDto(id, expenseType, amount, vatRate, comment);
    }

    boolean hasAmount() {
        return amount != null;
    }

    BigDecimal grossAmount() {
        return LineMoney.gross(amount);
    }

    BigDecimal netAmount() {
        return LineMoney.net(amount, vatRate.value());
    }

    BigDecimal vatAmount() {
        return LineMoney.vat(amount, vatRate.value());
    }

    Long getId() {
        return id;
    }

    ExpenseTypeDto getExpenseType() {
        return expenseType;
    }

    void setExpenseType(ExpenseTypeDto expenseType) {
        this.expenseType = expenseType;
    }

    BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    VatRateDto getVatRate() {
        return vatRate;
    }

    void setVatRate(VatRateDto vatRate) {
        this.vatRate = vatRate;
    }

    String getComment() {
        return comment;
    }

    void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReportLineModel other)) {
            return false;
        }
        return Objects.equals(id, other.id)
                && Objects.equals(expenseType, other.expenseType)
                && Objects.equals(amount, other.amount)
                && Objects.equals(vatRate, other.vatRate)
                && Objects.equals(comment, other.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, expenseType, amount, vatRate, comment);
    }
}

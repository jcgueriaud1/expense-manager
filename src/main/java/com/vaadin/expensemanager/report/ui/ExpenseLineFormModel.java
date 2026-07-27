package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;

/**
 * Mutable binding model for the modal line editor ({@link LineEditorDialog}).
 *
 * <p>A top-level class, not a dialog/view inner class (ADR-0022): {@code Binder}
 * binds its {@code ComboBox}/{@code BigDecimalField}/{@code TextField} fields to
 * these getters/setters, and extracting it keeps the editor focused on wiring.
 * Holds the chosen reference-data DTOs directly (not their ids) so the ComboBoxes
 * bind naturally; the dialog turns them back into an {@code ExpenseLineDto} on
 * save.
 */
final class ExpenseLineFormModel {

    private ExpenseTypeDto expenseType;
    private VatRateDto vatRate;
    /** The gross unit price, each (ADR-0023). */
    private BigDecimal amount;
    /** How many units, strictly positive; {@code 1} unless the user changes it. */
    private BigDecimal quantity = BigDecimal.ONE;
    private String comment;

    ExpenseTypeDto getExpenseType() {
        return expenseType;
    }

    void setExpenseType(ExpenseTypeDto expenseType) {
        this.expenseType = expenseType;
    }

    VatRateDto getVatRate() {
        return vatRate;
    }

    void setVatRate(VatRateDto vatRate) {
        this.vatRate = vatRate;
    }

    BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    BigDecimal getQuantity() {
        return quantity;
    }

    void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    String getComment() {
        return comment;
    }

    void setComment(String comment) {
        this.comment = comment;
    }
}

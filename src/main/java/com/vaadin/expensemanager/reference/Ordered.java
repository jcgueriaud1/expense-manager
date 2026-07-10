package com.vaadin.expensemanager.reference;

/**
 * Shared shape of a reference row that carries a mutable display order, so the
 * reorder (swap-with-neighbour) logic in {@link ReferenceDataService} can be
 * written once for both {@link VatRate} and {@link ExpenseType}.
 */
interface Ordered {

    Long getId();

    int getDisplayOrder();

    void setDisplayOrder(int displayOrder);
}

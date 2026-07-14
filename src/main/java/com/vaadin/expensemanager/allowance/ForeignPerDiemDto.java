package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * Immutable read model of a {@link ForeignPerDiemRate} for the UI and the
 * calculator (ADR-0003).
 *
 * @param id      persistent id
 * @param year    the year this rate applies to
 * @param country country name
 * @param amount  foreign per-diem amount (EUR)
 */
public record ForeignPerDiemDto(Long id, int year, String country, BigDecimal amount) {
}

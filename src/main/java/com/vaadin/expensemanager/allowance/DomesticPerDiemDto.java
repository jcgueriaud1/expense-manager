package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * Immutable read model of a {@link DomesticPerDiemRate} for the UI and the
 * calculator (ADR-0003).
 *
 * @param id                 persistent id
 * @param year               the year this rate applies to
 * @param fullDayAmount      full-day per-diem amount (EUR)
 * @param partialDayAmount   partial-day per-diem amount (EUR)
 * @param fullDayMinHours    hours a trip must exceed to earn the full per-diem
 * @param partialDayMinHours hours a trip must exceed to earn the partial per-diem
 */
public record DomesticPerDiemDto(Long id, int year, BigDecimal fullDayAmount,
        BigDecimal partialDayAmount, int fullDayMinHours, int partialDayMinHours) {
}

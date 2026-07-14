package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;

/**
 * The <strong>server-computed</strong> money breakdown a trip earns (Phase 4.3),
 * carried on its {@link TravelDto}. The client sends only trip inputs; this is
 * what the service fills in — either as a live preview
 * ({@link ExpenseReportService#previewDomesticTravel}) or read back off the
 * generated lines on load — so the dialog and the totals card can show and sum
 * the amounts without recomputing (ADR-0006, ADR-0010).
 *
 * <p>Each of the three tax-free allowances (per-diem, kilometre, meal) is a
 * gross amount with its explanation (the text written into the generated line's
 * comment). Parking is a VAT-bearing expense, so it also carries
 * {@link #parkingVatPercent} — the parking type's rate — letting the totals card
 * split it into Net/VAT live with the same {@code LineAmounts} maths the persisted
 * report uses. A zero amount means the trip earned nothing of that kind (no line).
 *
 * @param perDiem              tax-free per-diem, EUR scale 2 (0 → none)
 * @param perDiemExplanation   per-diem breakdown for the line comment, or {@code null}
 * @param kilometre            tax-free kilometre allowance, EUR scale 2 (0 → none)
 * @param kilometreExplanation kilometre breakdown for the line comment, or {@code null}
 * @param meal                 tax-free meal allowance, EUR scale 2 (0 → none)
 * @param mealExplanation      meal breakdown for the line comment, or {@code null}
 * @param parking              VAT-bearing parking expense, EUR scale 2 (0 → none)
 * @param parkingExplanation   parking breakdown for the line comment, or {@code null}
 * @param parkingVatPercent    the parking type's VAT rate (percent), for the live split
 */
public record TravelAllowances(BigDecimal perDiem, String perDiemExplanation,
        BigDecimal kilometre, String kilometreExplanation,
        BigDecimal meal, String mealExplanation,
        BigDecimal parking, String parkingExplanation, BigDecimal parkingVatPercent) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /** An all-zero breakdown for a trip whose outputs have not been computed yet. */
    public static TravelAllowances none() {
        return new TravelAllowances(ZERO, null, ZERO, null, ZERO, null, ZERO, null,
                ZERO);
    }

    /** The total tax-free allowance (per-diem + kilometre + meal), scale 2. */
    public BigDecimal taxFreeTotal() {
        return perDiem.add(kilometre).add(meal);
    }
}

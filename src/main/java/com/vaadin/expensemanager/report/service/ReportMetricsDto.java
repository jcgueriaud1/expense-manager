package com.vaadin.expensemanager.report.service;

import java.math.BigDecimal;

/**
 * The three at-a-glance aggregates above the My Reports list (issue #147,
 * ADR-0003) — "Needs you", "In flight", "Reimbursed &lt;year&gt;".
 *
 * <p>One record for all three cards, because they are one owner-scoped pass over
 * the same reports: splitting them into three service calls would mean three
 * scans and three chances for the numbers to disagree with each other.
 *
 * <p><strong>Zeroes, never nulls.</strong> A user with no reports gets
 * {@link #empty}: {@code 0} counts, {@code 0.00} totals, {@code 0} wait days, and
 * a real {@link #reimbursedYear}. A missing card reads as a broken page and a
 * {@code null} total as a bug; the zero is the honest answer.
 *
 * @param needsYouCount    how many reports await the owner — see
 *                         {@link ExpenseReportService#myMetrics()} for the
 *                         definition (drafts <em>and</em> rejected)
 * @param needsYouTotal    their combined total, EUR scale 2
 * @param rejectedCount    how many of {@link #needsYouCount} are rejected — a
 *                         <strong>breakdown of</strong> that figure, not an
 *                         addition to it
 * @param inFlightCount    how many reports are {@code SUBMITTED} and waiting
 * @param inFlightWaitDays whole days the <em>longest</em>-waiting submitted report
 *                         has been waiting, measured from its latest
 *                         {@code SUBMITTED} status change; {@code 0} when none
 * @param reimbursedYear   the calendar year the reimbursed figures cover — part of
 *                         the data so the "Reimbursed 2026" caption and the amount
 *                         under it can never disagree at a year boundary
 * @param reimbursedTotal  the combined total of the reports approved in
 *                         {@link #reimbursedYear}, EUR scale 2
 * @param approvedCount    how many reports were approved in {@link #reimbursedYear}
 */
public record ReportMetricsDto(int needsYouCount, BigDecimal needsYouTotal,
        int rejectedCount, int inFlightCount, long inFlightWaitDays,
        int reimbursedYear, BigDecimal reimbursedTotal, int approvedCount) {

    /** All three cards zeroed for {@code year} — the no-reports-yet answer. */
    public static ReportMetricsDto empty(int year) {
        var zero = BigDecimal.ZERO.setScale(2);
        return new ReportMetricsDto(0, zero, 0, 0, 0L, year, zero, 0);
    }
}

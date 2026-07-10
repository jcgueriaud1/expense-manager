package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.vaadin.expensemanager.report.domain.ReportStatus;

/**
 * Shared presentation helpers for the report views ({@link MyReportsView},
 * {@link ReportDetailView}) so the two don't duplicate status/money formatting.
 *
 * <p>Status is always rendered as <strong>text</strong>, never colour alone
 * (ADR-0020, no colour-only meaning).
 */
final class ReportViewSupport {

    private ReportViewSupport() {
    }

    /** Title-cased status label, e.g. {@code "Draft"} — text, never colour alone. */
    static String statusLabel(ReportStatus status) {
        var name = status.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /** EUR amount at scale 2, e.g. {@code "€0.00"} (ADR-0010). */
    static String formatEur(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

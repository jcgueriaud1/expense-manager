package com.vaadin.expensemanager.report.prototype;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PROTOTYPE — throwaway in-memory stub data + money maths for the Phase 2
 * report-detail UI prototype (issue #3, finding F-004). No persistence, no
 * services, no validation beyond what makes the screen runnable. Delete when
 * the line-editor design question is answered.
 *
 * <p>The seed VAT rates and expense-type defaults mirror the PRD's Finnish 2026
 * figures so the totals feel real; they are NOT authoritative here.
 */
final class PrototypeModel {

    private PrototypeModel() {
    }

    // --- Reference data (seeded, mirrors PRD 2026 Finnish figures) ---

    record VatRate(String id, BigDecimal percent) {
        String label() {
            return percent().stripTrailingZeros().toPlainString() + " %";
        }

        /** VAT fraction, e.g. 25.5 % -> 0.255. */
        BigDecimal fraction() {
            return percent().movePointLeft(2);
        }
    }

    record ExpenseType(String id, String name, VatRate defaultRate) {
    }

    static final VatRate VAT_255 = new VatRate("v255", new BigDecimal("25.5"));
    static final VatRate VAT_135 = new VatRate("v135", new BigDecimal("13.5"));
    static final VatRate VAT_10 = new VatRate("v10", new BigDecimal("10"));
    static final VatRate VAT_0 = new VatRate("v0", new BigDecimal("0"));

    static final List<VatRate> VAT_RATES = List.of(VAT_255, VAT_135, VAT_10, VAT_0);

    static final List<ExpenseType> EXPENSE_TYPES = List.of(
            new ExpenseType("t1", "Travel allowance", VAT_0),
            new ExpenseType("t2", "Taxi / transport", VAT_135),
            new ExpenseType("t3", "Accommodation", VAT_135),
            new ExpenseType("t4", "Restaurant / meals", VAT_135),
            new ExpenseType("t5", "Parking / supplies / goods", VAT_255),
            new ExpenseType("t6", "Publications", VAT_10));

    // --- Mutable working copy of a line (prototype only) ---

    private static final AtomicLong IDS = new AtomicLong(1000);

    static final class LineDraft {
        final long id = IDS.incrementAndGet();
        ExpenseType expenseType;
        BigDecimal amount; // gross, what the user paid
        VatRate vatRate;
        String comment;

        LineDraft() {
        }

        LineDraft(ExpenseType type, String amount, String comment) {
            this.expenseType = type;
            this.amount = new BigDecimal(amount);
            this.vatRate = type.defaultRate();
            this.comment = comment;
        }

        boolean isComplete() {
            return expenseType != null && vatRate != null
                    && amount != null && amount.signum() != 0;
        }
    }

    /** A transient report working copy — never persisted in the prototype. */
    static final class ReportDraft {
        LocalDate reportDate = LocalDate.now();
        String additionalInformation = "Helsinki client visit, June 2026";
        String status = "DRAFT";
        final List<LineDraft> lines = new ArrayList<>();
    }

    static ReportDraft seedReport() {
        var r = new ReportDraft();
        r.lines.add(new LineDraft(EXPENSE_TYPES.get(1), "48.60", "Airport → hotel"));
        r.lines.add(new LineDraft(EXPENSE_TYPES.get(2), "312.00", "2 nights, Hotel Kämp"));
        r.lines.add(new LineDraft(EXPENSE_TYPES.get(3), "86.40", "Dinner with client"));
        r.lines.add(new LineDraft(EXPENSE_TYPES.get(4), "24.00", "Airport parking"));
        return r;
    }

    // --- Money maths (ADR-0010: BigDecimal, scale 2, HALF_UP) ---

    record Totals(BigDecimal net, BigDecimal vat, BigDecimal gross) {
    }

    static Totals lineTotals(LineDraft line) {
        if (line.amount == null || line.vatRate == null) {
            return new Totals(z(), z(), z());
        }
        BigDecimal gross = line.amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.divide(
                BigDecimal.ONE.add(line.vatRate.fraction()), 2, RoundingMode.HALF_UP);
        BigDecimal vat = gross.subtract(net);
        return new Totals(net, vat, gross);
    }

    /** Sum per-line then total, to avoid rounding drift across mixed rates. */
    static Totals reportTotals(List<LineDraft> lines) {
        BigDecimal net = z(), vat = z(), gross = z();
        for (LineDraft l : lines) {
            var t = lineTotals(l);
            net = net.add(t.net());
            vat = vat.add(t.vat());
            gross = gross.add(t.gross());
        }
        return new Totals(net, vat, gross);
    }

    static String euro(BigDecimal v) {
        return "€" + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal z() {
        return BigDecimal.ZERO.setScale(2);
    }
}

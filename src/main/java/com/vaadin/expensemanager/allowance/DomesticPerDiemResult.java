package com.vaadin.expensemanager.allowance;

import java.math.BigDecimal;

/**
 * The outcome of a domestic per-diem calculation (Phase 4.2, ADR-0006) — the
 * Verohallinto full/partial day split, as <strong>two</strong> honest
 * {@code days × per-day rate} components (ADR-0023, issue #124).
 *
 * <p>Each component becomes its own generated line — {@code PER_DIEM_FULL} and
 * {@code PER_DIEM_PARTIAL} — so a 30 h trip reads "1 × €54.00" plus "1 × €25.00"
 * rather than one €79.00 lump. The full+partial mix per trip and the total euros are
 * the same as before the split; a {@linkplain PerDiemComponent#isEarned() component
 * that earned nothing} (no full days, or a leftover under the partial threshold)
 * simply generates no line, and a trip that is too short or not eligible earns
 * {@linkplain #none() neither}.
 *
 * <p>Free-meal halving is applied to each component's <em>unit price</em>, so the
 * day counts stay honest and each line rounds to cents independently (ADR-0023's
 * accepted per-line rounding). The {@link #amount()} — the trip's whole per-diem —
 * is derived by summing the components, never stored.
 *
 * @param full    the whole 24-hour periods valued at the full-day rate
 * @param partial the leftover valued at the partial-day rate (at most one day)
 */
public record DomesticPerDiemResult(PerDiemComponent full, PerDiemComponent partial) {

    /** The trip earned no per-diem at all — too short, or not eligible. */
    static DomesticPerDiemResult none() {
        return new DomesticPerDiemResult(PerDiemComponent.none(),
                PerDiemComponent.none());
    }

    /** The trip's whole per-diem, EUR scale 2 — the two components summed. */
    public BigDecimal amount() {
        return full.amount().add(partial.amount());
    }

    /** Whether the trip earned any allowance (drives whether lines are generated). */
    public boolean hasAllowance() {
        return amount().signum() != 0;
    }

    /** Number of whole 24-hour periods that earned a full day. */
    public int fullDays() {
        return full.days();
    }

    /** {@code 1} if the leftover earned a partial day, else {@code 0}. */
    public int partialDays() {
        return partial.days();
    }
}

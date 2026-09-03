package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
import com.vaadin.expensemanager.report.domain.LineAmounts;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;

/**
 * Shared presentation helpers for the report views ({@link MyReportsView},
 * {@link ReportDetailView}) so the two don't duplicate status/money formatting.
 *
 * <p>Status is always rendered as <strong>text</strong>, never colour alone
 * (ADR-0020, no colour-only meaning): {@link #statusBadge} carries the label
 * text and only <em>adds</em> colour via the Vaadin badge theme.
 */
public final class ReportViewSupport {

    private ReportViewSupport() {
    }

    /** Title-cased status label, e.g. {@code "Draft"} — text, never colour alone. */
    public static String statusLabel(ReportStatus status) {
        var name = status.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /**
     * The status as the official Vaadin {@link Badge} (since 25.1, styled under
     * Aura). The label text always renders, so meaning never rides on colour
     * alone (ADR-0020); the colour only reinforces it. The design draws all four
     * as the same <em>tinted</em> pill — a soft fill, a matching border and
     * saturated text — never a solid one, so no status takes {@code filled}:
     * approved is {@code success} (green), rejected {@code error} (red), and
     * submitted the plain default badge, which under Aura is the accent tint the
     * design draws it in. All are {@code small} to sit compactly beside a title.
     *
     * <p>Draft is the fourth tint, grey, and it has no theme variant: Aura's only
     * neutral badge variant is {@code contrast}, which is Lumo-only and silently
     * does nothing here. So it scopes the <em>accent</em> to neutral for that one
     * element with Aura's stock {@code aura-accent-neutral} class, and the default
     * badge styling then derives the grey fill, border and text from it — the same
     * mechanism the theme uses on buttons (F-067), applied per element because
     * only this status wants it.
     */
    public static Badge statusBadge(ReportStatus status) {
        var badge = new Badge(statusLabel(status));
        badge.addThemeVariants(BadgeVariant.SMALL);
        switch (status) {
            case APPROVED -> badge.addThemeVariants(BadgeVariant.SUCCESS);
            case REJECTED -> badge.addThemeVariants(BadgeVariant.ERROR);
            case SUBMITTED -> { /* the default badge — Aura tints it with the accent */ }
            case DRAFT -> badge.addClassName("aura-accent-neutral");
        }
        return badge;
    }

    /**
     * A friendly label for a generated (travel-owned) line kind (Phase 4.3). The two
     * per-diem kinds name the day they price — the card then reads
     * "days × per-day rate = gross" (issue #124).
     *
     * <p>The spelling lives on the kind itself, because the comment a Quantity
     * Override persists uses the same one (ADR-0024): the screen and the database
     * must not name the same line differently.
     */
    static String generatedLineLabel(GeneratedLineKind kind) {
        return kind.label();
    }

    /**
     * A line's gross for display — unit price × quantity (ADR-0023), via the same
     * domain helper the persisted line uses, so the editor's live "Line total", the
     * card, and the saved figure can never disagree. A half-filled line (either
     * part still empty) reads as {@code 0.00}.
     */
    static BigDecimal lineGross(BigDecimal unitPrice, BigDecimal quantity) {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return LineAmounts.grossOf(unitPrice, quantity);
    }

    /** Quantity without trailing zeros, e.g. {@code "3"} or {@code "12.5"}. */
    static String formatQuantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    /** EUR amount at scale 2, e.g. {@code "€0.00"} (ADR-0010). */
    public static String formatEur(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** VAT rate percent without trailing zeros, e.g. {@code "25.5 %"}. */
    static String formatPercent(BigDecimal percent) {
        return percent.stripTrailingZeros().toPlainString() + " %";
    }

    private static final DateTimeFormatter TRIP_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TRIP_DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TRIP_TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());

    /**
     * A status-history / rejection timestamp, e.g. {@code "14 Jul 2026, 08:00"},
     * rendered in the app's local zone (the transition {@link Instant} is a UTC
     * fact; the display is local, like the trip ranges).
     */
    public static String formatTimestamp(Instant instant) {
        return instant == null ? "" : TIMESTAMP.format(instant);
    }

    /**
     * A trip's <em>date</em> range for a report card's trip row, e.g.
     * {@code "25 Aug 2026 – 25 Aug 2026"} (en dash, spaced). Times are omitted —
     * the list summarises, the detail view's
     * {@link #formatTripRange(LocalDateTime, LocalDateTime) range} carries the
     * clock.
     *
     * <p>A single-day trip <strong>repeats</strong> the date rather than
     * collapsing to one: the design draws it that way, and a half-range reads as
     * missing data. Returns an empty string if either endpoint is missing.
     */
    public static String formatTripDates(LocalDateTime departure,
            LocalDateTime returnAt) {
        if (departure == null || returnAt == null) {
            return "";
        }
        return TRIP_DATE.format(departure) + " – " + TRIP_DATE.format(returnAt);
    }

    /**
     * A trip's date range for a Trip & Allowance card, e.g.
     * {@code "1 Jul 2026, 08:00 – 19:00"} for a same-day trip or
     * {@code "1 Jul 2026, 08:00 – 3 Jul 2026, 10:00"} across days. Returns an empty
     * string if either endpoint is missing.
     */
    static String formatTripRange(LocalDateTime departure, LocalDateTime returnAt) {
        if (departure == null || returnAt == null) {
            return "";
        }
        String start = TRIP_DATE_TIME.format(departure);
        String end = departure.toLocalDate().equals(returnAt.toLocalDate())
                ? TRIP_TIME.format(returnAt) : TRIP_DATE_TIME.format(returnAt);
        return start + " – " + end;
    }
}

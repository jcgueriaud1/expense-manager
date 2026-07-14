package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.vaadin.expensemanager.report.domain.GeneratedLineKind;
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
final class ReportViewSupport {

    /**
     * Aura palette colours cycled through to give each expense type a stable
     * colour dot on the detail line cards (the mockup's category swatch). Chosen
     * from the documented saturated palette so both colour schemes stay legible.
     */
    private static final String[] CATEGORY_COLORS = {
            "--aura-blue", "--aura-green", "--aura-orange",
            "--aura-purple", "--aura-red", "--aura-yellow"
    };

    private ReportViewSupport() {
    }

    /** Title-cased status label, e.g. {@code "Draft"} — text, never colour alone. */
    static String statusLabel(ReportStatus status) {
        var name = status.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /**
     * The status as the official Vaadin {@link Badge} (since 25.1, styled under
     * Aura). The label text always renders, so meaning never rides on colour
     * alone (ADR-0020); the variant only reinforces it: approved is
     * {@code success} (green), rejected {@code error} (red), submitted a
     * {@code filled} (solid) neutral badge to read as "handed off", and a draft
     * the plain default badge. All are {@code small} to sit compactly beside a
     * title. (Aura has no accent/primary badge variant — {@code contrast} is
     * Lumo-only — so submitted uses the filled neutral rather than a blue tint.)
     */
    static Badge statusBadge(ReportStatus status) {
        var badge = new Badge(statusLabel(status));
        badge.addThemeVariants(BadgeVariant.SMALL);
        switch (status) {
            case SUBMITTED -> badge.addThemeVariants(BadgeVariant.FILLED);
            case APPROVED -> badge.addThemeVariants(BadgeVariant.SUCCESS);
            case REJECTED -> badge.addThemeVariants(BadgeVariant.ERROR);
            case DRAFT -> { /* the plain default (neutral) badge */ }
        }
        return badge;
    }

    /**
     * A stable Aura palette colour for an expense type's swatch dot, derived from
     * its name so the same type always reads the same colour within a report.
     * Falls back to a neutral border colour for a not-yet-chosen type.
     */
    static String categoryColor(String expenseTypeName) {
        if (expenseTypeName == null || expenseTypeName.isBlank()) {
            return "var(--vaadin-border-color)";
        }
        int index = Math.floorMod(expenseTypeName.hashCode(), CATEGORY_COLORS.length);
        return "var(" + CATEGORY_COLORS[index] + ")";
    }

    /** A friendly label for a generated (travel-owned) line kind (Phase 4.3). */
    static String generatedLineLabel(GeneratedLineKind kind) {
        return switch (kind) {
            case PER_DIEM -> "Per diem allowance";
            case KILOMETRE -> "Kilometre allowance";
            case MEAL -> "Meal allowance";
            case PARKING -> "Parking";
        };
    }

    /** EUR amount at scale 2, e.g. {@code "€0.00"} (ADR-0010). */
    static String formatEur(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** VAT rate percent without trailing zeros, e.g. {@code "25.5 %"}. */
    static String formatPercent(BigDecimal percent) {
        return percent.stripTrailingZeros().toPlainString() + " %";
    }

    private static final DateTimeFormatter TRIP_DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TRIP_TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

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

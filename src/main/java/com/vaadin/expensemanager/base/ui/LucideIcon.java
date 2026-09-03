package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.icon.SvgIcon;

/**
 * The app's icon set — Lucide, delivered from one vendored sprite (ADR-0026).
 *
 * <p><strong>Every glyph in the app comes from here.</strong> The two sets Vaadin
 * 25.2 bundles — Vaadin Icons and Lumo Icons — are Lumo-era: heavier, filled glyphs
 * on a 16px grid, against Lucide's 24px 2px-stroke outlines. Mixing the two reads as
 * two products, and the Figma design draws Lucide, so this enum is the only door —
 * and the reason a grep for the old sets across {@code src/main/java} now returns
 * nothing (#163). Note that neither of those enums is <em>broken</em>; both are
 * defined and supported, which is the test {@code CLAUDE.md} sets. They are simply
 * the wrong set for this design.
 *
 * <p>Constants are named after the <em>glyph</em>, never after a use — {@link #PENCIL}
 * rather than {@code EDIT}. A glyph gets reused across unrelated actions, and a
 * use-named constant makes the second caller either lie or add a duplicate.
 *
 * <h2>Sizing</h2>
 *
 * Three role sizes, from the design and settled in
 * {@code docs/design/foundations/iconography.md}: {@link #SIZE_S} 16 inline beside
 * small text, {@link #SIZE_M} 20 in a button slot or field prefix, {@link #SIZE_L}
 * 24 standalone in a layout the app drew.
 *
 * <p>{@link #create()} applies {@link #SIZE_M}, the dominant case; {@link
 * #create(String)} overrides it. Note what that gives up: unsized, the base styles
 * size an icon at {@code 1lh} — one line height of its own font size, which on a
 * 14px/20px button label is <em>also</em> 20px, so the design's bound
 * {@code Button icon size} and the framework already agreed. Setting it explicitly
 * buys one greppable scale and costs {@code 1lh}'s contextual scaling, so an icon
 * dropped into small text no longer shrinks with it. That trade was taken
 * deliberately; it is the first thing to revisit if icons look oversized somewhere
 * dense.
 *
 * <p><strong>Stroke width is not set here.</strong> It comes from
 * {@code --vaadin-icon-stroke-width} in {@code aura-theme.css}, and reaches the glyph
 * only because no {@code <symbol>} in the sprite declares {@code stroke-width} of its
 * own — an element's own presentation attribute would beat the inherited value.
 *
 * <h2>Why an enum over the raw {@code SvgIcon}</h2>
 *
 * The sprite is addressed by string, and a typo in a string is a silently blank
 * icon: the {@code <use>} reference simply resolves to nothing, so it renders an
 * empty box and never errors. Behind the enum the glyph names are checked once,
 * here, by {@code LucideIconTest} against the sprite's actual symbol ids.
 *
 * <p>This deliberately does not implement {@code IconFactory} — that interface's
 * {@code create()} returns the font-icon {@link com.vaadin.flow.component.icon.Icon},
 * which is the Lumo type this set exists to get away from.
 */
public enum LucideIcon {

    ARCHIVE("archive"),
    ARROW_DOWN("arrow-down"),
    ARROW_LEFT("arrow-left"),
    ARROW_UP("arrow-up"),
    BED("bed"),
    CAR_TAXI_FRONT("car-taxi-front"),
    COPY("copy"),
    ELLIPSIS_VERTICAL("ellipsis-vertical"),
    FILE_TEXT("file-text"),
    INBOX("inbox"),
    MAP_PIN("map-pin"),
    PAPERCLIP("paperclip"),
    PENCIL("pencil"),
    PLANE("plane"),
    PLUS("plus"),
    SEARCH("search"),
    TRASH_2("trash-2"),
    TRIANGLE_ALERT("triangle-alert"),
    UPLOAD("upload"),
    UTENSILS("utensils");

    /**
     * The vendored sprite, relative like every other static resource the app
     * serves out of {@code META-INF/resources} (compare {@code images/logo.svg}).
     */
    public static final String SPRITE = "icons/lucide.svg";

    /** Inline beside small text — the design's 16px trip-row glyph. */
    public static final String SIZE_S = "var(--em-icon-size-s)";

    /** A button slot or field prefix — the design's bound {@code Button icon size}. */
    public static final String SIZE_M = "var(--em-icon-size-m)";

    /** Standalone in a layout the app drew — the design's 24px section chevron. */
    public static final String SIZE_L = "var(--em-icon-size-l)";

    private final String glyph;

    LucideIcon(String glyph) {
        this.glyph = glyph;
    }

    /** The upstream Lucide name, and the {@code <symbol>} id in the sprite. */
    public String glyph() {
        return glyph;
    }

    /**
     * A fresh icon at {@link #SIZE_M}, the button-slot and field-prefix size.
     *
     * <p>Always a new instance: a {@link com.vaadin.flow.component.Component} can
     * only be attached in one place, so a shared constant would move the glyph
     * rather than draw it twice.
     */
    public SvgIcon create() {
        return create(SIZE_M);
    }

    /**
     * A fresh icon at an explicit size.
     *
     * @param size one of {@link #SIZE_S}, {@link #SIZE_M}, {@link #SIZE_L} — or a
     *             raw CSS length only where the design has no role size, which today
     *             is {@link EmptyState}'s {@code 3em} alone: that one is relative to
     *             the heading beneath it, so the glyph keeps its proportion if the
     *             type scale moves
     */
    public SvgIcon create(String size) {
        var icon = new SvgIcon(SPRITE, glyph);
        icon.setSize(size);
        return icon;
    }

    /**
     * The constant for a stored {@linkplain #glyph() glyph name}, or empty when the
     * name is absent or names no glyph in this set.
     *
     * <p>This is the door for glyph names that live in the <em>database</em> —
     * today {@code ExpenseType.icon} (ADR-0026). It returns an {@link Optional}
     * rather than throwing because the stored value is admin-editable data, and a
     * type whose glyph was renamed out of the sprite by an upgrade must render
     * without a glyph, not break the report it appears on. Nothing rests on the
     * glyph: every row names its type in text beside it (ADR-0020).
     */
    public static java.util.Optional<LucideIcon> ofGlyph(String glyph) {
        if (glyph == null || glyph.isBlank()) {
            return java.util.Optional.empty();
        }
        for (LucideIcon icon : values()) {
            if (icon.glyph.equals(glyph)) {
                return java.util.Optional.of(icon);
            }
        }
        return java.util.Optional.empty();
    }
}

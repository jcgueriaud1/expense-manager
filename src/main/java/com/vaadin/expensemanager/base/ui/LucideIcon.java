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
 * {@link #create()} leaves the size to the context, which is what a button or a
 * field prefix wants: Aura already sizes an icon in every slot it owns, and an
 * explicit size there fights the theme. Pass a size only for an icon the theme has
 * no opinion about — a standalone glyph in a layout the app drew itself, such as
 * {@link EmptyState}'s.
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
    UPLOAD("upload");

    /**
     * The vendored sprite, relative like every other static resource the app
     * serves out of {@code META-INF/resources} (compare {@code images/logo.svg}).
     */
    public static final String SPRITE = "icons/lucide.svg";

    private final String glyph;

    LucideIcon(String glyph) {
        this.glyph = glyph;
    }

    /** The upstream Lucide name, and the {@code <symbol>} id in the sprite. */
    public String glyph() {
        return glyph;
    }

    /**
     * A fresh icon, sized by whatever slot it lands in.
     *
     * <p>Always a new instance: a {@link com.vaadin.flow.component.Component} can
     * only be attached in one place, so a shared constant would move the glyph
     * rather than draw it twice.
     */
    public SvgIcon create() {
        return new SvgIcon(SPRITE, glyph);
    }

    /**
     * A fresh icon at an explicit size, for the slots the theme does not size.
     *
     * @param size any CSS length — prefer an {@code em} so the glyph tracks the
     *             surrounding text, or a {@code --vaadin-icon-size-*} token
     */
    public SvgIcon create(String size) {
        var icon = create();
        icon.setSize(size);
        return icon;
    }
}

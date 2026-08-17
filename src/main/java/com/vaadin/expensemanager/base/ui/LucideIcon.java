package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.icon.SvgIcon;

import java.util.Locale;

/**
 * The application's icon set: <a href="https://lucide.dev">Lucide</a>.
 *
 * <p>Lucide is not one of the collections Vaadin bundles (it ships Vaadin Icons
 * and Lumo Icons), so the icons this app uses are vendored as individual SVG
 * files under {@code META-INF/resources/icons/lucide/}, licensed ISC — with an
 * MIT-licensed Feather-derived subset — per the {@code LICENSE} file beside
 * them. Keeping them as static resources means no npm dependency and no
 * frontend build: this app runs on Vaadin's prebuilt bundle.
 *
 * <p>This enum mirrors {@code VaadinIcon}'s ergonomics — {@code
 * LucideIcon.PLUS.create()} — so call sites read the same as before. Each
 * constant's name is the Lucide slug in upper snake case, and {@link #path()}
 * derives the file name from it, so adding an icon is: drop {@code
 * <slug>.svg} into that folder and add the matching constant.
 *
 * <p><strong>Rendering.</strong> {@link SvgIcon} fetches the file and inlines
 * it, copying the source SVG's {@code viewBox}, {@code fill}, {@code stroke}
 * and {@code stroke-width} onto the rendered icon — which is what makes
 * Lucide's stroke-drawn icons come out right and inherit the surrounding text
 * colour through {@code stroke="currentColor"}. (A sprite reference —
 * {@code sprite.svg#plus} — copies none of those, so Lucide icons render
 * wrongly that way. Hence one file per icon.) A consequence of being
 * stroke-drawn rather than filled: {@code SvgIcon.setColor} sets {@code fill}
 * and so does nothing here — recolour with the CSS {@code color} property
 * instead.
 *
 * <p>Icons referenced from {@code @Menu(icon = …)} annotations can't come from
 * this enum — an annotation needs a compile-time constant — so those carry the
 * same path as a literal, e.g. {@code "icons/lucide/inbox.svg"}. {@code
 * MenuIconsExistTest} guards that the two stay in step.
 */
public enum LucideIcon {

    ARROW_DOWN,
    ARROW_LEFT,
    ARROW_UP,
    COINS,
    FILE_TEXT,
    INBOX,
    LAYOUT_DASHBOARD,
    LOG_OUT,
    MAP_PIN_OFF,
    PAPERCLIP,
    PERCENT,
    PLANE,
    PLUS,
    RECEIPT,
    ROTATE_CCW_CLOCK,
    SEARCH,
    SQUARE_PEN,
    SUN_MOON,
    TAGS,
    TRASH_2,
    TRIANGLE_ALERT,
    UPLOAD,
    USERS;

    /** Folder the vendored SVGs are served from, relative to the web root. */
    public static final String FOLDER = "icons/lucide/";

    /**
     * A new icon component for this glyph. Fresh instance per call: a component
     * belongs to one place in the UI tree.
     */
    public SvgIcon create() {
        return new SvgIcon(path());
    }

    /**
     * Path to the vendored SVG, relative to the web root — no leading slash, so
     * it still resolves when the app is served under a context path.
     */
    public String path() {
        return FOLDER + name().toLowerCase(Locale.ROOT).replace('_', '-') + ".svg";
    }
}

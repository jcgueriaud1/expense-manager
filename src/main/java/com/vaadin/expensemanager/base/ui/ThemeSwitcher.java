package com.vaadin.expensemanager.base.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.page.WebStorage;

/**
 * The colour-scheme choice — follow the OS ("System"), or force "Light" / "Dark".
 *
 * <p><strong>A menu group, not a component.</strong> It used to be a
 * {@code MenuBar} of its own in the {@code AppLayout} navbar. The designed
 * header (#146) has no room for a second control beside the avatar, so the three
 * choices now install into the avatar menu instead (#145 decision 1) — a menu
 * inside a menu is what a nested {@code MenuBar} would have been, without the
 * second trigger. Dark mode is a requirement, so this could not simply be
 * dropped when the drawer went.
 *
 * <p>The scheme is applied with Flow's {@link com.vaadin.flow.component.page.Page#setColorScheme
 * Page.setColorScheme}, which sets an inline {@code color-scheme} on the document
 * root. That wins over the {@code html { color-scheme: light dark }} default that
 * the Aura theme establishes (see {@code META-INF/resources/aura-theme.css}).
 * Because Aura resolves every surface colour through the CSS {@code light-dark()}
 * function keyed off that property, flipping it re-themes the whole UI live — no
 * page reload. "System" resets to {@link ColorScheme.Value#NORMAL}, clearing the
 * override so the stylesheet's {@code light dark} (the OS preference) applies again.
 *
 * <p>The choice is persisted per-browser in {@code localStorage} via
 * {@link WebStorage}, under {@link #STORAGE_KEY}. An early inline script in the
 * page head (see {@code Application#configurePage}) re-applies it before first
 * paint, so a reload never flashes the wrong scheme.
 */
public final class ThemeSwitcher {

    /** localStorage key shared with the early bootstrap script in the page head. */
    public static final String STORAGE_KEY = "expense-manager.color-scheme";

    /** The label of the group this installs, and what a test looks for. */
    public static final String LABEL = "Colour theme";

    private final Map<ColorScheme.Value, MenuItem> choices = new LinkedHashMap<>();

    /**
     * Adds a checkable <em>Colour theme</em> group — System, Light, Dark — to
     * {@code parent}, and returns the item that opens it.
     */
    public static MenuItem addTo(SubMenu parent) {
        return new ThemeSwitcher(parent).trigger;
    }

    private final MenuItem trigger;

    private ThemeSwitcher(SubMenu parent) {
        trigger = parent.addItem(LABEL);

        SubMenu menu = trigger.getSubMenu();
        addChoice(menu, "System", ColorScheme.Value.NORMAL);
        addChoice(menu, "Light", ColorScheme.Value.LIGHT);
        addChoice(menu, "Dark", ColorScheme.Value.DARK);

        // Reflect the persisted choice once the client round-trips it back. The
        // head bootstrap script has already applied it visually before paint.
        WebStorage.getItem(STORAGE_KEY,
                stored -> markChecked(ColorScheme.Value.fromString(stored)));
    }

    private void addChoice(SubMenu menu, String label, ColorScheme.Value scheme) {
        var item = menu.addItem(label, event -> apply(scheme));
        item.setCheckable(true);
        choices.put(scheme, item);
    }

    private void apply(ColorScheme.Value scheme) {
        markChecked(scheme);
        UI.getCurrent().getPage().setColorScheme(scheme);
        if (scheme == ColorScheme.Value.NORMAL) {
            WebStorage.removeItem(STORAGE_KEY);
        } else {
            WebStorage.setItem(STORAGE_KEY, scheme.getValue());
        }
    }

    /** Ticks the item matching {@code scheme}; unrecognised ⇒ System (NORMAL). */
    private void markChecked(ColorScheme.Value scheme) {
        var current = scheme == null ? ColorScheme.Value.NORMAL : scheme;
        choices.forEach((value, item) -> item.setChecked(value == current));
    }
}

package com.vaadin.expensemanager.base.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.page.WebStorage;

/**
 * The colour-scheme choice offered in the account menu: follow the OS
 * ("System"), or force "Light" / "Dark".
 *
 * <p>The scheme is applied with Flow's {@link com.vaadin.flow.component.page.Page#setColorScheme
 * Page.setColorScheme}, which sets an inline {@code color-scheme} on the document
 * root. That wins over the {@code html { color-scheme: light dark }} default that
 * the Aura theme establishes (see {@code META-INF/resources/vaadin-blue-inter.css}).
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

    private final Map<ColorScheme.Value, MenuItem> choices = new LinkedHashMap<>();

    /**
     * Adds System / Light / Dark as checkable items of an existing menu.
     *
     * <p>The switcher is a set of menu items rather than a control of its own:
     * the shell's one menu hangs off the account avatar (ADR-0025), so this
     * populates a submenu there instead of adding a second bar to the header.
     */
    public static void addChoicesTo(SubMenu menu) {
        new ThemeSwitcher(menu);
    }

    private ThemeSwitcher(SubMenu menu) {
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

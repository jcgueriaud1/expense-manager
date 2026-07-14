package com.vaadin.expensemanager.base.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;

/**
 * Navbar control that lets the user override the app's colour scheme: follow the
 * OS ("System"), or force "Light" / "Dark".
 *
 * <p>The scheme is applied by setting an inline {@code color-scheme} on the
 * document root, which wins over the {@code html { color-scheme: light dark }}
 * default that the Aura theme establishes (see
 * {@code META-INF/resources/vaadin-blue-inter.css}). Because Aura resolves every
 * surface colour through the CSS {@code light-dark()} function keyed off that
 * property, flipping it re-themes the whole UI live — no page reload. "System"
 * clears the override so the stylesheet's {@code light dark} (the OS preference)
 * applies again.
 *
 * <p>The choice is persisted per-browser in {@code localStorage} under
 * {@link #STORAGE_KEY}. An early inline script in the page head (see
 * {@code Application#configurePage}) re-applies it before first paint, so a
 * reload never flashes the wrong scheme.
 */
public class ThemeSwitcher extends MenuBar {

    /** localStorage key shared with the early bootstrap script in the page head. */
    public static final String STORAGE_KEY = "expense-manager.color-scheme";

    /** Sentinel scheme value meaning "follow the OS" — clears any override. */
    private static final String SYSTEM = "";

    private final Map<String, MenuItem> choices = new LinkedHashMap<>();

    public ThemeSwitcher() {
        var trigger = addItem(new Icon(VaadinIcon.ADJUST));
        trigger.setAriaLabel("Change colour theme");

        SubMenu menu = trigger.getSubMenu();
        addChoice(menu, "System", SYSTEM);
        addChoice(menu, "Light", "light");
        addChoice(menu, "Dark", "dark");

        // Reflect the persisted choice once the client round-trips it back.
        getElement().executeJs("return localStorage.getItem($0)", STORAGE_KEY)
                .then(String.class, this::markChecked);
    }

    private void addChoice(SubMenu menu, String label, String scheme) {
        var item = menu.addItem(label, event -> apply(scheme));
        item.setCheckable(true);
        choices.put(scheme, item);
    }

    private void apply(String scheme) {
        markChecked(scheme);
        if (SYSTEM.equals(scheme)) {
            getElement().executeJs(
                    "document.documentElement.style.removeProperty('color-scheme');"
                            + "localStorage.removeItem($0)",
                    STORAGE_KEY);
        } else {
            getElement().executeJs(
                    "document.documentElement.style.colorScheme=$0;"
                            + "localStorage.setItem($1,$0)",
                    scheme, STORAGE_KEY);
        }
    }

    /** Ticks the item matching {@code scheme}; {@code null}/blank ⇒ System. */
    private void markChecked(String scheme) {
        var current = scheme == null ? SYSTEM : scheme;
        choices.forEach((value, item) -> item.setChecked(value.equals(current)));
    }
}

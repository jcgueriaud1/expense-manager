package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;

/**
 * The vertical-ellipsis (⋮) overflow menu that carries one repeating row's
 * actions — a {@code Grid} row, a {@code .rate-list-card} row. The spec is
 * {@code docs/design/components/row-action-menu.md}.
 *
 * <p><strong>A {@link MenuBar} with a single top-level item, not a
 * {@code Button} plus a {@code ContextMenu}.</strong> Vaadin's own guidance is
 * explicit that a one-item menu bar <em>is</em> a drop-down button, and gives
 * better keyboard and assistive-technology behaviour than the pair. It is also
 * not a {@code GridContextMenu}: that is right-click only, and a context menu
 * must never be the only route to a task.
 *
 * <p><strong>The trigger is named after its row, and every action is
 * text-labelled.</strong> An icon-only button with no accessible name announces
 * as "button" and nothing else, and "Actions" alone is useless on a page holding
 * fourteen of them — so the row's own subject is required at construction. Text
 * on the items is not a style choice either: once an action is behind a click it
 * is the only thing that makes it discoverable, which is ADR-0020's rule that
 * meaning never rests on an icon or a colour alone.
 *
 * <p>No property is overridden. {@code MenuBarVariant.LUMO_ICON} and
 * {@code LUMO_TERTIARY_INLINE} would be the obvious way to tighten the trigger's
 * padding and both are Lumo-only — accepted and silently inert under Aura
 * (F-013, F-017) — so the {@code row-action-menu} class in {@code styles.css}
 * carries anything geometry needs.
 *
 * <p>A row whose actions are all unavailable renders <strong>no</strong> menu
 * rather than a disabled trigger, the rule {@code expense-line-card} already
 * follows: build one only when there is something in it.
 */
public class RowActionMenu extends MenuBar {

    private final MenuItem trigger;

    /**
     * @param rowLabel what this row is — a country, a rate name. It becomes the
     *                 trigger's accessible name ("Actions for Sweden"), so it
     *                 has to identify the row and not the action
     */
    public RowActionMenu(String rowLabel) {
        addClassName("row-action-menu");
        // The spec's default state is "no fill", and Aura's stock menu-bar button
        // is a surface with a border and a shadow. TERTIARY is what removes it —
        // the theme-agnostic constant, and Aura-supported, unlike the LUMO_ICON /
        // LUMO_TERTIARY_INLINE pair. It also makes the glyph accent blue, which is
        // what TERTIARY means in this app (button.md) rather than the design's
        // near-black; that divergence is settled there and reported in #160.
        addThemeVariants(MenuBarVariant.TERTIARY);
        trigger = addItem(LucideIcon.ELLIPSIS_VERTICAL.create());
        trigger.getElement().setAttribute("aria-label", "Actions for " + rowLabel);
    }

    /**
     * Appends one text-labelled action to the menu.
     *
     * @return this, so a row's actions read as one chained statement
     */
    public RowActionMenu addAction(String text, Runnable action) {
        trigger.getSubMenu().addItem(text, event -> action.run());
        return this;
    }
}

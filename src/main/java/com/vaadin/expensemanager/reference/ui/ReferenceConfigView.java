package com.vaadin.expensemanager.reference.ui;

import java.util.List;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.AbstractIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Abstract base for the two ADMIN reference-data screens ({@link VatRateView},
 * {@link ExpenseTypeView}) — the shape they share: an {@code <h2>} heading, an
 * intro paragraph, a primary "Add" button that opens the editor, and a
 * {@link #grid}. Row-action buttons that both screens use identically — the
 * accessible icon button, the boundary-disabled reorder buttons, and the
 * text-status active toggle (ADR-0020) — are provided as {@code protected}
 * helpers.
 *
 * <p>Each subclass configures the grid's columns and its actions cell with the
 * plain Vaadin API and implements the two kind-specific hooks: {@link #fetchItems}
 * (the display-ordered rows) and {@link #openEditor} (build the form + an
 * {@link com.vaadin.expensemanager.base.ui.EditorDialog}). No configuration
 * object — the subclass owns its grid.
 *
 * @param <T> the grid row (DTO) type
 */
abstract class ReferenceConfigView<T> extends VerticalLayout {

    /** The screen's grid; the subclass adds its columns and actions cell. */
    protected final Grid<T> grid = new Grid<>();

    private List<T> items = List.of();

    protected ReferenceConfigView(String heading, String intro, String addButtonText) {
        setPadding(true);
        setSpacing(true);

        var addButton = new Button(addButtonText, LucideIcon.PLUS.create(),
                event -> openEditor(null));
        addButton.addThemeVariants(ButtonVariant.PRIMARY);

        var header = new HorizontalLayout(new H2(heading), addButton);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        add(header);

        if (intro != null) {
            add(new Paragraph(intro));
        }
        grid.setAllRowsVisible(true);
        add(grid);
    }

    /** The display-ordered rows to show (called on every {@link #refresh}). */
    protected abstract List<T> fetchItems();

    /** Opens the add ({@code existing == null}) or edit editor for a row. */
    protected abstract void openEditor(T existing);

    /** Reloads the grid from {@link #fetchItems} — call after any mutation. */
    protected void refresh() {
        items = fetchItems();
        grid.setItems(items);
    }

    /** The rows currently shown, in order — for reorder boundary checks. */
    protected List<T> currentItems() {
        return items;
    }

    /** Index of {@code item} among {@link #currentItems} (rows carry a unique id). */
    protected int indexOf(T item) {
        return items.indexOf(item);
    }

    /**
     * An accessible tertiary icon button.
     *
     * <p>The icon arrives built rather than as a collection member: typed to
     * {@link LucideIcon} this base would pin both subclasses to one icon set, which
     * is exactly the coupling #163 removed. {@link AbstractIcon} is the common
     * supertype of every icon Vaadin has, so a subclass can pass whatever it needs
     * without this class knowing where glyphs come from.
     */
    protected Button iconButton(AbstractIcon<?> icon, String ariaLabel, Runnable action) {
        var button = new Button(icon, event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel(ariaLabel);
        return button;
    }

    /** A reorder icon button, disabled at the list boundary. */
    protected Button reorderButton(AbstractIcon<?> icon, String ariaLabel, boolean enabled,
            Runnable action) {
        var button = iconButton(icon, ariaLabel, action);
        button.setEnabled(enabled);
        return button;
    }

    /** The Activate/Deactivate toggle (deactivate never deletes, ADR-0018). */
    protected Button activeToggle(boolean active, String subject, Runnable action) {
        var button = new Button(active ? "Deactivate" : "Activate", event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel((active ? "Deactivate " : "Activate ") + subject);
        return button;
    }

    /** Text status, never colour alone (ADR-0020 no colour-only meaning). */
    protected static String statusLabel(boolean active) {
        return active ? "Active" : "Inactive";
    }
}

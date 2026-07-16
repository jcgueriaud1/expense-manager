package com.vaadin.expensemanager.base.ui;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.function.SerializableBiConsumer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.SerializablePredicate;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.function.ValueProvider;

/**
 * One deep editor module for an admin reference-config kind: it renders the
 * grid + add/edit dialog and owns the four cross-cutting rules every such screen
 * repeats — an always-enabled-Save + top-of-form error summary (via
 * {@link AdminEditor}), accessible icon-button row actions, boundary-disabled
 * reorder, and text-not-colour status (ADR-0020). Each kind shrinks to a
 * {@link Config}: its columns, its form fields + validators, and its create /
 * update / reorder / active-toggle service calls.
 *
 * <p>Written and UI-tested once (see {@code ReferenceConfigEditorUiTest}); the
 * per-kind view tests then assert only their kind-specific columns and
 * fields/validators. Reorder and the active-toggle are optional — a kind that
 * omits them (e.g. the allowance foreign per-diem grid) simply gets a grid with
 * an add/edit-only action column.
 *
 * @param <T> the grid row (DTO) type
 */
public class ReferenceConfigEditor<T> extends VerticalLayout {

    private final transient Config<T> config;
    private final Grid<T> grid = new Grid<>();
    private transient List<T> items = List.of();

    public ReferenceConfigEditor(Config<T> config) {
        this.config = config;
        setPadding(true);
        setSpacing(true);

        var addButton = new Button(config.addButtonText, new Icon(VaadinIcon.PLUS),
                event -> openFormFor(null));
        addButton.addThemeVariants(config.addButtonVariant);

        var heading = config.headingLevel == 3
                ? new H3(config.heading)
                : new H2(config.heading);
        var header = new HorizontalLayout(heading, addButton);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        add(header);

        if (config.intro != null) {
            add(new Paragraph(config.intro));
        }

        for (var column : config.columns) {
            grid.addColumn(column.text)
                    .setHeader(column.header).setAutoWidth(true).setFlexGrow(column.flexGrow);
        }
        if (config.showStatus) {
            grid.addColumn(row -> statusLabel(config.active.test(row)))
                    .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        }
        grid.addComponentColumn(this::actions)
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(config.actionsFlexGrow);
        grid.setAllRowsVisible(true);
        add(grid);

        refresh();
    }

    /** Reloads the grid from the config's items supplier (call after any mutation). */
    public void refresh() {
        items = config.items.get();
        grid.setItems(items);
    }

    private Component actions(T row) {
        int index = indexOf(row);
        var buttons = new ArrayList<Component>();

        buttons.add(AdminEditor.iconButton(VaadinIcon.EDIT, config.editLabel.apply(row),
                () -> openFormFor(row)));

        if (config.reorderSubject != null) {
            String subject = config.reorderSubject.apply(row);
            buttons.add(reorderButton(VaadinIcon.ARROW_UP, "Move " + subject + " up",
                    index > 0, () -> {
                        config.move.accept(config.id.apply(row), -1);
                        refresh();
                    }));
            buttons.add(reorderButton(VaadinIcon.ARROW_DOWN, "Move " + subject + " down",
                    index >= 0 && index < items.size() - 1, () -> {
                        config.move.accept(config.id.apply(row), 1);
                        refresh();
                    }));
        }
        if (config.toggleSubject != null) {
            boolean active = config.active.test(row);
            buttons.add(activeToggle(active, config.toggleSubject.apply(row), () -> {
                config.setActive.accept(config.id.apply(row), !active);
                refresh();
            }));
        }
        return new HorizontalLayout(buttons.toArray(Component[]::new));
    }

    private void openFormFor(T existing) {
        config.editorForm.apply(existing).open(this::refresh);
    }

    private int indexOf(T row) {
        Long id = config.id.apply(row);
        for (int i = 0; i < items.size(); i++) {
            if (config.id.apply(items.get(i)).equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static Button reorderButton(VaadinIcon icon, String ariaLabel, boolean enabled,
            Runnable action) {
        var button = AdminEditor.iconButton(icon, ariaLabel, action);
        button.setEnabled(enabled);
        return button;
    }

    private static Button activeToggle(boolean active, String subject, Runnable action) {
        var button = new Button(active ? "Deactivate" : "Activate", event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel((active ? "Deactivate " : "Activate ") + subject);
        return button;
    }

    /** Text status, never colour alone (ADR-0020 no colour-only meaning). */
    private static String statusLabel(boolean active) {
        return active ? "Active" : "Inactive";
    }

    /**
     * The per-kind configuration a {@link ReferenceConfigEditor} renders. A fluent
     * mutable holder (each setter returns {@code this}); required pieces are the
     * heading, add-button text, at least one column, the {@code id} accessor, the
     * {@code items} supplier, the {@code editLabel}, and the {@code editorForm}.
     * Reorder, the active-toggle, and the status column are opt-in.
     *
     * @param <T> the grid row (DTO) type
     */
    public static final class Config<T> {

        private String heading;
        private int headingLevel = 2;
        private String intro;
        private String addButtonText = "Add";
        private ButtonVariant addButtonVariant = ButtonVariant.PRIMARY;
        private int actionsFlexGrow = 1;
        private boolean showStatus;

        private final List<Column<T>> columns = new ArrayList<>();
        private ValueProvider<T, Long> id;
        private SerializablePredicate<T> active = row -> true;
        private SerializableSupplier<List<T>> items;
        private ValueProvider<T, String> editLabel;
        private SerializableFunction<T, EditorFormSpec<?>> editorForm;

        private ValueProvider<T, String> reorderSubject;
        private SerializableBiConsumer<Long, Integer> move;
        private ValueProvider<T, String> toggleSubject;
        private SerializableBiConsumer<Long, Boolean> setActive;

        /** The section heading text (rendered at {@code level}, default {@code <h2>}). */
        public Config<T> heading(String heading) {
            this.heading = heading;
            return this;
        }

        /** Heading level: {@code 2} for a top-level route screen, {@code 3} when embedded. */
        public Config<T> headingLevel(int level) {
            this.headingLevel = level;
            return this;
        }

        /** Optional intro paragraph under the header; omit for an embedded grid. */
        public Config<T> intro(String intro) {
            this.intro = intro;
            return this;
        }

        public Config<T> addButtonText(String text) {
            this.addButtonText = text;
            return this;
        }

        public Config<T> addButtonVariant(ButtonVariant variant) {
            this.addButtonVariant = variant;
            return this;
        }

        /** Flex-grow of the trailing Actions column (default {@code 1}). */
        public Config<T> actionsFlexGrow(int flexGrow) {
            this.actionsFlexGrow = flexGrow;
            return this;
        }

        /** Adds a text data column ({@code flexGrow 1} lets it absorb slack). */
        public Config<T> column(String header, ValueProvider<T, String> text, int flexGrow) {
            this.columns.add(new Column<>(header, text, flexGrow));
            return this;
        }

        /** Show a text {@code Active}/{@code Inactive} status column from {@link #active}. */
        public Config<T> showStatus(boolean showStatus) {
            this.showStatus = showStatus;
            return this;
        }

        public Config<T> id(ValueProvider<T, Long> id) {
            this.id = id;
            return this;
        }

        public Config<T> active(SerializablePredicate<T> active) {
            this.active = active;
            return this;
        }

        public Config<T> items(SerializableSupplier<List<T>> items) {
            this.items = items;
            return this;
        }

        /** Full aria-label for a row's Edit button, e.g. {@code "Edit rate 13.5 %"}. */
        public Config<T> editLabel(ValueProvider<T, String> editLabel) {
            this.editLabel = editLabel;
            return this;
        }

        /** Builds the add ({@code existing == null}) / edit form for a row. */
        public Config<T> editorForm(SerializableFunction<T, EditorFormSpec<?>> editorForm) {
            this.editorForm = editorForm;
            return this;
        }

        /**
         * Enable reorder: {@code subject} is spliced into the aria-labels
         * {@code "Move <subject> up"} / {@code "Move <subject> down"}, and
         * {@code move} swaps display order ({@code -1} up, {@code +1} down).
         */
        public Config<T> reorder(ValueProvider<T, String> subject,
                SerializableBiConsumer<Long, Integer> move) {
            this.reorderSubject = subject;
            this.move = move;
            return this;
        }

        /**
         * Enable the active-toggle: {@code subject} is spliced into
         * {@code "Deactivate <subject>"} / {@code "Activate <subject>"}, and
         * {@code setActive} flips the flag (never deletes; ADR-0018).
         */
        public Config<T> toggle(ValueProvider<T, String> subject,
                SerializableBiConsumer<Long, Boolean> setActive) {
            this.toggleSubject = subject;
            this.setActive = setActive;
            return this;
        }
    }

    /** A text grid column: header, per-row text, and flex-grow. */
    private record Column<T>(String header, ValueProvider<T, String> text, int flexGrow) {
    }
}

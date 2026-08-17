package com.vaadin.expensemanager.user.ui;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import java.util.List;
import java.util.Locale;

import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.UserAdminService;
import com.vaadin.expensemanager.user.UserSummaryDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * ADMIN-only screen that lists every user and lets an admin find people quickly
 * (issue #64, Phase 6). Read path only — no mutation in this slice.
 *
 * <p>Structure mirrors {@code ExpenseTypeView}: an intro paragraph and a
 * {@link Grid} of {@link UserSummaryDto}, under the screen title that
 * {@code MainLayout} renders in the navbar. The side-nav item is
 * auto-generated from {@code @Menu} and access-filtered, so navigation is gated
 * by {@code @RolesAllowed("ADMIN")} here while the real enforcement lives in
 * {@link UserAdminService#list()} (two-layer authorization, ADR-0008).
 *
 * <p><strong>Search + filter</strong> above the grid — a free-text search over
 * name/email, a role filter, and an enabled/revoked filter — are applied
 * in-memory over the loaded list (the internal user base is small). Status and
 * role render as <strong>text, never colour alone</strong> (ADR-0020).
 *
 * <p><strong>Write path (#65).</strong> A per-row action opens a modal editor
 * (single-select role + enabled/revoked toggle) driving
 * {@link UserAdminService#setRole} / {@link UserAdminService#setEnabled}. The
 * editor uses the always-enabled Save + top-of-form {@link ErrorSummary}
 * (F-013, ADR-0020): service-side lockout guards come back as
 * {@link IllegalArgumentException} and are shown in the summary with the grid
 * left unchanged. Changes take effect at the user's <strong>next login</strong>
 * — there is no forced logout in V1 (ADR-0008), as the intro notes.
 */
@Route("users")
@PageTitle("Users")
@Menu(title = "Users", order = 4, icon = "icons/lucide/users.svg")
@RolesAllowed("ADMIN")
public class UserManagementView extends VerticalLayout {

    private static final String ALL_ROLES = "All roles";
    private static final String ALL_STATUSES = "All statuses";
    private static final String ENABLED = "Enabled";
    private static final String REVOKED = "Revoked";

    private final transient UserAdminService service;
    private final Grid<UserSummaryDto> grid = new Grid<>();

    private final TextField search = new TextField();
    private final ComboBox<String> roleFilter = new ComboBox<>("Role");
    private final ComboBox<String> statusFilter = new ComboBox<>("Status");

    private List<UserSummaryDto> users = List.of();

    public UserManagementView(UserAdminService service) {
        this.service = service;
        setPadding(true);
        setSpacing(true);

        // Title is rendered by MainLayout in the navbar.
        add(new Paragraph(
                "Everyone with access to the expense manager. Search by name or "
                        + "email and filter by role or status to find someone. "
                        + "Use Manage to change a role or revoke access. A revoked "
                        + "account can no longer sign in. Changes to a role or to "
                        + "access take effect the next time that person signs in — "
                        + "anyone already signed in keeps their current access until "
                        + "then."));

        add(filters());

        grid.addColumn(UserSummaryDto::email)
                .setHeader("Email").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(UserSummaryDto::name)
                .setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(dto -> dto.role().name())
                .setHeader("Role").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> statusLabel(dto.enabled()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::manageButton)
                .setHeader("").setAutoWidth(true).setFlexGrow(0);
        grid.setAllRowsVisible(true);
        add(grid);

        refresh();
    }

    /** Per-row action opening the role/access editor for that user. */
    private Button manageButton(UserSummaryDto user) {
        var button = new Button(LucideIcon.SQUARE_PEN.create(), event -> openEditor(user));
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel("Edit user " + user.email());
        return button;
    }

    /**
     * Opens a modal editor for one user with an always-enabled Save and a
     * top-of-form error summary (F-013, ADR-0020). Save writes only the fields
     * that changed through {@link UserAdminService}; a service-side lockout guard
     * ({@link IllegalArgumentException}) shows in the summary and leaves the grid
     * unchanged.
     */
    private void openEditor(UserSummaryDto user) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Manage " + user.name());

        var errorSummary = new ErrorSummary();

        var roleField = new RadioButtonGroup<Role>();
        roleField.setLabel("Role");
        roleField.setItems(Role.USER, Role.ADMIN);
        roleField.setItemLabelGenerator(Role::name);

        var enabledField = new Checkbox("Account enabled");
        enabledField.setHelperText("Clear to revoke access; the change applies at "
                + "the next login.");

        var binder = new Binder<UserFormModel>();
        binder.forField(roleField).asRequired("Select a role")
                .bind(UserFormModel::role, UserFormModel::setRole);
        binder.forField(enabledField)
                .bind(UserFormModel::enabled, UserFormModel::setEnabled);

        var model = new UserFormModel(user.role(), user.enabled());
        binder.readBean(model);

        var form = new VerticalLayout(roleField, enabledField);
        form.setPadding(false);
        form.setSpacing(true);

        var save = new Button("Save", event -> {
            errorSummary.clear();
            if (binder.writeBeanIfValid(model)) {
                try {
                    applyChanges(user, model);
                    dialog.close();
                } catch (DomainRuleException ex) {
                    // A lockout rule lands in the summary; anything technical
                    // propagates to the global UiErrorHandler.
                    errorSummary.show(ex.getMessage());
                }
            } else {
                errorSummary.showValidationErrors(binder.validate());
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> dialog.close());

        var content = new VerticalLayout(errorSummary, form);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    /**
     * Persists only the fields that changed, then refreshes the grid. Each call
     * is a separate guarded transaction; a rejected guard throws before any write
     * and re-throws to the editor. The final {@link #refresh()} reflects whatever
     * did persist so the grid never shows stale state.
     */
    private void applyChanges(UserSummaryDto original, UserFormModel model) {
        try {
            if (model.role() != original.role()) {
                service.setRole(original.id(), model.role());
            }
            if (model.enabled() != original.enabled()) {
                service.setEnabled(original.id(), model.enabled());
            }
        } finally {
            refresh();
        }
    }

    /** Mutable editor bean bound to the role and enabled fields. */
    private static final class UserFormModel {
        private Role role;
        private boolean enabled;

        UserFormModel(Role role, boolean enabled) {
            this.role = role;
            this.enabled = enabled;
        }

        Role role() {
            return role;
        }

        void setRole(Role role) {
            this.role = role;
        }

        boolean enabled() {
            return enabled;
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private HorizontalLayout filters() {
        search.setLabel("Search");
        search.setPlaceholder("Search by name or email");
        search.setPrefixComponent(LucideIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setWidth("20em");
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> applyFilters());

        roleFilter.setItems(ALL_ROLES, Role.USER.name(), Role.ADMIN.name());
        roleFilter.setValue(ALL_ROLES);
        roleFilter.addValueChangeListener(event -> applyFilters());

        statusFilter.setItems(ALL_STATUSES, ENABLED, REVOKED);
        statusFilter.setValue(ALL_STATUSES);
        statusFilter.addValueChangeListener(event -> applyFilters());

        var bar = new HorizontalLayout(search, roleFilter, statusFilter);
        bar.setWidthFull();
        return bar;
    }

    /** Reloads from the service (picks up newly provisioned users) and re-filters. */
    private void refresh() {
        users = service.list();
        applyFilters();
    }

    /** Applies the search text, role, and enabled/revoked filters in memory. */
    private void applyFilters() {
        var term = search.getValue() == null ? ""
                : search.getValue().strip().toLowerCase(Locale.ROOT);
        var role = roleFilter.getValue();
        var status = statusFilter.getValue();

        grid.setItems(users.stream()
                .filter(u -> term.isEmpty()
                        || u.name().toLowerCase(Locale.ROOT).contains(term)
                        || u.email().toLowerCase(Locale.ROOT).contains(term))
                .filter(u -> ALL_ROLES.equals(role) || u.role().name().equals(role))
                .filter(u -> matchesStatus(u, status))
                .toList());
    }

    private static boolean matchesStatus(UserSummaryDto user, String status) {
        return switch (status) {
            case ENABLED -> user.enabled();
            case REVOKED -> !user.enabled();
            default -> true; // ALL_STATUSES
        };
    }

    /** Text status, never colour alone (ADR-0020 no colour-only meaning). */
    private static String statusLabel(boolean enabled) {
        return enabled ? ENABLED : REVOKED;
    }
}

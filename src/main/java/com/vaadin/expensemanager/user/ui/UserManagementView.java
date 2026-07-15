package com.vaadin.expensemanager.user.ui;

import java.util.List;
import java.util.Locale;

import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.UserAdminService;
import com.vaadin.expensemanager.user.UserSummaryDto;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * ADMIN-only screen that lists every user and lets an admin find people quickly
 * (issue #64, Phase 6). Read path only — no mutation in this slice.
 *
 * <p>Structure mirrors {@code ExpenseTypeView}: an H2 header, an intro
 * paragraph, and a {@link Grid} of {@link UserSummaryDto}. The side-nav item is
 * auto-generated from {@code @Menu} and access-filtered, so navigation is gated
 * by {@code @RolesAllowed("ADMIN")} here while the real enforcement lives in
 * {@link UserAdminService#list()} (two-layer authorization, ADR-0008).
 *
 * <p><strong>Search + filter</strong> above the grid — a free-text search over
 * name/email, a role filter, and an enabled/revoked filter — are applied
 * in-memory over the loaded list (the internal user base is small). Status and
 * role render as <strong>text, never colour alone</strong> (ADR-0020).
 */
@Route("users")
@PageTitle("Users")
@Menu(title = "Users", order = 4, icon = "vaadin:users")
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

        add(new H2("Users"));
        add(new Paragraph(
                "Everyone with access to the expense manager. Search by name or "
                        + "email and filter by role or status to find someone. "
                        + "A revoked account can no longer sign in."));

        add(filters());

        grid.addColumn(UserSummaryDto::email)
                .setHeader("Email").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(UserSummaryDto::name)
                .setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(dto -> dto.role().name())
                .setHeader("Role").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> statusLabel(dto.enabled()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.setAllRowsVisible(true);
        add(grid);

        refresh();
    }

    private HorizontalLayout filters() {
        search.setLabel("Search");
        search.setPlaceholder("Search by name or email");
        search.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
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

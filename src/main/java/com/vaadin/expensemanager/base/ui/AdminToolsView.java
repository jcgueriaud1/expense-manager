package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.security.StandInPrivilegedService;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * A stand-in ADMIN-only route that proves the route-security layer (ADR-0008,
 * Phase 1.2).
 *
 * <p>The real admin destinations (user management, rate/type config, the
 * approval queue) arrive in later phases; this placeholder exists so the
 * two-layer pattern is verifiable now:
 *
 * <ul>
 *   <li>{@code @RolesAllowed("ADMIN")} gates <em>navigation</em>. A plain USER
 *       cannot reach {@code /admin} by typing the URL — navigation access
 *       control reroutes them — and the auto-generated side menu
 *       ({@code MainLayout}) hides this {@code @Menu} entry from users whose role
 *       can't use it.</li>
 *   <li>Rendering it still calls
 *       {@link StandInPrivilegedService#privilegedAdminOperation()}, whose own
 *       {@code @RolesAllowed("ADMIN")} is the real enforcement point — so even if
 *       the route guard were bypassed, the privileged operation would not
 *       execute.</li>
 * </ul>
 *
 * <p>An ADMIN reaches this view through the {@code {ADMIN}} role directly; a USER
 * is excluded because the {@code ADMIN > USER} hierarchy only widens access
 * downward, never up.
 */
@Route("admin")
@PageTitle("Admin Tools")
@Menu(title = "Admin Tools", order = 1, icon = "vaadin:cog")
@RolesAllowed("ADMIN")
public class AdminToolsView extends VerticalLayout {

    public AdminToolsView(StandInPrivilegedService privilegedService) {
        // Route security let us navigate here; the method-security check inside
        // the service is the actual enforcement of the privileged operation.
        String result = privilegedService.privilegedAdminOperation();

        var heading = new H2("Admin tools");
        var note = new Paragraph("This admin-only area is a Phase 1.2 stand-in "
                + "that proves two-layer authorization. Real admin tools — user "
                + "management, rate and expense-type configuration, the approval "
                + "queue — replace it in later phases.");
        var operationOutcome = new Paragraph(
                "Stand-in privileged operation ran: " + result);

        add(heading, note, operationOutcome);
        setSpacing(false);
        setPadding(true);
    }
}

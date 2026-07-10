package com.vaadin.expensemanager.report.prototype;

import java.util.List;
import java.util.Map;

import com.vaadin.expensemanager.report.prototype.PrototypeModel.ReportDraft;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;

import jakarta.annotation.security.PermitAll;

/**
 * PROTOTYPE ROUTE — three structurally different takes on the Phase 2 report
 * <em>line editor</em> (issue #3, finding F-004), switchable via {@code ?variant=}
 * and the floating bottom bar:
 *
 * <ul>
 *   <li><b>A</b> — inline editable Grid (ADR-0015 provisional pick)</li>
 *   <li><b>B</b> — master–detail (list + persistent side form)</li>
 *   <li><b>C</b> — stacked cards + modal dialog editor</li>
 * </ul>
 *
 * <p>Throwaway: in-memory stub data (one {@link ReportDraft} kept in the Vaadin
 * session so edits survive variant switches), no services, no persistence, no
 * tests. Hidden in production. Delete once the design question is answered —
 * see {@code NOTES.md} in this package.
 */
@Route("prototype/report-detail")
@PageTitle("Prototype · Report detail")
@PermitAll
public class ReportDetailPrototypeView extends VerticalLayout implements BeforeEnterObserver {

    private static final String SESSION_KEY = "prototype-report-draft";

    private static final List<PrototypeSwitcher.Variant> VARIANTS = List.of(
            new PrototypeSwitcher.Variant("A", VariantAInlineGrid.NAME),
            new PrototypeSwitcher.Variant("B", VariantBMasterDetail.NAME),
            new PrototypeSwitcher.Variant("C", VariantCCardDialog.NAME),
            new PrototypeSwitcher.Variant("D", VariantDCardsSidePanel.NAME));

    public ReportDetailPrototypeView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Never expose the prototype in a production build.
        if (VaadinService.getCurrent().getDeploymentConfiguration().isProductionMode()) {
            event.forwardTo("");
            return;
        }

        String requested = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("variant", List.of("A")).get(0).toUpperCase();
        String variant = VARIANTS.stream().anyMatch(v -> v.key().equals(requested))
                ? requested : "A";

        removeAll();
        add(banner(), render(variant, report()), switcher(variant));
    }

    private ReportDraft report() {
        var session = VaadinSession.getCurrent();
        var report = (ReportDraft) session.getAttribute(SESSION_KEY);
        if (report == null) {
            report = PrototypeModel.seedReport();
            session.setAttribute(SESSION_KEY, report);
        }
        return report;
    }

    private Component render(String variant, ReportDraft report) {
        return switch (variant) {
            case "B" -> new VariantBMasterDetail(report);
            case "C" -> new VariantCCardDialog(report);
            case "D" -> new VariantDCardsSidePanel(report);
            default -> new VariantAInlineGrid(report);
        };
    }

    private Component banner() {
        var text = new Span("⚠ PROTOTYPE — throwaway line-editor exploration for issue #3 (F-004). "
                + "Edits are in-memory only.");
        var reset = new Button("Reset data", e -> {
            VaadinSession.getCurrent().setAttribute(SESSION_KEY, null);
            UI.getCurrent().getPage().reload();
        });
        reset.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);

        var bar = new HorizontalLayout(text, reset);
        bar.setAlignItems(Alignment.CENTER);
        bar.setWidthFull();
        bar.getStyle()
                .set("background", "color-mix(in srgb, var(--aura-red) 15%, transparent)")
                .set("color", "var(--aura-red-text)")
                .set("padding", "4px 12px")
                .set("font-size", "var(--aura-font-size-s)");
        return bar;
    }

    private PrototypeSwitcher switcher(String current) {
        return new PrototypeSwitcher(VARIANTS, current, key ->
                UI.getCurrent().navigate(ReportDetailPrototypeView.class,
                        QueryParameters.simple(Map.of("variant", key))));
    }
}

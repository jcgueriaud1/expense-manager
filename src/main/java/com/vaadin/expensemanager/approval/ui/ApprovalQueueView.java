package com.vaadin.expensemanager.approval.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.expensemanager.approval.service.ApprovalService;
import com.vaadin.expensemanager.approval.service.ReviewSummaryDto;
import com.vaadin.expensemanager.base.ui.EmptyState;
import com.vaadin.expensemanager.report.ui.ReportViewSupport;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import jakarta.annotation.security.RolesAllowed;

/**
 * The admin approval queue (Phase 5.1, UC-004) — every {@code SUBMITTED} report
 * awaiting review, across all owners, newest submission first.
 *
 * <p>Two-layer authorization (ADR-0008): {@code @RolesAllowed("ADMIN")} gates
 * navigation (a USER can't reach the route and the auto-menu hides its
 * {@code @Menu} entry), while the real enforcement is {@link ApprovalService}'s
 * method security. Each row is a {@link RouterLink} into the report detail view's
 * {@code /review/{id}} alias (a real, keyboard-operable link, ADR-0020), where the
 * admin reviews it read-only and approves. When the queue is empty the shared
 * {@link EmptyState} renders instead (ADR-0017).
 *
 * <p>Mirrors the owner's {@code MyReportsView} card list, adding the submitter's
 * name and submitted-at — the two things a cross-owner queue shows that an owner's
 * own list never needs. Accessible and usable at ~360px: one centred column that
 * scrolls, status shown as a text badge (never colour alone, ADR-0020).
 */
@Route("approvals")
@PageTitle("Approvals")
@Menu(title = "Approvals", order = 5, icon = "vaadin:inbox")
@RolesAllowed("ADMIN")
public class ApprovalQueueView extends VerticalLayout {

    private static final DateTimeFormatter SUBMITTED_AT = DateTimeFormatter
            .ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    public ApprovalQueueView(ApprovalService approvalService) {
        List<ReviewSummaryDto> submitted = approvalService.listSubmitted();
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        setAlignItems(FlexComponent.Alignment.CENTER);

        var content = new VerticalLayout(header(submitted.size()));
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();
        content.setMaxWidth("46rem");

        if (submitted.isEmpty()) {
            content.add(new EmptyState("vaadin:inbox", "Nothing to review",
                    "Submitted reports awaiting approval will appear here."));
            add(content);
            return;
        }

        var stack = new VerticalLayout();
        stack.setPadding(false);
        stack.setSpacing("var(--vaadin-gap-m)");
        stack.setWidthFull();
        submitted.forEach(dto -> stack.add(queueCard(dto)));
        content.add(stack);
        add(content);
    }

    /** Title + count of reports awaiting review. */
    private Component header(int count) {
        var title = new H2("Approvals");
        title.addClassName("reports-title");
        var subtitle = new Span(count + (count == 1
                ? " report awaiting review" : " reports awaiting review"));
        subtitle.addClassName("muted");
        var column = new VerticalLayout(title, subtitle);
        column.setPadding(false);
        column.setSpacing(false);
        return column;
    }

    /**
     * One clickable queue card, a {@link RouterLink} to the report detail view's
     * {@code /review/{id}} alias. The alias is not addressable by navigation-target
     * class (it is not the primary route), so the href is set directly — the link
     * keeps its {@code router-link} behaviour (client-side SPA navigation) while
     * staying a real, keyboard-operable anchor (ADR-0020).
     */
    private Component queueCard(ReviewSummaryDto dto) {
        var title = new Span(dto.additionalInformation() == null
                || dto.additionalInformation().isBlank()
                ? "Expense report" : dto.additionalInformation());
        title.addClassName("report-card-title");

        var topRow = new HorizontalLayout(title, ReportViewSupport.statusBadge(dto.status()));
        topRow.setWidthFull();
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topRow.expand(title);

        var submitter = new Span(dto.submitterName()
                + (dto.submittedAt() == null ? ""
                : " · submitted " + SUBMITTED_AT.format(dto.submittedAt())));
        submitter.addClassName("muted");

        var total = new Span(ReportViewSupport.formatEur(dto.total()));
        total.addClassName("report-card-total");

        var bottomRow = new HorizontalLayout(submitter, total);
        bottomRow.setWidthFull();
        bottomRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        bottomRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        bottomRow.addClassName("report-card-footer");

        // The empty RouterLink carries the router-link attribute (client-side SPA
        // navigation); the href points at the review alias directly, since an alias
        // is not addressable via a navigation-target class.
        var card = new RouterLink();
        card.getElement().setAttribute("href", "review/" + dto.id());
        card.add(topRow, bottomRow);
        card.addClassName("report-card");
        card.addClassName("report-card--actionable");
        return card;
    }
}

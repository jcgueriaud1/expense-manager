package com.vaadin.expensemanager.approval.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.expensemanager.approval.service.ApprovalService;
import com.vaadin.expensemanager.approval.service.ReviewedSummaryDto;
import com.vaadin.expensemanager.base.ui.EmptyState;
import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.ui.ReportViewSupport;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import jakarta.annotation.security.RolesAllowed;

/**
 * The admin review history (Issue #110) — every report in a terminal status
 * ({@code APPROVED} or {@code REJECTED}) across all owners, newest <em>decision</em>
 * first. A separate surface from the {@link ApprovalQueueView} (pending work): this
 * is the look-back at reports already decided on.
 *
 * <p>Two-layer authorization (ADR-0008): {@code @RolesAllowed("ADMIN")} gates
 * navigation (a USER can't reach the route, and the top nav hides the
 * entry that leads to it), while the real enforcement is {@link ApprovalService}'s
 * method security. Each row is a {@link RouterLink} into the report detail view's
 * {@code /review/{id}} alias (a real, keyboard-operable link, ADR-0020), which
 * renders read-only with the full status history. When there is nothing yet the
 * shared {@link EmptyState} renders instead (ADR-0017).
 *
 * <p>Mirrors {@link ApprovalQueueView}'s centred card list, but each card leads
 * with the <strong>decision</strong> — the deciding admin and when — and makes the
 * outcome obvious via the status badge (never colour alone, ADR-0020), showing the
 * rejection reason for a rejected report. Accessible and usable at ~360px.
 */
@Route("approval-history")
@PageTitle("Review history")
@RolesAllowed("ADMIN")
public class ReviewHistoryView extends VerticalLayout {

    private static final DateTimeFormatter DECIDED_AT = DateTimeFormatter
            .ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    public ReviewHistoryView(ApprovalService approvalService) {
        List<ReviewedSummaryDto> reviewed = approvalService.listReviewed();
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        setAlignItems(FlexComponent.Alignment.CENTER);

        var content = new VerticalLayout(header(reviewed.size()));
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();
        content.setMaxWidth("46rem");

        if (reviewed.isEmpty()) {
            content.add(new EmptyState(LucideIcon.ARCHIVE.create(), "No reviewed reports yet",
                    "Reports you approve or reject will appear here."));
            add(content);
            return;
        }

        var stack = new VerticalLayout();
        stack.setPadding(false);
        stack.setSpacing("var(--vaadin-gap-m)");
        stack.setWidthFull();
        reviewed.forEach(dto -> stack.add(historyCard(dto)));
        content.add(stack);
        add(content);
    }

    /** Title + count of reviewed reports. */
    private Component header(int count) {
        var title = new H2("Review history");
        title.addClassName("reports-title");
        var subtitle = new Span(count + (count == 1
                ? " reviewed report" : " reviewed reports"));
        subtitle.addClassName("muted");
        var column = new VerticalLayout(title, subtitle);
        column.setPadding(false);
        column.setSpacing(false);
        return column;
    }

    /**
     * One clickable history card, a {@link RouterLink} to the report detail view's
     * {@code /review/{id}} alias (set directly, as the alias is not addressable by
     * navigation-target class — the same mechanism as the queue card). It leads with
     * the outcome badge and the decision (who decided, and when), showing the
     * rejection reason for a rejected report.
     */
    private Component historyCard(ReviewedSummaryDto dto) {
        var title = new Span(dto.additionalInformation() == null
                || dto.additionalInformation().isBlank()
                ? "Expense report" : dto.additionalInformation());
        title.addClassName("report-card-title");

        var topRow = new HorizontalLayout(title, ReportViewSupport.statusBadge(dto.status()));
        topRow.setWidthFull();
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topRow.expand(title);

        var decision = new Span(decisionText(dto));
        decision.addClassName("muted");

        var total = new Span(ReportViewSupport.formatEur(dto.total()));
        total.addClassName("report-card-total");

        var bottomRow = new HorizontalLayout(decision, total);
        bottomRow.setWidthFull();
        bottomRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        bottomRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        bottomRow.addClassName("report-card-footer");

        var card = new RouterLink();
        card.getElement().setAttribute("href", "review/" + dto.id());
        card.add(topRow, bottomRow);
        // The rejection reason, when there is one, sits below the decision line.
        if (dto.rejectionComment() != null && !dto.rejectionComment().isBlank()) {
            var reason = new Span("Reason: " + dto.rejectionComment());
            reason.addClassName("muted");
            card.add(reason);
        }
        card.addClassName("report-card");
        card.addClassName("report-card--actionable");
        return card;
    }

    /** "{submitter} · approved/rejected by {admin} on {date}", trailing parts dropped if absent. */
    private String decisionText(ReviewedSummaryDto dto) {
        var verb = dto.status() == ReportStatus.APPROVED ? "approved" : "rejected";
        var text = new StringBuilder(dto.submitterName());
        if (dto.decidedByName() != null) {
            text.append(" · ").append(verb).append(" by ").append(dto.decidedByName());
        }
        if (dto.decidedAt() != null) {
            text.append(" on ").append(DECIDED_AT.format(dto.decidedAt()));
        }
        return text.toString();
    }
}

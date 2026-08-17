package com.vaadin.expensemanager.approval.ui;

import com.vaadin.expensemanager.base.ui.AdminLayout;
import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.ViewHeader;
import com.vaadin.expensemanager.base.ui.MainLayout;
import com.vaadin.expensemanager.base.ui.StatCard;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.expensemanager.approval.service.ApprovalService;
import com.vaadin.expensemanager.approval.service.ReviewSummaryDto;
import com.vaadin.expensemanager.base.ui.EmptyState;
import com.vaadin.expensemanager.report.ui.ReportViewSupport;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
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
@Route(value = "approvals", layout = AdminLayout.class)
@PageTitle("Approvals")
@Menu(title = "Approvals", order = 10, icon = "icons/lucide/inbox.svg")
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

        var content = new VerticalLayout(new ViewHeader(this), statCards(submitted));
        content.setPadding(false);
        content.setSpacing("var(--vaadin-gap-xl)");
        content.setWidthFull();
        content.setMaxWidth(MainLayout.CONTENT_MAX_WIDTH);

        if (submitted.isEmpty()) {
            content.add(new EmptyState(LucideIcon.INBOX, "Nothing to review",
                    "Submitted reports awaiting approval will appear here."));
            add(content);
            return;
        }

        var stack = new VerticalLayout();
        stack.setPadding(false);
        stack.setSpacing("var(--vaadin-gap-l)");
        // RouterLink is not HasSize, so the card links take their width from the
        // stack's cross-axis alignment rather than setting it themselves.
        stack.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        stack.setWidthFull();
        submitted.forEach(dto -> stack.add(queueCard(dto)));
        content.add(stack);
        add(content);
    }

    /**
     * What the queue can say about itself, above the queue (ADR-0026): how much
     * work is waiting, how much money it represents, and how long the oldest
     * report has been sitting there — the number that turns a queue into a
     * priority. All three come from the list already loaded.
     */
    private Component statCards(List<ReviewSummaryDto> submitted) {
        var total = submitted.stream().map(ReviewSummaryDto::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var waiting = new StatCard("Awaiting review",
                String.valueOf(submitted.size()),
                submitted.isEmpty() ? "queue is clear"
                        : ReportViewSupport.formatEur(total) + " in total");

        var oldest = new StatCard("Oldest waiting", oldestWaiting(submitted),
                submitted.isEmpty() ? null : "since submission");

        var submitters = new StatCard("From",
                String.valueOf(submitted.stream().map(ReviewSummaryDto::submitterName)
                        .distinct().count()),
                submitted.size() == 1 ? "one person" : "people");

        var row = new HorizontalLayout(waiting, oldest, submitters);
        row.setWidthFull();
        row.addClassName("stat-cards");
        return row;
    }

    /** "9 days", or an em dash when nothing is waiting. */
    private static String oldestWaiting(List<ReviewSummaryDto> submitted) {
        return submitted.stream()
                .map(ReviewSummaryDto::submittedAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .map(oldest -> {
                    var days = Duration.between(oldest, Instant.now()).toDays();
                    return days == 0 ? "today" : days + (days == 1 ? " day" : " days");
                })
                .orElse("—");
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
        var card = new Card();
        card.addThemeVariants(CardVariant.OUTLINED);
        card.setWidthFull();
        card.setAriaRole("presentation");
        card.add(topRow, bottomRow);
        card.addClassName("report-card");
        card.addClassName("report-card--actionable");

        // The anchor wraps the card: the href points at the review alias directly,
        // since an alias is not addressable via a navigation-target class.
        var link = new RouterLink();
        link.getElement().setAttribute("href", "review/" + dto.id());
        link.add(card);
        link.addClassName("card-link");
        return link;
    }
}

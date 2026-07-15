package com.vaadin.expensemanager.approval.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.report.domain.ReportStatus;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for the approval tracer
 * bullet's <strong>admin</strong> surfaces (Phase 5.1): the ADMIN-only queue
 * ({@link ApprovalQueueView}) and the admin-review mode on
 * {@link ReportDetailView} reached from it. The non-admin access checks live in
 * {@link ApprovalAccessUiTest}.
 *
 * <p>Runs as the seeded admin via class-level {@link WithUserDetails} — the same
 * shape as {@code ReportDetailViewUiTest} (a method-level mix of users on this
 * {@code @Transactional} base loses the security context under full-suite
 * ordering, F-020, so admin and user cases are split into two classes). Covers
 * cross-owner queue visibility, the empty state, and the read-only review +
 * approve happy path, with persisted state asserted through the repository. Mind
 * F-035 (signal short-circuit) and F-036 (overlay/visibility).
 *
 * <p>Runs as the seeded admin via class-level {@link WithUserDetails}, the same
 * pattern as {@code ReportDetailViewUiTest}/{@code AdminToolsViewUiTest}.
 */
@WithUserDetails("admin@vaadin.com")
class ApprovalQueueViewUiTest extends AbstractApprovalViewUiTest {

    private static final String ADMIN_EMAIL = "admin@vaadin.com";

    @Test
    void adminSeesSubmittedReportsAcrossOwnersWithReviewLinks() {
        var userReport = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");
        seedSubmittedReportForOwner(ADMIN_EMAIL, LocalDate.of(2026, 6, 1), "admin trip");

        navigate(ApprovalQueueView.class);

        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("user trip", "admin trip", "Demo User",
                "Expense Admin");
        // Each row is a real router link into the /review/{id} alias.
        assertThat($(RouterLink.class).all().stream()
                .anyMatch(a -> ("review/" + userReport).equals(
                        a.getElement().getAttribute("href")))).isTrue();
    }

    @Test
    void anEmptyQueueShowsTheEmptyState() {
        navigate(ApprovalQueueView.class);

        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Nothing to review");
    }

    @Test
    void adminOpensAReportReadOnlyAndApprovesIt() {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        navigate("review/" + id, ReportDetailView.class);

        // Read-only review: no owner editing affordances, but the line is shown.
        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("user trip", "Submitted");
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
        assertThat(findButton().withText("Delete").exists()).isFalse();
        assertThat(findButton().withText("Add expense").exists()).isFalse();

        // Approve moves it to APPROVED and drops the action (no longer reviewable).
        findButton().withText("Approve").click();

        assertThat(reportRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.APPROVED);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Approved");
        assertThat(findButton().withText("Approve").exists()).isFalse();
    }
}

package com.vaadin.expensemanager.approval.ui;

import java.time.LocalDate;

import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.report.ui.ReportDetailView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Browserless view test (pyramid layer 3, ADR-0008/ADR-0012) for the approval
 * flow's <strong>non-admin</strong> access rules (Phase 5.1): a plain USER is kept
 * out of the queue route and out of admin review mode, and sees their own
 * approved report read-only.
 *
 * <p>Runs as the seeded plain user via class-level {@link WithUserDetails} (split
 * from the admin cases in {@link ApprovalQueueViewUiTest} so each class pins one
 * user — a method-level mix loses the security context under full-suite ordering,
 * F-020).
 */
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ApprovalAccessUiTest extends AbstractApprovalViewUiTest {

    @Test
    void aNonAdminCannotReachTheQueueRoute() {
        // Navigation access control rejects a USER: the ADMIN-only view never
        // resolves (mirrors AdminToolsViewUiTest).
        assertThatThrownBy(() -> navigate(ApprovalQueueView.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void aNonAdminCannotReachTheReviewHistoryRoute() {
        assertThatThrownBy(() -> navigate(ReviewHistoryView.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void aNonAdminCannotEnterReviewMode() {
        var id = seedSubmittedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        // The review alias is @PermitAll but gated in-view: a USER is forwarded to
        // the dashboard, never rendering the report in review mode.
        navigate("review/" + id, MyReportsView.class);
        assertThat(getCurrentView()).isInstanceOf(MyReportsView.class);
    }

    @Test
    void ownerSeesTheirApprovedReportReadOnly() {
        var id = seedApprovedReportForOwner(LocalUserSeeder.PLAIN_USER_EMAIL,
                LocalDate.of(2026, 6, 2), "user trip");

        navigate(ReportDetailView.class, id);

        var text = getCurrentView().getElement().getTextRecursively();
        assertThat(text).contains("Approved");
        assertThat(findButton().withText("Save").exists()).isFalse();
        assertThat(findButton().withText("Submit for approval").exists()).isFalse();
        assertThat(findButton().withText("Delete").exists()).isFalse();
        assertThat(findButton().withText("Add expense").exists()).isFalse();
        // The owner never sees an Approve action — that is the admin's alone.
        assertThat(findButton().withText("Approve").exists()).isFalse();
    }
}

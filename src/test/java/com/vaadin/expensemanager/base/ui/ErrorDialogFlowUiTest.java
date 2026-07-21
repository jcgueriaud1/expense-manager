package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless end-to-end test (pyramid layer 3, ADR-0012) for the shared error
 * routing (issue #86), driven through the real {@link EditorDialog} + the real
 * {@link FormErrorHandler} Spring bean.
 *
 * <p>Proves the acceptance split live, in a real UI: a save action that fails with a
 * <em>technical</em> error opens the generic {@link ErrorDialog} and leaves the
 * form's error summary hidden (the raw cause never reaches the user under the
 * non-local {@code test} profile), while a {@link DomainRuleException} lands its
 * message in the summary and opens no dialog.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
class ErrorDialogFlowUiTest extends SpringBrowserlessTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    private FormErrorHandler errorHandler;

    @Test
    void aTechnicalFailureOpensTheDialogAndLeavesTheSummaryHidden() {
        navigate(DashboardView.class);
        openEditorWhoseSaveThrows(new IllegalStateException("stack trace guts"));

        clickSave();

        // The generic dialog is up, showing the reassuring message but never the raw
        // cause (test != local profile).
        var dialogs = $(ErrorDialog.class).all();
        assertThat(dialogs).hasSize(1);
        String dialogText = textOf(dialogs.getFirst());
        assertThat(dialogText).contains(ErrorDialog.MESSAGE);
        assertThat(dialogText).doesNotContain("stack trace guts");
        // The form's own summary stays hidden — the failure did not leak into it.
        assertThat($(ErrorSummary.class).all()).noneMatch(Component::isVisible);
    }

    @Test
    void aDomainRuleLandsInTheSummaryAndOpensNoDialog() {
        navigate(DashboardView.class);
        openEditorWhoseSaveThrows(new DomainRuleException("Name is required"));

        clickSave();

        // No technical dialog; the user-actionable message shows in the form summary.
        assertThat($(ErrorDialog.class).all()).isEmpty();
        var summaries = $(ErrorSummary.class).all();
        assertThat(summaries).anyMatch(summary ->
                summary.isVisible() && textOf(summary).contains("Name is required"));
    }

    /** Opens a minimal, always-valid editor whose Save action throws {@code failure}. */
    private void openEditorWhoseSaveThrows(RuntimeException failure) {
        new EditorDialog<>("Editor", new TextField("Name"),
                new Binder<>(Object.class), new Object(), errorHandler)
                .onSave(() -> {
                    throw failure;
                })
                .open();
    }

    private void clickSave() {
        var save = $(Button.class).all().stream()
                .filter(button -> "Save".equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No 'Save' button in the editor"));
        test(save).click();
    }

    /** Flattens every rendered text node in a component tree, for content assertions. */
    private static String textOf(Component component) {
        var text = new StringBuilder();
        if (component instanceof HasText hasText) {
            text.append(hasText.getText()).append(' ');
        }
        component.getChildren().forEach(child -> text.append(textOf(child)));
        return text.toString();
    }
}

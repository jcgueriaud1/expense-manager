package com.vaadin.expensemanager.base.ui;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Unit test (pyramid layer 1, ADR-0012) for the error classification the whole app
 * now shares (issue #86): a domain rule reaches the summary, a stale write the
 * reload affordance, and anything technical the generic dialog — with its detail
 * shown only under the local developer profile.
 */
class FormErrorHandlerTest {

    @Test
    void domainRuleReachesTheSummaryWithItsMessage() {
        var handler = new FormErrorHandler(false);
        var summary = new ArrayList<String>();

        handler.handle(new DomainRuleException("Name is required"), summary::add,
                () -> fail("a domain rule must not trigger the conflict path"));

        assertThat(summary).containsExactly("Name is required");
    }

    @Test
    void aStaleWriteTriggersTheConflictAffordance() {
        var handler = new FormErrorHandler(false);
        var reloaded = new AtomicBoolean(false);

        handler.handle(new ObjectOptimisticLockingFailureException(Object.class, 1L),
                message -> fail("a conflict must not reach the summary"),
                () -> reloaded.set(true));

        assertThat(reloaded).isTrue();
    }

    @Test
    void aTechnicalErrorShowsTheGenericMessageAndHidesTheCauseOutsideLocal() {
        var dialog = new FormErrorHandler(false)
                .technicalDialog(new IllegalStateException("stack trace guts"));

        assertThat(textOf(dialog))
                .contains(ErrorDialog.MESSAGE)
                .doesNotContain("stack trace guts")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void aTechnicalErrorRevealsTheCauseUnderLocal() {
        var dialog = new FormErrorHandler(true)
                .technicalDialog(new IllegalStateException("stack trace guts"));

        assertThat(textOf(dialog))
                .contains(ErrorDialog.MESSAGE)
                .contains("IllegalStateException")
                .contains("stack trace guts");
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

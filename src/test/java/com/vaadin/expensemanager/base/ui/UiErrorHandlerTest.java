package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test (pyramid layer 1, ADR-0012) for the technical-error dialog the global
 * {@link UiErrorHandler} shows (issue #86): the reassuring message always renders,
 * and the underlying cause is revealed only under the local developer profile.
 */
class UiErrorHandlerTest {

    @Test
    void hidesTheCauseOutsideLocal() {
        var dialog = new UiErrorHandler(false)
                .technicalDialog(new IllegalStateException("stack trace guts"));

        assertThat(textOf(dialog))
                .contains(ErrorDialog.MESSAGE)
                .doesNotContain("stack trace guts")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void revealsTheCauseUnderLocal() {
        var dialog = new UiErrorHandler(true)
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

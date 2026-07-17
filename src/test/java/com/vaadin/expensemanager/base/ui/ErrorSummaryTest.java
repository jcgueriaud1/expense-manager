package com.vaadin.expensemanager.base.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit unit tests for {@link ErrorSummary} — no UI/Spring needed, the
 * component's {@code focus()} is a no-op while detached.
 *
 * <p>Guards the fix for issue #85: a validation error that reaches the summary
 * with a <em>blank</em> message (a field missing its bad-input / incomplete-input
 * error text) must not render an empty, meaningless bullet.
 */
class ErrorSummaryTest {

    /** A minimal bean so a {@link Binder} has something to bind against. */
    private static final class Bean {
        private String value = "x";

        String getValue() {
            return value;
        }

        void setValue(String value) {
            this.value = value;
        }
    }

    @Test
    void blankFieldErrorMessageRendersFallbackNotAnEmptyBullet() {
        var field = new TextField();
        var binder = new Binder<Bean>();
        binder.forField(field)
                // Fails with a blank message — the shape issue #85 reproduces when a
                // picker goes invalid without a configured bad/incomplete-input message.
                .withValidator(value -> false, "")
                .bind(Bean::getValue, Bean::setValue);
        binder.setBean(new Bean());

        var summary = new ErrorSummary();
        boolean shown = summary.showValidationErrors(binder.validate());

        assertThat(shown).isTrue();
        assertThat(summary.getElement().getTextRecursively())
                .contains("This field is invalid");
    }

    @Test
    void realFieldErrorMessagePassesThroughUnchanged() {
        var field = new TextField();
        var binder = new Binder<Bean>();
        binder.forField(field)
                .withValidator(value -> false, "Value is not allowed")
                .bind(Bean::getValue, Bean::setValue);
        binder.setBean(new Bean());

        var summary = new ErrorSummary();
        boolean shown = summary.showValidationErrors(binder.validate());

        assertThat(shown).isTrue();
        var text = summary.getElement().getTextRecursively();
        assertThat(text).contains("Value is not allowed");
        assertThat(text).doesNotContain("This field is invalid");
    }
}

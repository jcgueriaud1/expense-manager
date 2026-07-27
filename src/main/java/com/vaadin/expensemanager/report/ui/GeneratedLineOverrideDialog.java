package com.vaadin.expensemanager.report.ui;

import java.math.BigDecimal;
import java.util.function.Consumer;

import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.base.ui.ErrorSummary;
import com.vaadin.expensemanager.report.domain.QuantityOverride;
import com.vaadin.expensemanager.report.service.GeneratedLineView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatQuantity;

/**
 * The focused modal editor for one travel-generated line's <strong>Quantity
 * Override</strong> (glossary: Quantity Override, ADR-0024) — the correction a user
 * makes when the statutory calculation doesn't fit the trip ("2 full days, not the 3
 * you calculated, because the Wednesday was personal").
 *
 * <p>Only the <em>count</em> is editable: the unit price stays statutory and
 * server-computed, so what leaves this dialog is a count and a reason, never money.
 * The dialog shows the calculated baseline it is correcting, a whole-number count
 * field, and a <strong>mandatory</strong> reason.
 *
 * <p>Validation follows the project rule (ADR-0020): the confirm button is
 * <strong>always enabled</strong>; a blank reason, a missing count, or a count the
 * kind's rules reject (the partial-day cap of one, the floor) surfaces in the
 * dialog's own top-of-form {@link ErrorSummary} and commits nothing. The rules are
 * the domain's — {@link QuantityOverride#of} raises the same
 * {@link DomainRuleException} the save would, so the dialog cannot drift from the
 * server. The field enforces integrality at the widget as well; the domain enforces
 * it again on save.
 *
 * <p><strong>A count of {@code 0} is a valid claim</strong>: it drops the line from the
 * report (issue #132) — the correction "keep the two full days, lose the partial
 * leftover". This dialog does not warn about it, because whether that destroys an
 * attached receipt is knowledge only the view has (persisted <em>and</em> buffered
 * receipts); the view confirms before committing.
 *
 * <p>Like {@code TravelLineReceiptDialog}, the committed value travels back to the
 * view, which re-previews the trip server-side so the row's amount and the report
 * totals reflect the correction immediately (the money is never computed here).
 */
final class GeneratedLineOverrideDialog extends Dialog {

    private final ErrorSummary errorSummary = new ErrorSummary();
    private final IntegerField count = new IntegerField("Count");
    private final TextArea reason = new TextArea("Reason for the override");

    /**
     * @param line   the generated line being corrected (its {@link
     *               GeneratedLineView#quantity()} is the effective count today)
     * @param onSave receives the validated override to apply to the trip
     */
    GeneratedLineOverrideDialog(GeneratedLineView line,
            Consumer<QuantityOverride> onSave) {
        setHeaderTitle("Override count — " + line.kind().label());
        // Sized for a phone first: a fixed rem width that can never exceed the
        // viewport, and a single-column form (ADR-0020).
        setWidth("26rem");
        setMaxWidth("100%");
        addClassName("travel-line-dialog");

        count.setRequiredIndicatorVisible(true);
        // A count of discrete days or meals: whole numbers only, never below the
        // floor. The domain re-checks both, plus the per-kind cap.
        count.setMin(0);
        count.setStepButtonsVisible(true);
        count.setValue(line.quantity().intValue());
        // Zero is a legitimate claim, not an error, so the field says what it does
        // — the destructive part (a receipt goes with the line) is confirmed by the
        // view, which is the only place that knows whether one is attached.
        count.setHelperText((line.kind().isPerDiem()
                ? "Whole days only. " : "Whole meals only. ")
                + "0 removes this line from the report.");

        reason.setRequiredIndicatorVisible(true);
        reason.setMaxLength(500);
        reason.setPlaceholder("e.g. the Wednesday was personal time");
        reason.setValue(line.overrideReason() == null ? "" : line.overrideReason());

        var form = new FormLayout(count, reason);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        add(errorSummary, calculatedSummary(line), form);

        var save = new Button("Save override", event -> submit(line, onSave));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> close());
        getFooter().add(cancel, save);
    }

    /**
     * The statutory figure being corrected, so the user is deciding against something
     * concrete. For an already-overridden line that is the calculated baseline the
     * row carries; for a first override it is the line's current (still calculated)
     * quantity.
     */
    private static Div calculatedSummary(GeneratedLineView line) {
        BigDecimal calculated = line.calculatedQuantity() == null ? line.quantity()
                : line.calculatedQuantity();
        var heading = new Span("Calculated: "
                + formatQuantity(calculated) + " "
                + line.kind().countNoun(calculated.longValue()));
        heading.addClassName("travel-preview-amount");
        var detail = new Span(formatQuantity(calculated) + " × "
                + formatEur(line.unitPrice()) + " = "
                + formatEur(ReportViewSupport.lineGross(line.unitPrice(), calculated)));
        detail.addClassName("muted");
        var summary = new Div(heading, detail);
        summary.addClassName("travel-preview");
        return summary;
    }

    /**
     * Builds the override through the domain factory and hands it up, or surfaces the
     * domain's own message in the summary and keeps the dialog open (ADR-0020 — the
     * click is always allowed, the reason always shown).
     */
    private void submit(GeneratedLineView line, Consumer<QuantityOverride> onSave) {
        errorSummary.clear();
        Integer value = count.getValue();
        try {
            var override = QuantityOverride.of(line.kind(),
                    value == null ? null : BigDecimal.valueOf(value), reason.getValue());
            onSave.accept(override);
            close();
        } catch (DomainRuleException invalid) {
            errorSummary.show(invalid.getMessage());
        }
    }
}

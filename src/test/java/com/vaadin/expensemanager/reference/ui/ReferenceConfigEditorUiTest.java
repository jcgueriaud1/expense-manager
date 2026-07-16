package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browserless test (pyramid layer 3, ADR-0012) for the shared, generic
 * {@link com.vaadin.expensemanager.base.ui.ReferenceConfigEditor} — its behaviour
 * exercised <strong>once</strong> here, driven through {@link VatRateView} as a
 * representative config kind. The per-kind view tests ({@link VatRateViewUiTest},
 * {@link ExpenseTypeViewUiTest}) then assert only their kind-specific columns and
 * fields/validators.
 *
 * <p>Covers the module contract: the grid renders its rows (data + text status +
 * actions), the add/edit dialog round-trips, an invalid Save shows the
 * top-of-form error summary and persists nothing (ADR-0020 always-enabled Save),
 * reorder swaps neighbours with boundary buttons disabled, and the active-toggle
 * retains the row as inactive (ADR-0018, never deletes).
 */
class ReferenceConfigEditorUiTest extends AbstractReferenceDataViewUiTest {

    private static final int RATE_COL = 0;
    private static final int STATUS_COL = 1;
    private static final int ACTIONS_COL = 2;

    // ---------------------------------------------------------- grid renders

    @Test
    @WithUserDetails("admin@vaadin.com")
    void gridRendersRowsWithTextStatusAndActions() {
        navigate(VatRateView.class);
        var grid = findGrid(VatRateDto.class);

        // Data column + a text (never colour-only) status column.
        assertThat(columnText(grid, RATE_COL)).containsExactly("25.5 %", "13.5 %", "10 %", "0 %");
        assertThat(columnText(grid, STATUS_COL)).allMatch("Active"::equals);
        // Each row carries an accessible Edit action.
        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Edit rate 25.5 %")).isNotNull();
    }

    // ------------------------------------------------------ add/edit round-trip

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addThroughEditorAppendsActiveRow() {
        navigate(VatRateView.class);
        int before = findGrid(VatRateDto.class).size();

        findButton().withText("Add VAT rate").click();
        findBigDecimalField().setValue(new BigDecimal("8.5"));
        findButton().withText("Save").click();

        var grid = findGrid(VatRateDto.class);
        assertThat(grid.size()).isEqualTo(before + 1);
        assertThat(grid.getCellText(before, RATE_COL)).isEqualTo("8.5 %");
        assertThat(grid.getCellText(before, STATUS_COL)).isEqualTo("Active");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editThroughEditorChangesTheRow() {
        navigate(VatRateView.class);

        test(rowActionButton(findGrid(VatRateDto.class), 1, ACTIONS_COL,
                "Edit rate 13.5 %")).click();
        findBigDecimalField().setValue(new BigDecimal("12"));
        findButton().withText("Save").click();

        assertThat(columnText(findGrid(VatRateDto.class), RATE_COL))
                .contains("12 %").doesNotContain("13.5 %");
    }

    // ------------------------------------------------------ error summary

    @Test
    @WithUserDetails("admin@vaadin.com")
    void invalidSaveShowsErrorSummaryAndPersistsNothing() {
        navigate(VatRateView.class);
        int before = findGrid(VatRateDto.class).size();

        // Always-enabled Save: submitting the empty required field surfaces the
        // top-of-form summary (role="alert") and writes nothing (ADR-0020).
        findButton().withText("Add VAT rate").click();
        findButton().withText("Save").click();

        // The summary heading + the failed field's message appear in the dialog.
        var summaryText = find(Span.class).all().stream()
                .map(span -> span.getElement().getText()).toList();
        assertThat(summaryText).contains("Please fix the following:");
        var messages = find(ListItem.class).all().stream()
                .map(item -> item.getElement().getText()).toList();
        assertThat(messages).contains("Rate is required");
        // The dialog stays open (Save still present) and no row was added.
        assertThat(findButton().withText("Save").exists()).isTrue();
        assertThat(findGrid(VatRateDto.class).size()).isEqualTo(before);
    }

    // ----------------------------------------------------------- reorder

    @Test
    @WithUserDetails("admin@vaadin.com")
    void reorderSwapsRowsAndBoundaryButtonsAreDisabled() {
        navigate(VatRateView.class);
        var grid = findGrid(VatRateDto.class);
        assertThat(columnText(grid, RATE_COL))
                .containsExactly("25.5 %", "13.5 %", "10 %", "0 %");

        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Move rate 25.5 % up")
                .isEnabled()).isFalse();
        assertThat(rowActionButton(grid, 3, ACTIONS_COL, "Move rate 0 % down")
                .isEnabled()).isFalse();

        test(rowActionButton(grid, 1, ACTIONS_COL, "Move rate 13.5 % up")).click();

        assertThat(columnText(findGrid(VatRateDto.class), RATE_COL))
                .containsExactly("13.5 %", "25.5 %", "10 %", "0 %");
    }

    // -------------------------------------------------------- active-toggle

    @Test
    @WithUserDetails("admin@vaadin.com")
    void deactivateRetainsRowAsInactive() {
        navigate(VatRateView.class);

        test(rowActionButton(findGrid(VatRateDto.class), 0, ACTIONS_COL,
                "Deactivate rate 25.5 %")).click();

        var grid = findGrid(VatRateDto.class);
        assertThat(grid.getCellText(0, RATE_COL)).isEqualTo("25.5 %");
        assertThat(grid.getCellText(0, STATUS_COL)).isEqualTo("Inactive");
        // The toggle now offers to re-activate the retained row.
        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Activate rate 25.5 %")).isNotNull();
    }
}

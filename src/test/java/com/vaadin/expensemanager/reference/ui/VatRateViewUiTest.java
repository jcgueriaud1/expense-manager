package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link VatRateView} —
 * the ADMIN-only VAT-rate settings screen (issue #22, ADR-0008/0018).
 *
 * <p>Split from the expense-type screen ({@link ExpenseTypeViewUiTest}) so each
 * screen's access-control and CRUD behaviour is covered in isolation. Drives the
 * grid and editor through the tester DSL exactly as a user would: cell content is
 * read with {@code getCellText} (the browserless tier <em>does</em> render grid
 * cells — see the revised F-018), never by scraping element text. Authenticates
 * via {@link WithUserDetails} against the seeded records.
 */
class VatRateViewUiTest extends AbstractReferenceDataViewUiTest {

    private static final int RATE_COL = 0;
    private static final int STATUS_COL = 1;
    private static final int ACTIONS_COL = 2;

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeededRatesRendered() {
        navigate(VatRateView.class);
        var rates = columnText(findGrid(VatRateDto.class), RATE_COL);
        assertThat(rates).containsExactly("25.5 %", "13.5 %", "10 %", "0 %");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).contains("vat-rates");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNoMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).doesNotContain("vat-rates");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl() {
        assertThatThrownBy(() -> navigate(VatRateView.class))
                .isInstanceOf(Exception.class);
    }

    // ------------------------------------------------------------- CRUD: add

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addRateThroughEditorAppendsActiveRow() {
        navigate(VatRateView.class);
        int before = findGrid(VatRateDto.class).size();

        findButton().withText("Add VAT rate").click();
        findBigDecimalField().setValue(new BigDecimal("8.5"));
        findButton().withText("Save").click();

        var grid = findGrid(VatRateDto.class);
        assertThat(grid.size()).isEqualTo(before + 1);
        // New rate is appended last (highest display order) and starts active.
        assertThat(grid.getCellText(before, RATE_COL)).isEqualTo("8.5 %");
        assertThat(grid.getCellText(before, STATUS_COL)).isEqualTo("Active");
    }

    // ------------------------------------------------------------ CRUD: edit

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editRateThroughEditorChangesTheCell() {
        navigate(VatRateView.class);

        // 13.5 % is the second seeded row (index 1).
        test(rowActionButton(findGrid(VatRateDto.class), 1, ACTIONS_COL,
                "Edit rate 13.5 %")).click();
        findBigDecimalField().setValue(new BigDecimal("12"));
        findButton().withText("Save").click();

        var rates = columnText(findGrid(VatRateDto.class), RATE_COL);
        assertThat(rates).contains("12 %").doesNotContain("13.5 %");
    }

    // --------------------------------------------------------- CRUD: reorder

    @Test
    @WithUserDetails("admin@vaadin.com")
    void reorderRateSwapsRowsAndBoundaryButtonsAreDisabled() {
        navigate(VatRateView.class);
        var grid = findGrid(VatRateDto.class);
        assertThat(columnText(grid, RATE_COL))
                .containsExactly("25.5 %", "13.5 %", "10 %", "0 %");

        // The first row can't move up and the last can't move down.
        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Move rate 25.5 % up")
                .isEnabled()).isFalse();
        assertThat(rowActionButton(grid, 3, ACTIONS_COL, "Move rate 0 % down")
                .isEnabled()).isFalse();

        // Move 13.5 % (row 1) up; it swaps with 25.5 %.
        test(rowActionButton(grid, 1, ACTIONS_COL, "Move rate 13.5 % up")).click();

        assertThat(columnText(findGrid(VatRateDto.class), RATE_COL))
                .containsExactly("13.5 %", "25.5 %", "10 %", "0 %");
    }

    // ------------------------------------------------------ CRUD: deactivate

    @Test
    @WithUserDetails("admin@vaadin.com")
    void deactivateRetainsRowAsInactiveAndExcludesFromActiveOptions() {
        navigate(VatRateView.class);

        // 25.5 % is the first seeded row (index 0).
        test(rowActionButton(findGrid(VatRateDto.class), 0, ACTIONS_COL,
                "Deactivate rate 25.5 %")).click();

        // Retained in the admin grid, now flagged Inactive (never deleted) and in
        // the same position; the toggle now offers to re-activate it.
        var grid = findGrid(VatRateDto.class);
        assertThat(grid.getCellText(0, RATE_COL)).isEqualTo("25.5 %");
        assertThat(grid.getCellText(0, STATUS_COL)).isEqualTo("Inactive");
        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Activate rate 25.5 %"))
                .isNotNull();

        // Active-options query: 25.5 gone from active, still in the full listing.
        assertThat(service.activeVatRates())
                .noneMatch(r -> r.value().compareTo(new BigDecimal("25.5")) == 0);
        assertThat(service.allVatRates())
                .anyMatch(r -> r.value().compareTo(new BigDecimal("25.5")) == 0 && !r.active());
    }
}

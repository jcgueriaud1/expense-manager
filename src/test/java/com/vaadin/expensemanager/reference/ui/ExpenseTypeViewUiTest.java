package com.vaadin.expensemanager.reference.ui;

import java.math.BigDecimal;

import com.vaadin.expensemanager.base.ui.DashboardView;
import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for {@link ExpenseTypeView}
 * — the ADMIN-only expense-type settings screen (issue #22, ADR-0008/0018).
 *
 * <p>Companion to {@link VatRateViewUiTest}; covers this screen's access control
 * and CRUD, plus the two behaviours unique to expense types: an editor whose
 * default-rate {@code ComboBox} offers only <em>active</em> VAT rates (the
 * active-options query on the UI), and a required default rate. Grid cells are
 * read with {@code getCellText} (revised F-018).
 */
class ExpenseTypeViewUiTest extends AbstractReferenceDataViewUiTest {

    private static final int NAME_COL = 0;
    private static final int RATE_COL = 1;
    private static final int STATUS_COL = 2;
    private static final int ACTIONS_COL = 3;

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeededTypesRendered() {
        navigate(ExpenseTypeView.class);
        var grid = findGrid(ExpenseTypeDto.class);
        assertThat(columnText(grid, NAME_COL))
                .containsExactly("Travel allowance", "Taxi/transport", "Accommodation",
                        "Restaurant/meals", "Parking/supplies/goods", "Publications",
                        "Kilometre allowance", "Meal allowance", "Other");
        // Each type renders its seeded default VAT rate.
        assertThat(grid.getCellText(0, RATE_COL)).isEqualTo("0 %");
        assertThat(grid.getCellText(3, RATE_COL)).isEqualTo("13.5 %");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).contains("expense-types");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNoMenuEntry() {
        navigate(DashboardView.class);
        assertThat(menuItemPaths()).doesNotContain("expense-types");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl() {
        assertThatThrownBy(() -> navigate(ExpenseTypeView.class))
                .isInstanceOf(Exception.class);
    }

    // ------------------------------------------------------------- CRUD: add

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addTypeThroughEditorAppendsActiveRow() {
        navigate(ExpenseTypeView.class);
        int before = findGrid(ExpenseTypeDto.class).size();

        findButton().withText("Add expense type").click();
        findTextField().withLabel("Name").setValue("Software licences");
        findComboBox(VatRateDto.class).withLabel("Default VAT rate").selectItem("25.5 %");
        findButton().withText("Save").click();

        var grid = findGrid(ExpenseTypeDto.class);
        assertThat(grid.size()).isEqualTo(before + 1);
        assertThat(grid.getCellText(before, NAME_COL)).isEqualTo("Software licences");
        assertThat(grid.getCellText(before, RATE_COL)).isEqualTo("25.5 %");
        assertThat(grid.getCellText(before, STATUS_COL)).isEqualTo("Active");
    }

    // ------------------------------------------------------------ CRUD: edit

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editTypeThroughEditorRenamesAndRepointsDefaultRate() {
        navigate(ExpenseTypeView.class);

        // Restaurant/meals is the fourth seeded row (index 3).
        test(rowActionButton(findGrid(ExpenseTypeDto.class), 3, ACTIONS_COL,
                "Edit expense type Restaurant/meals")).click();
        findTextField().withLabel("Name").setValue("Restaurant");
        findComboBox(VatRateDto.class).withLabel("Default VAT rate").selectItem("25.5 %");
        findButton().withText("Save").click();

        var grid = findGrid(ExpenseTypeDto.class);
        assertThat(columnText(grid, NAME_COL))
                .contains("Restaurant").doesNotContain("Restaurant/meals");
        // Row 3 kept its position; its default rate is now the repointed one.
        assertThat(grid.getCellText(3, NAME_COL)).isEqualTo("Restaurant");
        assertThat(grid.getCellText(3, RATE_COL)).isEqualTo("25.5 %");
    }

    // --------------------------------------------------------- CRUD: reorder

    @Test
    @WithUserDetails("admin@vaadin.com")
    void reorderTypeSwapsRowsAndBoundaryButtonsAreDisabled() {
        navigate(ExpenseTypeView.class);
        var grid = findGrid(ExpenseTypeDto.class);

        // Travel allowance is first (row 0); "Other" is last (row 8) after the
        // V10 catch-all addition (issue #87).
        int last = grid.size() - 1;
        assertThat(rowActionButton(grid, 0, ACTIONS_COL, "Move Travel allowance up")
                .isEnabled()).isFalse();
        assertThat(rowActionButton(grid, last, ACTIONS_COL, "Move Other down")
                .isEnabled()).isFalse();

        // Move Taxi/transport (row 1) up; it swaps with Travel allowance.
        test(rowActionButton(grid, 1, ACTIONS_COL, "Move Taxi/transport up")).click();

        var names = columnText(findGrid(ExpenseTypeDto.class), NAME_COL);
        assertThat(names.get(0)).isEqualTo("Taxi/transport");
        assertThat(names.get(1)).isEqualTo("Travel allowance");
    }

    // ------------------------------------------------------ CRUD: deactivate

    @Test
    @WithUserDetails("admin@vaadin.com")
    void deactivateRetainsRowAsInactiveAndExcludesFromActiveOptions() {
        navigate(ExpenseTypeView.class);

        // Publications is the sixth seeded row (index 5).
        test(rowActionButton(findGrid(ExpenseTypeDto.class), 5, ACTIONS_COL,
                "Deactivate Publications")).click();

        // Retained in the admin grid at its position, flagged Inactive — never deleted.
        var grid = findGrid(ExpenseTypeDto.class);
        assertThat(grid.getCellText(5, NAME_COL)).isEqualTo("Publications");
        assertThat(grid.getCellText(5, STATUS_COL)).isEqualTo("Inactive");

        assertThat(service.activeExpenseTypes())
                .noneMatch(t -> t.name().equals("Publications"));
        assertThat(service.allExpenseTypes())
                .anyMatch(t -> t.name().equals("Publications") && !t.active());
    }

    // ------------------------------------------ active-options in the editor

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editorOffersOnlyActiveVatRatesAsDefault() {
        navigate(ExpenseTypeView.class);
        // Deactivate the 10 % rate, then open the add editor.
        var tenPercent = service.allVatRates().stream()
                .filter(r -> r.value().compareTo(new BigDecimal("10")) == 0)
                .findFirst().orElseThrow();
        service.setVatRateActive(tenPercent.id(), false);

        findButton().withText("Add expense type").click();

        var suggestions = findComboBox(VatRateDto.class)
                .withLabel("Default VAT rate").getSuggestions();
        assertThat(suggestions)
                .contains("25.5 %", "13.5 %", "0 %")
                .doesNotContain("10 %");
    }
}

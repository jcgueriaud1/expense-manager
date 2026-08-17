package com.vaadin.expensemanager.allowance.ui;

import com.vaadin.flow.server.menu.MenuConfiguration;
import java.math.BigDecimal;
import java.util.List;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.locator.Locators;
import com.vaadin.expensemanager.allowance.AllowanceRateService;
import com.vaadin.expensemanager.allowance.ForeignPerDiemDto;
import com.vaadin.expensemanager.report.ui.MyReportsView;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Browserless view test (pyramid layer 3, ADR-0012) for
 * {@link AllowanceRatesView} — the ADMIN-only allowance-rate settings screen
 * (issue #48, ADR-0008, PRD 4.1/4.4).
 *
 * <p>Drives the year selector, the rate panels, and the foreign per-diem grid
 * through the tester DSL as an admin would; access control mirrors the
 * reference-data screens. The singleton Testcontainers Postgres and
 * {@code @Transactional} rollback follow {@code AbstractReferenceDataViewUiTest}
 * (F-008: {@link SpringBrowserlessTest} occupies the inheritance slot).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AllowanceRatesViewUiTest extends SpringBrowserlessTest implements Locators {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    private AllowanceRateService service;

    private static final int COUNTRY_COL = 0;
    private static final int AMOUNT_COL = 1;

    // ------------------------------------------------------- access control

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminReachesViewWithSeeded2026Rendered() {
        navigate(AllowanceRatesView.class);

        assertThat(findComboBox(Integer.class).getSelected()).isEqualTo(2026);

        var grid = findGrid(ForeignPerDiemDto.class);
        assertThat(grid.size()).isEqualTo(12);
        assertThat(grid.getCellText(0, COUNTRY_COL)).isEqualTo("Belgium");
        assertThat(grid.getCellText(9, COUNTRY_COL)).isEqualTo("Sweden");
        assertThat(grid.getCellText(9, AMOUNT_COL)).isEqualTo("€68.00");
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesMenuEntry() {
        navigate(MyReportsView.class);
        assertThat(menuItemPaths()).contains("allowance-rates");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesNoMenuEntry() {
        navigate(MyReportsView.class);
        assertThat(menuItemPaths()).doesNotContain("allowance-rates");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userCannotReachRouteByUrl() {
        assertThatThrownBy(() -> navigate(AllowanceRatesView.class))
                .isInstanceOf(Exception.class);
    }

    // --------------------------------------------------------- add a country

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addCountryThroughEditorAppendsRow() {
        navigate(AllowanceRatesView.class);
        int before = findGrid(ForeignPerDiemDto.class).size();

        findButton().withText("Add country").click();
        findTextField().withLabel("Country").setValue("Japan");
        findBigDecimalField().withLabel("Amount (€)").setValue(new BigDecimal("85"));
        findButton().withText("Save").click();

        assertThat(findGrid(ForeignPerDiemDto.class).size()).isEqualTo(before + 1);
        assertThat(service.foreignPerDiem(2026, "Japan")).isPresent();
    }

    // ------------------------------------------------------------ edit rate

    @Test
    @WithUserDetails("admin@vaadin.com")
    void editMealAllowanceThroughEditorPersists() {
        navigate(AllowanceRatesView.class);

        findButton().withAriaLabel("Edit meal allowance").click();
        findBigDecimalField().withLabel("Amount (€)").setValue(new BigDecimal("15"));
        findButton().withText("Save").click();

        assertThat(service.mealAllowance(2026).orElseThrow().amount())
                .isEqualByComparingTo("15.00");
    }

    // --------------------------------------------------------- add a year

    @Test
    @WithUserDetails("admin@vaadin.com")
    void addYearThroughEditorCopiesAndSelectsIt() {
        navigate(AllowanceRatesView.class);

        findButton().withText("Add year").click();
        findIntegerField().withLabel("Year").setValue(2027);
        findButton().withText("Save").click();

        // The new year is selected and its foreign per-diems were copied.
        assertThat(findComboBox(Integer.class).getSelected()).isEqualTo(2027);
        assertThat(findGrid(ForeignPerDiemDto.class).size()).isEqualTo(12);
        assertThat(service.availableYears()).containsExactly(2027, 2026);
    }

    /**
     * The auto-registered, access-filtered {@code @Menu} entry paths. Read from
     * {@link MenuConfiguration} rather than a rendered nav component: that set is
     * what drives both the header's Admin item and AdminLayout's sub-tabs
     * (ADR-0025), so it is the thing this test is actually about.
     */
    private List<String> menuItemPaths() {
        return MenuConfiguration.getMenuEntries().stream()
                .map(entry -> entry.path().startsWith("/")
                        ? entry.path().substring(1) : entry.path())
                .toList();
    }
}

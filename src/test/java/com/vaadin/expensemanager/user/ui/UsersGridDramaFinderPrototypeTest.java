package com.vaadin.expensemanager.user.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.vaadin.addons.dramafinder.element.GridElement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROTOTYPE (not part of the suite) — evaluates driving the <em>already-running</em>
 * local app on :8080 with Playwright + DramaFinder instead of the Playwright MCP.
 *
 * <p>Deliberately standalone: no Spring context, no {@code SpringPlaywrightIT},
 * no frontend build. It logs in through the form-stub (dev password
 * {@code expense}) and asserts the seeded {@code /users} grid entirely through
 * the {@link GridElement} wrapper — no shadow-DOM {@code evaluate} probing.
 *
 * <p>Requires the app running in the {@code local} profile (seeded users). Run:
 * {@code mvn -Dtest=UsersGridDramaFinderPrototypeTest -Dsurefire.failIfNoSpecifiedTests=false test}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersGridDramaFinderPrototypeTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String DEV_PASSWORD = "expense";
    private static final String ADMIN_EMAIL = "jean-christophe@vaadin.com";

    private Playwright playwright;
    private Browser browser;
    private Page page;

    @BeforeAll
    void setUp() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        page = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)).newPage();
        loginAsAdmin();
    }

    @AfterAll
    void tearDown() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    /**
     * Deep-link to the secured view, sign in through the form-stub, then land on
     * {@code /users}. The Vaadin login fields sync to native {@code name=}
     * inputs, so filling them and submitting the native form is the reliable
     * path (Enter on the password field does not submit here). The post-login
     * redirect target is not assumed — we navigate to {@code /users} explicitly.
     */
    private void loginAsAdmin() {
        page.navigate(BASE_URL + "/users");
        page.waitForURL("**/login");
        page.locator("input[name=\"username\"]").fill(ADMIN_EMAIL);
        page.locator("input[name=\"password\"]").fill(DEV_PASSWORD);
        page.locator("form").evaluate("f => f.submit()");
        page.waitForURL(url -> !url.contains("/login"));

        page.navigate(BASE_URL + "/users");
        page.locator("vaadin-grid").waitFor();
    }

    @Test
    void gridHasExpectedColumns() {
        GridElement grid = GridElement.get(page);
        assertThat(grid.getColumnCount()).isEqualTo(5); // Email, Name, Role, Status, (edit)
        assertThat(grid.getHeaderCellContents())
                .contains("Email", "Name", "Role", "Status");
    }

    @Test
    void gridShowsSeededUsers() {
        GridElement grid = GridElement.get(page);
        // At least the migration-seeded admin + the LocalUserSeeder plain user.
        assertThat(grid.getTotalRowCount()).isGreaterThanOrEqualTo(2);
        assertThat(grid.findRowIndexesWithColumnText(0, ADMIN_EMAIL)).hasSize(1);
    }

    @Test
    void seededPlainUserRowHasExpectedCells() {
        GridElement grid = GridElement.get(page);
        List<Integer> rows = grid.findRowIndexesWithColumnText(0, "user@vaadin.com");
        assertThat(rows).hasSize(1);
        int row = rows.get(0);

        // Locate cells by column header via the wrapper, assert on the content
        // locator it hands back — no reaching into vaadin-grid-cell-content myself.
        assertCell(grid, row, "Name", "Demo User");
        assertCell(grid, row, "Role", "USER");
        assertCell(grid, row, "Status", "Enabled");

        page.screenshot(new Page.ScreenshotOptions()
                .setPath(screenshotPath()).setFullPage(true));
    }

    /** Resolve a body cell by column header through the wrapper, assert its text. */
    private static void assertCell(GridElement grid, int row, String header,
            String expected) {
        GridElement.CellElement cell = grid.findCell(row, header)
                .orElseThrow(() -> new AssertionError(
                        "No cell for header '" + header + "' at row " + row));
        PlaywrightAssertions.assertThat(cell.getCellContentLocator())
                .hasText(expected);
    }

    private static Path screenshotPath() {
        // Prototype: land the screenshot somewhere easy to open afterwards.
        return Paths.get("/private/tmp/claude-501",
                "-Users-jean-christophe-Documents-genius-expense-manager",
                "77876b3d-49c5-42d5-9ddd-65dc5ae67781", "scratchpad",
                "dramafinder-users-grid.png");
    }
}

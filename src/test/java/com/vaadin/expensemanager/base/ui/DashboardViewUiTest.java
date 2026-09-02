package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.menubar.MenuBar;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Server-side view test (pyramid layer 3, ADR-0012) for the role-aware dashboard
 * and shell header (UC-007, ADR-0017), driven with the browserless tester.
 *
 * <p>Verifies the acceptance criteria: the dashboard greets the user by name and
 * renders role-appropriate content that differs for ADMIN vs USER, and
 * {@link MainLayout}'s header shows the current-user identity plus a working
 * logout action. Authenticates as the seeded admin and plain user via
 * {@link WithUserDetails} — the same records the form-stub logs in.
 */
@SpringBootTest
@ActiveProfiles("test")
class DashboardViewUiTest extends SpringBrowserlessTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void adminSeesAdminGreetingAndContent() {
        var dashboard = navigate(DashboardView.class);

        var text = textOf(dashboard);
        assertThat(text).contains("Welcome, Expense Admin");
        assertThat(text).contains("administrator");
        assertThat(text).doesNotContain("signed in as a user");
    }

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void userSeesUserGreetingAndContent() {
        var dashboard = navigate(DashboardView.class);

        var text = textOf(dashboard);
        assertThat(text).contains("Welcome, Demo User");
        assertThat(text).contains("signed in as a user");
        assertThat(text).doesNotContain("administrator");
    }

    /**
     * Identity and logout survived the shell redesign (#146) — both moved from
     * the {@code AppLayout} navbar to behind the avatar, because the designed
     * bar has no room beside the three nav links (#145 decision 1).
     */
    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void headerShowsIdentityAndLogout() {
        navigate(DashboardView.class);

        var accountMenu = $(MenuBar.class).all().stream()
                .flatMap(bar -> bar.getItems().stream())
                .flatMap(item -> item.getSubMenu().getItems().stream())
                .toList();

        assertThat(accountMenu).extracting(item -> item.getElement().getTextRecursively())
                .contains("Demo User", "Sign out");

        var signOut = accountMenu.stream()
                .filter(item -> "Sign out".equals(item.getElement().getTextRecursively()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No 'Sign out' item in the shell"));
        assertThat(signOut.isEnabled()).isTrue();
    }

    private static String textOf(DashboardView root) {
        return root.getElement().getTextRecursively();
    }
}

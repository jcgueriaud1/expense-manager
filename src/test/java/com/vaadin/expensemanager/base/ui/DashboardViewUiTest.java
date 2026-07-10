package com.vaadin.expensemanager.base.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.expensemanager.user.LocalUserSeeder;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
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

    @Test
    @WithUserDetails(LocalUserSeeder.PLAIN_USER_EMAIL)
    void headerShowsIdentityAndLogout() {
        navigate(DashboardView.class);

        // Identity: the current user's name is rendered in the shell header.
        var names = $(Span.class).all().stream().map(Span::getText).toList();
        assertThat(names).anyMatch(t -> t.contains("Demo User"));

        // A usable logout control is present in the header.
        var signOut = $(Button.class).all().stream()
                .filter(b -> "Sign out".equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No 'Sign out' button in the shell"));
        assertThat(test(signOut).isUsable()).isTrue();
    }

    private static String textOf(DashboardView root) {
        return root.getElement().getTextRecursively();
    }
}

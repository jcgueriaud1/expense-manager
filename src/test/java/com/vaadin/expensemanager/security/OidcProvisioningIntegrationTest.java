package com.vaadin.expensemanager.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Provisioning integration test (pyramid layer 2, ADR-0007, ADR-0012): drives the
 * real {@link UserProvisioningService} with <em>synthesized</em> OIDC claims,
 * so the domain gate + claim/create policy is exercised end-to-end on
 * Testcontainers Postgres without a live Google exchange (F-014).
 *
 * <p>Covers the acceptance criteria: claim-preserves-ADMIN, new-USER,
 * wrong-domain, unverified-email, disabled-user, and no-re-sync of
 * {@code name}/{@code email} on a second login.
 */
class OidcProvisioningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserProvisioningService service;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Test
    void claimsSeededAdminRowPreservingAdminRole() {
        var principal = service.provision(
                google("admin-google-sub", adminEmail, "JC Guériaud", true, "vaadin.com"));

        assertThat(principal.getRoles())
                .as("the seeded ADMIN row is claimed, not replaced by a new USER")
                .containsExactly(Role.ADMIN);
        assertThat(principal.getEmail()).isEqualTo(adminEmail);
        assertThat(principal.getName()).isEqualTo("JC Guériaud");
        assertThat(principal.getAuthorities())
                .extracting("authority").contains("ROLE_ADMIN");

        var claimed = userRepository.findBySub("admin-google-sub").orElseThrow();
        assertThat(claimed.getRoles()).containsExactly(Role.ADMIN);
        assertThat(claimed.getName()).isEqualTo("JC Guériaud");
        assertThat(userRepository.findByEmail(adminEmail))
                .as("no duplicate account is created for the admin")
                .get()
                .extracting(User::getId)
                .isEqualTo(claimed.getId());
    }

    @Test
    void provisionsBrandNewUserAsUser() {
        var principal = service.provision(
                google("new-sub", "newbie@vaadin.com", "New Bie", true, "vaadin.com"));

        assertThat(principal.getRoles()).containsExactly(Role.USER);
        var created = userRepository.findBySub("new-sub").orElseThrow();
        assertThat(created.getEmail()).isEqualTo("newbie@vaadin.com");
        assertThat(created.getName()).isEqualTo("New Bie");
        assertThat(created.isEnabled()).isTrue();
    }

    @Test
    void rejectsWrongDomain() {
        assertThatThrownBy(() -> service.provision(
                google("intruder-sub", "someone@gmail.com", "Someone", true, "gmail.com")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting("error.errorCode")
                .isEqualTo(UserProvisioningService.ERROR_DOMAIN);
    }

    @Test
    void rejectsUnverifiedEmail() {
        assertThatThrownBy(() -> service.provision(
                google("unverified-sub", "pending@vaadin.com", "Pending", false, "vaadin.com")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting("error.errorCode")
                .isEqualTo(UserProvisioningService.ERROR_DOMAIN);
    }

    @Test
    void rejectsDisabledUser() {
        var disabled = new User("disabled@vaadin.com", "Disabled", Set.of(Role.USER));
        disabled.claim("disabled-sub", "Disabled");
        disabled.setEnabled(false);
        userRepository.saveAndFlush(disabled);

        assertThatThrownBy(() -> service.provision(
                google("disabled-sub", "disabled@vaadin.com", "Disabled", true, "vaadin.com")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting("error.errorCode")
                .isEqualTo(UserProvisioningService.ERROR_DISABLED);
    }

    @Test
    void doesNotResyncNameOrEmailOnSecondLogin() {
        service.provision(google("stable-sub", "first@vaadin.com", "First Name", true, "vaadin.com"));

        // Second login: same Google subject, but Google now reports a different
        // name and email. Neither must be re-synced (ADR-0007).
        var principal = service.provision(
                google("stable-sub", "changed@vaadin.com", "Changed Name", true, "vaadin.com"));

        assertThat(principal.getName()).isEqualTo("First Name");
        assertThat(principal.getEmail()).isEqualTo("first@vaadin.com");

        var stored = userRepository.findBySub("stable-sub").orElseThrow();
        assertThat(stored.getName()).isEqualTo("First Name");
        assertThat(stored.getEmail()).isEqualTo("first@vaadin.com");
    }

    @Test
    void neverOverwritesLocalRolesFromGoogle() {
        // Google carries no roles; provisioning must leave the admin's ADMIN
        // intact and never downgrade it to USER on claim.
        var principal = service.provision(
                google("role-sub", adminEmail, "JC", true, "vaadin.com"));
        assertThat(principal.getRoles()).containsExactly(Role.ADMIN);

        // A subsequent login still resolves by sub to the same ADMIN row.
        var again = service.provision(
                google("role-sub", adminEmail, "JC", true, "vaadin.com"));
        assertThat(again.getRoles()).containsExactly(Role.ADMIN);
    }

    private static OidcUser google(String sub, String email, String name,
            boolean emailVerified, String hd) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        claims.put("email", email);
        claims.put("email_verified", emailVerified);
        if (name != null) {
            claims.put("name", name);
        }
        if (hd != null) {
            claims.put("hd", hd);
        }
        var idToken = new OidcIdToken("id-token-" + sub,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), claims);
        return new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
    }
}

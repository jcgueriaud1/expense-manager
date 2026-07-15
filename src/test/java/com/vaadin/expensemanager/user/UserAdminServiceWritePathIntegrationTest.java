package com.vaadin.expensemanager.user;

import java.util.Set;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.security.LocalUserDetailsService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Write-path service + lockout-guard integration test (pyramid layer 2, ADR-0012,
 * ADR-0008) for {@link UserAdminService} on Testcontainers Postgres (issue #65).
 *
 * <p>Covers the mutating half of UC-006: promote/demote with the canonical stored
 * role set, revoke/restore, and every lockout guard — last-enabled-admin and
 * self-demote/self-disable — proving they hold at the service layer, not just the
 * UI.
 *
 * <p>Authenticates as the seeded bootstrap admin by writing the context on the
 * app's <strong>global</strong> {@code SecurityContextHolder} in {@link
 * BeforeEach} rather than via {@code @WithUserDetails}: the guards resolve the
 * acting admin through {@code CurrentUserProvider} → Vaadin's
 * {@code AuthenticationContext}, which reads the global strategy, whereas
 * {@code @WithUserDetails} writes to its own holder that the service no longer
 * reads once a browserless context has installed Vaadin's strategy globally
 * (finding F-020). Writing and reading through the same holder here is
 * order-independent.
 */
class UserAdminServiceWritePathIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@vaadin.com";
    private static final String OTHER_ADMIN_EMAIL = "other.admin@vaadin.com";

    @Autowired
    private UserAdminService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocalUserDetailsService userDetailsService;

    @Autowired
    private SecurityContextHolderStrategy securityContextHolderStrategy;

    @BeforeEach
    void authenticateAsBootstrapAdmin() {
        SecurityContextHolder.setContextHolderStrategy(securityContextHolderStrategy);
        var principal = userDetailsService.loadUserByUsername(ADMIN_EMAIL);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", principal.getAuthorities());
        var context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
    }

    @AfterEach
    void clearAuthentication() {
        securityContextHolderStrategy.clearContext();
    }

    // ------------------------------------------------------------ happy paths

    @Test
    void promoteUserToAdminStoresCanonicalAdminSet() {
        var id = plainUserId();

        var dto = service.setRole(id, Role.ADMIN);

        assertThat(dto.role()).isEqualTo(Role.ADMIN);
        assertThat(storedRoles(id)).containsExactly(Role.ADMIN);
    }

    @Test
    void demoteAdminToUserStoresCanonicalUserSet() {
        var other = seedEnabledAdmin();

        var dto = service.setRole(other.getId(), Role.USER);

        assertThat(dto.role()).isEqualTo(Role.USER);
        // Exactly {USER} — never a redundant {USER, ADMIN}.
        assertThat(storedRoles(other.getId())).containsExactly(Role.USER);
    }

    @Test
    void revokeThenRestorePersistsEnabledFlag() {
        var id = plainUserId();

        assertThat(service.setEnabled(id, false).enabled()).isFalse();
        assertThat(userRepository.findById(id).orElseThrow().isEnabled()).isFalse();

        assertThat(service.setEnabled(id, true).enabled()).isTrue();
        assertThat(userRepository.findById(id).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void nonLastNonSelfAdminCanBeDisabled() {
        var other = seedEnabledAdmin();

        var dto = service.setEnabled(other.getId(), false);

        assertThat(dto.enabled()).isFalse();
        assertThat(userRepository.findById(other.getId()).orElseThrow().isEnabled())
                .isFalse();
    }

    // ------------------------------------------------------- last-admin guards

    @Test
    void cannotDemoteTheLastAdmin() {
        var id = adminId(); // the bootstrap admin is the only admin

        assertThatThrownBy(() -> service.setRole(id, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last administrator");

        assertThat(storedRoles(id)).containsExactly(Role.ADMIN);
    }

    @Test
    void cannotDisableTheLastAdmin() {
        var id = adminId();

        assertThatThrownBy(() -> service.setEnabled(id, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last administrator");

        assertThat(userRepository.findById(id).orElseThrow().isEnabled()).isTrue();
    }

    // ------------------------------------------------------------- self guards

    @Test
    void cannotRemoveOwnAdminRole() {
        seedEnabledAdmin(); // another admin exists, so this is not the last-admin case
        var id = adminId();

        assertThatThrownBy(() -> service.setRole(id, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own");

        assertThat(storedRoles(id)).containsExactly(Role.ADMIN);
    }

    @Test
    void cannotDisableOwnAccount() {
        seedEnabledAdmin();
        var id = adminId();

        assertThatThrownBy(() -> service.setEnabled(id, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own");

        assertThat(userRepository.findById(id).orElseThrow().isEnabled()).isTrue();
    }

    // --------------------------------------------------------------- helpers

    private Long adminId() {
        return userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
    }

    private Long plainUserId() {
        return userRepository.findByEmail(LocalUserSeeder.PLAIN_USER_EMAIL)
                .orElseThrow().getId();
    }

    private User seedEnabledAdmin() {
        return userRepository.saveAndFlush(
                new User(OTHER_ADMIN_EMAIL, "Other Admin", Set.of(Role.ADMIN)));
    }

    private Set<Role> storedRoles(Long id) {
        return userRepository.findById(id).orElseThrow().getRoles();
    }
}

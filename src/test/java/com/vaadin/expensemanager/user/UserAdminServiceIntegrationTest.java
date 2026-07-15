package com.vaadin.expensemanager.user;

import java.util.Set;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service + method-security integration test (pyramid layer 2, ADR-0012,
 * ADR-0008) for {@link UserAdminService}, on Testcontainers Postgres (issue
 * #64).
 *
 * <p>Covers the DTO-boundary read path an admin drives through the Users screen
 * — every user is listed with its effective single role and enabled/revoked
 * status — and the two-layer authorization contract: {@link
 * UserAdminService#list()} is ADMIN-only, so a plain USER calling it is
 * rejected independently of the route.
 */
class UserAdminServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserAdminService service;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listReturnsEverySeededUserWithEffectiveRoleAndStatus() {
        var users = service.list();

        // The seeded bootstrap admin and the local/test plain user both appear.
        var admin = users.stream()
                .filter(u -> u.email().equals("admin@vaadin.com"))
                .findFirst().orElseThrow();
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.enabled()).isTrue();

        var plain = users.stream()
                .filter(u -> u.email().equals(LocalUserSeeder.PLAIN_USER_EMAIL))
                .findFirst().orElseThrow();
        assertThat(plain.role()).isEqualTo(Role.USER);
        assertThat(plain.enabled()).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void effectiveRoleCollapsesStoredSetAndReflectsRevokedStatus() {
        var revokedAdmin = new User("revoked.admin@vaadin.com", "Revoked Admin",
                Set.of(Role.ADMIN));
        revokedAdmin.setEnabled(false);
        userRepository.save(revokedAdmin);

        var dto = service.list().stream()
                .filter(u -> u.email().equals("revoked.admin@vaadin.com"))
                .findFirst().orElseThrow();

        // Stored set {ADMIN} collapses to the single effective role ADMIN.
        assertThat(dto.role()).isEqualTo(Role.ADMIN);
        assertThat(dto.enabled()).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listIsOrderedByNameAscending() {
        var names = service.list().stream().map(UserSummaryDto::name).toList();
        assertThat(names).isSorted();
    }

    @Test
    @WithMockUser(roles = "USER")
    void plainUserIsRejected() {
        assertThatThrownBy(() -> service.list())
                .isInstanceOf(AccessDeniedException.class);
    }
}

package com.vaadin.expensemanager.security;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import com.vaadin.expensemanager.user.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Method-security slice (pyramid layer 2, ADR-0008, ADR-0012): the reusable test
 * seam for two-layer authorization. It exercises the enforcement layer directly
 * against a real {@code @RolesAllowed}-guarded bean using Spring Security's test
 * authentication, not a real login.
 *
 * <p>Anchored on {@link UserAdminService} — the concrete admin service that
 * replaced the Phase 1.2 stand-in (issue #64) — this covers the config half of
 * the contract: the {@code ADMIN > USER} {@link RoleHierarchy} bean, plus a
 * representative check that an ADMIN-guarded operation admits an admin and
 * rejects a plain USER. Per-service behaviour is covered by each service's own
 * layer-2 test (e.g. {@code UserAdminServiceIntegrationTest},
 * {@code ReferenceDataServiceIntegrationTest}, whose ADMIN reads reaching
 * USER-guarded queries also prove the hierarchy at the method level).
 */
class MethodSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private RoleHierarchy roleHierarchy;

    @Test
    void roleHierarchyExpandsAdminToUser() {
        var reachable = roleHierarchy.getReachableGrantedAuthorities(
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(reachable)
                .extracting(a -> a.getAuthority())
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminMayCallAdminOperation() {
        assertThat(userAdminService.list()).isNotEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    void userMayNotCallAdminOperation() {
        assertThatThrownBy(userAdminService::list)
                .isInstanceOf(AccessDeniedException.class);
    }
}

package com.vaadin.expensemanager.security;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
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
 * against a real {@code @RolesAllowed}-guarded bean — {@link
 * StandInPrivilegedService} — using Spring Security's test authentication, not a
 * real login. Later phases point the same slice at their concrete admin services
 * (approve/reject, user management) once those replace the stand-in.
 *
 * <p>Covers the acceptance criteria: a plain USER is rejected from the
 * ADMIN-guarded operation; an ADMIN is allowed; and an ADMIN — who stores only
 * {@code {ADMIN}} — passes a USER-guarded operation through the {@code ADMIN >
 * USER} {@link RoleHierarchy} without holding a second role.
 */
class MethodSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StandInPrivilegedService privilegedService;

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
        assertThat(privilegedService.privilegedAdminOperation())
                .isEqualTo("privileged-admin-operation-executed");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminMayCallUserOperationViaHierarchy() {
        // The admin stores only ROLE_ADMIN; the hierarchy grants USER access.
        assertThat(privilegedService.userLevelOperation())
                .isEqualTo("user-level-operation-executed");
    }

    @Test
    @WithMockUser(roles = "USER")
    void userMayCallUserOperation() {
        assertThat(privilegedService.userLevelOperation())
                .isEqualTo("user-level-operation-executed");
    }

    @Test
    @WithMockUser(roles = "USER")
    void userMayNotCallAdminOperation() {
        assertThatThrownBy(privilegedService::privilegedAdminOperation)
                .isInstanceOf(AccessDeniedException.class);
    }
}

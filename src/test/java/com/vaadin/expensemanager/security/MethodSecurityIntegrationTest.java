package com.vaadin.expensemanager.security;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Method-security slice (pyramid layer 2, ADR-0008, ADR-0012): verifies that
 * {@code @PreAuthorize} is enforced on service methods and that the
 * {@code ADMIN > USER} {@link RoleHierarchy} grants an admin — who stores only
 * {@code {ADMIN}} — access to USER-guarded methods at runtime.
 */
@Import(MethodSecurityIntegrationTest.GuardedService.class)
class MethodSecurityIntegrationTest extends AbstractIntegrationTest {

    /** A minimal guarded bean standing in for real approve()/admin services. */
    @TestConfiguration
    static class GuardedService {
        @Bean
        Guarded guarded() {
            return new Guarded();
        }
    }

    static class Guarded {
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "admin-ok";
        }

        @PreAuthorize("hasRole('USER')")
        String userLevel() {
            return "user-ok";
        }
    }

    @Autowired
    private Guarded guarded;

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
    void adminMayCallAdminMethod() {
        assertThat(guarded.adminOnly()).isEqualTo("admin-ok");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminMayCallUserMethodViaHierarchy() {
        // The admin stores only ROLE_ADMIN; the hierarchy grants USER access.
        assertThat(guarded.userLevel()).isEqualTo("user-ok");
    }

    @Test
    @WithMockUser(roles = "USER")
    void userMayCallUserMethod() {
        assertThat(guarded.userLevel()).isEqualTo("user-ok");
    }

    @Test
    @WithMockUser(roles = "USER")
    void userMayNotCallAdminMethod() {
        assertThatThrownBy(() -> guarded.adminOnly())
                .isInstanceOf(AccessDeniedException.class);
    }
}

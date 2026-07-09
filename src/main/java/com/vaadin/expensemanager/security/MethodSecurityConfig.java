package com.vaadin.expensemanager.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Global authorization wiring shared by every profile (ADR-0008).
 *
 * <p>Turns on method security ({@code @PreAuthorize}/{@code @RolesAllowed} on
 * service methods — the real enforcement point, defense in depth) and declares
 * the {@code ADMIN > USER} {@link RoleHierarchy}. Expressing subsumption once as
 * a bean means an admin stores only {@code {ADMIN}} yet resolves USER access at
 * runtime; Spring Security applies the hierarchy to both method checks and
 * Vaadin route access, so stored roles and effective authorities differ by
 * design.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("USER")
                .build();
    }
}

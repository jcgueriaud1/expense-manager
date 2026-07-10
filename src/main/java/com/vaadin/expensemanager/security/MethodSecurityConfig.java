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
 *
 * <p>{@code jsr250Enabled = true} makes Spring method security honour
 * {@code @RolesAllowed} (JSR-250) — <em>off</em> by default, unlike
 * {@code @PreAuthorize}. This lets service methods and Vaadin route views wear
 * the <em>same</em> {@code jakarta.annotation.security.RolesAllowed} annotation
 * (F-012): route security reads it for navigation UX, method security reads it
 * for the real enforcement, so the two layers stay legible as one vocabulary.
 */
@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
public class MethodSecurityConfig {

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("USER")
                .build();
    }
}

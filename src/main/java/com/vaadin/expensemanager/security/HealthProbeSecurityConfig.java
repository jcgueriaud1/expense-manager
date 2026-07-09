package com.vaadin.expensemanager.security;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opens the Actuator health endpoint (and its {@code liveness}/{@code readiness}
 * probe groups) to unauthenticated access (ADR-0013, Phase 0.8).
 *
 * <p>Container orchestration gates traffic and restarts on these probes, and a
 * Docker healthcheck / k8s probe cannot log in — so the probes must be reachable
 * without authentication. This is a dedicated, high-priority filter chain scoped
 * (via {@link EndpointRequest}) to the health endpoint only; every other request
 * still falls through to the application's own security (the full Vaadin route +
 * method security lands in Phase 1.4). See friction log F-003.
 */
@Configuration
public class HealthProbeSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain healthProbeFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.to(HealthEndpoint.class))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}

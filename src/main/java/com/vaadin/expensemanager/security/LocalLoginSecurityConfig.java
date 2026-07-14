package com.vaadin.expensemanager.security;

import com.vaadin.expensemanager.security.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Vaadin route security + the {@code local}/{@code test} form-stub login
 * (ADR-0008, ADR-0012).
 *
 * <p>{@link VaadinSecurityConfigurer} wires Vaadin navigation access control
 * (annotation-driven: login public, {@code @PermitAll} views authenticated,
 * {@code @RolesAllowed} views role-gated) and enables {@code formLogin} against
 * {@link LoginView}. Authentication resolves through the auto-configured
 * {@code DaoAuthenticationProvider} over {@link LocalUserDetailsService} + the
 * {@link PasswordEncoder} below, so the stub carries production-identical
 * authorities.
 *
 * <p>The catch-all rule is relaxed to permit unmatched requests so the custom
 * {@code NotFoundView} still renders (real routes stay guarded by navigation
 * access control); the health-probe chain ({@code @Order(1)}) keeps precedence.
 *
 * <p>Profile-scoped: {@code staging}/{@code prod} swap this for real Google
 * OAuth (ADR-0007, ADR-0013, Phase 1.2).
 */
@Configuration
@Profile({"local", "test"})
@EnableConfigurationProperties(DevLoginProperties.class)
public class LocalLoginSecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain vaadinFormLoginFilterChain(HttpSecurity http) throws Exception {
        return http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer
                .loginView(LoginView.class)
                .anyRequest(auth -> auth.permitAll())
        ).build();
    }
}

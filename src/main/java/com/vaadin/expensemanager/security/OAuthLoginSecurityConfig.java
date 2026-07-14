package com.vaadin.expensemanager.security;

import com.vaadin.expensemanager.security.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Vaadin route security + real Google OAuth2 login for the Google-backed
 * profiles {@code staging}/{@code prod}/{@code vherd} (ADR-0007, ADR-0008,
 * ADR-0013).
 *
 * <p>The Google-login counterpart of {@link LocalLoginSecurityConfig}:
 * instead of the form-stub it wires Spring's OAuth2 login against the {@code google}
 * client registration, with {@link ProvisioningOidcUserService} as the
 * {@code OidcUserService} so the domain gate + claim/create runs at login and the
 * returned principal carries local roles.
 *
 * <p>{@link VaadinSecurityConfigurer#loginView(Class)} keeps {@link LoginView} as
 * the navigation login target (unauthenticated navigation → {@code /login}); the
 * view renders a "Sign in with Google" link to {@code /oauth2/authorization/google}
 * on these profiles rather than the email/password form. A rejected login is sent
 * back to the login view by {@link OAuthLoginFailureHandler} with a message-code
 * query parameter. The catch-all is relaxed so the custom {@code NotFoundView}
 * still renders; the health-probe chain ({@code @Order(1)}) keeps precedence.
 */
@Configuration
@Profile({"staging", "prod", "vherd"})
public class OAuthLoginSecurityConfig {

    @Bean
    SecurityFilterChain vaadinOauthFilterChain(HttpSecurity http,
            ProvisioningOidcUserService oidcUserService) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer
                .loginView(LoginView.class)
                .anyRequest(auth -> auth.permitAll())
        );
        http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                .failureHandler(new OAuthLoginFailureHandler())
        );
        return http.build();
    }
}

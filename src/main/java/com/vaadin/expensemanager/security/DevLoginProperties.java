package com.vaadin.expensemanager.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single shared dev password backing the {@code local}/{@code test}
 * form-stub login (ADR-0012, ADR-0013).
 *
 * <p>Bound from {@code app.security.dev-login.password}, supplied per
 * environment via the {@code DEV_LOGIN_PASSWORD} env var with a safe local
 * default. Never used in {@code staging}/{@code prod}, which authenticate via
 * real Google OAuth.
 */
@ConfigurationProperties(prefix = "app.security.dev-login")
public record DevLoginProperties(String password) {
}

package com.vaadin.expensemanager.security.ui;

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.core.env.Environment;

/**
 * Public login page (UC-007) that branches per profile between the two login
 * paths that both land on the same local {@code User} records and authorities.
 *
 * <p>On {@code local}/{@code test} it hosts the form-stub sign-in (ADR-0012): a
 * {@link LoginForm} POSTing natively to Spring Security's {@code /login} endpoint,
 * authenticating by email + the shared dev password. On {@code staging}/
 * {@code prod} it instead offers a "Sign in with Google" link to
 * {@code /oauth2/authorization/google}, which kicks off the real OAuth2 flow with
 * domain-gated provisioning (ADR-0007); the form is not rendered there.
 *
 * <p>Standalone ({@code autoLayout = false}) so it renders outside the
 * authenticated {@link com.vaadin.expensemanager.base.ui.MainLayout} shell, and
 * {@link AnonymousAllowed} so unauthenticated users can reach it. A failed login
 * (form error, or an OAuth rejection routed here by
 * {@code OAuthLoginFailureHandler}) arrives with an {@code error} query
 * parameter that {@link #beforeEnter} turns into the appropriate message —
 * distinguishing "limited to vaadin.com accounts" (wrong domain / unverified)
 * from "access disabled — contact an administrator" (disabled user).
 */
@Route(value = "login", autoLayout = false)
@PageTitle("Sign in · Expense Manager")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final boolean oauthMode;
    private final String publicBasePath;
    private final LoginForm loginForm = new LoginForm();
    private final Paragraph errorMessage = new Paragraph();

    public LoginView(Environment environment) {
        this.oauthMode = environment.matchesProfiles("staging", "prod", "vherd");
        // Public reverse-proxy path prefix for browser-facing links: empty at the
        // root (staging/prod), "/expense-manager" on vherd. NOT the servlet
        // context-path — V-Herd serves the app under /expense-manager but strips
        // that prefix before forwarding, so the container runs at root; only the
        // browser needs the prefix (e.g. on the Google authorize link below).
        this.publicBasePath = environment.getProperty("app.public-base-path", "");

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        errorMessage.setVisible(false);
        errorMessage.getStyle().setColor("var(--aura-red-text)");

        if (oauthMode) {
            buildGoogleSignIn();
        } else {
            buildFormStubSignIn();
        }
    }

    private void buildGoogleSignIn() {
        var title = new H1("Expense Manager");

        // A native <a href> (router-ignored) so the click reaches Spring's
        // authorization endpoint as a full navigation, not client-side routing.
        // Prefixed with the public base path so it resolves to the correct public
        // URL under a sub-path deployment (e.g.
        // https://v-herd.eu/expense-manager/oauth2/authorization/google on vherd).
        var googleLogin = new Anchor(publicBasePath + "/oauth2/authorization/google",
                new Button("Sign in with Google"));
        googleLogin.setRouterIgnore(true);

        var hint = new Paragraph("Sign in with your vaadin.com Google account.");
        hint.getStyle().setColor("var(--vaadin-text-color-secondary)");

        add(title, errorMessage, googleLogin, hint);
        setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER,
                title, errorMessage, googleLogin, hint);
    }

    private void buildFormStubSignIn() {
        var i18n = LoginI18n.createDefault();
        var header = new LoginI18n.Header();
        header.setTitle("Expense Manager");
        header.setDescription("Sign in with your email and dev password");
        i18n.setHeader(header);
        i18n.getForm().setUsername("Email");
        loginForm.setI18n(i18n);

        // Native POST to Spring Security's form-login processing URL.
        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        var hint = new Paragraph(
                "Local/test sign-in: use a seeded account's email with the dev password.");
        hint.getStyle().setColor("var(--vaadin-text-color-secondary)");

        add(loginForm, hint);
        setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, loginForm, hint);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Security (form login) and OAuthLoginFailureHandler both append
        // ?error on a failed-authentication redirect; the OAuth handler adds a
        // code distinguishing the rejection reason.
        var errorParam = event.getLocation().getQueryParameters()
                .getParameters().get("error");
        if (errorParam == null) {
            return;
        }
        showError(errorParam.isEmpty() ? "" : errorParam.get(0));
    }

    private void showError(String code) {
        if (oauthMode) {
            errorMessage.setText(switch (code) {
                case "domain" -> "Sign-in is limited to vaadin.com accounts.";
                case "disabled" -> "Your access is disabled — contact an administrator.";
                default -> "Sign-in failed. Please try again.";
            });
            errorMessage.setVisible(true);
        } else {
            // The form-stub surfaces failures through the LoginForm's own i18n.
            loginForm.setError(true);
        }
    }
}

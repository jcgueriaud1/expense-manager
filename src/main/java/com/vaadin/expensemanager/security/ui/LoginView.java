package com.vaadin.expensemanager.security.ui;

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

/**
 * Public login page hosting the form-stub sign-in (ADR-0012, UC-007).
 *
 * <p>Standalone ({@code autoLayout = false}) so it renders outside the
 * authenticated {@link com.vaadin.expensemanager.base.ui.MainLayout} shell, and
 * {@link AnonymousAllowed} so unauthenticated users can reach it. The
 * {@link LoginForm} submits natively to Spring Security's {@code /login}
 * processing endpoint wired by {@code VaadinSecurityConfigurer}; authentication
 * is by email + the shared dev password.
 *
 * <p>On the {@code local}/{@code test} profiles the seeded accounts are the
 * bootstrap admin and {@code user@vaadin.com}; both use the dev password.
 */
@Route(value = "login", autoLayout = false)
@PageTitle("Sign in · Expense Manager")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

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
        hint.getStyle().setColor("var(--lumo-secondary-text-color)");

        add(loginForm, hint);
        setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, loginForm, hint);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Security appends ?error on a failed authentication redirect.
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}

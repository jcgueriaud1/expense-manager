package com.vaadin.expensemanager.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/**
 * Redirects a rejected Google login back to {@link com.vaadin.expensemanager.security.ui.LoginView}
 * with an {@code error} code the view turns into the right message (ADR-0007).
 *
 * <p>The {@link UserProvisioningService} throws
 * {@link OAuth2AuthenticationException}s whose {@code OAuth2Error} code
 * distinguishes the domain gate from a disabled user; this handler maps those
 * onto the login view's query parameter so the UX can say
 * <em>"limited to vaadin.com accounts"</em> vs <em>"access disabled"</em>.
 * Anything else (a real OAuth protocol error, the hijack guard) falls through to
 * a generic message.
 */
public class OAuthLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        getRedirectStrategy().sendRedirect(request, response, "/login?error=" + errorCode(exception));
    }

    private static String errorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            var code = oauthException.getError().getErrorCode();
            if (UserProvisioningService.ERROR_DOMAIN.equals(code)) {
                return "domain";
            }
            if (UserProvisioningService.ERROR_DISABLED.equals(code)) {
                return "disabled";
            }
        }
        return "generic";
    }
}

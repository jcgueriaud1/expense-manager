package com.vaadin.expensemanager.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * The {@code OidcUserService} Spring's OAuth2 login invokes at Google sign-in
 * (ADR-0007, Phase 1.3): a thin adapter that fetches the Google-issued
 * {@link OidcUser} via the default flow and then hands it to
 * {@link UserProvisioningService} for the domain gate + claim/create policy.
 *
 * <p>Kept deliberately free of {@code @Transactional} (and any advice) so it is
 * <em>not</em> wrapped in a CGLIB proxy — {@code OidcUserService} declares
 * {@code final} setters that such a proxy cannot handle. The transaction lives on
 * {@link UserProvisioningService#provision(OidcUser)} instead.
 *
 * <p>Wired into the filter chain only on {@code staging}/{@code prod}
 * ({@link OAuthLoginSecurityConfig}); the form-stub (ADR-0012) replaces it in
 * {@code local}/{@code test}.
 */
@Service
public class ProvisioningOidcUserService extends OidcUserService {

    private final UserProvisioningService provisioningService;

    public ProvisioningOidcUserService(UserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return provisioningService.provision(super.loadUser(userRequest));
    }
}

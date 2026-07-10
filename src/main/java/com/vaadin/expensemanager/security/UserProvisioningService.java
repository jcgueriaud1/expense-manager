package com.vaadin.expensemanager.security;

import java.util.Set;

import com.vaadin.expensemanager.user.Role;
import com.vaadin.expensemanager.user.User;
import com.vaadin.expensemanager.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The domain-gated auto-provisioning policy at Google login (ADR-0007, Phase 1.3).
 *
 * <p>Given the Google-issued {@link OidcUser}, in one transaction it
 * <ol>
 *   <li><strong>gates</strong> on {@code hd == "vaadin.com"} <em>and</em>
 *       {@code email_verified == true} — anything else is rejected as
 *       {@link #ERROR_DOMAIN};</li>
 *   <li>resolves the local {@link User}: look up by Google {@code sub}; on miss,
 *       <em>claim</em> an {@code email}-matching row whose {@code sub} is still
 *       {@code null} (populating {@code sub}/{@code name}, preserving its role —
 *       this is how the bootstrap admin keeps {@code ADMIN}); on a full miss,
 *       create a fresh {@code USER} linked to the {@code sub};</li>
 *   <li>refuses a locally-{@code disabled} user as {@link #ERROR_DISABLED},
 *       even with a valid Google account (the "revoke access" lever).</li>
 * </ol>
 *
 * <p>{@code name}/{@code email} are set once at provision/claim and never
 * re-synced; {@code roles}/{@code enabled} are the local source of truth and are
 * never overwritten by Google.
 *
 * <p>Deliberately a plain {@code @Service} rather than logic bolted onto the
 * {@link ProvisioningOidcUserService} adapter: {@code OidcUserService} has
 * {@code final} setters that a CGLIB transactional proxy cannot handle, and this
 * split also lets the provisioning policy be driven directly in tests with a
 * synthesized {@link OidcUser} — no live Google exchange (ADR-0012, F-014).
 */
@Service
public class UserProvisioningService {

    /** Wrong Google Workspace domain, or an unverified email. */
    public static final String ERROR_DOMAIN = "vaadin_domain_required";

    /** A known user whose local {@code enabled} flag has been turned off. */
    public static final String ERROR_DISABLED = "access_disabled";

    /** Email already linked to a different Google {@code sub} (hijack guard). */
    public static final String ERROR_CONFLICT = "email_sub_conflict";

    static final String ALLOWED_DOMAIN = "vaadin.com";

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    private final UserRepository userRepository;

    public UserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Runs the gate + claim/create policy for a Google login.
     *
     * @param oidcUser the Google-issued principal (real, or synthesized in tests)
     * @return the local {@link AppOidcUser} whose authorities are the local roles
     * @throws OAuth2AuthenticationException if the domain gate or the enabled
     *         check rejects the login
     */
    @Transactional
    public AppOidcUser provision(OidcUser oidcUser) throws OAuth2AuthenticationException {
        var sub = oidcUser.getSubject();
        var email = oidcUser.getEmail();
        var hd = oidcUser.getClaimAsString("hd");
        var emailVerified = Boolean.TRUE.equals(oidcUser.getEmailVerified());

        if (!ALLOWED_DOMAIN.equals(hd) || !emailVerified) {
            log.info("Rejected Google login for {} (hd={}, email_verified={})",
                    email, hd, emailVerified);
            throw reject(ERROR_DOMAIN, "Sign-in is limited to vaadin.com accounts.");
        }

        var user = resolveUser(sub, email, displayName(oidcUser));

        if (!user.isEnabled()) {
            log.info("Rejected Google login for disabled user {}", email);
            throw reject(ERROR_DISABLED, "Access is disabled — contact an administrator.");
        }

        return new AppOidcUser(user, oidcUser);
    }

    private User resolveUser(String sub, String email, String name) {
        var bySub = userRepository.findBySub(sub);
        if (bySub.isPresent()) {
            return bySub.get();
        }

        var byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            var existing = byEmail.get();
            if (existing.getSub() != null) {
                // Same email, different sub: the row is already linked to another
                // Google identity. Claiming it would be a hijack (ADR-0007).
                log.warn("Email {} already linked to a different Google sub", email);
                throw reject(ERROR_CONFLICT, "Sign-in failed — contact an administrator.");
            }
            existing.claim(sub, name);
            log.info("Claimed pre-existing row for {} (roles preserved)", email);
            return userRepository.save(existing);
        }

        var created = new User(email, name, Set.of(Role.USER));
        // Link the fresh row to the Google identity so the next login resolves it
        // by sub (claim only sets sub/name here — sub starts null on a new row).
        created.claim(sub, name);
        log.info("Provisioned new USER for {}", email);
        return userRepository.save(created);
    }

    /** Google's display name, falling back to the email if absent. */
    private static String displayName(OidcUser oidcUser) {
        var name = oidcUser.getFullName();
        return (name != null && !name.isBlank()) ? name : oidcUser.getEmail();
    }

    private static OAuth2AuthenticationException reject(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null), description);
    }
}

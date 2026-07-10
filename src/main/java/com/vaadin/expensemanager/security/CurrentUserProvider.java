package com.vaadin.expensemanager.security;

import java.util.Optional;

import com.vaadin.expensemanager.user.CurrentUser;
import com.vaadin.flow.spring.security.AuthenticationContext;

import org.springframework.stereotype.Component;

/**
 * Accessor that adapts the authenticated principal into an immutable
 * {@link CurrentUser} record (ADR-0008).
 *
 * <p>Reads the current {@link AppUserPrincipal} from Vaadin's
 * {@link AuthenticationContext} (backed by the Spring security context) and maps
 * its captured id/email/name/roles into the record — <strong>no per-call DB
 * query</strong>. Works for both login paths because both principals implement
 * {@link AppUserPrincipal}.
 */
@Component
public class CurrentUserProvider {

    private final AuthenticationContext authenticationContext;

    public CurrentUserProvider(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    /**
     * The logged-in user as an immutable record, or empty if the request is
     * unauthenticated.
     */
    public Optional<CurrentUser> get() {
        return authenticationContext.getAuthenticatedUser(AppUserPrincipal.class)
                .map(CurrentUserProvider::toRecord);
    }

    /**
     * The logged-in user, or a failure if there is none — for call sites (guarded
     * views/services) where absence is a programming error, not a branch.
     */
    public CurrentUser require() {
        return get().orElseThrow(() -> new IllegalStateException(
                "No authenticated user in the current security context"));
    }

    static CurrentUser toRecord(AppUserPrincipal principal) {
        return new CurrentUser(principal.getId(), principal.getEmail(),
                principal.getName(), principal.getRoles());
    }
}

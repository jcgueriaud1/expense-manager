package com.vaadin.expensemanager.security;

import java.util.Set;

import com.vaadin.expensemanager.user.CurrentUser;
import com.vaadin.expensemanager.user.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test (pyramid layer 1) for adapting an {@link AppUserPrincipal} into
 * the immutable {@link CurrentUser} record (ADR-0008).
 *
 * <p>Runs with no Spring context and no repository — which is the point: the
 * accessor builds the record from fields the principal already carries, so
 * resolving the current user costs <strong>no per-call DB query</strong>. The
 * stored roles pass through unchanged (an admin holds only {@code {ADMIN}}); the
 * {@code ADMIN ⊇ USER} expansion is a runtime authorization concern, not part of
 * this record.
 */
class CurrentUserProviderTest {

    private record StubPrincipal(Long getId, String getEmail, String getName,
            Set<Role> getRoles) implements AppUserPrincipal {
    }

    @Test
    void adaptsPrincipalFieldsIntoRecord() {
        var principal = new StubPrincipal(7L, "user@vaadin.com", "Demo User",
                Set.of(Role.USER));

        CurrentUser current = CurrentUserProvider.toRecord(principal);

        assertThat(current.id()).isEqualTo(7L);
        assertThat(current.email()).isEqualTo("user@vaadin.com");
        assertThat(current.name()).isEqualTo("Demo User");
        assertThat(current.roles()).containsExactly(Role.USER);
        assertThat(current.isAdmin()).isFalse();
    }

    @Test
    void adminStoringOnlyAdminRoleIsReportedAsAdmin() {
        var principal = new StubPrincipal(1L, "admin@vaadin.com", "Expense Admin",
                Set.of(Role.ADMIN));

        CurrentUser current = CurrentUserProvider.toRecord(principal);

        // Stored roles are preserved verbatim — subsumption is not baked in here.
        assertThat(current.roles()).containsExactly(Role.ADMIN);
        assertThat(current.isAdmin()).isTrue();
    }

    @Test
    void recordRolesAreImmutable() {
        var current = new CurrentUser(1L, "a@vaadin.com", "A", Set.of(Role.USER));

        assertThat(current.roles()).isUnmodifiable();
    }
}

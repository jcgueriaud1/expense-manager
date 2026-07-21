package com.vaadin.expensemanager.user;

import java.util.List;
import java.util.Set;

import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.expensemanager.security.CurrentUserProvider;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN-only service for user administration (issue #64, #65, Phase 6).
 *
 * <p>Owns the transaction boundary and entity↔DTO mapping (ADR-0003): the
 * {@link User} entity never leaves this class; callers exchange
 * {@link UserSummaryDto} records.
 *
 * <p><strong>Two-layer authorization (ADR-0008).</strong> Every method is
 * {@code @RolesAllowed("ADMIN")} — the real enforcement point, independent of
 * the ADMIN-only route that hosts the users screen. A plain USER invoking any
 * method (read or mutate) is rejected with an {@code AccessDeniedException} even
 * if the route guard is bypassed.
 *
 * <p><strong>Write path (#65).</strong> {@link #setRole(Long, Role)} and
 * {@link #setEnabled(Long, boolean)} are the mutation levers behind the Users
 * editor. Both enforce <strong>lockout guards in the service</strong> (not just
 * the UI), rejected as {@link IllegalArgumentException} so the editor can surface
 * the specific message in its error summary:
 * <ul>
 *   <li>the last <em>enabled</em> ADMIN cannot be demoted or disabled — the
 *       system must always retain one administrator who can sign in; and</li>
 *   <li>the acting admin (resolved via {@link CurrentUserProvider}) cannot remove
 *       their own ADMIN role or disable their own account.</li>
 * </ul>
 * The last-admin guard is checked before the self guard, so the sole
 * administrator hits the "last administrator" message.
 *
 * <p><strong>Revocation timing is next-login</strong> (ADR-0008 V1 limitation):
 * there is no session registry or forced logout, so setting {@code enabled=false}
 * takes effect the next time that person signs in —
 * {@link com.vaadin.expensemanager.security.UserProvisioningService} refuses
 * disabled users at login.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public UserAdminService(UserRepository userRepository,
            CurrentUserProvider currentUserProvider) {
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Every user in the system, in a stable display order, as immutable
     * summaries (admin listing). The effective single role is derived from the
     * stored set per {@link #toDto(User)}.
     */
    @RolesAllowed("ADMIN")
    @Transactional(readOnly = true)
    public List<UserSummaryDto> list() {
        return userRepository.findAllByOrderByNameAscIdAsc().stream()
                .map(UserAdminService::toDto)
                .toList();
    }

    /**
     * Sets a user's role, writing the <strong>canonical stored set</strong> —
     * {@code {ADMIN}} for admin, {@code {USER}} for user — so the representation
     * never drifts into a redundant {@code {USER, ADMIN}} combination.
     *
     * <p>Demoting an admin to user is rejected when it would remove the last
     * enabled administrator, or when the acting admin targets themselves.
     *
     * @throws IllegalArgumentException if no user has {@code userId}, or a lockout
     *         guard rejects the demotion
     */
    @RolesAllowed("ADMIN")
    @Transactional
    public UserSummaryDto setRole(Long userId, Role role) {
        var user = load(userId);
        var demoting = role == Role.USER && user.getRoles().contains(Role.ADMIN);
        if (demoting && user.isEnabled() && !anotherEnabledAdminExists(userId)) {
            throw new DomainRuleException(
                    "Cannot remove the last administrator's role");
        }
        if (demoting && isActingUser(userId)) {
            throw new DomainRuleException(
                    "You cannot remove your own administrator role");
        }
        user.setRoles(role == Role.ADMIN ? Set.of(Role.ADMIN) : Set.of(Role.USER));
        return toDto(user);
    }

    /**
     * Enables (restores) or disables (revokes) a user's access.
     *
     * <p>Disabling is rejected when it would leave no enabled administrator, or
     * when the acting admin targets their own account. Revocation takes effect at
     * the user's next login (ADR-0008): there is no forced logout in V1.
     *
     * @throws IllegalArgumentException if no user has {@code userId}, or a lockout
     *         guard rejects the disable
     */
    @RolesAllowed("ADMIN")
    @Transactional
    public UserSummaryDto setEnabled(Long userId, boolean enabled) {
        var user = load(userId);
        var disabling = !enabled && user.isEnabled();
        if (disabling && user.getRoles().contains(Role.ADMIN)
                && !anotherEnabledAdminExists(userId)) {
            throw new DomainRuleException(
                    "Cannot disable the last administrator");
        }
        if (disabling && isActingUser(userId)) {
            throw new DomainRuleException(
                    "You cannot disable your own account");
        }
        user.setEnabled(enabled);
        return toDto(user);
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(
                () -> new IllegalArgumentException("No user with id " + userId));
    }

    private boolean isActingUser(Long userId) {
        return userId.equals(currentUserProvider.require().id());
    }

    /**
     * Whether an enabled ADMIN other than {@code excludedId} exists — the invariant
     * the last-admin guards protect. The internal user base is small, so scanning
     * the whole table is cheap (mirrors the view's in-memory filtering).
     */
    private boolean anotherEnabledAdminExists(Long excludedId) {
        return userRepository.findAll().stream()
                .anyMatch(u -> !u.getId().equals(excludedId)
                        && u.isEnabled() && u.getRoles().contains(Role.ADMIN));
    }

    /**
     * Collapses the stored role set to a single effective role for display:
     * {@code ADMIN} if the set contains {@code ADMIN} (which subsumes USER,
     * ADR-0008), otherwise {@code USER}.
     */
    private static UserSummaryDto toDto(User user) {
        var role = user.getRoles().contains(Role.ADMIN) ? Role.ADMIN : Role.USER;
        return new UserSummaryDto(user.getId(), user.getEmail(), user.getName(),
                role, user.isEnabled());
    }
}

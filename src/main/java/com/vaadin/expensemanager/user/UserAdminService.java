package com.vaadin.expensemanager.user;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN-only service for user administration (issue #64, Phase 6).
 *
 * <p>Owns the transaction boundary and entity↔DTO mapping (ADR-0003): the
 * {@link User} entity never leaves this class; callers exchange
 * {@link UserSummaryDto} records.
 *
 * <p><strong>Two-layer authorization (ADR-0008).</strong> {@link #list()} is
 * {@code @RolesAllowed("ADMIN")} — the real enforcement point, independent of
 * the ADMIN-only route that hosts the users screen. A plain USER invoking it is
 * rejected with an {@code AccessDeniedException} even if the route guard is
 * bypassed.
 *
 * <p>This slice is the Phase 6 read path only — there are deliberately no
 * mutation methods yet (role changes / enable-revoke land in a later slice).
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;

    public UserAdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

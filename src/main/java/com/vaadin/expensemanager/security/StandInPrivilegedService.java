package com.vaadin.expensemanager.security;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.stereotype.Service;

/**
 * A stand-in privileged service that proves the method-security layer (ADR-0008,
 * Phase 1.2).
 *
 * <p>The real admin operations — approve/reject (Phase 5), user management
 * (Phase 6), rate/type config (Phase 2/4) — do not exist yet, so this bean holds
 * placeholder operations whose only job is to demonstrate and lock in the
 * enforcement pattern that every later phase reuses:
 *
 * <ul>
 *   <li>{@link #privilegedAdminOperation()} is guarded by
 *       {@code @RolesAllowed("ADMIN")} — the <em>real</em> enforcement point.
 *       Route security (which view you can navigate to) drives UX; this method
 *       check is what actually stops a privileged operation from running, so a
 *       bypassed route guard still cannot invoke it (defense in depth).</li>
 *   <li>{@link #userLevelOperation()} is guarded by {@code @RolesAllowed("USER")}
 *       and exists to prove the {@code ADMIN > USER} {@code RoleHierarchy}
 *       ({@link MethodSecurityConfig}): an admin who stores only {@code {ADMIN}}
 *       passes it without holding a second role.</li>
 * </ul>
 *
 * <p>When the concrete admin services land, they annotate their methods the same
 * way and reuse the method-security test slice this class anchors; this stand-in
 * is then deleted.
 */
@Service
public class StandInPrivilegedService {

    /**
     * Placeholder for a real ADMIN-only operation (e.g. approve a report, change
     * a user's roles). Guarded by {@code @RolesAllowed("ADMIN")}; a plain USER
     * invoking it is rejected with an {@code AccessDeniedException}.
     *
     * @return a marker result confirming the call was authorized and executed
     */
    @RolesAllowed("ADMIN")
    public String privilegedAdminOperation() {
        return "privileged-admin-operation-executed";
    }

    /**
     * Placeholder for a USER-level operation, used to demonstrate that an admin
     * reaches USER-guarded methods through the {@code ADMIN > USER} role
     * hierarchy without storing a second role.
     *
     * @return a marker result confirming the call was authorized and executed
     */
    @RolesAllowed("USER")
    public String userLevelOperation() {
        return "user-level-operation-executed";
    }
}

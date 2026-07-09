package com.vaadin.expensemanager.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link User} (ADR-0003).
 *
 * <p>Stays inside the service layer — the UI never sees it or the entities it
 * returns. Lookups by {@code email} back the local form-stub
 * {@code UserDetailsService} (ADR-0012); lookups by {@code sub} back Google
 * provisioning (Phase 1.2).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findBySub(String sub);

    boolean existsByEmail(String email);
}

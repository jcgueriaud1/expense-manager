package com.vaadin.expensemanager.allowance;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link KilometreRate} (ADR-0003). Stays inside
 * the service layer — the UI never sees it or the entities it returns.
 */
public interface KilometreRateRepository extends JpaRepository<KilometreRate, Long> {

    /** The kilometre rate for a given year, if configured. */
    Optional<KilometreRate> findByYear(int year);
}

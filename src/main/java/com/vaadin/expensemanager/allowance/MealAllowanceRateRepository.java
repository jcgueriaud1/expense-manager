package com.vaadin.expensemanager.allowance;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link MealAllowanceRate} (ADR-0003). Stays
 * inside the service layer — the UI never sees it or the entities it returns.
 */
public interface MealAllowanceRateRepository extends JpaRepository<MealAllowanceRate, Long> {

    /** The meal allowance for a given year, if configured. */
    Optional<MealAllowanceRate> findByYear(int year);
}

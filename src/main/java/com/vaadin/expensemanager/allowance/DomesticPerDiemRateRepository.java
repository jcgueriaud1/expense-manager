package com.vaadin.expensemanager.allowance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link DomesticPerDiemRate} (ADR-0003). Stays
 * inside the service layer — the UI never sees it or the entities it returns.
 */
public interface DomesticPerDiemRateRepository extends JpaRepository<DomesticPerDiemRate, Long> {

    /** The domestic per-diem rate for a given year, if configured. */
    Optional<DomesticPerDiemRate> findByYear(int year);

    /** All years with a domestic per-diem row, newest first (admin listing). */
    List<DomesticPerDiemRate> findAllByOrderByYearDesc();
}

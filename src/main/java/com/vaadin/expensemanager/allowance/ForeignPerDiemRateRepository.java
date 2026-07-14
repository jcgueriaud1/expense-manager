package com.vaadin.expensemanager.allowance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ForeignPerDiemRate} (ADR-0003). Stays
 * inside the service layer — the UI never sees it or the entities it returns.
 */
public interface ForeignPerDiemRateRepository extends JpaRepository<ForeignPerDiemRate, Long> {

    /** All foreign per-diems for a year, in country order. */
    List<ForeignPerDiemRate> findByYearOrderByCountryAsc(int year);

    /** The foreign per-diem for a (year, country) pair, if configured. */
    Optional<ForeignPerDiemRate> findByYearAndCountryIgnoreCase(int year, String country);
}

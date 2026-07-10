package com.vaadin.expensemanager.reference;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link VatRate} (ADR-0003). Stays inside the
 * service layer — the UI never sees it or the entities it returns.
 */
public interface VatRateRepository extends JpaRepository<VatRate, Long> {

    /** All rates in display order (admin listing), including inactive ones. */
    List<VatRate> findAllByOrderByDisplayOrderAscIdAsc();

    /**
     * The "active options" query (ADR-0018): only {@code active} rates, in
     * display order — what new lines may choose. Deactivated rows are retained in
     * the table but excluded here.
     */
    List<VatRate> findByActiveTrueOrderByDisplayOrderAscIdAsc();
}

package com.vaadin.expensemanager.reference;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ExpenseType} (ADR-0003). Stays inside
 * the service layer — the UI never sees it or the entities it returns.
 */
public interface ExpenseTypeRepository extends JpaRepository<ExpenseType, Long> {

    /** All types in display order (admin listing), including inactive ones. */
    List<ExpenseType> findAllByOrderByDisplayOrderAscIdAsc();

    /**
     * The "active options" query (ADR-0018): only {@code active} types, in
     * display order — what new lines may choose. Deactivated rows are retained in
     * the table but excluded here.
     */
    List<ExpenseType> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    /**
     * The active expense type used for generated per-diem lines (Phase 4.2),
     * resolved by name (e.g. {@code "Travel allowance"}). See finding F-034 on the
     * name coupling.
     */
    Optional<ExpenseType> findFirstByNameIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(
            String name);
}

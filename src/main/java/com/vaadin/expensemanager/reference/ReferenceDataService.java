package com.vaadin.expensemanager.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.vaadin.expensemanager.base.DomainRuleException;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reference-data service for VAT rates and expense types (ADR-0018, Phase 2.1).
 *
 * <p>Owns the transaction boundary and entity↔DTO mapping (ADR-0003): entities
 * never leave this class; callers exchange {@link VatRateDto} /
 * {@link ExpenseTypeDto} records.
 *
 * <p><strong>Two-layer authorization (ADR-0008).</strong> Every mutating
 * operation and every full-listing (admin) read is {@code @RolesAllowed("ADMIN")}
 * — the real enforcement point, independent of the ADMIN-only route that hosts
 * the settings UI. The "active options" reads are {@code @RolesAllowed("USER")}
 * because the Phase 2.3 line editor (a USER surface) consumes them; an admin
 * reaches them through the {@code ADMIN > USER} hierarchy.
 *
 * <p><strong>History semantics (ADR-0018).</strong> Deactivation flips the
 * {@code active} flag and never deletes: {@link #setVatRateActive} /
 * {@link #setExpenseTypeActive} keep the row in the table so historical lines
 * retain their value, while the {@code active*} queries exclude it from new
 * choices. There is deliberately no delete operation.
 */
@Service
public class ReferenceDataService {

    private final VatRateRepository vatRateRepository;
    private final ExpenseTypeRepository expenseTypeRepository;

    public ReferenceDataService(VatRateRepository vatRateRepository,
            ExpenseTypeRepository expenseTypeRepository) {
        this.vatRateRepository = vatRateRepository;
        this.expenseTypeRepository = expenseTypeRepository;
    }

    // ---------------------------------------------------------------- VAT rates

    /** All VAT rates in display order (admin listing), active and inactive. */
    @RolesAllowed("ADMIN")
    @Transactional(readOnly = true)
    public List<VatRateDto> allVatRates() {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(ReferenceDataService::toDto)
                .toList();
    }

    /**
     * The active VAT rates a new line may choose, in display order (ADR-0018).
     * Excludes deactivated rows, which remain in the table for history.
     */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public List<VatRateDto> activeVatRates() {
        return vatRateRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(ReferenceDataService::toDto)
                .toList();
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public VatRateDto createVatRate(BigDecimal value) {
        var rate = new VatRate(normalizePercent(value), nextVatRateOrder());
        return toDto(vatRateRepository.save(rate));
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public VatRateDto updateVatRate(Long id, BigDecimal value) {
        var rate = requireVatRate(id);
        rate.setValue(normalizePercent(value));
        return toDto(rate);
    }

    /**
     * Deactivate ({@code false}) or reactivate ({@code true}) a rate. Never
     * deletes — the row stays so historical lines keep their value (ADR-0018).
     */
    @RolesAllowed("ADMIN")
    @Transactional
    public void setVatRateActive(Long id, boolean active) {
        requireVatRate(id).setActive(active);
    }

    /** Reorder a rate by swapping display order with its neighbour ({@code -1} up, {@code +1} down). */
    @RolesAllowed("ADMIN")
    @Transactional
    public void moveVatRate(Long id, int direction) {
        var all = vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc();
        swapOrder(all, id, direction);
    }

    // ------------------------------------------------------------ Expense types

    /** All expense types in display order (admin listing), active and inactive. */
    @RolesAllowed("ADMIN")
    @Transactional(readOnly = true)
    public List<ExpenseTypeDto> allExpenseTypes() {
        return expenseTypeRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(ReferenceDataService::toDto)
                .toList();
    }

    /**
     * The active expense types a new line may choose, in display order
     * (ADR-0018). Excludes deactivated rows.
     */
    @RolesAllowed("USER")
    @Transactional(readOnly = true)
    public List<ExpenseTypeDto> activeExpenseTypes() {
        return expenseTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(ReferenceDataService::toDto)
                .toList();
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public ExpenseTypeDto createExpenseType(String name, Long defaultVatRateId) {
        var type = new ExpenseType(requireName(name), nextExpenseTypeOrder(),
                requireVatRate(defaultVatRateId));
        return toDto(expenseTypeRepository.save(type));
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public ExpenseTypeDto updateExpenseType(Long id, String name, Long defaultVatRateId) {
        var type = requireExpenseType(id);
        type.setName(requireName(name));
        type.setDefaultVatRate(requireVatRate(defaultVatRateId));
        return toDto(type);
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public void setExpenseTypeActive(Long id, boolean active) {
        requireExpenseType(id).setActive(active);
    }

    @RolesAllowed("ADMIN")
    @Transactional
    public void moveExpenseType(Long id, int direction) {
        var all = expenseTypeRepository.findAllByOrderByDisplayOrderAscIdAsc();
        swapOrder(all, id, direction);
    }

    // --------------------------------------------------------------- internals

    private int nextVatRateOrder() {
        return vatRateRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .mapToInt(VatRate::getDisplayOrder).max().orElse(-1) + 1;
    }

    private int nextExpenseTypeOrder() {
        return expenseTypeRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .mapToInt(ExpenseType::getDisplayOrder).max().orElse(-1) + 1;
    }

    private VatRate requireVatRate(Long id) {
        return vatRateRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("No VAT rate with id " + id));
    }

    private ExpenseType requireExpenseType(Long id) {
        return expenseTypeRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("No expense type with id " + id));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainRuleException("Name is required");
        }
        return name.strip();
    }

    /** Percents are stored at scale 2 (ADR-0010 money/percent convention); reject negatives. */
    private static BigDecimal normalizePercent(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new DomainRuleException("Rate must be zero or positive");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Swaps the display order of the row {@code id} with its neighbour in the
     * ordered list. No-op at the boundaries (already first/last). Works for both
     * reference tables via their shared {@link Ordered} shape.
     */
    private static <T extends Ordered> void swapOrder(List<T> ordered, Long id, int direction) {
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("No row with id " + id);
        }
        int target = index + Integer.signum(direction);
        if (target < 0 || target >= ordered.size()) {
            return; // already at the boundary
        }
        T a = ordered.get(index);
        T b = ordered.get(target);
        int tmp = a.getDisplayOrder();
        a.setDisplayOrder(b.getDisplayOrder());
        b.setDisplayOrder(tmp);
    }

    private static VatRateDto toDto(VatRate r) {
        return new VatRateDto(r.getId(), r.getValue(), r.getDisplayOrder(), r.isActive());
    }

    private static ExpenseTypeDto toDto(ExpenseType t) {
        var rate = t.getDefaultVatRate();
        return new ExpenseTypeDto(t.getId(), t.getName(), t.getDisplayOrder(), t.isActive(),
                rate.getId(), rate.getValue());
    }
}

package com.vaadin.expensemanager.report.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vaadin.expensemanager.base.AuditedEntity;
import com.vaadin.expensemanager.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The expense-report aggregate root (ADR-0006) — the core of the application.
 *
 * <p>Owns its report-level fields (a required user-entered {@link #reportDate}
 * defaulting to today in the UI, optional {@link #additionalInformation}, no
 * title), its lifecycle {@link #status} (starting {@link ReportStatus#DRAFT}),
 * its ordered {@link ExpenseLine} collection, and an ordered {@link StatusChange}
 * log. The report total is derived from the lines, never stored — sum-per-line
 * then total, to avoid rounding drift across mixed VAT rates (ADR-0010).
 *
 * <p><strong>Invariants live here, not in the service (ADR-0006).</strong> The
 * aggregate owns the delete guard — {@link #assertDeletable()} rejects deleting
 * anything but a {@code DRAFT} — and the edit guard on report-level fields
 * ({@link #updateDetails}). Authorization ("who owns this") stays in the
 * security/service layer; the domain only enforces state validity.
 *
 * <p>Versioned for optimistic locking (ADR-0011): a stale whole-aggregate write
 * throws {@code OptimisticLockException}. Per ADR-0003 the entity never leaves
 * the service layer — the UI exchanges {@code ReportDetailDto} /
 * {@code ReportSummaryDto} records instead.
 */
@Entity
@Table(name = "expense_report")
public class ExpenseReport extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "additional_information")
    private String additionalInformation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status = ReportStatus.DRAFT;

    /**
     * The report's expense lines in insertion order, owned by the aggregate
     * (cascade + orphan removal, order persisted via {@link OrderColumn}). As
     * with {@link #statusHistory}, the collection is the <strong>owning</strong>
     * side — it maps the {@code report_id} FK and the {@code line_index} order
     * column — so Hibernate populates {@code line_index} on insert. Mutated only
     * through {@link #reconcileLines} (ADR-0006, ADR-0019).
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "report_id", nullable = false)
    @OrderColumn(name = "line_index")
    private List<ExpenseLine> lines = new ArrayList<>();

    /**
     * The report's trips in insertion order, owned by the aggregate (cascade +
     * orphan removal, order persisted via {@link OrderColumn}), like {@link
     * #lines}. Each {@link Travel} owns a generated per-diem line inside {@link
     * #lines} linked back to it; {@link #reconcile} keeps the two in step —
     * editing a trip regenerates its line, removing a trip orphan-removes it
     * (Phase 4.2/4.3). Empty until the first trip is added.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "report_id", nullable = false)
    @OrderColumn(name = "travel_index")
    private List<Travel> travels = new ArrayList<>();

    /**
     * Ordered status-history log, owned by the aggregate (cascade + orphan
     * removal, insertion order persisted via {@link OrderColumn}). Empty until
     * the first transition is recorded (Phase 2.4).
     *
     * <p>The collection is the <strong>owning</strong> side (it maps the
     * {@code report_id} FK and the {@code entry_index} order column); {@link
     * StatusChange}'s back-reference is read-only. Owning the {@code @OrderColumn}
     * here — rather than on a {@code mappedBy} inverse — is what lets Hibernate
     * populate {@code entry_index} on insert (and avoids HHH160246).
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "report_id", nullable = false)
    @OrderColumn(name = "entry_index")
    private List<StatusChange> statusHistory = new ArrayList<>();

    /** JPA constructor. */
    protected ExpenseReport() {
    }

    /**
     * Opens a new report in {@link ReportStatus#DRAFT} for {@code owner}.
     *
     * @param owner                the owning user (required, never reassigned)
     * @param reportDate           the user-entered report date (required)
     * @param additionalInformation optional free-text note ({@code null}/blank ok)
     */
    public ExpenseReport(User owner, LocalDate reportDate, String additionalInformation) {
        this.owner = owner;
        this.reportDate = requireDate(reportDate);
        this.additionalInformation = normalize(additionalInformation);
    }

    /**
     * Updates the report-level fields. Allowed only while the report is editable
     * ({@code DRAFT}/{@code REJECTED}, ADR-0006); a locked report rejects the
     * change rather than silently ignoring it.
     *
     * @throws IllegalStateException if the report is not editable
     */
    public void updateDetails(LocalDate reportDate, String additionalInformation) {
        if (!status.isEditable()) {
            throw new IllegalStateException(
                    "Report " + id + " is " + status + " and cannot be edited");
        }
        this.reportDate = requireDate(reportDate);
        this.additionalInformation = normalize(additionalInformation);
    }

    /**
     * Submits the report for approval (glossary: Submit): {@code DRAFT →
     * SUBMITTED}, appending the first {@link StatusChange} (the acting user is
     * the owner; no comment). Requires at least one line — an empty report is
     * blocked with a friendly reason. There is <strong>no total-sign guard</strong>:
     * a negative or zero total is permitted (ADR-0006), only an empty line set
     * is not.
     *
     * <p>An illegal transition (submitting anything but a {@code DRAFT}) is
     * rejected rather than silently ignored; authorization ("who may submit")
     * stays in the security/service layer (ADR-0008).
     *
     * @param actingUser  the user performing the submit (the owner)
     * @param submittedAt when the transition happened (supplied by the caller so
     *                    the domain stays free of the clock)
     * @throws IllegalStateException if the report is not a {@code DRAFT} or has
     *                               no lines
     */
    public void submit(User actingUser, Instant submittedAt) {
        if (status != ReportStatus.DRAFT) {
            throw new IllegalStateException(
                    "Report " + id + " is " + status + " and cannot be submitted");
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException(
                    "Add at least one line before submitting.");
        }
        ReportStatus from = status;
        status = ReportStatus.SUBMITTED;
        recordStatusChange(from, status, actingUser, null, submittedAt);
    }

    /**
     * Guards the draft-only delete invariant (ADR-0006, glossary): a report is
     * hard-deletable only while {@code DRAFT}. Submitted/approved/rejected
     * reports are retained for the audit trail.
     *
     * @throws IllegalStateException if the report is not a {@code DRAFT}
     */
    public void assertDeletable() {
        if (!status.isDeletable()) {
            throw new IllegalStateException(
                    "Report " + id + " is " + status + " and cannot be deleted");
        }
    }

    /**
     * Appends a status-history entry (glossary: Status Change). Package-internal
     * seam used by the transition methods (submit/approve/reject) that land from
     * Phase 2.4 — entries are created only here so the aggregate owns ordering.
     */
    void recordStatusChange(ReportStatus fromStatus, ReportStatus toStatus,
            User actingUser, String comment, Instant changedAt) {
        statusHistory.add(new StatusChange(this, fromStatus, toStatus, actingUser,
                comment, changedAt));
    }

    /**
     * Reconciles the whole aggregate against the desired sets in one shot
     * (ADR-0019): the manual line specs and the trip specs. Convenience for the
     * common create/update path; equivalent to {@link #reconcileTravels} then
     * {@link #reconcileLines}. Trips are reconciled first so a manual-line
     * reorder sees the up-to-date generated lines.
     */
    public void reconcile(List<ExpenseLineSpec> lineSpecs, List<TravelSpec> travelSpecs) {
        reconcileTravels(travelSpecs);
        reconcileLines(lineSpecs);
    }

    /**
     * Reconciles the <strong>manual</strong> lines against the desired set
     * (ADR-0019), leaving the travel-generated lines untouched. Matches each spec
     * on its nullable line id — non-null updates the existing manual line,
     * {@code null} inserts a new one — and orphan-removes any existing manual line
     * whose id is absent from {@code specs}. The {@link OrderColumn} is rewritten
     * so manual lines follow the spec order and the generated per-diem lines trail
     * them in trip order.
     *
     * <p>Editable only while the report is a {@code DRAFT}/{@code REJECTED}
     * (ADR-0006); a locked report rejects the change. Per-line invariants
     * (required type/rate, non-zero amount) are enforced by {@link ExpenseLine}.
     *
     * @throws IllegalStateException    if the report is not editable
     * @throws IllegalArgumentException if a spec references a manual line id not on
     *                                  this report
     */
    public void reconcileLines(List<ExpenseLineSpec> specs) {
        assertLinesEditable();
        Map<Long, ExpenseLine> existingById = lines.stream()
                .filter(line -> !line.isGenerated() && line.getId() != null)
                .collect(Collectors.toMap(ExpenseLine::getId, Function.identity()));

        List<ExpenseLine> desired = new ArrayList<>(specs.size());
        for (ExpenseLineSpec spec : specs) {
            if (spec.id() != null) {
                ExpenseLine line = existingById.get(spec.id());
                if (line == null) {
                    throw new IllegalArgumentException(
                            "No line with id " + spec.id() + " on report " + id);
                }
                line.update(spec.expenseType(), spec.amount(), spec.vatRate(),
                        spec.comment());
                desired.add(line);
            } else {
                desired.add(new ExpenseLine(spec.expenseType(), spec.amount(),
                        spec.vatRate(), spec.comment()));
            }
        }
        // Orphan-remove manual lines absent from the desired set (identity match —
        // ExpenseLine has no value equality); never touch generated lines. Then
        // append the new manual lines and reorder [manual…, generated…].
        lines.removeIf(line -> !line.isGenerated() && !desired.contains(line));
        for (ExpenseLine line : desired) {
            if (!lines.contains(line)) {
                lines.add(line);
            }
        }
        orderLines(desired);
    }

    /**
     * Reconciles the trips against the desired set and regenerates their per-diem
     * lines (Phase 4.2/4.3, ADR-0019). Each spec matches on its nullable travel id
     * (non-null → update the trip, {@code null} → insert); a trip absent from the
     * set is orphan-removed along with its generated line. A trip that earned a
     * per-diem ({@link TravelSpec#hasPerDiem()}) gets a read-only 0 %-VAT generated
     * line created or regenerated in place; a trip that earned nothing (not-eligible
     * or too short) has any prior generated line removed. The manual lines and their
     * order are preserved; the generated lines trail them in trip order.
     *
     * @throws IllegalStateException    if the report is not editable
     * @throws IllegalArgumentException if a spec references a travel id not on this
     *                                  report
     */
    public void reconcileTravels(List<TravelSpec> specs) {
        assertLinesEditable();
        Map<Long, Travel> existingById = travels.stream()
                .filter(travel -> travel.getId() != null)
                .collect(Collectors.toMap(Travel::getId, Function.identity()));

        List<Travel> desired = new ArrayList<>(specs.size());
        for (TravelSpec spec : specs) {
            if (spec.id() != null) {
                Travel travel = existingById.get(spec.id());
                if (travel == null) {
                    throw new IllegalArgumentException(
                            "No travel with id " + spec.id() + " on report " + id);
                }
                travel.update(spec);
                desired.add(travel);
            } else {
                desired.add(new Travel(spec));
            }
        }
        travels.removeIf(travel -> !desired.contains(travel));
        for (Travel travel : desired) {
            if (!travels.contains(travel)) {
                travels.add(travel);
            }
        }
        travels.sort(Comparator.comparingInt(desired::indexOf));

        regeneratePerDiemLines(specs, desired);
    }

    /**
     * Creates/regenerates/removes the generated per-diem line for each reconciled
     * trip (parallel to {@code desired}/{@code specs} by index). Existing generated
     * lines are matched to their trip by reference so a re-cost updates in place.
     */
    private void regeneratePerDiemLines(List<TravelSpec> specs, List<Travel> desired) {
        Map<Travel, ExpenseLine> byTravel = lines.stream()
                .filter(ExpenseLine::isGenerated)
                .collect(Collectors.toMap(ExpenseLine::getTravel, Function.identity()));

        List<ExpenseLine> desiredGenerated = new ArrayList<>();
        for (int i = 0; i < desired.size(); i++) {
            Travel travel = desired.get(i);
            TravelSpec spec = specs.get(i);
            if (!spec.hasPerDiem()) {
                continue; // not-eligible / too short → no generated line
            }
            ExpenseLine line = byTravel.get(travel);
            if (line == null) {
                line = ExpenseLine.generated(travel, spec.perDiemType(),
                        spec.perDiemAmount(), spec.perDiemRate(),
                        spec.perDiemExplanation());
            } else {
                line.updateGenerated(travel, spec.perDiemType(), spec.perDiemAmount(),
                        spec.perDiemRate(), spec.perDiemExplanation());
            }
            desiredGenerated.add(line);
        }
        // Drop generated lines for removed trips or trips that no longer earn a
        // per-diem; keep every manual line.
        lines.removeIf(line -> line.isGenerated() && !desiredGenerated.contains(line));
        for (ExpenseLine line : desiredGenerated) {
            if (!lines.contains(line)) {
                lines.add(line);
            }
        }
        orderLines(lines.stream().filter(line -> !line.isGenerated()).toList());
    }

    /**
     * Rewrites the {@link OrderColumn} so the collection reads [manual lines in
     * {@code manualOrder}, then generated per-diem lines in trip order]. Keeps the
     * generated lines grouped after the manual ones regardless of which reconcile
     * ran, so {@link #manualLines()} indexing (used for receipt mapping) is stable.
     */
    private void orderLines(List<ExpenseLine> manualOrder) {
        List<ExpenseLine> ordered = new ArrayList<>(manualOrder);
        for (Travel travel : travels) {
            lines.stream()
                    .filter(line -> line.isGenerated() && travel == line.getTravel())
                    .findFirst().ifPresent(ordered::add);
        }
        for (ExpenseLine line : lines) {
            if (!ordered.contains(line)) {
                ordered.add(line);
            }
        }
        lines.sort(Comparator.comparingInt(ordered::indexOf));
    }

    private void assertLinesEditable() {
        if (!status.isEditable()) {
            throw new IllegalStateException(
                    "Report " + id + " is " + status + " and its lines cannot be edited");
        }
    }

    /**
     * The derived report grand total (gross) — never stored (ADR-0010, ADR-0019):
     * the VAT-bearing manual lines plus the tax-free per-diem allowance. Equals the
     * sum of every line's gross; {@code 0.00} with no lines.
     */
    public BigDecimal total() {
        return totals().gross().add(perDiemTotal());
    }

    /** The derived net total of the VAT-bearing (manual) lines, scale 2. */
    public BigDecimal netTotal() {
        return totals().net();
    }

    /** The derived VAT total of the VAT-bearing (manual) lines, scale 2. */
    public BigDecimal vatTotal() {
        return totals().vat();
    }

    /**
     * The manual (VAT-bearing) lines' derived net/VAT/gross — sum-per-line then
     * total, so a report with mixed VAT rates carries no rounding drift (ADR-0010).
     * The tax-free per-diem allowance is broken out separately ({@link
     * #perDiemTotal()}), so Net/VAT here exclude it (Phase 4.3).
     */
    public LineAmounts totals() {
        return lines.stream().filter(line -> !line.isGenerated())
                .map(ExpenseLine::amounts)
                .reduce(LineAmounts.zero(), LineAmounts::add);
    }

    /**
     * The tax-free per-diem allowance subtotal (Phase 4.3): the sum of the
     * generated per-diem lines' gross; {@code 0.00} when there are no trips (or
     * none earned an allowance).
     */
    public BigDecimal perDiemTotal() {
        return lines.stream().filter(ExpenseLine::isGenerated)
                .map(ExpenseLine::gross)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public User getOwner() {
        return owner;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public ReportStatus getStatus() {
        return status;
    }

    /** Unmodifiable view of the status history in insertion order. */
    public List<StatusChange> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    /** Unmodifiable view of the expense lines in insertion order. */
    public List<ExpenseLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * The manual (user-entered, non-generated) lines in order — the ones the
     * detail view shows as editable cards and maps receipts against by position
     * (ADR-0021). The generated per-diem lines are excluded.
     */
    public List<ExpenseLine> manualLines() {
        return lines.stream().filter(line -> !line.isGenerated()).toList();
    }

    /** Unmodifiable view of the trips in insertion order. */
    public List<Travel> getTravels() {
        return Collections.unmodifiableList(travels);
    }

    /** The generated per-diem line for {@code travel}, if the trip earned one. */
    public java.util.Optional<ExpenseLine> perDiemLineFor(Travel travel) {
        return lines.stream()
                .filter(line -> line.isGenerated() && travel == line.getTravel())
                .findFirst();
    }

    private static LocalDate requireDate(LocalDate reportDate) {
        if (reportDate == null) {
            throw new IllegalArgumentException("Report date is required");
        }
        return reportDate;
    }

    /** Trims and collapses blank optional text to {@code null}. */
    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}

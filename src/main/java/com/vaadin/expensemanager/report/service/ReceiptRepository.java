package com.vaadin.expensemanager.report.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.vaadin.expensemanager.report.domain.Receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link Receipt} (ADR-0003, ADR-0021).
 *
 * <p>Stays inside the service layer. The write path uses
 * {@link #findByExpenseLineId} (to overwrite or remove one line's receipt) and
 * plain {@code save}; the report load path uses {@link #findSummariesByExpenseLineIdIn},
 * a projection that selects everything <em>except</em> the {@code data} bytea, so
 * a receipt summary is threaded through the aggregate without ever loading the
 * blob (ADR-0021). The read path streams the bytes through
 * {@link #findDownloadByIdAndOwnerId}, the one query that touches the bytea.
 */
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    /**
     * The <strong>dedicated download projection</strong> (ADR-0021, ADR-0008):
     * one receipt's bytea by id, but only if it belongs to {@code ownerId}. The
     * owning-report authorization check <em>is</em> the {@code where} clause — a
     * non-owner (or a missing id) gets an empty result, indistinguishably, so the
     * read path can never leak or serve another user's receipt.
     *
     * <p>Native SQL because the join crosses associations JPQL cannot express:
     * {@link com.vaadin.expensemanager.report.domain.ExpenseLine} carries no ORM
     * back-reference to its report (the report owns the line unidirectionally,
     * ADR-0021), so the {@code expense_line.report_id → expense_report.owner_id}
     * hop only exists at the table level. This is the sole place the {@code data}
     * bytea is ever selected.
     */
    @Query(nativeQuery = true, value = """
            select r.data as data, r.filename as filename,
                   r.content_type as contentType
            from receipt r
            join expense_line l on l.id = r.expense_line_id
            join expense_report rep on rep.id = l.report_id
            where r.id = :receiptId and rep.owner_id = :ownerId
            """)
    Optional<ReceiptDownloadView> findDownloadByIdAndOwnerId(
            @Param("receiptId") Long receiptId, @Param("ownerId") Long ownerId);

    /**
     * The download projection <strong>without owner scoping</strong>: one
     * receipt's bytea by id, whoever owns the owning report. Used only on the
     * admin read path (an admin reviews any user's report), so an admin can see
     * a receipt attached to another user's report; the owner-scoped
     * {@link #findDownloadByIdAndOwnerId} stays the query for ordinary users, so
     * a non-admin still can never reach another user's bytes (ADR-0008).
     */
    @Query(nativeQuery = true, value = """
            select r.data as data, r.filename as filename,
                   r.content_type as contentType
            from receipt r
            where r.id = :receiptId
            """)
    Optional<ReceiptDownloadView> findDownloadById(@Param("receiptId") Long receiptId);

    /** The receipt owned by one line, if any (write path: overwrite / remove). */
    Optional<Receipt> findByExpenseLineId(Long expenseLineId);

    /**
     * Blob-free summaries for the given lines (report load path). Selecting the
     * columns explicitly keeps the {@code data} bytea out of the query entirely.
     */
    @Query("""
            select r.id as id, r.expenseLine.id as expenseLineId,
                   r.filename as filename, r.contentType as contentType,
                   r.sizeBytes as sizeBytes
            from Receipt r
            where r.expenseLine.id in :lineIds
            """)
    List<ReceiptSummaryView> findSummariesByExpenseLineIdIn(
            @Param("lineIds") Collection<Long> lineIds);
}

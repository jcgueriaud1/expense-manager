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
 * blob (ADR-0021). Streaming the bytes themselves is the read-path slice.
 */
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

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

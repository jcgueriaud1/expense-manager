package com.vaadin.expensemanager.report.domain;

import com.vaadin.expensemanager.base.AuditedEntity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * A scanned document attached to one {@link ExpenseLine} (glossary: Receipt) —
 * 0..1 per line, always optional (ADR-0009, ADR-0021).
 *
 * <p>Physically separated from the hot {@code expense_line} row: the blob lives
 * on its own table, and this entity is the <strong>owning</strong> side of a
 * <em>unidirectional</em> one-to-one — it maps the {@code expense_line_id} FK,
 * and {@link ExpenseLine} carries no reference back. That is what makes "the
 * bytea never rides a line/report load" structural rather than dependent on a
 * lazy-fetch annotation on the parent (a nullable inverse one-to-one is the
 * fragile-lazy case; there simply is no inverse here, ADR-0021).
 *
 * <p>{@link #contentType} is the server-side <strong>sniffed</strong> signature
 * (magic bytes via {@link ReceiptValidator}), never the browser's claim, so a
 * receipt served back inline is what it says it is. The service reads the small
 * summary fields through a projection that omits {@link #data}; the bytes are
 * loaded only by the dedicated download query on the read-path slice.
 */
@Entity
@Table(name = "receipt")
public class Receipt extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_line_id", nullable = false, unique = true,
            updatable = false)
    private ExpenseLine expenseLine;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "filename")
    private String filename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // Only ever read by the dedicated download projection (read-path slice), never
    // on a summary/aggregate query — see the class Javadoc and ADR-0021.
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    private byte[] data;

    /** JPA constructor. */
    protected Receipt() {
    }

    /**
     * Attaches {@code data} to {@code line} with the already-sniffed
     * {@code contentType} (the caller validates via {@link ReceiptValidator}).
     * The service owns a receipt's lifecycle directly (it is not part of the
     * {@link ExpenseReport} aggregate's invariants), so this seam is public.
     */
    public Receipt(ExpenseLine expenseLine, byte[] data, String filename,
            String contentType) {
        this.expenseLine = expenseLine;
        replace(data, filename, contentType);
    }

    /** Overwrites the stored bytes/metadata in place (replace = overwrite). */
    public void replace(byte[] data, String filename, String contentType) {
        this.data = data;
        this.sizeBytes = data.length;
        this.filename = filename;
        this.contentType = contentType;
    }

    public Long getId() {
        return id;
    }

    public ExpenseLine getExpenseLine() {
        return expenseLine;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFilename() {
        return filename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getData() {
        return data;
    }
}

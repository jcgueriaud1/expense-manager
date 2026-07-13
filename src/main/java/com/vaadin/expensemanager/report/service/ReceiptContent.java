package com.vaadin.expensemanager.report.service;

/**
 * The bytes of one receipt, resolved for streaming on the read path (ADR-0021).
 *
 * <p>This is the <strong>only</strong> carrier in the service layer that holds a
 * receipt's {@code byte[]}, and it exists solely to feed the {@code DownloadHandler}
 * — it is never mapped onto {@link ExpenseLineDto}/{@link ReportDetailDto} and
 * never touches the aggregate load path. The service builds it from the
 * owner-scoped {@link ReceiptDownloadView} projection (so it only ever exists for
 * a receipt the current user is authorized to read, ADR-0008), copying the values
 * eagerly so nothing lazy is dereferenced while streaming.
 *
 * @param data        the file bytes (the sniffed content, magic-byte validated on
 *                    the write path, ADR-0021)
 * @param filename    the original client filename, for {@code Content-Disposition}
 * @param contentType the stored server-side sniffed MIME type, served verbatim
 */
public record ReceiptContent(byte[] data, String filename, String contentType) {

    /** Copies a download projection into a detached, session-free carrier. */
    static ReceiptContent from(ReceiptDownloadView view) {
        return new ReceiptContent(view.getData(), view.getFilename(),
                view.getContentType());
    }
}

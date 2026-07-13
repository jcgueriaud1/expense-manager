package com.vaadin.expensemanager.report.domain;

/**
 * Server-side gate for an uploaded receipt (ADR-0021): the file must be within
 * the {@value #MAX_SIZE_BYTES}-byte cap and match one of the allowed magic-byte
 * signatures ({@link ReceiptType}). The browser's {@code Content-Type} is never
 * trusted — the returned type is the one <em>sniffed</em> from the bytes, so a
 * renamed/mislabeled file is rejected and the stored type is trustworthy.
 *
 * <p>Rejections throw {@link ReceiptRejectedException} with a user-facing
 * message the upload UI shows verbatim; the control is never disabled, the
 * reason is explained instead (ADR-0020).
 */
public final class ReceiptValidator {

    /** 10 MB, aligned with the client-side and Spring multipart caps (ADR-0021). */
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private ReceiptValidator() {
    }

    /**
     * Validates the uploaded bytes and returns the sniffed {@link ReceiptType}.
     *
     * @throws ReceiptRejectedException if empty, over the size cap, or not a
     *                                  JPEG/PNG/PDF by magic-byte check
     */
    public static ReceiptType validate(byte[] data) {
        if (data == null || data.length == 0) {
            throw new ReceiptRejectedException("The file is empty.");
        }
        if (data.length > MAX_SIZE_BYTES) {
            throw new ReceiptRejectedException(
                    "The file is larger than the 10 MB limit.");
        }
        return ReceiptType.sniff(data).orElseThrow(() -> new ReceiptRejectedException(
                "Only JPEG, PNG or PDF files are accepted."));
    }
}

package com.vaadin.expensemanager.report.domain;

import java.util.Optional;

/**
 * The receipt file types the app accepts, each identified by its leading
 * <strong>magic bytes</strong> (ADR-0021). The allow-list is JPEG / PNG / PDF;
 * everything else — including a JPEG renamed to {@code .pdf} — is rejected,
 * because {@link #sniff} matches on the file's actual signature, not its name or
 * the browser's {@code Content-Type} claim.
 *
 * <p>The {@link #contentType()} here is the value stored on the {@link Receipt}:
 * the sniffed, trustworthy MIME type. This makes "we accept images," not "we
 * accept things labelled as images," so inline preview on the read path is safe.
 */
public enum ReceiptType {

    /** JPEG — {@code FF D8 FF}. */
    JPEG("image/jpeg", 0xFF, 0xD8, 0xFF),
    /** PNG — {@code 89 50 4E 47} ({@code \x89PNG}). */
    PNG("image/png", 0x89, 0x50, 0x4E, 0x47),
    /** PDF — {@code 25 50 44 46} ({@code %PDF}). */
    PDF("application/pdf", 0x25, 0x50, 0x44, 0x46);

    private final String contentType;
    private final byte[] signature;

    ReceiptType(String contentType, int... signature) {
        this.contentType = contentType;
        this.signature = new byte[signature.length];
        for (int i = 0; i < signature.length; i++) {
            this.signature[i] = (byte) signature[i];
        }
    }

    /** The sniffed MIME type stored on the receipt. */
    public String contentType() {
        return contentType;
    }

    private boolean matches(byte[] data) {
        if (data == null || data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Identifies {@code data} by its leading magic bytes, or empty if it matches
     * no allowed signature.
     */
    public static Optional<ReceiptType> sniff(byte[] data) {
        for (ReceiptType type : values()) {
            if (type.matches(data)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}

package com.vaadin.expensemanager.report.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Magic-byte validator unit tests (pyramid layer 1, DB-free, ADR-0012, ADR-0021).
 *
 * <p>Proves the trust model: the type is decided by the file's leading bytes, not
 * its name or a claimed content type. Each allowed signature is accepted and maps
 * to the sniffed MIME type; a JPEG renamed to {@code .pdf}, an unknown type, an
 * over-cap file and an empty file are all rejected with a clear message.
 */
class ReceiptValidatorTest {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};

    @Test
    void acceptsJpegAndReturnsSniffedType() {
        assertThat(ReceiptValidator.validate(withTrailing(JPEG_MAGIC)))
                .isEqualTo(ReceiptType.JPEG);
        assertThat(ReceiptType.JPEG.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void acceptsPngAndReturnsSniffedType() {
        assertThat(ReceiptValidator.validate(withTrailing(PNG_MAGIC)))
                .isEqualTo(ReceiptType.PNG);
        assertThat(ReceiptType.PNG.contentType()).isEqualTo("image/png");
    }

    @Test
    void acceptsPdfAndReturnsSniffedType() {
        assertThat(ReceiptValidator.validate(withTrailing(PDF_MAGIC)))
                .isEqualTo(ReceiptType.PDF);
        assertThat(ReceiptType.PDF.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void rejectsAFileWhoseBytesMatchNoAllowedSignature() {
        // Looks like a GIF ("GIF8") — a plausible image the browser might label
        // image/gif, but not on our allow-list.
        byte[] gif = {0x47, 0x49, 0x46, 0x38};
        assertThatThrownBy(() -> ReceiptValidator.validate(gif))
                .isInstanceOf(ReceiptRejectedException.class)
                .hasMessageContaining("JPEG, PNG or PDF");
    }

    @Test
    void rejectsAMislabeledFile() {
        // A plain-text file the user renamed to receipt.pdf: its bytes are not a
        // PDF signature, so magic-byte validation rejects it regardless of name.
        byte[] notReallyPdf = "this is not a pdf".getBytes();
        assertThatThrownBy(() -> ReceiptValidator.validate(notReallyPdf))
                .isInstanceOf(ReceiptRejectedException.class)
                .hasMessageContaining("JPEG, PNG or PDF");
    }

    @Test
    void rejectsAnOverCapFile() {
        byte[] tooBig = new byte[(int) ReceiptValidator.MAX_SIZE_BYTES + 1];
        // Valid JPEG signature, but over the 10 MB cap.
        System.arraycopy(JPEG_MAGIC, 0, tooBig, 0, JPEG_MAGIC.length);
        assertThatThrownBy(() -> ReceiptValidator.validate(tooBig))
                .isInstanceOf(ReceiptRejectedException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void acceptsAFileExactlyAtTheCap() {
        byte[] atCap = new byte[(int) ReceiptValidator.MAX_SIZE_BYTES];
        System.arraycopy(PNG_MAGIC, 0, atCap, 0, PNG_MAGIC.length);
        assertThat(ReceiptValidator.validate(atCap)).isEqualTo(ReceiptType.PNG);
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> ReceiptValidator.validate(new byte[0]))
                .isInstanceOf(ReceiptRejectedException.class)
                .hasMessageContaining("empty");
    }

    /** Pads a magic-byte prefix with a little trailing content, as a real file has. */
    private static byte[] withTrailing(byte[] magic) {
        byte[] file = new byte[magic.length + 8];
        System.arraycopy(magic, 0, file, 0, magic.length);
        return file;
    }
}

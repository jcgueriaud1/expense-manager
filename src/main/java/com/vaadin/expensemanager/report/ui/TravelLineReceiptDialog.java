package com.vaadin.expensemanager.report.ui;

import java.io.ByteArrayInputStream;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.report.domain.ReceiptRejectedException;
import com.vaadin.expensemanager.report.domain.ReceiptType;
import com.vaadin.expensemanager.report.domain.ReceiptValidator;
import com.vaadin.expensemanager.report.service.GeneratedLineView;
import com.vaadin.expensemanager.report.service.ReceiptUpload;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatEur;
import static com.vaadin.expensemanager.report.ui.ReportViewSupport.generatedLineLabel;

/**
 * The focused modal editor for one <strong>generated</strong> (travel-owned) line
 * (Phase 4.3, ADR-0021). A generated line's amount and comment are
 * server-computed and read-only — the only thing the user can change is its
 * <em>receipt</em>, so this dialog shows the line as a read-only summary above the
 * same always-enabled attach/replace/remove control the {@link LineEditorDialog}
 * uses for manual lines.
 *
 * <p>The bytes are validated server-side by magic bytes ({@link ReceiptValidator})
 * the moment they arrive and buffered in memory; nothing persists until the report
 * is saved. The buffered mutation travels back to the view as a
 * {@link ReceiptUpload} alongside the (optimistically updated) {@link
 * GeneratedLineView}, so the line never carries the {@code byte[]}.
 */
final class TravelLineReceiptDialog extends Dialog {

    private final GeneratedLineView line;
    private final Function<Long, DownloadHandler> savedReceiptSource;

    // Receipt working state, mirroring LineEditorDialog: receiptTouched separates
    // "left as-is" (send no command) from an explicit attach/remove.
    private boolean receiptTouched;
    private byte[] pendingData;
    private String pendingFilename;
    private String pendingContentType;
    private long pendingSize;

    private final Span receiptStatus = new Span();
    private final Button removeReceipt = new Button("Remove");
    private final Div receiptPreview = new Div();

    /**
     * @param line               the generated line being edited (read-only amount/comment)
     * @param savedReceiptSource maps a persisted receipt id to a streaming handler,
     *                           so an already-saved receipt previews from the DB
     * @param onSave             receives the (optimistically updated) line and the
     *                           buffered receipt mutation ({@code null} if unchanged)
     */
    TravelLineReceiptDialog(GeneratedLineView line,
            Function<Long, DownloadHandler> savedReceiptSource,
            BiConsumer<GeneratedLineView, ReceiptUpload> onSave) {
        this.line = line;
        this.savedReceiptSource = savedReceiptSource;
        setHeaderTitle(generatedLineLabel(line.kind()));
        setWidth("28rem");
        addClassName("travel-line-dialog");

        var amount = new Span(formatEur(line.amount()));
        amount.addClassName("travel-preview-amount");
        var summary = new Div(amount);
        summary.addClassName("travel-preview");
        if (line.comment() != null && !line.comment().isBlank()) {
            var detail = new Span(line.comment());
            detail.addClassName("muted");
            summary.add(detail);
        }
        summary.setVisible(true);

        add(summary, receiptSection());

        var save = new Button("Save receipt", event -> {
            onSave.accept(withReceiptSummary(), receiptCommand());
            close();
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> close());
        getFooter().add(cancel, save);
    }

    /** The always-enabled receipt attach/replace/remove control (ADR-0021). */
    private Div receiptSection() {
        var heading = new Span("Receipt (optional)");
        heading.getStyle().setFontWeight("600");

        var uploadRef = new Upload[1];
        var upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
            try {
                ReceiptType sniffed = ReceiptValidator.validate(data);
                buffer(data, metadata.fileName(), sniffed.contentType());
                notifySuccess("Receipt attached.");
            } catch (ReceiptRejectedException rejected) {
                notifyError(rejected.getMessage());
            }
            uploadRef[0].clearFileList();
        }));
        uploadRef[0] = upload;
        upload.setMaxFiles(1);
        upload.setMaxFileSize((int) ReceiptValidator.MAX_SIZE_BYTES);
        upload.setAcceptedFileTypes("image/jpeg", "image/png", "application/pdf");
        var uploadButton = new Button("Upload receipt…", LucideIcon.UPLOAD.create());
        upload.setUploadButton(uploadButton);
        upload.addFileRejectedListener(event -> notifyError(event.getErrorMessage()));

        removeReceipt.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR,
                ButtonVariant.SMALL);
        removeReceipt.addClickListener(event -> remove());
        receiptStatus.getStyle().setColor("var(--vaadin-text-color-secondary)");
        receiptStatus.getStyle().setFontSize("var(--aura-font-size-s)");

        var statusRow = new Div(receiptStatus, removeReceipt);
        statusRow.getStyle().set("display", "flex").set("align-items", "center")
                .set("gap", "var(--vaadin-gap-m)");

        var section = new Div(heading, receiptPreview, statusRow, upload);
        section.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)")
                .set("margin-top", "var(--vaadin-gap-m)");
        refreshReceiptStatus();
        return section;
    }

    private void buffer(byte[] data, String filename, String contentType) {
        this.receiptTouched = true;
        this.pendingData = data;
        this.pendingFilename = filename;
        this.pendingContentType = contentType;
        this.pendingSize = data.length;
        refreshReceiptStatus();
    }

    private void remove() {
        this.receiptTouched = true;
        this.pendingData = null;
        this.pendingFilename = null;
        this.pendingContentType = null;
        this.pendingSize = 0;
        refreshReceiptStatus();
    }

    private void refreshReceiptStatus() {
        String filename = effectiveFilename();
        boolean present = filename != null;
        receiptStatus.setText(present ? "📎 " + filename : "No receipt attached.");
        removeReceipt.setVisible(present);
        receiptPreview.removeAll();
        Component preview = receiptPreviewComponent();
        if (preview != null) {
            receiptPreview.add(preview);
        }
    }

    private Component receiptPreviewComponent() {
        if (receiptTouched) {
            if (pendingData == null) {
                return null;
            }
            byte[] data = pendingData;
            String filename = pendingFilename;
            String contentType = pendingContentType;
            return ReceiptPreview.forReceipt(filename, contentType,
                    () -> DownloadHandler.fromInputStream(event ->
                            new DownloadResponse(new ByteArrayInputStream(data),
                                    filename, contentType, data.length)).inline());
        }
        if (line.hasReceipt() && line.receiptId() != null) {
            Long receiptId = line.receiptId();
            return ReceiptPreview.forReceipt(line.receiptFilename(),
                    line.receiptContentType(), () -> savedReceiptSource.apply(receiptId));
        }
        return null;
    }

    /** The filename that would apply on save, honouring any buffered change. */
    private String effectiveFilename() {
        return receiptTouched ? pendingFilename : line.receiptFilename();
    }

    /** The buffered receipt mutation, or {@code null} if the receipt is unchanged. */
    private ReceiptUpload receiptCommand() {
        if (!receiptTouched) {
            return null;
        }
        return pendingData == null ? ReceiptUpload.REMOVE
                : new ReceiptUpload(pendingData, pendingFilename);
    }

    /** The line with its (optimistic) receipt summary applied, for the card. */
    private GeneratedLineView withReceiptSummary() {
        if (!receiptTouched) {
            return line;
        }
        if (pendingData == null) {
            return line.withoutReceipt();
        }
        // Newly buffered: no id yet (assigned on save), filename/type/size known.
        return line.withReceipt(null, pendingFilename, pendingContentType, pendingSize);
    }

    private void notifySuccess(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.SUCCESS);
    }

    private void notifyError(String message) {
        Notification.show(message, 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.ERROR);
    }
}

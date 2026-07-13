package com.vaadin.expensemanager.report.ui;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.vaadin.expensemanager.reference.ExpenseTypeDto;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.expensemanager.report.domain.ReceiptRejectedException;
import com.vaadin.expensemanager.report.domain.ReceiptType;
import com.vaadin.expensemanager.report.domain.ReceiptValidator;
import com.vaadin.expensemanager.report.service.ExpenseLineDto;
import com.vaadin.expensemanager.report.service.ReceiptUpload;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;

import static com.vaadin.expensemanager.report.ui.ReportViewSupport.formatPercent;

/**
 * The focused modal editor for one expense line (variant C, issue #24), now
 * carrying its receipt (Phase 3.1, ADR-0021).
 *
 * <p>Editing a card opens this dialog over the report; adding a line opens it
 * empty. It binds an {@link ExpenseLineFormModel} with Binder + field validation
 * (ADR-0015): a missing type, missing/zero amount, or missing VAT rate surfaces
 * in a top-of-dialog error summary behind an <strong>always-enabled</strong>
 * Save (never a disabled button, ADR-0020). Choosing an expense type pre-fills
 * that type's default VAT rate, which the user can still override — done with a
 * value-change listener guarded by {@code isFromClient()}, since Binder can't
 * express a cross-field default declaratively (finding F-004).
 *
 * <p><strong>Receipt (ADR-0021).</strong> An always-enabled {@link Upload}
 * control accepts a single JPEG/PNG/PDF ≤ 10 MB. The bytes are validated
 * <em>server-side by magic bytes</em> ({@link ReceiptValidator}) the moment they
 * arrive — a renamed/mislabeled file is rejected with a clear message, never a
 * disabled control — then buffered in memory; nothing is persisted until the
 * report is saved. A buffered/attached receipt shows its filename with a Remove
 * affordance; upload again to replace (overwrite, no history). The buffered
 * mutation travels back to the view as a {@link ReceiptUpload} alongside the
 * edited line, so the line DTO itself never carries the {@code byte[]}.
 *
 * <p>New lines offer only <em>active</em> types/rates; when editing a historical
 * line whose type or rate has since been deactivated, that option is injected
 * into its ComboBox so the line still displays and round-trips (ADR-0018).
 */
final class LineEditorDialog extends Dialog {

    private final Binder<ExpenseLineFormModel> binder = new Binder<>();
    private final ExpenseLineFormModel model = new ExpenseLineFormModel();
    private final Div errorSummary = new Div();

    // Receipt working state. receiptTouched distinguishes "left as-is" (send no
    // command) from an explicit attach/remove. When touched: pendingData == null
    // means remove; non-null means attach (with its sniffed type + size).
    private final ExpenseLineDto existing;
    private final Function<Long, DownloadHandler> savedReceiptSource;
    private boolean receiptTouched;
    private byte[] pendingData;
    private String pendingFilename;
    private String pendingContentType;
    private long pendingSize;

    private final Span receiptStatus = new Span();
    private final Button removeReceipt = new Button("Remove");
    private final Div receiptPreview = new Div();

    /**
     * @param types    active expense types offered to new lines, in display order
     * @param rates    active VAT rates offered to new lines, in display order
     * @param existing           the line being edited, or {@code null} to add a
     *                           new one
     * @param savedReceiptSource maps a persisted receipt id to a streaming
     *                           {@link DownloadHandler} (the service's read path),
     *                           so an already-saved receipt previews from the DB
     * @param onSave             receives the edited/created line and the buffered
     *                           receipt mutation ({@code null} when the receipt was
     *                           left unchanged)
     */
    LineEditorDialog(List<ExpenseTypeDto> types, List<VatRateDto> rates,
            ExpenseLineDto existing, Function<Long, DownloadHandler> savedReceiptSource,
            BiConsumer<ExpenseLineDto, ReceiptUpload> onSave) {
        this.existing = existing;
        this.savedReceiptSource = savedReceiptSource;
        setHeaderTitle(existing == null ? "Add expense" : "Edit expense");
        setWidth("28rem");

        // Item sets start from the active options; a historical line's now-inactive
        // type/rate is added so it still shows when editing (ADR-0018).
        List<ExpenseTypeDto> typeItems = withHistoricalType(types, existing);
        List<VatRateDto> rateItems = withHistoricalRate(rates, existing);

        var typeField = new ComboBox<ExpenseTypeDto>("Expense type");
        typeField.setItems(typeItems);
        typeField.setItemLabelGenerator(ExpenseTypeDto::name);
        typeField.setRequiredIndicatorVisible(true);

        var vatField = new ComboBox<VatRateDto>("VAT rate");
        vatField.setItems(rateItems);
        vatField.setItemLabelGenerator(rate -> formatPercent(rate.value()));
        vatField.setRequiredIndicatorVisible(true);

        var amountField = new BigDecimalField("Gross amount (paid)");
        amountField.setRequiredIndicatorVisible(true);

        var commentField = new TextField("Comment");
        commentField.setMaxLength(500);

        // Choosing a type pre-fills its default rate (overridable). Guarded by
        // isFromClient() so binder.readBean below doesn't clobber a loaded rate.
        typeField.addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) {
                rateItems.stream()
                        .filter(rate -> rate.id().equals(event.getValue().defaultVatRateId()))
                        .findFirst()
                        .ifPresent(vatField::setValue);
            }
        });

        binder.forField(typeField)
                .asRequired("Expense type is required")
                .bind(ExpenseLineFormModel::getExpenseType,
                        ExpenseLineFormModel::setExpenseType);
        binder.forField(vatField)
                .asRequired("VAT rate is required")
                .bind(ExpenseLineFormModel::getVatRate,
                        ExpenseLineFormModel::setVatRate);
        binder.forField(amountField)
                .asRequired("Amount is required")
                .withValidator(amount -> amount == null || amount.signum() != 0,
                        "Amount must not be zero")
                .bind(ExpenseLineFormModel::getAmount, ExpenseLineFormModel::setAmount);
        binder.forField(commentField)
                .bind(ExpenseLineFormModel::getComment, ExpenseLineFormModel::setComment);

        if (existing != null) {
            model.setExpenseType(findById(typeItems, existing.expenseTypeId(),
                    ExpenseTypeDto::id));
            model.setVatRate(findById(rateItems, existing.vatRateId(),
                    VatRateDto::id));
            model.setAmount(existing.amount());
            model.setComment(existing.comment());
        }
        binder.readBean(model);

        errorSummary.getElement().setAttribute("role", "alert");
        errorSummary.setVisible(false);
        errorSummary.getStyle().setColor("var(--aura-red-text)");

        var form = new FormLayout(typeField, amountField, vatField, commentField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(errorSummary, form, receiptSection());

        var save = new Button("Save expense", event -> save(onSave));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        var cancel = new Button("Cancel", event -> close());
        getFooter().add(cancel, save);
    }

    /** The always-enabled receipt attach/replace/remove control (ADR-0021). */
    private Div receiptSection() {
        var heading = new Span("Receipt (optional)");
        heading.getStyle().setFontWeight("600");

        // Server-side magic-byte validation on the buffered bytes: the browser's
        // content type is never trusted, and the stored type is the sniffed one.
        // The handler references the Upload to reset it, so hold it via a 1-slot.
        var uploadRef = new Upload[1];
        var upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
            try {
                ReceiptType sniffed = ReceiptValidator.validate(data);
                buffer(data, metadata.fileName(), sniffed.contentType());
                notifySuccess("Receipt attached.");
            } catch (ReceiptRejectedException rejected) {
                notifyError(rejected.getMessage());
            }
            // Empty the component's own file list (this callback runs on the UI
            // thread on completion) so my status row is the single source of
            // truth and the always-enabled button stays usable for a replacement
            // — never a disabled control (ADR-0020). UploadHandler drives the
            // progress-listener model, so the legacy SucceededEvent never fires.
            uploadRef[0].clearFileList();
        }));
        uploadRef[0] = upload;
        upload.setMaxFiles(1);
        upload.setMaxFileSize((int) ReceiptValidator.MAX_SIZE_BYTES);
        upload.setAcceptedFileTypes("image/jpeg", "image/png", "application/pdf");
        var uploadButton = new Button("Upload receipt…", VaadinIcon.UPLOAD.create());
        upload.setUploadButton(uploadButton);
        // Client-side rejection (wrong type / too big) — surfaced, never silent.
        upload.addFileRejectedListener(event -> notifyError(event.getErrorMessage()));

        removeReceipt.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR,
                ButtonVariant.SMALL);
        removeReceipt.addClickListener(event -> remove());
        receiptStatus.getStyle().setColor("var(--vaadin-text-color-secondary)");
        receiptStatus.getStyle().setFontSize("var(--aura-font-size-s)");

        var statusRow = new Div(receiptStatus, removeReceipt);
        statusRow.getStyle().set("display", "flex").set("align-items", "center")
                .set("gap", "var(--vaadin-gap)");

        var section = new Div(heading, receiptPreview, statusRow, upload);
        section.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)")
                .set("margin-top", "var(--vaadin-gap)");
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

    /** Clears a buffered upload or marks an existing receipt for removal on save. */
    private void remove() {
        this.receiptTouched = true;
        this.pendingData = null;
        this.pendingFilename = null;
        this.pendingContentType = null;
        this.pendingSize = 0;
        refreshReceiptStatus();
    }

    /** Reflects the effective receipt (buffered attach, kept, or removed). */
    private void refreshReceiptStatus() {
        String filename = effectiveFilename();
        boolean present = filename != null;
        receiptStatus.setText(present ? "📎 " + filename : "No receipt attached.");
        removeReceipt.setVisible(present);
        refreshReceiptPreview();
    }

    /**
     * Rebuilds the inline preview for the effective receipt (ADR-0021): a freshly
     * buffered upload previews from its in-memory bytes; an untouched, already
     * <em>saved</em> receipt previews from the DB stream. An existing but not-yet-
     * saved buffered attachment reopened for edit has no bytes here (they live in
     * the report's working copy), so only its filename shows until saved.
     */
    private void refreshReceiptPreview() {
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
            // Buffered bytes: stream them straight from memory (no DB, no id yet).
            byte[] data = pendingData;
            String filename = pendingFilename;
            String contentType = pendingContentType;
            return ReceiptPreview.forReceipt(filename, contentType,
                    () -> DownloadHandler.fromInputStream(event ->
                            new DownloadResponse(new ByteArrayInputStream(data),
                                    filename, contentType, data.length)).inline());
        }
        if (existing != null && existing.hasReceipt() && existing.receiptId() != null) {
            Long receiptId = existing.receiptId();
            return ReceiptPreview.forReceipt(existing.receiptFilename(),
                    existing.receiptContentType(),
                    () -> savedReceiptSource.apply(receiptId));
        }
        return null;
    }

    /** The filename that would apply on save, honouring any buffered change. */
    private String effectiveFilename() {
        if (receiptTouched) {
            return pendingFilename;
        }
        return existing == null ? null : existing.receiptFilename();
    }

    private void save(BiConsumer<ExpenseLineDto, ReceiptUpload> onSave) {
        clearErrors();
        if (!binder.writeBeanIfValid(model)) {
            showErrors(binder.validate().getValidationErrors().stream()
                    .map(ValidationResult::getErrorMessage).distinct().toList());
            return;
        }
        var type = model.getExpenseType();
        var rate = model.getVatRate();
        var base = ExpenseLineDto.of(existing == null ? null : existing.id(),
                type.id(), type.name(), rate.id(), rate.value(),
                model.getAmount(), model.getComment());
        onSave.accept(withReceiptSummary(base), receiptCommand());
        close();
    }

    /** The buffered receipt mutation, or {@code null} if the receipt is unchanged. */
    private ReceiptUpload receiptCommand() {
        if (!receiptTouched) {
            return null;
        }
        return pendingData == null ? ReceiptUpload.REMOVE
                : new ReceiptUpload(pendingData, pendingFilename);
    }

    /** Applies the (optimistic) receipt summary to the edited line for display. */
    private ExpenseLineDto withReceiptSummary(ExpenseLineDto base) {
        if (!receiptTouched) {
            // Unchanged: carry the existing summary through so the card is stable.
            return existing == null ? base
                    : base.withReceipt(existing.receiptId(), existing.receiptFilename(),
                            existing.receiptContentType(), existing.receiptSizeBytes());
        }
        if (pendingData == null) {
            return base.withoutReceipt();
        }
        // Newly buffered: no id yet (assigned on save), filename/type/size known.
        return base.withReceipt(null, pendingFilename, pendingContentType, pendingSize);
    }

    private void notifySuccess(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.SUCCESS);
    }

    private void notifyError(String message) {
        Notification.show(message, 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.ERROR);
    }

    private static List<ExpenseTypeDto> withHistoricalType(List<ExpenseTypeDto> active,
            ExpenseLineDto existing) {
        var items = new ArrayList<>(active);
        if (existing != null && existing.expenseTypeId() != null
                && items.stream().noneMatch(t -> t.id().equals(existing.expenseTypeId()))) {
            items.add(new ExpenseTypeDto(existing.expenseTypeId(),
                    existing.expenseTypeName() + " (inactive)", Integer.MAX_VALUE,
                    false, existing.vatRateId(), existing.vatRatePercent()));
        }
        return items;
    }

    private static List<VatRateDto> withHistoricalRate(List<VatRateDto> active,
            ExpenseLineDto existing) {
        var items = new ArrayList<>(active);
        if (existing != null && existing.vatRateId() != null
                && items.stream().noneMatch(r -> r.id().equals(existing.vatRateId()))) {
            items.add(new VatRateDto(existing.vatRateId(), existing.vatRatePercent(),
                    Integer.MAX_VALUE, false));
        }
        return items;
    }

    private static <T> T findById(List<T> items, Long id,
            java.util.function.Function<T, Long> idOf) {
        if (id == null) {
            return null;
        }
        return items.stream().filter(item -> id.equals(idOf.apply(item)))
                .findFirst().orElse(null);
    }

    private void clearErrors() {
        errorSummary.removeAll();
        errorSummary.setVisible(false);
    }

    private void showErrors(List<String> messages) {
        errorSummary.removeAll();
        if (messages.isEmpty()) {
            errorSummary.setVisible(false);
            return;
        }
        var heading = new Span("Please fix the following:");
        heading.getStyle().setFontWeight("600");
        var list = new UnorderedList();
        messages.forEach(message -> list.add(new ListItem(message)));
        errorSummary.add(heading, list);
        errorSummary.setVisible(true);
    }
}

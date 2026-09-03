package com.vaadin.expensemanager.report.ui;

import java.util.function.Supplier;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.streams.DownloadHandler;

/**
 * The read affordance for one attached receipt (Phase 3.2, ADR-0021): an
 * <strong>image</strong> renders a clickable thumbnail that enlarges in a
 * keyboard-operable dialog; a <strong>PDF</strong> renders an "open" link that
 * opens the browser's PDF viewer in a new tab. The same affordance serves a
 * <em>saved</em> receipt (streamed from the DB via the service's
 * {@code DownloadHandler}) and an <em>unsaved</em> one (streamed from the buffered
 * bytes in the working copy) — the caller only supplies a factory that hands back
 * a fresh {@link DownloadHandler} per request, so the bytes are never held here
 * and each {@code <img>}/link gets its own single-use stream.
 *
 * <p>Accessibility (ADR-0020): the thumbnail is a real {@link Button}
 * (focusable, Enter/Space-activatable) with an accessible name; the enlarge
 * dialog is a Vaadin {@link Dialog} (focus-trapped, Esc-closable) with a title
 * naming the receipt and a Close button; on a phone-sized viewport it expands to
 * a full-screen sheet via the {@code receipt-preview-dialog} class (styles.css).
 */
final class ReceiptPreview {

    private ReceiptPreview() {
    }

    /**
     * The inline affordance for a receipt whose content type decides the shape.
     *
     * @param filename       display / accessible name
     * @param contentType    stored sniffed MIME type (image/* → thumbnail, else link)
     * @param handlerFactory yields a fresh streaming handler on each call
     */
    static Component forReceipt(String filename, String contentType,
            Supplier<DownloadHandler> handlerFactory) {
        return isImage(contentType) ? imageThumbnail(filename, handlerFactory)
                : openLink(filename, handlerFactory);
    }

    /**
     * The <strong>chip</strong> form: a paperclip and the filename, which is what the
     * report-detail design draws for an attachment
     * ({@code docs/design/components/expense-line-card.md}).
     *
     * <p>Only the resting presentation changes — activating the chip opens the same
     * enlarge dialog an image thumbnail did, or the same new-tab PDF view, so
     * ADR-0021's read affordance is intact behind it. The filename renders at
     * {@code --vaadin-text-color} rather than the secondary colour every other
     * sub-line uses, which is the design's call and deliberate: a receipt's filename
     * is the thing the user came to check.
     *
     * <p>Used on a row, where a thumbnail no longer fits; the dialogs keep
     * {@link #forReceipt}, whose 56px thumbnail is the editing affordance.
     */
    static Component chip(String filename, String contentType,
            Supplier<DownloadHandler> handlerFactory) {
        if (isImage(contentType)) {
            var button = new Button(filename,
                    event -> openEnlargeDialog(filename, handlerFactory));
            button.setIcon(LucideIcon.PAPERCLIP.create(LucideIcon.SIZE_S));
            button.addThemeVariants(ButtonVariant.TERTIARY);
            button.setAriaLabel("Preview receipt: " + filename);
            button.addClassName("expense-row-attachment");
            return button;
        }
        var anchor = new Anchor(handlerFactory.get(), AttachmentType.INLINE, filename);
        anchor.setTarget(AnchorTarget.BLANK);
        anchor.getElement().setAttribute("aria-label", "Open receipt: " + filename);
        anchor.addComponentAsFirst(LucideIcon.PAPERCLIP.create(LucideIcon.SIZE_S));
        anchor.addClassName("expense-row-attachment");
        return anchor;
    }

    /**
     * The chip for an attachment whose bytes are not in hand — a buffered receipt on
     * a line reopened from a not-yet-saved report. It names the file and is
     * deliberately <em>not</em> activatable: there is nothing to stream yet.
     */
    static Component inertChip(String filename) {
        var chip = new Span(filename);
        chip.addComponentAsFirst(LucideIcon.PAPERCLIP.create(LucideIcon.SIZE_S));
        chip.addClassNames("expense-row-attachment", "expense-row-attachment--inert");
        return chip;
    }

    private static boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    /** A small clickable thumbnail; clicking (or Enter/Space) enlarges it. */
    private static Component imageThumbnail(String filename,
            Supplier<DownloadHandler> handlerFactory) {
        var thumbnail = new Image(handlerFactory.get(), filename);
        thumbnail.setWidth("56px");
        thumbnail.setHeight("56px");
        thumbnail.getStyle().set("object-fit", "cover")
                .set("border-radius", "var(--vaadin-radius-m)")
                .set("border", "1px solid var(--vaadin-border-color)");

        var button = new Button(thumbnail,
                event -> openEnlargeDialog(filename, handlerFactory));
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel("Preview receipt: " + filename);
        button.getStyle().set("padding", "0").set("min-width", "0")
                .set("height", "auto");
        return button;
    }

    private static void openEnlargeDialog(String filename,
            Supplier<DownloadHandler> handlerFactory) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(filename);
        dialog.addClassName("receipt-preview-dialog");

        var full = new Image(handlerFactory.get(), filename);
        full.setMaxWidth("100%");
        full.getStyle().set("max-height", "78vh").set("object-fit", "contain")
                .set("display", "block").set("margin", "0 auto");
        dialog.add(full);

        var close = new Button("Close", event -> dialog.close());
        close.addThemeVariants(ButtonVariant.TERTIARY);
        dialog.getFooter().add(close);
        dialog.open();
    }

    /** A link that opens the PDF inline (browser viewer) in a new tab. */
    private static Component openLink(String filename,
            Supplier<DownloadHandler> handlerFactory) {
        var anchor = new Anchor(handlerFactory.get(), AttachmentType.INLINE,
                "Open " + filename);
        anchor.setTarget(AnchorTarget.BLANK);
        anchor.getElement().setAttribute("aria-label", "Open receipt: " + filename);
        var icon = LucideIcon.FILE_TEXT.create();
        icon.setSize("var(--aura-font-size-s)");
        anchor.addComponentAsFirst(icon);
        anchor.getStyle().set("display", "inline-flex").set("align-items", "center")
                .set("gap", "var(--vaadin-gap-s)")
                .set("font-size", "var(--aura-font-size-s)");
        return anchor;
    }
}

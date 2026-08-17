package com.vaadin.expensemanager.report.ui;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.Image;
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
        Component affordance = isImage(contentType)
                ? imageThumbnail(filename, handlerFactory)
                : openLink(filename, handlerFactory);
        // On an editable report the whole line card is clickable to open the
        // editor; keep a click on the preview (zoom / open) from also bubbling up
        // and opening that editor — mirroring how the trash button is isolated.
        affordance.getElement().executeJs(
                "this.addEventListener('click', e => e.stopPropagation())");
        return affordance;
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

package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;

/**
 * A screen's title row: its name on the left, its primary actions on the right.
 *
 * <p>Every screen in the app opens the same way, so the row is a component rather
 * than a shape each view rebuilds. The heading is a plain {@link H1} — Aura
 * already renders it large, heavy and margin-free, so there is nothing to style.
 *
 * <p>The title comes from the view's own {@code @PageTitle}, which is also what
 * the browser tab and (before ADR-0026) the navbar showed. Passing the view
 * rather than a string keeps a screen from being able to disagree with itself
 * about its own name.
 */
public class ViewHeader extends HorizontalLayout {

    /**
     * @param view    the screen this heads, which must carry {@code @PageTitle}
     * @param actions its primary actions, held at the trailing edge
     */
    public ViewHeader(Component view, Component... actions) {
        this(titleOf(view), actions);
    }

    public ViewHeader(String title, Component... actions) {
        super(new H1(title));
        add(actions);
        setWidthFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    }

    /** A view's declared page title. */
    private static String titleOf(Component view) {
        var annotation = view.getClass().getAnnotation(PageTitle.class);
        if (annotation == null) {
            throw new IllegalStateException(view.getClass().getSimpleName()
                    + " needs @PageTitle to head itself with ViewHeader");
        }
        return annotation.value();
    }
}

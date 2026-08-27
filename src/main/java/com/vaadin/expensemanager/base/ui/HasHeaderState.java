package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.Component;

/**
 * Implemented by a view that wants a header other than {@link HeaderState#DEFAULT}.
 *
 * <p>Which header a screen gets is a property of the screen, not of the shell, so
 * the shell asks rather than decides: {@link MainLayout} reads this off the view
 * as it is attached, and a view that does not implement it gets the plain bar.
 *
 * <p>The state is read <em>once</em>, at attach. A view whose header depends on
 * data it loads later — a report that turns out to be rejected — calls
 * {@link MainLayout#setHeaderState} instead, via
 * {@code findAncestor(MainLayout.class)}.
 */
public interface HasHeaderState {

    /** The header this view asks for. */
    HeaderState headerState();

    /**
     * The one-line status shown under the greeting in {@link HeaderState#HOME},
     * or {@code null} for none. Ignored by every other state, which draws no
     * hero to put it in.
     *
     * <p>The greeting itself is the shell's — it knows who is signed in, and the
     * view should not have to. This is the half only the view knows.
     */
    default String headerMessage() {
        return null;
    }

    /** The state {@code view} asks for, or {@link HeaderState#DEFAULT}. */
    static HeaderState of(Component view) {
        return view instanceof HasHeaderState aware
                ? aware.headerState()
                : HeaderState.DEFAULT;
    }

    /** The hero status line {@code view} supplies, or {@code null}. */
    static String messageOf(Component view) {
        return view instanceof HasHeaderState aware ? aware.headerMessage() : null;
    }
}

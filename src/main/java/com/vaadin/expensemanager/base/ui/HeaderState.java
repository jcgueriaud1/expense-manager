package com.vaadin.expensemanager.base.ui;

/**
 * The five states of the design's {@code Header} component (Figma
 * {@code 116:3876}), which a view picks by implementing {@link HasHeaderState}.
 *
 * <p>The design folds two things into one property, and this enum keeps that
 * shape rather than splitting it: {@link #HOME} is the only <em>tall</em> state —
 * it adds the greeting hero — and the four compact states differ only in the
 * colour of the bar. Splitting height and tint into two axes would invent three
 * combinations the design never drew (a tall green header, say).
 *
 * <p>The three status tints are the report statuses. Nothing selects them yet:
 * the report detail view is redesigned in its own issue, and it will ask for
 * {@link #APPROVED} / {@link #IN_PROGRESS} / {@link #REJECTED} then. They are
 * built here because they are the same code path as {@link #DEFAULT} and adding
 * them later would mean reopening the shell.
 */
public enum HeaderState {

    /** Tall: the bar plus a greeting, a status line and an illustration. */
    HOME("home", true),

    /** The plain coral bar. Every view that does not ask for another state. */
    DEFAULT("default", false),

    /** Compact, tinted {@code --aura-green}. */
    APPROVED("approved", false),

    /** Compact, tinted {@code --aura-blue}. */
    IN_PROGRESS("in-progress", false),

    /** Compact, tinted {@code --aura-red}. */
    REJECTED("rejected", false);

    private final String modifier;
    private final boolean tall;

    HeaderState(String modifier, boolean tall) {
        this.modifier = modifier;
        this.tall = tall;
    }

    /** The BEM modifier class the header carries in this state. */
    public String className() {
        return "app-header--" + modifier;
    }

    /** Whether this state renders the greeting hero. */
    public boolean isTall() {
        return tall;
    }
}

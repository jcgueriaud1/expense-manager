/**
 * Base UI shell and shared UX-state primitives (ADR-0017).
 *
 * <p>Holds the app-wide {@link com.vaadin.expensemanager.base.ui.MainLayout Aura
 * navigation shell}, the throwaway
 * {@link com.vaadin.expensemanager.base.ui.HomeView landing view}, the reusable
 * {@link com.vaadin.expensemanager.base.ui.EmptyState} placeholder, and the
 * global error surfaces
 * ({@link com.vaadin.expensemanager.base.ui.NotFoundView 404} and
 * {@link com.vaadin.expensemanager.base.ui.ErrorView uncaught-exception} pages).
 * Feature views reuse these instead of reinventing empty/error states, keeping
 * UX consistent across the app.
 *
 * <h2>Loading convention</h2>
 * Because this is Flow (server-side Java, ADR-0001), the server holds the
 * request thread until the response is ready, so most views have no visible
 * "loading" state — the page simply renders once. The convention for the cases
 * that <em>do</em> need one:
 *
 * <ul>
 *   <li><strong>Long-running UI actions</strong> (a button that triggers slow
 *       work): disable the trigger for the duration. Prefer
 *       {@code button.setDisableOnClick(true)} so the click can't be
 *       double-fired, and re-enable it when the work completes.</li>
 *   <li><strong>Deferred / background work</strong> that finishes off the
 *       request thread: the app runs with {@code @Push} enabled (see
 *       {@code Application}), so push the result from a background thread inside
 *       {@code ui.access(...)} and show a
 *       {@link com.vaadin.flow.component.progressbar.ProgressBar} (set
 *       {@code setIndeterminate(true)}) while it runs.</li>
 *   <li><strong>Data-bound components</strong> (Grid, ComboBox) backed by a lazy
 *       {@code DataProvider}: rely on the component's built-in loading
 *       indicator; do not add a second overlay.</li>
 * </ul>
 *
 * This is intentionally a thin, documented default — the first feature that
 * renders a genuinely slow flow refines it with concrete UX.
 */
package com.vaadin.expensemanager.base.ui;

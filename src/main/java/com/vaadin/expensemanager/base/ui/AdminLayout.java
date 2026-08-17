package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.router.ParentLayout;

import jakarta.annotation.security.RolesAllowed;

/**
 * The Admin destination: approvals, review history and user management as
 * sub-tabs (ADR-0025).
 *
 * <p>Everything about the shell — heading, tabs, content slot, tab-follows-route
 * — comes from {@link TabbedSectionLayout}. What makes a screen an admin screen
 * is that its {@code @Route} names this layout; the reference tables name
 * {@link ReferenceLayout} instead and so form their own destination.
 */
@ParentLayout(MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminLayout extends TabbedSectionLayout {
}

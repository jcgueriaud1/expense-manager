package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.router.ParentLayout;

import jakarta.annotation.security.RolesAllowed;

/**
 * The Reference tables destination: the rate and classification tables the rest
 * of the app costs against — VAT rates, expense types, allowance rates — as
 * sub-tabs (ADR-0025).
 *
 * <p>Its own header destination rather than a corner of Admin: these are the
 * figures the calculator runs on, consulted and amended as a group, and they
 * answer a different question from approvals or user management.
 *
 * <p>The shell comes from {@link TabbedSectionLayout}; membership is whichever
 * {@code @Menu} screens name this layout in their {@code @Route}.
 */
@ParentLayout(MainLayout.class)
@RolesAllowed("ADMIN")
public class ReferenceLayout extends TabbedSectionLayout {
}

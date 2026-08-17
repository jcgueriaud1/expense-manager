package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.menu.MenuEntry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared shell for a header destination that stands for several screens: a
 * heading naming the current screen, a tab row to move between them, and the
 * screen itself (ADR-0025).
 *
 * <p>Subclasses supply nothing but their identity. The tabs are the {@code @Menu}
 * views that name <em>this</em> layout in their {@code @Route}, so membership is
 * declared by the screen rather than listed here — a new reference table or
 * admin screen appears as a tab by annotating itself, already filtered by the
 * user's access (ADR-0008). Tab order is {@code @Menu order}.
 *
 * <p>Each screen keeps its own flat route, so it stays individually addressable
 * and the browser's back button moves between them.
 */
public abstract class TabbedSectionLayout extends VerticalLayout
        implements RouterLayout, AfterNavigationObserver {

    private final Tabs tabs = new Tabs();
    private final Div content = new Div();
    private final Map<String, Tab> tabsByPath = new LinkedHashMap<>();

    protected TabbedSectionLayout() {
        setPadding(false);
        setSpacing(false);
        setSizeFull();
        // Centre the column, like every other screen.
        setAlignItems(Alignment.CENTER);

        tabs.addClassName("admin-tabs");
        content.addClassName("admin-content");
        content.setWidthFull();
        content.setMinWidth("0");

        for (MenuEntry entry : MainLayout.entriesFor(getClass())) {
            var tab = new Tab(new RouterLink(entry.title(),
                    MainLayout.asComponent(entry.menuClass())));
            tabsByPath.put(MainLayout.normalizePath(entry.path()), tab);
            tabs.add(tab);
        }

        // The tab row is the first thing on the page: it says which of the
        // section's screens you are on, and each screen heads itself with its own
        // title row (ViewHeader) beside its actions.
        var tabRow = new VerticalLayout(tabs);
        tabRow.setPadding(true);
        tabRow.setWidthFull();

        var column = new VerticalLayout(tabRow, content);
        column.setPadding(false);
        column.setSpacing(false);
        column.setWidthFull();
        column.setMaxWidth(MainLayout.CONTENT_MAX_WIDTH);
        add(column);
        expand(column);
    }

    /**
     * Renders the selected screen. Swapping into a slot of our own — rather than
     * letting the default append to this layout — keeps the tab row above the
     * content and guarantees the previous screen is gone before the next arrives.
     */
    @Override
    public void showRouterLayoutContent(HasElement newContent) {
        content.removeAll();
        if (newContent != null) {
            content.getElement().appendChild(newContent.getElement());
        }
    }

    /**
     * Marks the tab of the screen just navigated to.
     *
     * <p>The selected tab follows the <em>route</em>, not a click: arriving by
     * deep link, browser back, or a redirect from another view all have to light
     * the right tab, and only the location tells us which that is.
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {

        var tab = tabsByPath.get(MainLayout.normalizePath(event.getLocation().getPath()));
        if (tab != null) {
            tabs.setSelectedTab(tab);
        }
    }

}

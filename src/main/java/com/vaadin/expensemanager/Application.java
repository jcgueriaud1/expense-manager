package com.vaadin.expensemanager;

import com.vaadin.expensemanager.base.ui.ThemeSwitcher;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;

@SpringBootApplication
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css") // Your custom styles
@Push
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Re-apply the user's persisted colour-scheme choice (see {@link ThemeSwitcher})
     * before first paint. Prepending this to the page head means a reload — on any
     * page, including the login view outside the main layout — never flashes the
     * theme default before the switcher's client code runs.
     *
     * <p>Sets both the inline {@code color-scheme} and the {@code theme} attribute
     * on {@code <html>}, mirroring what {@link com.vaadin.flow.component.page.Page#setColorScheme
     * Page.setColorScheme} does at runtime, so a reload reconstructs the exact same
     * document state a live switch produces (Aura re-themes off {@code color-scheme}
     * alone, but the {@code theme} attribute keeps the two paths in lockstep for any
     * {@code html[theme~="…"]} rules).
     */
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addInlineWithContents(Inline.Position.PREPEND,
                "try{var s=localStorage.getItem('" + ThemeSwitcher.STORAGE_KEY + "');"
                        + "if(s==='light'||s==='dark'){"
                        + "document.documentElement.style.colorScheme=s;"
                        + "document.documentElement.setAttribute('theme',s);}}catch(e){}",
                Inline.Wrapping.JAVASCRIPT);
    }

}

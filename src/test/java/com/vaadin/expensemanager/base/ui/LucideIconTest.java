package com.vaadin.expensemanager.base.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the vendored Lucide icon set (see {@link LucideIcon}).
 *
 * <p>An icon whose file is missing fails <em>silently</em>: {@code SvgIcon}
 * fetches the path, gets a 404, and renders nothing — no exception, no console
 * error, just a gap in the UI that only a human looking at the right screen
 * would notice. These tests turn that into a red build instead, which matters
 * because the enum constant and the file it names are only connected by a
 * string built at runtime.
 */
class LucideIconTest {

    @ParameterizedTest
    @EnumSource(LucideIcon.class)
    void everyConstantHasAVendoredSvgFile(LucideIcon icon) {
        assertThat(resource(icon.path()))
                .as("%s expects %s — vendor it from lucide.dev or drop the constant",
                        icon, icon.path())
                .isNotNull();
    }

    @Test
    void pathIsTheSlugifiedConstantName() {
        assertThat(LucideIcon.ROTATE_CCW_CLOCK.path())
                .isEqualTo("icons/lucide/rotate-ccw-clock.svg");
    }

    /**
     * The licence has to travel with the files: Lucide is ISC, and the
     * Feather-derived subset is additionally MIT — both require the copyright
     * and permission notice to ship with any copy.
     */
    @Test
    void licenceShipsAlongsideTheIcons() {
        assertThat(resource(LucideIcon.FOLDER + "LICENSE"))
                .as("the Lucide LICENSE must stay next to the vendored icons")
                .isNotNull();
    }

    private static java.net.URL resource(String path) {
        return LucideIconTest.class.getClassLoader()
                .getResource("META-INF/resources/" + path);
    }
}

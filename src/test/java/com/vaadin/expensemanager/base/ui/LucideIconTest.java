package com.vaadin.expensemanager.base.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Plain-JUnit tests for {@link LucideIcon} and the sprite behind it (#163,
 * ADR-0026) — no UI or Spring needed, an {@code SvgIcon} carries its addressing
 * on its own element while detached.
 *
 * <p><strong>Why the sprite needs a test at all.</strong> Every failure mode here
 * is silent in the browser. A glyph name that does not match a {@code <symbol>}
 * renders an empty box — {@code <use>} resolving to nothing is not an error. A
 * {@code <symbol>} missing {@code stroke="currentColor"} renders a black glyph
 * that looks right in light mode and disappears in dark. Neither shows up in a
 * green suite that only asserts a button exists, so the sprite's invariants are
 * asserted mechanically instead.
 */
class LucideIconTest {

    private static final Path SPRITE =
            Path.of("src/main/resources/META-INF/resources", LucideIcon.SPRITE);

    /** {@code id="…"} on a {@code <symbol>}, which is what a glyph resolves against. */
    private static final Pattern SYMBOL_ID =
            Pattern.compile("<symbol\\s+id=\"([^\"]+)\"");

    private static String sprite() throws IOException {
        return Files.readString(SPRITE, StandardCharsets.UTF_8);
    }

    private static List<String> symbolIds() throws IOException {
        var ids = Stream.<String>builder();
        Matcher m = SYMBOL_ID.matcher(sprite());
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids.build().toList();
    }

    @ParameterizedTest
    @EnumSource(LucideIcon.class)
    void everyConstantResolvesToASymbolInTheSprite(LucideIcon icon) throws IOException {
        assertThat(symbolIds())
                .as("%s addresses '%s', which must exist in %s — a glyph name with no "
                        + "matching <symbol> renders an empty box and never errors",
                        icon, icon.glyph(), LucideIcon.SPRITE)
                .contains(icon.glyph());
    }

    @Test
    void theSpriteCarriesNoGlyphTheEnumDoesNotClaim() throws IOException {
        var claimed = Arrays.stream(LucideIcon.values()).map(LucideIcon::glyph).toList();
        assertThat(symbolIds())
                .as("an unreferenced glyph is dead weight on every page load, and the "
                        + "sprite is hand-rebuilt — so it is the half that drifts")
                .allSatisfy(id -> assertThat(claimed).contains(id));
    }

    @ParameterizedTest
    @EnumSource(LucideIcon.class)
    void everySymbolStrokesInCurrentColorOnTheLucideGrid(LucideIcon icon)
            throws IOException {
        var symbol = symbolOf(icon);

        // Addressed through `symbol`, vaadin-icon renders <use href="…"> against the
        // external file and never fetches it, so it reads no attribute off the
        // sprite's root <svg> and sets none on the <svg> it renders (F-075). Each
        // symbol therefore has to carry its own presentation attributes.
        assertThat(symbol)
                .as("%s must stroke in currentColor or it is a black glyph that "
                        + "vanishes in dark mode", icon)
                .contains("stroke=\"currentColor\"")
                .contains("fill=\"none\"")
                .contains("stroke-width=\"2\"")
                .contains("stroke-linecap=\"round\"")
                .contains("stroke-linejoin=\"round\"")
                // vaadin-icon's `size` defaults to 24 and, with no viewBox read from
                // the file, that is the viewBox it renders. Lucide is a 24 grid, so
                // the two agree — but only as long as this stays 24.
                .contains("viewBox=\"0 0 24 24\"");
    }

    @Test
    void createAddressesTheSpriteBySymbolRatherThanAFragmentUrl() {
        var icon = LucideIcon.PLANE.create();

        assertThat(icon.getSrc()).isEqualTo("icons/lucide.svg");
        assertThat(icon.getSymbol()).isEqualTo("plane");
    }

    @Test
    void createReturnsAFreshIconEachTime() {
        // A Component attaches in one place only, so a cached constant would move
        // the glyph rather than draw it in both.
        assertThat(LucideIcon.PLUS.create()).isNotSameAs(LucideIcon.PLUS.create());
    }

    @Test
    void createLeavesSizeToTheContextUnlessAsked() {
        assertThat(LucideIcon.PLUS.create().getStyle().get("width")).isNull();
        assertThat(LucideIcon.PLUS.create("16px").getStyle().get("width"))
                .isEqualTo("16px");
    }

    @Test
    void theVendoredLicenceStaysBesideTheSprite() {
        // Lucide is ISC — attribution is a condition of use, so losing this file
        // is a licensing problem and not a tidy-up.
        assertThat(SPRITE.resolveSibling("LICENSE.lucide")).exists();
    }

    private String symbolOf(LucideIcon icon) throws IOException {
        var sprite = sprite();
        int start = sprite.indexOf("<symbol id=\"" + icon.glyph() + "\"");
        assertThat(start).as("<symbol id=\"%s\"> present", icon.glyph()).isNotNegative();
        return sprite.substring(start, sprite.indexOf("</symbol>", start));
    }
}

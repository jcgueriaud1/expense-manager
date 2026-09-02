package com.vaadin.expensemanager.base.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.xml.sax.SAXException;

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

    private static final Path THEME = Path.of(
            "src/main/resources/META-INF/resources/aura-theme.css");

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

    @Test
    void theSpriteIsWellFormedXml() throws IOException, ParserConfigurationException {
        // Every other test in this class reads the sprite as TEXT, so all of them pass
        // on a file the browser cannot parse. That is not hypothetical: an XML comment
        // may not contain a double hyphen, and a comment naming a custom property in
        // full ("--vaadin-icon-stroke-width") silently made this file malformed. The
        // browser then resolved every external <use> to nothing and every icon in the
        // app vanished — with no console error, because the failure is inside the
        // browser's own SVG resolution rather than a fetch. F-076.
        var factory = DocumentBuilderFactory.newInstance();
        // No network: the SVG doctype/entities must never be fetched from a test.
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.newDocumentBuilder().parse(SPRITE.toFile());
        } catch (SAXException e) {
            throw new AssertionError(("%s is not well-formed XML, so every <use> "
                    + "against it resolves to nothing and every icon renders blank "
                    + "with no console error. Parser said: %s")
                            .formatted(LucideIcon.SPRITE, e.getMessage()), e);
        }
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
        // symbol therefore has to carry its own presentation attributes — every one
        // except stroke-width, see below.
        assertThat(symbol)
                .as("%s must stroke in currentColor or it is a black glyph that "
                        + "vanishes in dark mode", icon)
                .contains("stroke=\"currentColor\"")
                .contains("fill=\"none\"")
                .contains("stroke-linecap=\"round\"")
                .contains("stroke-linejoin=\"round\"")
                // vaadin-icon's `size` defaults to 24 and, with no viewBox read from
                // the file, that is the viewBox it renders. Lucide is a 24 grid, so
                // the two agree — but only as long as this stays 24.
                .contains("viewBox=\"0 0 24 24\"");
    }

    @ParameterizedTest
    @EnumSource(LucideIcon.class)
    void noSymbolDeclaresStrokeWidthSoTheThemeTokenCanReachIt(LucideIcon icon)
            throws IOException {
        // The one presentation attribute that must NOT be here. The theme sets
        // --vaadin-icon-stroke-width, which the base styles apply to the <svg>
        // vaadin-icon renders; stroke-width is inherited, so it reaches the <use>'s
        // referenced content — unless the referenced element declares it itself,
        // because a presentation attribute on an element beats an inherited value at
        // any specificity. That is F-072's mechanism one layer down, and re-adding
        // this attribute kills the theme knob in total silence.
        assertThat(symbolOf(icon))
                .as("%s must not declare stroke-width — it would silently make "
                        + "--vaadin-icon-stroke-width inert (see aura-theme.css)", icon)
                .doesNotContain("stroke-width");
    }

    @Test
    void theThemeDeclaresTheStrokeWidthTheSymbolsNoLongerCarry() throws IOException {
        // Load-bearing, and the reason the assertion above is safe. The base styles
        // guard the rule with `@container style(--vaadin-icon-stroke-width)`, so it
        // applies only while the property is set, and the sprite branch sets no
        // stroke-width of its own. Unset this and every icon in the app renders at
        // the SVG default of 1 — visibly thin, with nothing logged.
        assertThat(Files.readString(THEME, StandardCharsets.UTF_8))
                .as("aura-theme.css must declare --vaadin-icon-stroke-width; without "
                        + "it every glyph falls back to stroke-width 1")
                .containsPattern("--vaadin-icon-stroke-width:\\s*2\\s*;");
    }

    @Test
    void theThemeDeclaresEveryRoleSizeTheEnumOffers() throws IOException {
        // SIZE_S/M/L are var() references, so a missing declaration renders unset
        // rather than wrong — visible, but only if someone looks at that one icon.
        var theme = Files.readString(THEME, StandardCharsets.UTF_8);
        assertThat(theme).contains("--em-icon-size-s:", "--em-icon-size-m:",
                "--em-icon-size-l:");
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
    void createAppliesTheButtonRoleSizeAndTakesAnOverride() {
        assertThat(LucideIcon.PLUS.create().getStyle().get("width"))
                .isEqualTo(LucideIcon.SIZE_M);
        assertThat(LucideIcon.PLANE.create(LucideIcon.SIZE_S).getStyle().get("width"))
                .isEqualTo(LucideIcon.SIZE_S);
    }

    @Test
    void theRoleSizesReferencePropertiesRatherThanBakingInPixels() {
        // A raw px here would put the scale in two places, and the Java copy is the
        // one nobody looks at when the design moves.
        assertThat(LucideIcon.SIZE_S).isEqualTo("var(--em-icon-size-s)");
        assertThat(LucideIcon.SIZE_M).isEqualTo("var(--em-icon-size-m)");
        assertThat(LucideIcon.SIZE_L).isEqualTo("var(--em-icon-size-l)");
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

package com.vaadin.expensemanager.base.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The one acceptance criterion of #163 that no ordinary test can hold: that
 * {@link LucideIcon} stays the <em>only</em> icon set in the app.
 *
 * <p>Issue #163 phrased "done" as a grep over {@code src/main/java} returning
 * nothing. A grep is only true on the day it is run, and the 25 call sites it
 * cleared were themselves added one at a time by people reaching for the obvious
 * import. So the grep runs here instead, where re-introducing the old set fails a
 * build rather than passing review.
 *
 * <p><strong>This is not a claim that the banned sets are broken.</strong> Both are
 * defined, supported and correctly typed — {@code CLAUDE.md}'s test for a token is
 * whether the theme defines it, and these pass it. They are banned for one narrower
 * reason: they draw a different picture from the design. Lumo-era glyphs are filled
 * and heavier on a 16px grid, Lucide is a 24px 2px-stroke outline set, and side by
 * side the weight mismatch reads as a different product (ADR-0026).
 *
 * <p>Scanning source text rather than bytecode is deliberate: two of the four
 * patterns are <em>strings</em>, invisible to any reflective or bytecode check.
 */
class IconSetGuardTest {

    private static final Path SOURCES = Path.of("src/main/java");

    /**
     * What may not appear in {@code src/main/java}, and the message a developer who
     * trips it should read. {@link LucideIcon} names two of these in its own javadoc
     * to explain the ban, so it is the single exempt file.
     */
    private static final Map<Pattern, String> BANNED = Map.of(
            Pattern.compile("\\bVaadinIcon\\b"),
            "Vaadin Icons — use LucideIcon instead (ADR-0026)",
            Pattern.compile("\\bLumoIcon\\b"),
            "Lumo Icons — use LucideIcon instead (ADR-0026)",
            Pattern.compile("\"vaadin:[a-z0-9-]+\""),
            "a 'vaadin:name' font-icon string — the Lumo addressing form; "
                    + "pass LucideIcon.X.create() instead (#163)",
            Pattern.compile("\"lumo:[a-z0-9-]+\""),
            "a 'lumo:name' font-icon string — the Lumo addressing form; "
                    + "pass LucideIcon.X.create() instead (#163)");

    private static final List<String> EXEMPT = List.of(
            "LucideIcon.java", "IconSetGuardTest.java");

    @Test
    void noViewReachesForAnIconSetOtherThanLucide() throws IOException {
        record Offence(String file, int line, String what, String text) {
        }

        List<Offence> offences;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            offences = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !EXEMPT.contains(p.getFileName().toString()))
                    .flatMap(p -> readLines(p).stream()
                            .flatMap(entry -> BANNED.entrySet().stream()
                                    .filter(ban -> ban.getKey()
                                            .matcher(entry.getValue()).find())
                                    .map(ban -> new Offence(
                                            SOURCES.relativize(p).toString(),
                                            entry.getKey(), ban.getValue(),
                                            entry.getValue().strip()))))
                    .toList();
        }

        assertThat(offences)
                .as("every glyph the app draws comes from LucideIcon (#163). Offending "
                        + "lines, each with the set it reached for:%n%s",
                        offences.stream()
                                .map(o -> "  %s:%d — %s%n      %s".formatted(
                                        o.file(), o.line(), o.what(), o.text()))
                                .reduce("", (a, b) -> a + b + "\n"))
                .isEmpty();
    }

    @Test
    void theGuardWouldActuallyCatchTheOldCallShapes() {
        // A guard whose patterns have quietly stopped matching passes for the wrong
        // reason, and reads identically in a green suite. So the four shapes this
        // ban exists to catch are asserted against the patterns directly.
        assertThat(matches("var b = new Button(new Icon(VaadinIcon.PLUS));")).isTrue();
        assertThat(matches("icon.setIcon(LumoIcon.CHECKMARK.create());")).isTrue();
        assertThat(matches("new EmptyState(\"vaadin:inbox\", \"None\", \"…\");")).isTrue();
        assertThat(matches("new Icon(\"lumo:plus\");")).isTrue();

        // And the shapes it must not catch: the package the app imports every day,
        // and the set it is supposed to use.
        assertThat(matches("import com.vaadin.flow.component.icon.SvgIcon;")).isFalse();
        assertThat(matches("var b = new Button(LucideIcon.PLUS.create());")).isFalse();
    }

    private static boolean matches(String line) {
        return BANNED.keySet().stream().anyMatch(p -> p.matcher(line).find());
    }

    /** Line number (1-based) to line text. */
    private static List<Map.Entry<Integer, String>> readLines(Path path) {
        try {
            var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return Stream.iterate(0, i -> i + 1).limit(lines.size())
                    .map(i -> Map.entry(i + 1, lines.get(i)))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}

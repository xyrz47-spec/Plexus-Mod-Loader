package dev.polymodloader.runtime.metadata.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MinecraftVersionPatternTest {

    @ParameterizedTest
    @CsvSource({
        "26.2, 26.2, true",
        "26.2, 26.2.1, false",
        "26.*, 26.1, true",
        "26.*, 26.2.1, true",
        "26.*, 27.1, false",
        "26.*, 260.1, false",
        "26.2.*, 26.2, true",
        "26.2.*, 26.2.3, true",
        "26.2.*, 26.3, false",
        "*, 26.3-snapshot-1, true",
        "'>=26.2 <27', 26.2, true",
        "'>=26.2 <27', 26.4.1, true",
        "'>=26.2 <27', 27.0, false",
        "'>=26.2 <27', 26.1, false",
        "'>=26.2', 26.3-snapshot-1, false",
        "'=26.2', 26.2.0, true",
        "26.3-snapshot-1, 26.3-snapshot-1, true",
    })
    void matchesGameVersions(String pattern, String gameVersion, boolean expected) {
        assertEquals(
                expected,
                MinecraftVersionPattern.parse(pattern).orElseThrow().matches(gameVersion),
                pattern + " vs " + gameVersion);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "26.x.*", "26.*.2", ">=26.2-pre1", "~26", "26 27", "26*"})
    void rejectsUnsupportedSyntax(String pattern) {
        assertTrue(MinecraftVersionPattern.parse(pattern).isEmpty(), pattern);
    }

    @Test
    void anyMatchesSkipsUnparsableEntries() {
        assertTrue(MinecraftVersionPattern.anyMatches(List.of("26 27", "26.*"), "26.2"));
        assertFalse(MinecraftVersionPattern.anyMatches(List.of("26 27"), "26"));
    }
}

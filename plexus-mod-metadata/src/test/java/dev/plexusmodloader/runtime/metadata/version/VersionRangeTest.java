package dev.plexusmodloader.runtime.metadata.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionRangeTest {

    @ParameterizedTest
    @CsvSource({
        "'>=0.1.0 <0.2.0', 0.1.0, true",
        "'>=0.1.0 <0.2.0', 0.1.9, true",
        "'>=0.1.0 <0.2.0', 0.2.0, false",
        "'>=0.1.0 <0.2.0', 0.1.0-alpha.1, false",
        "'>=0.1.0-alpha <0.2.0', 0.1.0-alpha.1, true",
        "'>1.0.0', 1.0.0, false",
        "'<=1.0.0', 1.0.0, true",
        "1.2.3, 1.2.3+build.5, true",
        "'=1.2.3', 1.2.4, false",
        "*, 99.0.0, true",
    })
    void matchesSemanticVersionPrecedence(String range, String version, boolean expected) {
        assertEquals(
                expected,
                VersionRange.parse(range)
                        .orElseThrow()
                        .contains(SemanticVersion.parse(version).orElseThrow()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "~1.0.0", "^1.0.0", ">=1.0", "[1.0,2.0)", "latest"})
    void rejectsUnsupportedSyntax(String range) {
        assertTrue(VersionRange.parse(range).isEmpty());
    }

    @Test
    void orderingFollowsSemVerSpecificationExample() {
        String[] ordered = {
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0"
        };
        for (int i = 0; i + 1 < ordered.length; i++) {
            SemanticVersion lower = SemanticVersion.parse(ordered[i]).orElseThrow();
            SemanticVersion higher = SemanticVersion.parse(ordered[i + 1]).orElseThrow();
            assertTrue(lower.compareTo(higher) < 0, ordered[i] + " < " + ordered[i + 1]);
        }
    }

    @Test
    void rejectsLeadingZeros() {
        assertFalse(SemanticVersion.parse("01.0.0").isPresent());
    }
}

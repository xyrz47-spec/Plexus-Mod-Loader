package dev.plexusmodloader.compat.quilt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;
import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.api.VersionRange;

class QuiltVersionSpecifierTest {

    private static Predicate<String> spec(String specifier) throws VersionFormatException {
        return QuiltVersionSpecifier.accepts(QuiltVersionSpecifier.read(specifier));
    }

    private static Predicate<String> versions(String json) throws VersionFormatException {
        return QuiltVersionSpecifier.accepts(QuiltVersionSpecifier.readVersions(JsonParser.parseString(json)));
    }

    private static void accepts(Predicate<String> predicate, String... versions) {
        for (String version : versions) {
            assertTrue(predicate.test(version), version);
        }
    }

    private static void rejects(Predicate<String> predicate, String... versions) {
        for (String version : versions) {
            assertFalse(predicate.test(version), version);
        }
    }

    @Test
    void operators() throws VersionFormatException {
        accepts(spec("*"), "0.0.1", "26.2", "anything");
        accepts(spec("=1.0.0"), "1.0.0");
        rejects(spec("=1.0.0"), "1.0.1", "0.9.9");
        accepts(spec(">=1.0.0"), "1.0.0", "2.0.0");
        rejects(spec(">=1.0.0"), "0.9.9");
        accepts(spec(">1.0.0"), "1.0.1");
        rejects(spec(">1.0.0"), "1.0.0");
        accepts(spec("<=1.0.0"), "1.0.0", "0.1.0");
        rejects(spec("<=1.0.0"), "1.0.1");
        accepts(spec("<1.0.0"), "0.9.9");
        rejects(spec("<1.0.0"), "1.0.0");
    }

    @Test
    void aBareSemanticVersionMeansCaret() throws VersionFormatException {
        accepts(spec("1.0.0"), "1.0.0", "1.5.0", "1.99.99");
        rejects(spec("1.0.0"), "0.9.0", "2.0.0");
        accepts(spec("^1.0.0"), "1.0.0", "1.5.0");
        rejects(spec("^1.0.0"), "2.0.0");
    }

    @Test
    void tildeAndWildcards() throws VersionFormatException {
        accepts(spec("~1.0.0"), "1.0.0", "1.0.9");
        rejects(spec("~1.0.0"), "1.1.0", "0.9.0");
        accepts(spec("1.0.x"), "1.0.0", "1.0.7");
        rejects(spec("1.0.x"), "1.1.0");
        accepts(spec("26.2.x"), "26.2", "26.2.1");
        rejects(spec("26.2.x"), "26.3", "26.1.2");
    }

    @Test
    void aBareVersionThatIsNotSemanticMustBeEqual() throws VersionFormatException {
        accepts(spec("snapshot-abc"), "snapshot-abc");
        rejects(spec("snapshot-abc"), "snapshot-abd");
    }

    @Test
    void preReleasesAndBuildMetadata() throws VersionFormatException {
        accepts(spec(">=1.0.0-beta.2"), "1.0.0-beta.3", "1.0.0");
        rejects(spec(">=1.0.0-beta.2"), "1.0.0-beta.1");
        accepts(spec(">=0.30.0"), "0.31.0-beta.4");
        accepts(spec("=1.0.0+build.5"), "1.0.0+build.5");
    }

    @Test
    void unreadableSpecifiersAreRefused() {
        for (String bad : List.of("~abc", "^abc", "1.x.x", "", ">", "<")) {
            assertThrows(VersionFormatException.class, () -> QuiltVersionSpecifier.read(bad), bad);
        }
    }

    @Test
    void versionsValues() throws VersionFormatException {
        assertEquals(VersionRange.ANY, QuiltVersionSpecifier.readVersions(null));
        accepts(versions("\">=2.0.0\""), "2.0.0");
        Predicate<String> deprecatedArray = versions("[\"=1.0.0\", \"=2.0.0\"]");
        accepts(deprecatedArray, "1.0.0", "2.0.0");
        rejects(deprecatedArray, "1.5.0");
        Predicate<String> any = versions("{\"any\": [\"26.1.x\", \"26.2.x\"]}");
        accepts(any, "26.1", "26.1.2", "26.2");
        rejects(any, "26.3", "25.0");
        Predicate<String> all = versions("{\"all\": [\">=1.0.0\", \"<2.0.0\"]}");
        accepts(all, "1.0.0", "1.9.9");
        rejects(all, "2.0.0", "0.9.0");
        Predicate<String> nested = versions("{\"any\": [\"=0.5.0\", {\"all\": [\">=1.0.0\", \"<1.2.0\"]}]}");
        accepts(nested, "0.5.0", "1.1.0");
        rejects(nested, "1.2.0");
    }

    @Test
    void versionsValuesQuiltLoaderRefuses() {
        for (String bad : List.of(
                "{}",
                "{\"all\": []}",
                "{\"all\": [\"*\"]}",
                "{\"any\": \"1.0.0\"}",
                "{\"any\": [1]}",
                "{\"any\": [\"~abc\"]}",
                "5",
                "[5]",
                "true")) {
            assertThrows(
                    VersionFormatException.class,
                    () -> QuiltVersionSpecifier.readVersions(JsonParser.parseString(bad)),
                    bad);
        }
    }

    /**
     * Quilt Loader 0.31.0-beta.4 compares the joined range with {@code VersionRange.NONE}/{@code ANY} by identity, and
     * joining always makes a new range: an empty {@code any} is read and matches nothing, an {@code any} of {@code *}
     * matches everything. The same values give the same ranges here.
     */
    @Test
    void versionsObjectsQuiltLoaderReadsDespiteTheRfc() throws VersionFormatException {
        rejects(versions("{\"any\": []}"), "1.0.0", "0.0.1");
        accepts(versions("{\"any\": [\"*\"]}"), "1.0.0", "0.0.1");
        rejects(versions("{\"all\": [\"<1.0.0\", \">2.0.0\"]}"), "0.5.0", "3.0.0");
        accepts(spec("="), "");
    }

    @Test
    void theSameTextMeansSomethingElseUnderFabric() throws Exception {
        VersionPredicate fabricBare = VersionPredicate.parse("1.0.0");
        assertFalse(fabricBare.test(net.fabricmc.loader.api.Version.parse("1.2.0")), "Fabric: exactly 1.0.0");
        assertTrue(spec("1.0.0").test("1.2.0"), "Quilt: ^1.0.0");

        VersionPredicate fabricWildcard = VersionPredicate.parse("26.2.x");
        assertTrue(fabricWildcard.test(net.fabricmc.loader.api.Version.parse("26.2.1")));
        assertFalse(fabricWildcard.test(net.fabricmc.loader.api.Version.parse("26.3")));
        accepts(spec("26.2.x"), "26.2.1");
        rejects(spec("26.2.x"), "26.3");
    }
}

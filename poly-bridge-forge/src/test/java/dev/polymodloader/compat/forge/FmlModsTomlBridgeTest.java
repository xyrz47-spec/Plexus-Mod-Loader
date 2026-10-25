package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.CompatibilityStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FmlModsTomlBridgeTest {

    @Test
    void neoForgeModsAreBlockedWithTheFmlFeaturesTheyNeed() {
        BridgeAssessment assessment = new NeoForgeBridge().assess("""
                        modLoader = "javafml"
                        loaderVersion = "[1,)"
                        license = "MIT"
                        [[mods]]
                        modId = "examplemod"
                        version = "1.2.3"
                        [[mixins]]
                        config = "examplemod.mixins.json"
                        [[accessTransformers]]
                        file = "META-INF/accesstransformer.cfg"
                        [[dependencies.examplemod]]
                        modId = "neoforge"
                        type = "required"
                        """, GameSide.CLIENT);

        assertEquals("examplemod", assessment.modId());
        assertEquals("1.2.3", assessment.version());
        assertEquals(CompatibilityStatus.BLOCKED, assessment.status());
        assertEquals(
                List.of("POLY-COMPAT-201", "POLY-COMPAT-202", "POLY-COMPAT-203", "POLY-COMPAT-204"),
                assessment.findings().stream().map(CompatibilityFinding::code).toList());
    }

    @Test
    void everyModIdOfTheJarIsReported() {
        BridgeAssessment assessment = new NeoForgeBridge().assess("""
                        modLoader = "javafml"
                        [[mods]]
                        modId = "core"
                        version = "2.0"
                        [[mods]]
                        modId = "core_addon"
                        """, GameSide.CLIENT);

        assertEquals(List.of("core", "core_addon"), assessment.allIds());
    }

    /** Values of an unexpected TOML type used to throw out of the bridge and stop the launch. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "mods = 3",
                "mods = [1, 2]",
                "[[mods]]\nmodId = 5\nversion = [1]",
                "modLoader = 7\n[[mods]]\nmodId = \"a\"",
                "mixins = \"a.json\"\n[[mods]]\nmodId = \"a\"",
                "accessTransformers = 1\n[[mods]]\nmodId = \"a\"",
                "dependencies = \"x\"\n[[mods]]\nmodId = \"a\"",
                ""
            })
    void malformedMetadataNeverThrows(String metadata) {
        for (GameSide side : GameSide.values()) {
            BridgeAssessment neoForge = assertDoesNotThrow(() -> new NeoForgeBridge().assess(metadata, side));
            BridgeAssessment forge = assertDoesNotThrow(() -> new MinecraftForgeBridge().assess(metadata, side));
            assertEquals(CompatibilityStatus.BLOCKED, neoForge.status());
            assertEquals(CompatibilityStatus.BLOCKED, forge.status());
        }
    }

    @Test
    void invalidTomlIsReported() {
        BridgeAssessment assessment = new MinecraftForgeBridge().assess("modLoader = = broken", GameSide.CLIENT);

        assertEquals(CompatibilityStatus.BLOCKED, assessment.status());
        assertEquals("POLY-COMPAT-200", assessment.findings().getFirst().code());
    }
}

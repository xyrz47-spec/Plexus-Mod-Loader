package dev.polymodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersionScaffolderTest {

    @TempDir
    Path root;

    @Test
    void copiesModulesWithTheVersionRenamed() throws IOException {
        writeVersions("supported=26.2\ndefault=26.2\njava.26.2=25\n");
        write(
                "poly-version-adapter-v26_2/src/main/java/dev/polymodloader/game/version/v26_2/Minecraft26_2Adapter.java",
                """
                package dev.polymodloader.game.version.v26_2;
                /** Adapter for Minecraft 26.2, not 26.2.1 or 0.26.2; layer v26_2.GameLayer26_2 */
                final class Minecraft26_2Adapter {
                    static final String GAME_VERSION = "26.2";
                    static final String ID = "minecraft-26.2";
                    static final int UNRELATED = 126_2;
                }
                """);
        write(
                "poly-version-adapter-v26_2/src/main/resources/META-INF/services/dev.polymodloader.game.version.GameVersionAdapter",
                "dev.polymodloader.game.version.v26_2.Minecraft26_2Adapter\n");
        write("poly-game-v26_2/build.gradle.kts", "description = \"Game layer for Minecraft 26.2\"\n");
        write("poly-game-v26_2/src/main/java/dev/polymodloader/game/layer/v26_2/GameLayer26_2.java", "class GameLayer26_2 {}\n");
        write("poly-game-v26_2/build/classes/Stale.class", "stale");
        write("poly-game-v26_2/logs/latest.log", "log");

        List<Path> created = VersionScaffolder.scaffold(root, "26.2", "26.3", 26);

        assertEquals(4, created.size());
        String adapter = Files.readString(root.resolve(
                "poly-version-adapter-v26_3/src/main/java/dev/polymodloader/game/version/v26_3/Minecraft26_3Adapter.java"));
        assertTrue(adapter.contains("package dev.polymodloader.game.version.v26_3;"), adapter);
        assertTrue(adapter.contains("Adapter for Minecraft 26.3, not 26.2.1 or 0.26.2; layer v26_3.GameLayer26_3"), adapter);
        assertTrue(adapter.contains("final class Minecraft26_3Adapter"), adapter);
        assertTrue(adapter.contains("GAME_VERSION = \"26.3\""), adapter);
        assertTrue(adapter.contains("\"minecraft-26.3\""), adapter);
        assertTrue(adapter.contains("UNRELATED = 126_2"), adapter);
        assertEquals(
                "dev.polymodloader.game.version.v26_3.Minecraft26_3Adapter\n",
                Files.readString(root.resolve(
                        "poly-version-adapter-v26_3/src/main/resources/META-INF/services/dev.polymodloader.game.version.GameVersionAdapter")));
        assertTrue(Files.exists(
                root.resolve("poly-game-v26_3/src/main/java/dev/polymodloader/game/layer/v26_3/GameLayer26_3.java")));
        assertFalse(Files.exists(root.resolve("poly-game-v26_3/build")));
        assertFalse(Files.exists(root.resolve("poly-game-v26_3/logs")));

        MinecraftVersions versions = MinecraftVersions.read(root);
        assertEquals(List.of("26.2", "26.3"), versions.supported());
        assertEquals("26.2", versions.defaultVersion());
        assertEquals(26, versions.javaVersion("26.3"));
        assertTrue(Files.readString(root.resolve(MinecraftVersions.FILE)).startsWith("# comment\n"));
    }

    @Test
    void refusesUnknownSourcesAndExistingTargets() throws IOException {
        writeVersions("supported=26.2\ndefault=26.2\njava.26.2=25\n");
        Files.createDirectories(root.resolve("poly-version-adapter-v26_2"));
        Files.createDirectories(root.resolve("poly-game-v26_3"));

        assertThrows(IllegalArgumentException.class, () -> VersionScaffolder.scaffold(root, "26.1", "26.3", 25));
        assertThrows(IllegalArgumentException.class, () -> VersionScaffolder.scaffold(root, "26.2", "26.2", 25));
        assertThrows(IllegalArgumentException.class, () -> VersionScaffolder.scaffold(root, "26.2", "26.3", 25));
        assertThrows(IllegalArgumentException.class, () -> VersionScaffolder.scaffold(root, "26.2", "../x", 25));
    }

    @Test
    void parsesTheVersionsFile() {
        Properties properties = new Properties();
        properties.setProperty("supported", "26.2, 26.3-snapshot-1");
        properties.setProperty("default", "26.2");
        properties.setProperty("java.26.2", "25");
        properties.setProperty("java.26.3-snapshot-1", "26");

        MinecraftVersions versions = MinecraftVersions.parse(properties);

        assertEquals(List.of("26.2", "26.3-snapshot-1"), versions.supported());
        assertEquals("poly-game-v26_3_snapshot_1", MinecraftVersions.gameLayerModule("26.3-snapshot-1"));
        assertEquals("dev.polymodloader.game.layer.v26_2.GameLayer26_2", MinecraftVersions.gameLayerClass("26.2"));
        assertEquals("26.3-snapshot-1", versions.versionOfModule("poly-game-v26_3_snapshot_1").orElseThrow());
        assertTrue(versions.versionOfModule("poly-game-api").isEmpty());

        properties.setProperty("default", "27.1");
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.parse(properties));
        properties.setProperty("default", "26.2");
        properties.remove("java.26.2");
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.parse(properties));
    }

    @Test
    void hotfixesRunOnTheModulesOfTheVersionTheyAreListedUnder() {
        Properties properties = new Properties();
        properties.setProperty("supported", "26.1, 26.2");
        properties.setProperty("default", "26.2");
        properties.setProperty("java.26.1", "25");
        properties.setProperty("java.26.2", "25");
        properties.setProperty("hotfixes.26.1", "26.1.1, 26.1.2");

        MinecraftVersions versions = MinecraftVersions.parse(properties);

        assertEquals(List.of("26.1", "26.2"), versions.supported());
        assertEquals(List.of("26.1", "26.1.1", "26.1.2", "26.2"), versions.runnable());
        assertEquals("26.1", versions.modulesVersion("26.1.2"));
        assertEquals("26.2", versions.modulesVersion("26.2"));
        assertEquals(25, versions.javaVersion("26.1.1"));
        // Hotfixes have no modules of their own.
        assertTrue(versions.versionOfModule("poly-game-v26_1_1").isEmpty());
        assertThrows(org.gradle.api.GradleException.class, () -> versions.modulesVersion("26.1.3"));

        properties.setProperty("hotfixes.26.0", "26.0.1");
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.parse(properties));
        properties.remove("hotfixes.26.0");
        properties.setProperty("hotfixes.26.2", "26.1.1");
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.parse(properties));
    }

    @Test
    void experimentalVersionsHaveModulesButAreNotOffered() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("supported", "26.1, 26.2");
        properties.setProperty("experimental", "1.21.11");
        properties.setProperty("default", "26.2");
        properties.setProperty("java.26.1", "25");
        properties.setProperty("java.26.2", "25");
        properties.setProperty("java.1.21.11", "25");

        MinecraftVersions versions = MinecraftVersions.parse(properties);

        assertEquals(List.of("26.1", "26.2"), versions.runnable(), "the installer offers only supported versions");
        assertEquals(List.of("26.1", "26.2", "1.21.11"), versions.withModules());
        assertTrue(versions.isExperimental("1.21.11"));
        assertFalse(versions.isExperimental("26.2"));
        assertEquals("1.21.11", versions.versionOfModule("poly-game-v1_21_11").orElseThrow());
        assertEquals("1.21.11", versions.modulesVersion("1.21.11"));
        assertEquals(25, versions.javaVersion("1.21.11"));

        properties.setProperty("experimental", "26.2");
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.parse(properties));
        properties.setProperty("experimental", "1.21.11");
        properties.remove("java.1.21.11");
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.parse(properties));
        properties.setProperty("java.1.21.11", "25");
        properties.setProperty("default", "1.21.11");
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersions.parse(properties), "default must be supported");

        writeVersions("supported=26.2\ndefault=26.2\njava.26.2=25\n");
        Files.createDirectories(root.resolve("poly-version-adapter-v26_2"));
        Files.createDirectories(root.resolve("poly-game-v26_2"));
        VersionScaffolder.scaffold(root, "26.2", "1.21.11", 25, true);
        VersionScaffolder.addToVersionsFile(root.resolve(MinecraftVersions.FILE), "1.21.10", 25, true);
        MinecraftVersions scaffolded = MinecraftVersions.read(root);
        assertEquals(List.of("26.2"), scaffolded.supported());
        assertEquals(List.of("1.21.11", "1.21.10"), scaffolded.experimental());
        assertThrows(
                IllegalArgumentException.class, () -> VersionScaffolder.scaffold(root, "26.2", "1.21.11", 25, true));
    }

    private void writeVersions(String content) throws IOException {
        write(MinecraftVersions.FILE, "# comment\n" + content);
    }

    private void write(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

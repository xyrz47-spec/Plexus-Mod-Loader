package dev.plexusmodloader.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EarlyWindowSettingsTest {

    @Test
    void sizeAndOptionsComeFromTheGamesArgumentsAndOptionsFile(@TempDir Path gameDirectory) throws IOException {
        Files.writeString(
                gameDirectory.resolve("options.txt"), "version:4000\nguiScale:3\ndarkMojangStudiosBackground:true\n");
        EarlyWindowSettings settings = EarlyWindowSettings.forGame(
                "Minecraft* Plexus 26.2",
                List.of(
                        "--username",
                        "Dev",
                        "--gameDir",
                        gameDirectory.toString(),
                        "--width",
                        "1280",
                        "--height",
                        "720"),
                Optional.empty(),
                Optional.empty());
        assertEquals(1280, settings.width());
        assertEquals(720, settings.height());
        assertEquals(3, settings.guiScale());
        assertTrue(settings.darkBackground());
    }

    @Test
    void withoutThemTheGamesDefaults(@TempDir Path gameDirectory) {
        EarlyWindowSettings settings = EarlyWindowSettings.forGame(
                "t",
                List.of("--gameDir", gameDirectory.toString(), "--width", "nope"),
                Optional.empty(),
                Optional.empty());
        assertEquals(854, settings.width());
        assertEquals(480, settings.height());
        assertEquals(0, settings.guiScale());
        assertFalse(settings.darkBackground());
    }

    @Test
    void argumentsReadBackAsWritten() {
        EarlyWindowSettings settings = new EarlyWindowSettings(
                "Minecraft* Plexus 26.3",
                1000,
                600,
                2,
                true,
                Optional.of(Path.of("client 26.3.jar")),
                Optional.of("assets/minecraft/textures/gui/title/mojangstudios.png"));
        assertEquals(settings, EarlyWindowSettings.fromArguments(settings.toArguments()));
        EarlyWindowSettings bare = new EarlyWindowSettings("t", 854, 480, 0, false, Optional.empty(), Optional.empty());
        assertEquals(bare, EarlyWindowSettings.fromArguments(bare.toArguments()));
    }
}

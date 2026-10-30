package dev.plexusmodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeoForgeServerLaunchTest {

    @Test
    void argumentFilesDropCommentsAndSplitAtWhiteSpace() {
        assertEquals(
                List.of("-Xmx4G", "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED"),
                NeoForgeServerLaunch.tokens("# a comment\n-Xmx4G\n\n  --add-opens java.base/java.lang.invoke=ALL-UNNAMED\n"));
    }

    @Test
    void theGameTestServerStartsWhereNeoForgesServerStartWould(@TempDir Path server) throws Exception {
        Path arguments = NeoForgeServerLaunch.argumentsFile(server, "26.2.0.93");
        Files.createDirectories(arguments.getParent());
        String sep = File.pathSeparator;
        Files.writeString(
                arguments,
                "--add-opens java.base/java.lang.invoke=ALL-UNNAMED\n-DlibraryDirectory=libraries\n-classpath libraries/a.jar"
                        + sep + "libraries/b.jar\n" + NeoForgeServerLaunch.SERVER_MAIN
                        + " --fml.neoForgeVersion 26.2.0.93 --fml.mcVersion 26.2\n");
        Files.writeString(server.resolve("user_jvm_args.txt"), "# Xmx and Xms set here\n-Dfrom.user=1\n");

        List<String> command = NeoForgeServerLaunch.gameTestServer(
                "java", server, "26.2.0.93", List.of("-Xms4g", "-Xmx4g"), List.of("--universe", "universe"));

        String libraries = server.resolve("libraries").toString();
        assertEquals(
                List.of(
                        "java",
                        "-Xms4g",
                        "-Xmx4g",
                        "-Dfrom.user=1",
                        "--add-opens",
                        "java.base/java.lang.invoke=ALL-UNNAMED",
                        "-DlibraryDirectory=" + libraries,
                        "-classpath",
                        server.resolve("libraries/a.jar") + sep + server.resolve("libraries/b.jar"),
                        NeoForgeServerLaunch.GAME_TEST_SERVER_MAIN,
                        "--fml.neoForgeVersion",
                        "26.2.0.93",
                        "--fml.mcVersion",
                        "26.2",
                        "--universe",
                        "universe"),
                command);
    }

    @Test
    void aMissingInstallationIsReported(@TempDir Path server) {
        assertThrows(
                GradleException.class,
                () -> NeoForgeServerLaunch.gameTestServer("java", server, "26.2.0.93", List.of(), List.of()));
    }

    @Test
    void measuredRunsStartWithTheWarmUpRunsCache(@TempDir Path work) throws Exception {
        Path warmup = work.resolve("connector--1");
        Files.createDirectories(warmup.resolve(".cache/connector"));
        Files.writeString(warmup.resolve(".cache/connector/mod.jar"), "transformed");
        Path jar = Files.writeString(work.resolve("fabric-mod.jar"), "jar");
        Path warmCache = work.resolve("reference-cache");

        NeoForgeServerLaunch.prepareRun(warmup, List.of(jar.toFile()), warmCache);
        assertFalse(Files.exists(warmCache));
        NeoForgeServerLaunch.keepCache(warmup, warmCache);

        Path run = work.resolve("connector-0");
        NeoForgeServerLaunch.prepareRun(run, List.of(jar.toFile()), warmCache);
        assertTrue(Files.isRegularFile(run.resolve("mods/fabric-mod.jar")));
        assertEquals("transformed", Files.readString(run.resolve(".cache/connector/mod.jar")));
    }
}

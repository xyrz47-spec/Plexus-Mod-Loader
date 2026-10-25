package dev.polymodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModCorpusTaskTest {

    @TempDir
    Path output;

    @Test
    void jarsTheLockNoLongerListsAreRemoved() throws IOException {
        Path listed = touch("forge/libIPN-forge-26.2-6.9.0.jar");
        Path replaced = touch("forge/libIPN-forge-26.3-6.9.0.jar");
        Path otherLoader = touch("fabric/sodium.jar");

        int removed =
                ModCorpusTask.removeUnlisted(output, Set.of(listed, otherLoader), ModCorpusTask.DEFAULT_LOADERS);

        assertEquals(1, removed);
        assertTrue(Files.exists(listed));
        assertTrue(Files.exists(otherLoader));
        assertFalse(Files.exists(replaced));
    }

    @Test
    void onlyJarsInLoaderFoldersAreTouched() throws IOException {
        Path note = touch("forge/README.txt");
        Path elsewhere = touch("nested/some.jar");

        assertEquals(0, ModCorpusTask.removeUnlisted(output, Set.of(), ModCorpusTask.DEFAULT_LOADERS));

        assertTrue(Files.exists(note));
        assertTrue(Files.exists(elsewhere));
    }

    @Test
    void missingLoaderFoldersAreFine() throws IOException {
        assertEquals(0, ModCorpusTask.removeUnlisted(output, Set.of(), ModCorpusTask.DEFAULT_LOADERS));
    }

    @Test
    void onlyTheTasksOwnLoaderFoldersAreCleaned() throws IOException {
        // modCorpus (fabric, neoforge, forge) and modCorpusQuilt (quilt) share one output folder.
        Path quilt = touch("quilt/voicechat-quilt.jar");
        Path fabric = touch("fabric/sodium.jar");

        assertEquals(1, ModCorpusTask.removeUnlisted(output, Set.of(), List.of("quilt")));
        assertEquals(0, ModCorpusTask.removeUnlisted(output, Set.of(fabric), ModCorpusTask.DEFAULT_LOADERS));

        assertFalse(Files.exists(quilt));
        assertTrue(Files.exists(fabric));
    }

    @Test
    void theNewestBuildMadeForQuiltAloneIsPicked() {
        // Modrinth lists versions newest first; a build that also names fabric is a Fabric jar.
        JsonArray versions = versions(
                "{\"id\": \"both\", \"loaders\": [\"fabric\", \"quilt\"]}",
                "{\"id\": \"quilt-new\", \"loaders\": [\"quilt\"]}",
                "{\"id\": \"quilt-old\", \"loaders\": [\"quilt\", \"neoforge\"]}");

        assertEquals("quilt-new", ModCorpusTask.quiltOnlyBuild(versions).get("id").getAsString());
    }

    @Test
    void aProjectWithoutAQuiltOnlyBuildIsNotInTheQuiltCorpus() {
        JsonArray versions = versions(
                "{\"id\": \"both\", \"loaders\": [\"fabric\", \"quilt\"]}", "{\"id\": \"forge\", \"loaders\": [\"forge\"]}");

        assertNull(ModCorpusTask.quiltOnlyBuild(versions));
    }

    @Test
    void aDependencyPrefersItsQuiltOnlyBuildAndOtherwiseTakesTheNewest() {
        JsonArray both = versions(
                "{\"id\": \"both\", \"loaders\": [\"fabric\", \"quilt\"]}", "{\"id\": \"quilt\", \"loaders\": [\"quilt\"]}");
        JsonArray fabricOnly = versions("{\"id\": \"both\", \"loaders\": [\"fabric\", \"quilt\"]}");

        assertEquals("quilt", ModCorpusTask.dependencyBuild(both).get("id").getAsString());
        assertEquals("both", ModCorpusTask.dependencyBuild(fabricOnly).get("id").getAsString());
        assertNull(ModCorpusTask.dependencyBuild(new JsonArray()));
    }

    @Test
    void fileNamesWindowsRefusesAreMadeSafe() {
        // A real 26.1 Quilt build: "abandoned Quilt 26.*.jar".
        assertEquals("abandoned Quilt 26._.jar", ModCorpusTask.localFileName("abandoned Quilt 26.*.jar"));
        assertEquals("a_b_c_d_e_f_g_h.jar", ModCorpusTask.localFileName("a<b>c:d\"e|f?g\\h.jar"));
        assertEquals("PinChat 3.1.0 Quilt 26.2.jar", ModCorpusTask.localFileName("PinChat 3.1.0 Quilt 26.2.jar"));
    }

    private static JsonArray versions(String... versions) {
        return JsonParser.parseString("[" + String.join(",", versions) + "]").getAsJsonArray();
    }

    private Path touch(String path) throws IOException {
        Path file = output.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, path);
        return file;
    }
}

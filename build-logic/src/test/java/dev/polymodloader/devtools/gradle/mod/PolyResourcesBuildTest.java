package dev.polymodloader.devtools.gradle.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Runs a real build of a mod project with the {@code dev.polymodloader.mod} plugin: the Kotlin DSL, the
 * generated files reaching the jar's resources, the merge of a hand-written language file, and {@code
 * checkPolyResources} failing on a broken reference. Projects live under {@code build/testkit} (not a JUnit
 * temporary directory, which the Gradle daemon can keep locked on Windows).
 */
class PolyResourcesBuildTest {

    private static final String BUILD_SCRIPT = """
            plugins {
                id("dev.polymodloader.mod")
            }

            polyResources {
                item("ruby")
                block("ruby_block") {
                    texture = "minecraft:block/redstone_block"
                    minedWith = "pickaxe"
                }
            }
            """;

    private static Path project(TestInfo test) throws IOException {
        Path directory = Path.of("build/testkit", test.getTestMethod().orElseThrow().getName()).toAbsolutePath();
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        write(directory, "settings.gradle.kts", "rootProject.name = \"ruby-mod\"\n");
        write(directory, "build.gradle.kts", BUILD_SCRIPT);
        write(
                directory,
                "src/main/resources/poly.mod.json",
                "{\"schemaVersion\": 1, \"id\": \"ruby_mod\", \"version\": \"0.1.0\", \"minecraft\": [\"26.*\"]}\n");
        write(directory, "src/main/resources/assets/ruby_mod/lang/en_us.json", "{\"item.ruby_mod.ruby\": \"Shiny Ruby\"}\n");
        return directory;
    }

    private static void write(Path root, String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static GradleRunner runner(Path project, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    @Test
    void generatedFilesAreInTheResourcesAndTheLangFileIsMerged(TestInfo test) throws IOException {
        Path project = project(test);
        write(project, "src/main/resources/assets/ruby_mod/textures/item/ruby.png", "");

        BuildResult result = runner(project, "checkPolyResources").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkPolyResources").getOutcome());
        Path resources = project.resolve("build/resources/main");
        for (String path : new String[] {
            "assets/ruby_mod/items/ruby.json",
            "assets/ruby_mod/models/item/ruby.json",
            "assets/ruby_mod/blockstates/ruby_block.json",
            "assets/ruby_mod/models/block/ruby_block.json",
            "assets/ruby_mod/items/ruby_block.json",
            "data/ruby_mod/loot_table/blocks/ruby_block.json",
            "data/minecraft/tags/block/mineable/pickaxe.json",
            "poly.mod.json"
        }) {
            assertTrue(Files.isRegularFile(resources.resolve(path)), path);
        }
        JsonObject lang = JsonParser.parseString(Files.readString(
                        resources.resolve("assets/ruby_mod/lang/en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals("Shiny Ruby", lang.get("item.ruby_mod.ruby").getAsString());
        assertEquals("Ruby Block", lang.get("block.ruby_mod.ruby_block").getAsString());
    }

    @Test
    void missingTextureFailsTheCheck(TestInfo test) throws IOException {
        Path project = project(test);

        BuildResult result = runner(project, "checkPolyResources").buildAndFail();

        assertTrue(result.getOutput().contains("PLEXUS-RESOURCE-007 assets/ruby_mod/models/item/ruby.json"), result.getOutput());
        assertTrue(result.getOutput().contains("assets/ruby_mod/textures/item/ruby.png"), result.getOutput());
    }
}

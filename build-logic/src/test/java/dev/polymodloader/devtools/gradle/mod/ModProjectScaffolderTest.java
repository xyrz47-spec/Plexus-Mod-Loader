package dev.polymodloader.devtools.gradle.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModProjectScaffolderTest {

    private static final Path TEMPLATE = Path.of("../templates/plexus-mod-template");

    @TempDir
    Path workspace;

    private static final ModProjectScaffolder.ModNames RUBY =
            new ModProjectScaffolder.ModNames("ruby_tools", "Ruby Tools", "io.github.you.rubytools");

    @Test
    void namesAreDerivedFromTheModId() {
        assertEquals("RubyToolsMod", RUBY.className());
        assertEquals("ruby-tools", RUBY.projectName());
        assertEquals("io.github.you", RUBY.group());
        assertEquals("RubyMod", new ModProjectScaffolder.ModNames("ruby_mod", "Ruby", "a.b").className());
    }

    @Test
    void templateIsCopiedWithEveryNameReplaced() throws IOException {
        Path target = workspace.resolve("ruby-tools");
        List<Path> created = ModProjectScaffolder.scaffold(TEMPLATE, target, RUBY);

        Path entryPoint = target.resolve("src/main/java/io/github/you/rubytools/RubyToolsMod.java");
        assertTrue(created.contains(entryPoint), created::toString);
        String java = Files.readString(entryPoint, StandardCharsets.UTF_8);
        assertTrue(java.startsWith("package io.github.you.rubytools;"), java);
        assertTrue(java.contains("public final class RubyToolsMod implements CommonModInitializer"), java);

        Path metaPath = target.resolve("src/main/resources/plexus.mod.json");
        if (!Files.exists(metaPath)) {
            metaPath = target.resolve("src/main/resources/poly.mod.json");
        }
        String metadata = Files.readString(metaPath, StandardCharsets.UTF_8);
        assertTrue(metadata.contains("\"id\": \"ruby_tools\""), metadata);
        assertTrue(metadata.contains("\"name\": \"Ruby Tools\""), metadata);
        assertTrue(metadata.contains("\"io.github.you.rubytools.RubyToolsMod\""), metadata);

        String build = Files.readString(target.resolve("build.gradle.kts"), StandardCharsets.UTF_8);
        assertTrue(build.contains("group = \"io.github.you\""), build);
        assertTrue(build.contains("[ruby_tools] Hello from ruby_tools"), build);
        assertTrue(
                Files.readString(target.resolve("settings.gradle.kts"), StandardCharsets.UTF_8)
                        .contains("rootProject.name = \"ruby-tools\""));
        assertTrue(Files.size(target.resolve("gradle/wrapper/gradle-wrapper.jar")) > 0);

        try (Stream<Path> files = Files.walk(target)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertFalse(file.toString().contains("mymod"), file::toString);
                if (file.toString().endsWith(".jar")) {
                    continue;
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String old : List.of("my_mod", "MyMod", "com.example.mymod", "my-poly-mod")) {
                    assertFalse(text.contains(old), () -> file + " still contains " + old);
                }
            }
        }
    }

    @Test
    void defaultsComeFromTheModId() {
        ModProjectScaffolder.ModNames names = ModProjectScaffolder.ModNames.withDefaults("ruby_tools", null, null);
        assertEquals("Ruby Tools", names.modName());
        assertEquals("com.example.rubytools", names.javaPackage());
        assertThrows(
                IllegalArgumentException.class, () -> ModProjectScaffolder.ModNames.withDefaults(null, null, null));
    }

    @Test
    void unzippedTemplateIsRenamedInPlace() throws IOException {
        Path project = workspace.resolve("unzipped");
        copyTemplate(project);

        List<Path> changed = ModProjectScaffolder.renameInPlace(project, RUBY);

        Path entryPoint = project.resolve("src/main/java/io/github/you/rubytools/RubyToolsMod.java");
        assertTrue(changed.contains(entryPoint), changed::toString);
        assertTrue(Files.readString(entryPoint, StandardCharsets.UTF_8).startsWith("package io.github.you.rubytools;"));
        assertFalse(Files.exists(project.resolve("src/main/java/com")), "the template's package folders are removed");
        assertTrue(Files.readString(project.resolve("settings.gradle.kts"), StandardCharsets.UTF_8)
                .contains("rootProject.name = \"ruby-tools\""));
        assertTrue(Files.size(project.resolve("gradle/wrapper/gradle-wrapper.jar")) > 0);
        assertNoTemplateNames(project);

        // A second run finds no template left to rename and changes nothing.
        assertThrows(IllegalArgumentException.class, () -> ModProjectScaffolder.renameInPlace(project, RUBY));
        assertTrue(Files.exists(entryPoint));
    }

    @Test
    void renameInPlaceLeavesTheProjectAloneOnAConflict() throws IOException {
        Path project = workspace.resolve("conflict");
        copyTemplate(project);
        Path taken = project.resolve("src/main/java/io/github/you/rubytools/RubyToolsMod.java");
        Files.createDirectories(taken.getParent());
        Files.writeString(taken, "mine");

        assertThrows(IllegalArgumentException.class, () -> ModProjectScaffolder.renameInPlace(project, RUBY));
        assertEquals("mine", Files.readString(taken));
        assertTrue(Files.exists(project.resolve("src/main/java/com/example/mymod/MyMod.java")));
        assertTrue(Files.readString(project.resolve("settings.gradle.kts")).contains("my-poly-mod"));
    }

    private static void copyTemplate(Path target) throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATE)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path destination = target.resolve(TEMPLATE.relativize(file).toString());
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination);
            }
        }
    }

    private static void assertNoTemplateNames(Path project) throws IOException {
        try (Stream<Path> files = Files.walk(project)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertFalse(file.toString().contains("mymod"), file::toString);
                if (file.toString().endsWith(".jar")) {
                    continue;
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String old : List.of("my_mod", "MyMod", "com.example.mymod", "my-poly-mod")) {
                    assertFalse(text.contains(old), () -> file + " still contains " + old);
                }
            }
        }
    }

    @Test
    void existingFolderWithFilesIsRefused() throws IOException {
        Path target = workspace.resolve("taken");
        Files.createDirectories(target);
        Files.writeString(target.resolve("notes.txt"), "mine");

        assertThrows(IllegalArgumentException.class, () -> ModProjectScaffolder.scaffold(TEMPLATE, target, RUBY));
        assertEquals("mine", Files.readString(target.resolve("notes.txt")));
    }

    @Test
    void invalidNamesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ModProjectScaffolder.ModNames("Ruby", "Ruby", "a.b"));
        assertThrows(IllegalArgumentException.class, () -> new ModProjectScaffolder.ModNames("r", "Ruby", "a.b"));
        assertThrows(
                IllegalArgumentException.class, () -> new ModProjectScaffolder.ModNames("ruby", "Ruby", "a.int.b"));
        assertThrows(
                IllegalArgumentException.class, () -> new ModProjectScaffolder.ModNames("ruby", "Say \"hi\"", "a.b"));
    }
}

package dev.polymodloader.devtools.gradle.minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Everything needed to start one side of a prepared Minecraft distribution: the game jar (loaded by
 * Poly Mod Loader's game class loader, never put on the JVM class path), the library class path, and the
 * already-resolved JVM and game arguments from Mojang's version manifest.
 *
 * @param gameVersion Minecraft version id, e.g. {@code 26.2}
 * @param side {@code client} or {@code server}
 * @param gameJar path of the game jar
 * @param libraries library jars for the JVM class path
 * @param jvmArguments resolved JVM arguments (without class path)
 * @param gameArguments resolved game arguments; may contain {@code ${game_directory}}
 */
record MinecraftLaunchSpec(
        String gameVersion,
        String side,
        String gameJar,
        List<String> libraries,
        List<String> jvmArguments,
        List<String> gameArguments) {

    static final String GAME_DIRECTORY_PLACEHOLDER = "${game_directory}";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    void write(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write launch spec " + file, e);
        }
    }

    static MinecraftLaunchSpec read(Path file) {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MinecraftLaunchSpec.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read launch spec " + file, e);
        }
    }

    boolean allFilesExist() {
        return Files.isRegularFile(Path.of(gameJar))
                && libraries.stream().map(Path::of).allMatch(Files::isRegularFile);
    }
}

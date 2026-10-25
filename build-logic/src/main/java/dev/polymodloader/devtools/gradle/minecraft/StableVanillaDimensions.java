package dev.polymodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives a copied test world the game's own nether and end generators when they are flat. From Minecraft 26.3 the game
 * test server creates its world with the {@code flat_all_dimensions} preset; the game counts a flat nether or end as
 * experimental world settings and asks for a backup before it opens the world ({@code BackupConfirmScreen}), which
 * stops Quick Play. Game tests run in the overworld, so nothing was generated in those dimensions and the swap loses
 * nothing. Worlds whose nether and end are not flat (26.2 and earlier) are left as they are.
 */
final class StableVanillaDimensions {

    static final String SETTINGS = "data/minecraft/world_gen_settings.dat";

    private StableVanillaDimensions() {}

    /**
     * Rewrites {@code world}'s world generation settings in place.
     *
     * @return the dimensions that were changed; empty when there was nothing to change or no settings file
     */
    static List<String> apply(Path world) throws IOException {
        Path file = world.resolve(SETTINGS);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        GzipNbt.Root root = GzipNbt.read(file);
        if (!(root.compound().entries().get("data") instanceof GzipNbt.Compound data)
                || !(data.entries().get("dimensions") instanceof GzipNbt.Compound dimensions)) {
            return List.of();
        }
        List<String> changed = new ArrayList<>();
        if (replaceIfFlat(dimensions, "minecraft:the_nether", noise("minecraft:nether", multiNoise("minecraft:nether")))) {
            changed.add("minecraft:the_nether");
        }
        if (replaceIfFlat(dimensions, "minecraft:the_end", noise("minecraft:end", typeOnly("minecraft:the_end")))) {
            changed.add("minecraft:the_end");
        }
        if (!changed.isEmpty()) {
            GzipNbt.write(file, root);
        }
        return changed;
    }

    private static boolean replaceIfFlat(GzipNbt.Compound dimensions, String key, GzipNbt.Compound generator) {
        if (!(dimensions.entries().get(key) instanceof GzipNbt.Compound stem)
                || !(stem.entries().get("generator") instanceof GzipNbt.Compound current)
                || !(current.entries().get("type") instanceof GzipNbt.Value type)
                || !"minecraft:flat".equals(type.value())) {
            return false;
        }
        stem.entries().put("generator", generator);
        return true;
    }

    /** As in the game's {@code worldgen/world_preset/normal.json}. */
    private static GzipNbt.Compound noise(String settings, GzipNbt.Compound biomeSource) {
        GzipNbt.Compound generator = typeOnly("minecraft:noise");
        generator.entries().put("biome_source", biomeSource);
        generator.entries().put("settings", GzipNbt.string(settings));
        return generator;
    }

    private static GzipNbt.Compound multiNoise(String preset) {
        GzipNbt.Compound source = typeOnly("minecraft:multi_noise");
        source.entries().put("preset", GzipNbt.string(preset));
        return source;
    }

    private static GzipNbt.Compound typeOnly(String type) {
        GzipNbt.Compound compound = new GzipNbt.Compound();
        compound.entries().put("type", GzipNbt.string(type));
        return compound;
    }
}

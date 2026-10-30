package dev.plexusmodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StableVanillaDimensionsTest {

    @TempDir
    Path world;

    /** The game test world of Minecraft 26.3 (preset flat_all_dimensions), as in its world_gen_settings.dat. */
    @Test
    void flatNetherAndEndGetTheGamesOwnGenerators() throws IOException {
        writeSettings("minecraft:flat", "minecraft:flat");

        assertEquals(List.of("minecraft:the_nether", "minecraft:the_end"), StableVanillaDimensions.apply(world));

        GzipNbt.Compound dimensions = dimensions();
        GzipNbt.Compound nether = generator(dimensions, "minecraft:the_nether");
        assertEquals("minecraft:noise", string(nether, "type"));
        assertEquals("minecraft:nether", string(nether, "settings"));
        GzipNbt.Compound netherBiomes = (GzipNbt.Compound) nether.entries().get("biome_source");
        assertEquals("minecraft:multi_noise", string(netherBiomes, "type"));
        assertEquals("minecraft:nether", string(netherBiomes, "preset"));
        GzipNbt.Compound end = generator(dimensions, "minecraft:the_end");
        assertEquals("minecraft:end", string(end, "settings"));
        assertEquals("minecraft:the_end", string((GzipNbt.Compound) end.entries().get("biome_source"), "type"));
        assertEquals("minecraft:flat", string(generator(dimensions, "minecraft:overworld"), "type"), "overworld kept");
    }

    @Test
    void noiseNetherAndEndAreLeftAlone() throws IOException {
        writeSettings("minecraft:noise", "minecraft:noise");
        byte[] before = Files.readAllBytes(world.resolve(StableVanillaDimensions.SETTINGS));

        assertEquals(List.of(), StableVanillaDimensions.apply(world));
        assertArrayEquals(before, Files.readAllBytes(world.resolve(StableVanillaDimensions.SETTINGS)));
    }

    @Test
    void aWorldWithoutSettingsIsLeftAlone() throws IOException {
        assertEquals(List.of(), StableVanillaDimensions.apply(world));
    }

    @Test
    void valuesOfEveryTypeSurviveAReadAndWrite() throws IOException {
        Path file = world.resolve("all.dat");
        GzipNbt.Compound compound = new GzipNbt.Compound();
        compound.entries().put("b", new GzipNbt.Value((byte) 1, (byte) 3));
        compound.entries().put("s", new GzipNbt.Value((byte) 2, (short) -4));
        compound.entries().put("i", new GzipNbt.Value((byte) 3, 5023));
        compound.entries().put("l", new GzipNbt.Value((byte) 4, -7L));
        compound.entries().put("f", new GzipNbt.Value((byte) 5, 1.5f));
        compound.entries().put("d", new GzipNbt.Value((byte) 6, 2.25));
        compound.entries().put("ba", new GzipNbt.Value((byte) 7, new byte[] {1, 2}));
        compound.entries().put("str", GzipNbt.string("ü"));
        compound.entries().put("ia", new GzipNbt.Value((byte) 11, new int[] {9}));
        compound.entries().put("la", new GzipNbt.Value((byte) 12, new long[] {8L}));
        compound.entries()
                .put("list", new GzipNbt.ListTag(GzipNbt.STRING, List.of(GzipNbt.string("a"), GzipNbt.string("b"))));
        GzipNbt.write(file, new GzipNbt.Root("", compound));
        byte[] written = Files.readAllBytes(file);

        GzipNbt.write(file, GzipNbt.read(file));

        assertArrayEquals(written, Files.readAllBytes(file));
    }

    private void writeSettings(String netherGenerator, String endGenerator) throws IOException {
        GzipNbt.Compound dimensions = new GzipNbt.Compound();
        dimensions.entries().put("minecraft:overworld", stem("minecraft:overworld", "minecraft:flat"));
        dimensions.entries().put("minecraft:the_nether", stem("minecraft:the_nether", netherGenerator));
        dimensions.entries().put("minecraft:the_end", stem("minecraft:the_end", endGenerator));
        GzipNbt.Compound data = new GzipNbt.Compound();
        data.entries().put("seed", new GzipNbt.Value((byte) 4, 0L));
        data.entries().put("dimensions", dimensions);
        GzipNbt.Compound root = new GzipNbt.Compound();
        root.entries().put("data", data);
        root.entries().put("DataVersion", new GzipNbt.Value((byte) 3, 5023));
        Path file = world.resolve(StableVanillaDimensions.SETTINGS);
        Files.createDirectories(file.getParent());
        GzipNbt.write(file, new GzipNbt.Root("", root));
    }

    private static GzipNbt.Compound stem(String type, String generatorType) {
        GzipNbt.Compound generator = new GzipNbt.Compound();
        generator.entries().put("type", GzipNbt.string(generatorType));
        GzipNbt.Compound stem = new GzipNbt.Compound();
        stem.entries().put("generator", generator);
        stem.entries().put("type", GzipNbt.string(type));
        return stem;
    }

    private GzipNbt.Compound dimensions() throws IOException {
        GzipNbt.Compound data = (GzipNbt.Compound) GzipNbt.read(world.resolve(StableVanillaDimensions.SETTINGS))
                .compound()
                .entries()
                .get("data");
        return (GzipNbt.Compound) data.entries().get("dimensions");
    }

    private static GzipNbt.Compound generator(GzipNbt.Compound dimensions, String key) {
        return (GzipNbt.Compound)
                ((GzipNbt.Compound) dimensions.entries().get(key)).entries().get("generator");
    }

    private static String string(GzipNbt.Compound compound, String key) {
        return (String) ((GzipNbt.Value) compound.entries().get(key)).value();
    }
}

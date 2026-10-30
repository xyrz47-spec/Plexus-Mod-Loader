package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Poly Mod Loader's art in NeoForge's and MinecraftForge's early loading windows. */
class EarlyScreenArtTest {

    private static final String THEME =
            "{\"decoration\": {\"version\": {\"type\": \"label\", \"text\": \"${version}\"}}}";

    @TempDir
    Path directory;

    @Test
    void neoForgesFoxIconAndVersionLabelBecomePolyModLoaders() throws IOException {
        ModuleReference reference = module(
                "earlydisplay",
                Map.of(
                        "net/neoforged/fml/earlydisplay/theme/fox_running.png",
                        png(151, 3584),
                        "net/neoforged/fml/earlydisplay/theme/neoforged_icon.png",
                        png(100, 100),
                        EarlyScreenArt.NEOFORGE_THEME,
                        THEME.getBytes(StandardCharsets.UTF_8),
                        "net/neoforged/fml/earlydisplay/Placeholder.class",
                        new byte[0]));
        List<String> log = new ArrayList<>();

        ModuleReference served = EarlyScreenArt.of(reference, directory.resolve("cache"), log::add);

        try (ModuleReader reader = served.open()) {
            assertArrayEquals(
                    art("neoforge_spinner.png"),
                    fromUri(reader, "net/neoforged/fml/earlydisplay/theme/fox_running.png"));
            assertArrayEquals(
                    art("neoforge_icon.png"),
                    fromUri(reader, "net/neoforged/fml/earlydisplay/theme/neoforged_icon.png"));
            String theme = new String(
                    reader.open(EarlyScreenArt.NEOFORGE_THEME).orElseThrow().readAllBytes(), UTF8);
            assertTrue(theme.contains("\"text\": \"Poly Mod Loader\"") && !theme.contains("${version}"), theme);
        }
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.getFirst().contains("NeoForge") && !log.getFirst().contains("kept"), log.toString());
    }

    @Test
    void forgesAnvilAndLogoBecomePolyModLoaders() throws IOException {
        ModuleReference reference = module(
                "fmlearlydisplay",
                Map.of(
                        "forge_anvil.png", png(128, 4096),
                        "forge_logo.png", png(589, 94),
                        "net/minecraftforge/fml/earlydisplay/DisplayWindow.class", new byte[0]));

        ModuleReference served = EarlyScreenArt.of(reference, directory.resolve("cache"), line -> {});

        try (ModuleReader reader = served.open()) {
            assertArrayEquals(art("forge_spinner.png"), fromUri(reader, "forge_anvil.png"));
            assertArrayEquals(
                    art("forge_logo.png"),
                    reader.open("forge_logo.png").orElseThrow().readAllBytes());
        }
    }

    /** An image of another size than Poly Mod Loader's art is drawn for stays the host's, and the log says so. */
    @Test
    void anImageOfAnotherSizeStaysTheHosts() throws IOException {
        byte[] anvil = png(128, 2048);
        ModuleReference reference = module(
                "fmlearlydisplay",
                Map.of(
                        "forge_anvil.png",
                        anvil,
                        "forge_logo.png",
                        png(589, 94),
                        "net/minecraftforge/fml/earlydisplay/DisplayWindow.class",
                        new byte[0]));
        List<String> log = new ArrayList<>();

        ModuleReference served = EarlyScreenArt.of(reference, directory.resolve("cache"), log::add);

        try (ModuleReader reader = served.open()) {
            assertArrayEquals(
                    anvil, reader.open("forge_anvil.png").orElseThrow().readAllBytes());
            assertArrayEquals(art("forge_logo.png"), fromUri(reader, "forge_logo.png"));
        }
        assertTrue(log.getFirst().contains("kept the host's [forge_anvil.png]"), log.toString());
    }

    @Test
    void anyOtherModuleIsLeftAlone() throws IOException {
        ModuleReference reference = module("other", Map.of("other/Thing.class", new byte[0]));

        assertSame(reference, EarlyScreenArt.of(reference, directory.resolve("cache"), line -> {}));
    }

    @Test
    void theLabelIsReplacedOnlyWhenTheThemeHasItOnce() {
        assertTrue(
                EarlyScreenArt.labelled(THEME.getBytes(StandardCharsets.UTF_8)).isPresent());
        assertFalse(EarlyScreenArt.labelled((THEME + THEME).getBytes(StandardCharsets.UTF_8))
                .isPresent());
        assertFalse(
                EarlyScreenArt.labelled("{}".getBytes(StandardCharsets.UTF_8)).isPresent());
    }

    @Test
    void theArtHasTheSizesItReplaces() throws IOException {
        for (EarlyScreenArt.Image image : EarlyScreenArt.NEOFORGE) {
            assertTrue(EarlyScreenArt.hasSize(art(image.replacement()), image.width(), image.height()), image.path());
        }
        for (EarlyScreenArt.Image image : EarlyScreenArt.FORGE) {
            assertTrue(EarlyScreenArt.hasSize(art(image.replacement()), image.width(), image.height()), image.path());
        }
    }

    private static final java.nio.charset.Charset UTF8 = StandardCharsets.UTF_8;

    private static byte[] fromUri(ModuleReader reader, String name) throws IOException {
        URI uri = reader.find(name).orElseThrow();
        assertEquals("file", uri.getScheme(), uri.toString());
        try (InputStream in = uri.toURL().openStream()) {
            return in.readAllBytes();
        }
    }

    private static byte[] art(String name) throws IOException {
        try (InputStream in = EarlyScreenArt.class.getResourceAsStream("earlyscreen/" + name)) {
            if (in == null) {
                throw new IOException("no earlyscreen/" + name);
            }
            return in.readAllBytes();
        }
    }

    /** The first 24 bytes of a PNG of {@code width} x {@code height}: enough for the size check. */
    private static byte[] png(int width, int height) {
        byte[] bytes = new byte[33];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        for (int i = 0; i < 4; i++) {
            bytes[16 + i] = (byte) (width >>> (24 - 8 * i));
            bytes[20 + i] = (byte) (height >>> (24 - 8 * i));
        }
        return bytes;
    }

    private ModuleReference module(String name, Map<String, byte[]> entries) throws IOException {
        Path jar = directory.resolve(name + ".jar");
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return ModuleFinder.of(jar).findAll().iterator().next();
    }
}

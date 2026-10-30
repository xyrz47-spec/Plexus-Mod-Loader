package dev.plexusmodloader.compat.forge;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Poly Mod Loader's art in the early loading window of MinecraftForge and NeoForge, which their FML opens before the
 * game's window. The window stays theirs (the game's Mojang red, the Mojang Studios logo, their progress bars and
 * startup log); its animation and logo become Poly Mod Loader's spinning gold cube, as on the game's loading screen
 * that follows (the game layers' {@code PlexusLoadingOverlay}), so one look carries from the first frame to the title
 * screen:
 *
 * <ul>
 *   <li>NeoForge's {@code earlydisplay} (FML 11 and 12): the running fox ({@code fox_running.png}, 28 frames of 151 x
 *       128) and the window icon ({@code neoforged_icon.png}, 100 x 100), and the version label of its default theme
 *       ({@code "text": "${version}"}) reads {@code Poly Mod Loader}, as on the game's loading screen (the
 *       window's title and startup log name NeoForge's version).
 *   <li>MinecraftForge's {@code fmlearlydisplay}: the anvil ({@code forge_anvil.png}, 32 frames of 128 x 128) and the
 *       logo ({@code forge_logo.png}, 589 x 94).
 * </ul>
 *
 * <p>The early windows read these through the module's reader: Forge with {@code ClassLoader.getResourceAsStream},
 * NeoForge with {@code ClassLoader.getResource} and the theme with {@code Class.getResourceAsStream}, which the game
 * class loader answers with {@code ModuleReader.find} (a URI) or {@code open}. The returned module's reader serves
 * Poly Mod Loader's files there, from copies in {@code cache} (the URI must name a file). An image is replaced only when
 * the host's has the size Poly Mod Loader's art is drawn for (checked against FML 11.0.16 and 12.0.12, MinecraftForge
 * 26.1-62.0.9 and 26.3-66.1.0), and the label only when the theme has exactly that text; otherwise that piece stays the
 * host's, and the log says so. The art is Poly Mod Loader's own ({@code earlyscreen/} beside this class).
 *
 * <p>Thread: the launch thread, while the host defines its modules.
 */
final class EarlyScreenArt {

    /** A host image and Poly Mod Loader's in its place, drawn for {@code width} x {@code height}. */
    record Image(String path, String replacement, int width, int height) {}

    static final String NEOFORGE_THEME = "net/neoforged/fml/earlydisplay/theme/theme-default.json";
    static final String NEOFORGE_LABEL = "\"text\": \"${version}\"";
    static final String POLY_LABEL = "\"text\": \"Poly Mod Loader\"";

    static final List<Image> NEOFORGE = List.of(
            new Image("net/neoforged/fml/earlydisplay/theme/fox_running.png", "neoforge_spinner.png", 151, 3584),
            new Image("net/neoforged/fml/earlydisplay/theme/neoforged_icon.png", "neoforge_icon.png", 100, 100));

    static final List<Image> FORGE = List.of(
            new Image("forge_anvil.png", "forge_spinner.png", 128, 4096),
            new Image("forge_logo.png", "forge_logo.png", 589, 94));

    private static final String ART = "earlyscreen/";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private EarlyScreenArt() {}

    /**
     * {@code reference} serving Poly Mod Loader's art when it is a host's early window module, else {@code reference}.
     *
     * @param cache where the replaced files are written (created when needed)
     * @param log one line naming what was replaced or left as it was
     */
    static ModuleReference of(ModuleReference reference, Path cache, Consumer<String> log) {
        Map<String, byte[]> served = new LinkedHashMap<>();
        List<String> kept = new ArrayList<>();
        String host;
        try (ModuleReader reader = reference.open()) {
            List<Image> images;
            if (reader.find(NEOFORGE.getFirst().path()).isPresent()) {
                host = "NeoForge";
                images = NEOFORGE;
            } else if (reader.find(FORGE.getFirst().path()).isPresent()
                    && reference.descriptor().packages().contains("net.minecraftforge.fml.earlydisplay")) {
                host = "MinecraftForge";
                images = FORGE;
            } else {
                return reference;
            }
            for (Image image : images) {
                Optional<byte[]> original = read(reader, image.path());
                if (original.isPresent() && hasSize(original.get(), image.width(), image.height())) {
                    served.put(image.path(), art(image.replacement()));
                } else {
                    kept.add(image.path());
                }
            }
            if (host.equals("NeoForge")) {
                Optional<byte[]> theme = read(reader, NEOFORGE_THEME);
                Optional<byte[]> labelled = theme.flatMap(EarlyScreenArt::labelled);
                if (labelled.isPresent()) {
                    served.put(NEOFORGE_THEME, labelled.get());
                } else {
                    kept.add(NEOFORGE_THEME);
                }
            }
        } catch (IOException e) {
            log.accept("Early loading window: " + reference.descriptor().name() + " could not be read (" + e
                    + "); its art stays the host's");
            return reference;
        }
        Map<String, Path> files = new LinkedHashMap<>();
        try {
            Files.createDirectories(cache);
            for (Map.Entry<String, byte[]> entry : served.entrySet()) {
                String name = entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1);
                Path file = cache.resolve(reference.descriptor().name() + "-" + name);
                if (!Files.exists(file) || !Arrays.equals(Files.readAllBytes(file), entry.getValue())) {
                    Files.write(file, entry.getValue());
                }
                files.put(entry.getKey(), file);
            }
        } catch (IOException e) {
            log.accept("Early loading window: Poly Mod Loader's art could not be written to " + cache + " (" + e + "); "
                    + host + "'s stays");
            return reference;
        }
        log.accept("Early loading window (" + host + "): Poly Mod Loader's art in place of " + files.keySet()
                + (kept.isEmpty() ? "" : "; kept the host's " + kept + " (not the shape Poly Mod Loader checked)"));
        return files.isEmpty() ? reference : serving(reference, files);
    }

    /** The default theme with Poly Mod Loader's version label; empty when it has the label other than once. */
    static Optional<byte[]> labelled(byte[] theme) {
        String text = new String(theme, StandardCharsets.UTF_8);
        int first = text.indexOf(NEOFORGE_LABEL);
        if (first < 0 || text.indexOf(NEOFORGE_LABEL, first + 1) >= 0) {
            return Optional.empty();
        }
        return Optional.of(text.replace(NEOFORGE_LABEL, POLY_LABEL).getBytes(StandardCharsets.UTF_8));
    }

    /** Whether {@code png} is a PNG image of {@code width} x {@code height} (its IHDR). */
    static boolean hasSize(byte[] png, int width, int height) {
        if (png.length < 24 || !Arrays.equals(Arrays.copyOf(png, PNG.length), PNG)) {
            return false;
        }
        return readInt(png, 16) == width && readInt(png, 20) == height;
    }

    private static int readInt(byte[] bytes, int at) {
        return ((bytes[at] & 0xFF) << 24)
                | ((bytes[at + 1] & 0xFF) << 16)
                | ((bytes[at + 2] & 0xFF) << 8)
                | (bytes[at + 3] & 0xFF);
    }

    private static Optional<byte[]> read(ModuleReader reader, String path) throws IOException {
        Optional<InputStream> input = reader.open(path);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream stream = input.get()) {
            return Optional.of(stream.readAllBytes());
        }
    }

    private static byte[] art(String name) throws IOException {
        try (InputStream in = EarlyScreenArt.class.getResourceAsStream(ART + name)) {
            if (in == null) {
                throw new IOException("Poly Mod Loader's " + ART + name + " is missing from its jar");
            }
            return in.readAllBytes();
        }
    }

    private static ModuleReference serving(ModuleReference reference, Map<String, Path> files) {
        return new ModuleReference(reference.descriptor(), reference.location().orElse(null)) {
            @Override
            public ModuleReader open() throws IOException {
                return new ServingReader(reference.open(), files);
            }
        };
    }

    /** A module's reader that serves {@code files} in place of the module's entries of those names. */
    private static final class ServingReader implements ModuleReader {

        private final ModuleReader reader;
        private final Map<String, Path> files;

        ServingReader(ModuleReader reader, Map<String, Path> files) {
            this.reader = reader;
            this.files = files;
        }

        @Override
        public Optional<URI> find(String name) throws IOException {
            Path file = files.get(name);
            return file != null ? Optional.of(file.toUri()) : reader.find(name);
        }

        @Override
        public Optional<InputStream> open(String name) throws IOException {
            Path file = files.get(name);
            if (file == null) {
                return reader.open(name);
            }
            return Optional.of(new ByteArrayInputStream(Files.readAllBytes(file)));
        }

        @Override
        public Stream<String> list() throws IOException {
            return reader.list();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}

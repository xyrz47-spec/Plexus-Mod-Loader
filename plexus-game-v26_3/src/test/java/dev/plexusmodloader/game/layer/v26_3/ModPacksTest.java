package dev.plexusmodloader.game.layer.v26_3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Overlays a mod jar's {@code pack.mcmeta} picks, read with the game's own metadata codecs. */
class ModPacksTest {

    /** As in the example native-showcase-mod: one overlay for 26.3's data, one for its assets. */
    private static final String TWO_OVERLAYS = """
            {
              "pack": {"description": "Demo", "min_format": 84, "max_format": 121},
              "overlays": {"entries": [
                {"directory": "plexus_26_3", "min_format": 121, "max_format": 121},
                {"directory": "plexus_26_3_assets", "min_format": 97, "max_format": 97}
              ]}
            }
            """;

    @TempDir
    Path directory;

    /** The pack description is a text component, whose codec needs the game's registries. */
    @BeforeAll
    static void startGame() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theDataOverlayOnTheServerAndTheAssetsOverlayOnTheClient() throws IOException {
        Path jar = jar("pack.mcmeta", TWO_OVERLAYS);

        assertEquals(List.of("plexus_26_3"), overlays(jar, PackType.SERVER_DATA));
        assertEquals(List.of("plexus_26_3_assets"), overlays(jar, PackType.CLIENT_RESOURCES));
    }

    @Test
    void noPackMetadataMeansNoOverlays() throws IOException {
        Path jar = jar("data/demo/tags/block/rocks.json", "{\"values\": []}");

        assertEquals(List.of(), overlays(jar, PackType.SERVER_DATA));
    }

    @Test
    void unreadablePackMetadataMeansNoOverlays() throws IOException {
        Path jar = jar("pack.mcmeta", "{\"overlays\": {\"entries\": []}}");

        assertEquals(List.of(), overlays(jar, PackType.SERVER_DATA));
    }

    private static List<String> overlays(Path jar, PackType type) {
        PackLocationInfo location =
                new PackLocationInfo("plexus/demo", Component.literal("Demo"), PackSource.BUILT_IN, Optional.empty());
        return ModPacks.overlays(location, new FilePackResources.FileResourcesSupplier(jar), type, jar);
    }

    private Path jar(String entry, String content) throws IOException {
        Path jar = directory.resolve("demo.jar");
        try (OutputStream out = Files.newOutputStream(jar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }
}

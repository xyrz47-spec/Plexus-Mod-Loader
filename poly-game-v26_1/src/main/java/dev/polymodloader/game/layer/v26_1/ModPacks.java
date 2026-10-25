package dev.polymodloader.game.layer.v26_1;

import dev.polymodloader.game.version.layer.ModResourcePack;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds every mod jar as a required pack ({@code poly/<mod id>}): its {@code assets/} to the client's
 * resource packs and its {@code data/} to the server's data packs, above the game's own packs.
 */
final class ModPacks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final PackSelectionConfig REQUIRED_ON_TOP = new PackSelectionConfig(true, Pack.Position.TOP, false);
    private static volatile Field packTypeField;

    private ModPacks() {}

    static void addTo(BuiltInPackSource source, Consumer<Pack> output) {
        LayerState.context().ifPresent(context -> {
            PackType type = packType(source);
            String root = type == PackType.CLIENT_RESOURCES ? "assets/" : "data/";
            for (ModResourcePack mod : context.resourcePacks()) {
                if (hasDirectory(mod.jar(), root)) {
                    output.accept(pack(
                            "poly/" + mod.modId(),
                            mod.name(),
                            mod.name() + " " + mod.version() + " (Poly Mod Loader)",
                            new FilePackResources.FileResourcesSupplier(mod.jar()),
                            new KnownPack("poly", mod.modId(), mod.version())));
                }
            }
            if (type == PackType.SERVER_DATA) {
                LayerState.generatedDataPack()
                        .ifPresent(directory -> output.accept(pack(
                                "poly/generated",
                                "Poly Mod Loader generated data",
                                "Data Poly Mod Loader derives from mod declarations",
                                new PathPackResources.PathResourcesSupplier(directory),
                                new KnownPack("poly", "generated", LayerState.generatedDataPackVersion()))));
            }
        });
    }

    /**
     * A required pack. Mod packs carry {@link KnownPack} information: the game treats registry
     * entries from known packs as stable, so mod worldgen, dimension, and damage type data do not
     * mark worlds as experimental, and clients with the same mod load them from their own jar.
     */
    private static Pack pack(
            String id, String title, String description, Pack.ResourcesSupplier resources, KnownPack known) {
        return new Pack(
                new PackLocationInfo(id, Component.literal(title), PackSource.BUILT_IN, Optional.of(known)),
                resources,
                new Pack.Metadata(
                        Component.literal(description), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of()),
                REQUIRED_ON_TOP);
    }

    private static boolean hasDirectory(Path jar, String root) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.stream().anyMatch(entry -> entry.getName().startsWith(root));
        } catch (IOException e) {
            LOGGER.warn("[Poly Mod Loader] Cannot read {} to add it as a pack: {}", jar, e.toString());
            return false;
        }
    }

    private static PackType packType(BuiltInPackSource source) {
        try {
            Field field = packTypeField;
            if (field == null) {
                field = BuiltInPackSource.class.getDeclaredField("packType");
                field.setAccessible(true);
                packTypeField = field;
            }
            return (PackType) field.get(source);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("POLY-CONTENT-004 cannot tell which pack type " + source + " loads", e);
        }
    }
}

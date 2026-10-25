package dev.polymodloader.game.layer.v1_21_11;

import dev.polymodloader.game.version.layer.ModResourcePack;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
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
 * resource packs and its {@code data/} to the server's data packs, above the game's own packs. A jar
 * with a {@code pack.mcmeta} also gets the overlay directories it lists for the running game's pack
 * format, as the game's own packs do, so one mod can carry data and assets for several versions.
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
                if (hasEntry(mod.jar(), name -> name.startsWith(root))) {
                    PackLocationInfo location = location(
                            "poly/" + mod.modId(), mod.name(), new KnownPack("poly", mod.modId(), mod.version()));
                    Pack.ResourcesSupplier resources = new FilePackResources.FileResourcesSupplier(mod.jar());
                    output.accept(pack(
                            location,
                            mod.name() + " " + mod.version() + " (Poly Mod Loader)",
                            resources,
                            overlays(location, resources, type, mod.jar())));
                }
            }
            if (type == PackType.SERVER_DATA) {
                LayerState.generatedDataPack()
                        .ifPresent(directory -> output.accept(pack(
                                location(
                                        "poly/generated",
                                        "Poly Mod Loader generated data",
                                        new KnownPack("poly", "generated", LayerState.generatedDataPackVersion())),
                                "Data Poly Mod Loader derives from mod declarations",
                                new PathPackResources.PathResourcesSupplier(directory),
                                List.of())));
            }
        });
    }

    /**
     * A required pack. Mod packs carry {@link KnownPack} information: the game treats registry
     * entries from known packs as stable, so mod worldgen, dimension, and damage type data do not
     * mark worlds as experimental, and clients with the same mod load them from their own jar.
     */
    private static Pack pack(
            PackLocationInfo location, String description, Pack.ResourcesSupplier resources, List<String> overlays) {
        return new Pack(
                location,
                resources,
                new Pack.Metadata(
                        Component.literal(description), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), overlays),
                REQUIRED_ON_TOP);
    }

    private static PackLocationInfo location(String id, String title, KnownPack known) {
        return new PackLocationInfo(id, Component.literal(title), PackSource.BUILT_IN, Optional.of(known));
    }

    /**
     * The overlay directories the jar's {@code pack.mcmeta} lists for the running game's pack format, read
     * the way the game reads its own packs; none without a {@code pack.mcmeta}. The pack stays required and
     * compatible whatever formats the file names: the mod's metadata decided where it runs.
     */
    static List<String> overlays(PackLocationInfo location, Pack.ResourcesSupplier resources, PackType type, Path jar) {
        if (!hasEntry(jar, name -> name.equals("pack.mcmeta"))) {
            return List.of();
        }
        Pack.Metadata metadata = Pack.readPackMetadata(
                location, resources, SharedConstants.getCurrentVersion().packVersion(type), type);
        if (metadata == null) {
            LOGGER.warn(
                    "[Poly Mod Loader] POLY-RESOURCE-010 cannot read pack.mcmeta of {} (see the warning above); "
                            + "its overlays are not used",
                    location.id());
            return List.of();
        }
        if (!metadata.overlays().isEmpty()) {
            LOGGER.info("[Poly Mod Loader] Pack {} uses overlays {}", location.id(), metadata.overlays());
        }
        return metadata.overlays();
    }

    private static boolean hasEntry(Path jar, Predicate<String> name) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.stream().anyMatch(entry -> name.test(entry.getName()));
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

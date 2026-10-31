package dev.plexusmodloader.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.plexusmodloader.runtime.discovery.DiscoveredModFile;
import dev.plexusmodloader.runtime.discovery.ModFileKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which jars MinecraftForge's FML runs, when some jars are built for several loaders. */
class ForgeJarsTest {

    private static final DiscoveredModFile FORGE = file("forge.jar", ModFileKind.FORGE);
    private static final DiscoveredModFile FABRIC = file("fabric.jar", ModFileKind.FABRIC);
    private static final DiscoveredModFile EVERY_LOADER =
            file("every-loader.jar", ModFileKind.FABRIC, ModFileKind.NEOFORGE, ModFileKind.FORGE);
    private static final DiscoveredModFile FORGE_AND_NEOFORGE =
            file("forge-and-neoforge.jar", ModFileKind.NEOFORGE, ModFileKind.FORGE);
    private static final DiscoveredModFile NEOFORGE = file("neoforge.jar", ModFileKind.NEOFORGE);
    private static final DiscoveredModFile PLEXUS = file("plexus.jar", ModFileKind.PLEXUS_NATIVE, ModFileKind.FORGE);

    @Test
    void withForgeModsAndNoModOnlyFabricRunsForgeRunsTheJarsForSeveralLoaders() {
        assertEquals(
                List.of(path(FORGE), path(EVERY_LOADER), path(FORGE_AND_NEOFORGE)),
                PlexusRuntime.forgeJars(List.of(FORGE, EVERY_LOADER, FORGE_AND_NEOFORGE, NEOFORGE, PLEXUS)));
    }

    /** With Fabric mods in the same game too (ADR-0021 G8), Forge still runs the jars for several loaders. */
    @Test
    void withAModOnlyFabricRunsForgeStillRunsTheJarsForSeveralLoaders() {
        assertEquals(
                List.of(path(FORGE), path(EVERY_LOADER)),
                PlexusRuntime.forgeJars(List.of(FORGE, FABRIC, EVERY_LOADER)));
    }

    @Test
    void withoutAModOnlyForgeRunsForgeDoesNotStart() {
        assertEquals(List.of(), PlexusRuntime.forgeJars(List.of(EVERY_LOADER, FORGE_AND_NEOFORGE, FABRIC)));
    }

    private static DiscoveredModFile file(String name, ModFileKind kind, ModFileKind... alsoFor) {
        return new DiscoveredModFile(Path.of(name), kind, Optional.empty(), Optional.empty(), Set.of(alsoFor));
    }

    private static Path path(DiscoveredModFile file) {
        return file.path();
    }
}

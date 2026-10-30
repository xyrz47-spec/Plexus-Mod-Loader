package dev.polymodloader.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.polymodloader.runtime.discovery.DiscoveredModFile;
import dev.polymodloader.runtime.discovery.ModFileKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Which jars NeoForge's FML runs (ADR-0026), when some jars are built for several loaders: as NeoForge itself, it reads
 * {@code neoforge.mods.toml} of a jar that also has {@code fabric.mod.json} (Collective, Iris, Zoomify in the corpus).
 */
class NeoForgeJarsTest {

    private static final DiscoveredModFile NEOFORGE = file("neoforge.jar", ModFileKind.NEOFORGE);
    private static final DiscoveredModFile FABRIC = file("fabric.jar", ModFileKind.FABRIC);
    private static final DiscoveredModFile FABRIC_AND_NEOFORGE =
            file("fabric-and-neoforge.jar", ModFileKind.FABRIC, ModFileKind.NEOFORGE);
    private static final DiscoveredModFile EVERY_LOADER =
            file("every-loader.jar", ModFileKind.FABRIC, ModFileKind.NEOFORGE, ModFileKind.FORGE);
    private static final DiscoveredModFile LIBRARY = file("library.jar", ModFileKind.FML_LIBRARY);
    private static final DiscoveredModFile POLY = file("poly.jar", ModFileKind.POLY_NATIVE, ModFileKind.NEOFORGE);

    @Test
    void withNeoForgeModsNeoForgeRunsTheJarsForSeveralLoadersAndFmlLibraries() {
        List<Path> jars =
                PolyRuntime.neoForgeJars(List.of(NEOFORGE, FABRIC, FABRIC_AND_NEOFORGE, EVERY_LOADER, LIBRARY, POLY));

        assertEquals(List.of(path(NEOFORGE), path(FABRIC_AND_NEOFORGE), path(EVERY_LOADER), path(LIBRARY)), jars);
    }

    @Test
    void withoutAModOnlyNeoForgeRunsTheJarsForSeveralLoadersStayFabrics() {
        assertEquals(List.of(path(LIBRARY)), PolyRuntime.neoForgeJars(List.of(FABRIC_AND_NEOFORGE, FABRIC, LIBRARY)));
    }

    /** Only a Fabric mod that NeoForge does not run keeps NeoForge out (one Mixin, ADR-0026 5). */
    @Test
    void onlyFabricModsNeoForgeDoesNotRunCountAsFabricMods() {
        List<DiscoveredModFile> files = List.of(NEOFORGE, FABRIC_AND_NEOFORGE, EVERY_LOADER);

        assertEquals(0, PolyRuntime.fabricModsBesides(files, PolyRuntime.neoForgeJars(files)));
        List<DiscoveredModFile> withFabric = List.of(NEOFORGE, FABRIC_AND_NEOFORGE, FABRIC);
        assertEquals(1, PolyRuntime.fabricModsBesides(withFabric, PolyRuntime.neoForgeJars(withFabric)));
    }

    private static DiscoveredModFile file(String name, ModFileKind kind, ModFileKind... alsoFor) {
        return new DiscoveredModFile(Path.of(name), kind, Optional.empty(), Optional.empty(), Set.of(alsoFor));
    }

    private static Path path(DiscoveredModFile file) {
        return file.path();
    }
}

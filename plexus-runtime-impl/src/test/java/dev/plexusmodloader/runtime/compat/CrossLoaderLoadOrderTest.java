package dev.plexusmodloader.runtime.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.plexusmodloader.compat.bridge.BridgeAssessment;
import dev.plexusmodloader.compat.bridge.BridgedDependency;
import dev.plexusmodloader.compat.bridge.CompatibilityStatus;
import dev.plexusmodloader.runtime.discovery.ForeignModResolver.ForeignMod;
import dev.plexusmodloader.runtime.metadata.ModEntrypoints;
import dev.plexusmodloader.runtime.metadata.ModEnvironment;
import dev.plexusmodloader.runtime.metadata.ModId;
import dev.plexusmodloader.runtime.metadata.ModMetadata;
import dev.plexusmodloader.runtime.metadata.version.SemanticVersion;
import dev.plexusmodloader.runtime.metadata.version.VersionRange;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossLoaderLoadOrderTest {

    private static ModMetadata nativeMod(String id, String... depends) {
        Map<String, VersionRange> ranges = new LinkedHashMap<>();
        for (String dependency : depends) {
            ranges.put(dependency, VersionRange.parse("*").orElseThrow());
        }
        return new ModMetadata(
                1,
                new ModId(id),
                SemanticVersion.parse("1.0.0").orElseThrow(),
                id,
                ModEnvironment.BOTH,
                List.of("26.2"),
                new ModEntrypoints(List.of(), List.of(), List.of()),
                ranges,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static ForeignMod fabricMod(String id, String... requires) {
        List<BridgedDependency> dependencies = java.util.Arrays.stream(requires)
                .map(dependency -> new BridgedDependency(dependency, BridgedDependency.Kind.REQUIRED, "*", v -> true))
                .toList();
        return new ForeignMod(
                Path.of(id + ".jar"),
                null,
                BridgeAssessment.of(
                        "Fabric",
                        id,
                        "1.0.0",
                        List.of(),
                        List.of(),
                        List.of(),
                        dependencies,
                        CompatibilityStatus.EXPERIMENTAL));
    }

    private static List<String> ids(List<CrossLoaderLoadOrder.LoadedMod> order) {
        return order.stream().map(CrossLoaderLoadOrder.LoadedMod::id).toList();
    }

    @Test
    void requiredModsComeFirstWhateverTheirLoader() {
        List<CrossLoaderLoadOrder.LoadedMod> order = CrossLoaderLoadOrder.of(
                List.of(nativeMod("native_lib"), nativeMod("a_native_addon", "fabric_lib")),
                List.of(fabricMod("fabric_lib"), fabricMod("b_fabric_addon", "native_lib", "fabric_lib")));

        List<String> ids = ids(order);
        assertEquals(4, ids.size());
        assertEquals(true, ids.indexOf("fabric_lib") < ids.indexOf("a_native_addon"), ids::toString);
        assertEquals(true, ids.indexOf("native_lib") < ids.indexOf("b_fabric_addon"), ids::toString);
        assertEquals(true, ids.indexOf("fabric_lib") < ids.indexOf("b_fabric_addon"), ids::toString);
    }

    @Test
    void independentModsRunInIdOrder() {
        assertEquals(
                List.of("alpha", "beta", "gamma"),
                ids(CrossLoaderLoadOrder.of(
                        List.of(nativeMod("gamma"), nativeMod("alpha")), List.of(fabricMod("beta")))));
    }

    @Test
    void aFabricCycleStillRunsEveryMod() {
        List<String> ids = ids(
                CrossLoaderLoadOrder.of(List.of(nativeMod("base")), List.of(fabricMod("x", "y"), fabricMod("y", "x"))));

        assertEquals(List.of("base", "x", "y"), ids);
    }

    private static ForeignMod quiltMod(String id, BridgedDependency... dependencies) {
        return new ForeignMod(
                Path.of(id + ".jar"),
                null,
                BridgeAssessment.of(
                        "Quilt",
                        id,
                        "1.0.0",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(dependencies),
                        CompatibilityStatus.EXPERIMENTAL));
    }

    @Test
    void loadedIfPresentTargetsAndGroupMembersComeFirst() {
        BridgedDependency optional =
                new BridgedDependency("z_optional", BridgedDependency.Kind.IF_PRESENT, "*", v -> true);
        BridgedDependency group = BridgedDependency.group(
                BridgedDependency.Kind.REQUIRED,
                "any of [absent, y_member]",
                List.of(
                        new BridgedDependency("absent", BridgedDependency.Kind.REQUIRED, "*", v -> true),
                        new BridgedDependency("y_member", BridgedDependency.Kind.REQUIRED, "*", v -> true)));
        BridgedDependency breaks = BridgedDependency.group(
                BridgedDependency.Kind.BREAKS,
                "all of [x_broken]",
                List.of(new BridgedDependency("x_broken", BridgedDependency.Kind.BREAKS, "*", v -> true)));

        List<String> ids = ids(CrossLoaderLoadOrder.of(
                List.of(),
                List.of(
                        quiltMod("a_user", optional, group, breaks),
                        quiltMod("x_broken"),
                        quiltMod("y_member"),
                        quiltMod("z_optional"))));

        assertEquals(List.of("x_broken", "y_member", "z_optional", "a_user"), ids);
    }
}

package dev.polymodloader.runtime.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.runtime.metadata.ModEntrypoints;
import dev.polymodloader.runtime.metadata.ModEnvironment;
import dev.polymodloader.runtime.metadata.ModId;
import dev.polymodloader.runtime.metadata.ModMetadata;
import dev.polymodloader.runtime.metadata.version.SemanticVersion;
import dev.polymodloader.runtime.metadata.version.VersionRange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModDependencyResolverTest {

    private static final SemanticVersion POLY_MOD_LOADER =
            SemanticVersion.parse("0.1.0").orElseThrow();
    private final ModDependencyResolver resolver = new ModDependencyResolver();

    @Test
    void ordersDependenciesFirstAndBreaksTiesById() {
        List<ModMetadata> mods = List.of(
                mod("zeta", "1.0.0", ModEnvironment.BOTH, Map.of("core", ">=1.0.0")),
                mod("alpha", "1.0.0", ModEnvironment.BOTH, Map.of("core", "*")),
                mod("core", "1.2.0", ModEnvironment.BOTH, Map.of("poly", ">=0.1.0 <0.2.0")));

        ModDependencyResolver.Result result = resolver.resolve(mods, "26.2", GameSide.CLIENT, POLY_MOD_LOADER);

        assertEquals(List.of(), result.problems());
        assertEquals(
                List.of("core", "alpha", "zeta"),
                result.loadOrder().stream().map(mod -> mod.id().value()).toList());
    }

    @Test
    void reportsMissingDependencyAndVersionMismatchTogether() {
        List<ModMetadata> mods =
                List.of(mod("addon", "1.0.0", ModEnvironment.BOTH, ordered("library", "*", "poly", ">=1.0.0")));

        ModDependencyResolver.Result result = resolver.resolve(mods, "26.2", GameSide.CLIENT, POLY_MOD_LOADER);

        assertTrue(result.loadOrder().isEmpty());
        assertEquals(
                List.of(DependencyProblemCode.MISSING_DEPENDENCY, DependencyProblemCode.DEPENDENCY_VERSION_MISMATCH),
                codes(result));
    }

    @Test
    void detectsCycles() {
        List<ModMetadata> mods = List.of(
                mod("first", "1.0.0", ModEnvironment.BOTH, Map.of("second", "*")),
                mod("second", "1.0.0", ModEnvironment.BOTH, Map.of("first", "*")));

        ModDependencyResolver.Result result = resolver.resolve(mods, "26.2", GameSide.CLIENT, POLY_MOD_LOADER);

        assertEquals(
                List.of(DependencyProblemCode.DEPENDENCY_CYCLE, DependencyProblemCode.DEPENDENCY_CYCLE), codes(result));
    }

    @Test
    void detectsDuplicateIds() {
        List<ModMetadata> mods = List.of(
                mod("same", "1.0.0", ModEnvironment.BOTH, Map.of()),
                mod("same", "2.0.0", ModEnvironment.BOTH, Map.of()));

        assertEquals(
                List.of(DependencyProblemCode.DUPLICATE_MOD_ID),
                codes(resolver.resolve(mods, "26.2", GameSide.CLIENT, POLY_MOD_LOADER)));
    }

    @Test
    void blocksClientOnlyModsOnDedicatedServer() {
        List<ModMetadata> mods = List.of(mod("shaders", "1.0.0", ModEnvironment.CLIENT, Map.of()));

        assertEquals(
                List.of(DependencyProblemCode.WRONG_ENVIRONMENT),
                codes(resolver.resolve(mods, "26.2", GameSide.DEDICATED_SERVER, POLY_MOD_LOADER)));
        assertEquals(List.of(), codes(resolver.resolve(mods, "26.2", GameSide.CLIENT, POLY_MOD_LOADER)));
    }

    @Test
    void rejectsModsForOtherMinecraftVersions() {
        List<ModMetadata> mods = List.of(mod("old", "1.0.0", ModEnvironment.BOTH, Map.of()));

        assertEquals(
                List.of(DependencyProblemCode.UNSUPPORTED_MINECRAFT_VERSION),
                codes(resolver.resolve(mods, "26.3", GameSide.CLIENT, POLY_MOD_LOADER)));
    }

    @Test
    void acceptsMinecraftWildcardsAndRanges() {
        ModMetadata wildcard = withMinecraft(mod("wild", "1.0.0", ModEnvironment.BOTH, Map.of()), "26.*");
        ModMetadata range = withMinecraft(mod("ranged", "1.0.0", ModEnvironment.BOTH, Map.of()), ">=26.2 <26.4");

        assertEquals(
                List.of(), codes(resolver.resolve(List.of(wildcard, range), "26.3", GameSide.CLIENT, POLY_MOD_LOADER)));
        assertEquals(
                List.of(DependencyProblemCode.UNSUPPORTED_MINECRAFT_VERSION),
                codes(resolver.resolve(List.of(wildcard, range), "26.4", GameSide.CLIENT, POLY_MOD_LOADER)));
    }

    @Test
    void reportsDeclaredConflicts() {
        ModMetadata first = new ModMetadata(
                1,
                new ModId("first"),
                SemanticVersion.parse("1.0.0").orElseThrow(),
                "first",
                ModEnvironment.BOTH,
                List.of("26.2"),
                new ModEntrypoints(List.of(), List.of(), List.of()),
                Map.of(),
                List.of("second"),
                List.of(),
                List.of(),
                List.of());
        List<ModMetadata> mods = List.of(first, mod("second", "1.0.0", ModEnvironment.BOTH, Map.of()));

        assertEquals(
                List.of(DependencyProblemCode.CONFLICTING_MODS),
                codes(resolver.resolve(mods, "26.2", GameSide.CLIENT, POLY_MOD_LOADER)));
    }

    @Test
    void aNativeModMayRequireALoadableModOfAnotherLoader() {
        List<ModMetadata> mods =
                List.of(mod("addon", "1.0.0", ModEnvironment.BOTH, Map.of("fabric_lib", ">=1.2.0 <2.0.0")));

        assertEquals(
                List.of(),
                codes(resolver.resolve(
                        mods, "26.2", GameSide.CLIENT, POLY_MOD_LOADER, Map.of("fabric_lib", "1.2"), Map.of())));
        assertEquals(
                List.of(DependencyProblemCode.DEPENDENCY_VERSION_MISMATCH),
                codes(resolver.resolve(
                        mods, "26.2", GameSide.CLIENT, POLY_MOD_LOADER, Map.of("fabric_lib", "2.0.0+26.2"), Map.of())));
    }

    @Test
    void aForeignVersionThatIsNotSemVerOnlySatisfiesAnyVersion() {
        Map<String, String> foreign = Map.of("lib", "r42-final");

        assertEquals(
                List.of(DependencyProblemCode.DEPENDENCY_VERSION_MISMATCH),
                codes(resolver.resolve(
                        List.of(mod("aa", "1.0.0", ModEnvironment.BOTH, Map.of("lib", ">=1.0.0"))),
                        "26.2",
                        GameSide.CLIENT,
                        POLY_MOD_LOADER,
                        foreign,
                        Map.of())));
        assertEquals(
                List.of(),
                codes(resolver.resolve(
                        List.of(mod("aa", "1.0.0", ModEnvironment.BOTH, Map.of("lib", "*"))),
                        "26.2",
                        GameSide.CLIENT,
                        POLY_MOD_LOADER,
                        foreign,
                        Map.of())));
    }

    @Test
    void requiringAnInstalledButBlockedModSaysWhy() {
        ModDependencyResolver.Result result = resolver.resolve(
                List.of(mod("addon", "1.0.0", ModEnvironment.BOTH, Map.of("sodium", "*"))),
                "26.2",
                GameSide.CLIENT,
                POLY_MOD_LOADER,
                Map.of(),
                Map.of("sodium", "Fabric mod sodium 0.8.0 (sodium.jar) is BLOCKED: [POLY-COMPAT-101]"));

        assertEquals(List.of(DependencyProblemCode.UNAVAILABLE_DEPENDENCY), codes(result));
        assertTrue(result.problems().getFirst().toString().startsWith("POLY-DEPENDENCY-009 mod=addon"));
        assertTrue(result.problems().getFirst().message().contains("POLY-COMPAT-101"));
    }

    @Test
    void shortForeignVersionsAreReadAsSemVer() {
        assertEquals(
                "1.2.0",
                ModDependencyResolver.lenientVersion("1.2").orElseThrow().toString());
        assertEquals(
                "7.0.0", ModDependencyResolver.lenientVersion("7").orElseThrow().toString());
        assertEquals(
                "3.1.0-beta.1+26.2",
                ModDependencyResolver.lenientVersion("3.1.0-beta.1+26.2")
                        .orElseThrow()
                        .toString());
        assertTrue(ModDependencyResolver.lenientVersion("1.2.3.4").isEmpty());
    }

    private static ModMetadata withMinecraft(ModMetadata mod, String... minecraft) {
        return new ModMetadata(
                mod.schemaVersion(),
                mod.id(),
                mod.version(),
                mod.name(),
                mod.environment(),
                List.of(minecraft),
                mod.entrypoints(),
                mod.depends(),
                mod.conflicts(),
                mod.capabilities(),
                mod.replacesSystems(),
                mod.transforms());
    }

    private static List<DependencyProblemCode> codes(ModDependencyResolver.Result result) {
        return result.problems().stream().map(DependencyProblem::code).toList();
    }

    private static ModMetadata mod(String id, String version, ModEnvironment environment, Map<String, String> depends) {
        Map<String, VersionRange> ranges = new LinkedHashMap<>();
        depends.forEach(
                (key, value) -> ranges.put(key, VersionRange.parse(value).orElseThrow()));
        return new ModMetadata(
                1,
                new ModId(id),
                SemanticVersion.parse(version).orElseThrow(),
                id,
                environment,
                List.of("26.2"),
                new ModEntrypoints(List.of(), List.of(), List.of()),
                ranges,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static Map<String, String> ordered(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}

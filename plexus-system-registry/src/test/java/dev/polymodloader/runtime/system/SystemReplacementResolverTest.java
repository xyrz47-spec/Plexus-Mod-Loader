package dev.polymodloader.runtime.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.system.brand.GameBrandSystem;
import dev.polymodloader.runtime.metadata.ModEntrypoints;
import dev.polymodloader.runtime.metadata.ModEnvironment;
import dev.polymodloader.runtime.metadata.ModId;
import dev.polymodloader.runtime.metadata.ModMetadata;
import dev.polymodloader.runtime.metadata.SystemReplacementDeclaration;
import dev.polymodloader.runtime.metadata.version.SemanticVersion;
import dev.polymodloader.runtime.metadata.version.VersionRange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemReplacementResolverTest {

    private final SystemReplacementResolver resolver = new SystemReplacementResolver();
    private final List<SystemDefinition<?>> definitions = BuiltInSystems.definitions();

    @Test
    void withoutReplacementsTheDefaultIsUsed() {
        SystemReplacementResolver.Result result =
                resolver.resolve(List.of(mod("plain", List.of(), false)), definitions);

        assertEquals(Map.of(), result.replacements());
        assertEquals(List.of(), result.problems());
    }

    @Test
    void aSingleReplacementIsSelected() {
        SystemReplacementResolver.Result result =
                resolver.resolve(List.of(mod("branding", List.of(), true)), definitions);

        SystemReplacementResolver.SelectedReplacement selected =
                result.replacements().get(GameBrandSystem.ID);
        assertEquals("branding", selected.modId());
        assertEquals("com.example.Provider", selected.providerClass());
    }

    @Test
    void twoIndependentReplacementsConflict() {
        SystemReplacementResolver.Result result =
                resolver.resolve(List.of(mod("first", List.of(), true), mod("second", List.of(), true)), definitions);

        assertTrue(result.replacements().isEmpty());
        assertEquals(1, result.problems().size());
        String problem = result.problems().getFirst();
        assertTrue(
                problem.startsWith("PLEXUS-SYSTEM-001 system=" + GameBrandSystem.ID + ": mods [first, second]"),
                problem);
    }

    @Test
    void legacySystemIdIsResolvedToCanonical() {
        ModMetadata legacy = metadata(
                "legacy_mod",
                List.of(),
                List.of(new SystemReplacementDeclaration("poly:game_brand", "com.example.LegacyProvider", false)));

        SystemReplacementResolver.Result result = resolver.resolve(List.of(legacy), definitions);

        assertEquals(List.of(), result.problems());
        SystemReplacementResolver.SelectedReplacement selected =
                result.replacements().get(GameBrandSystem.ID);
        assertEquals("legacy_mod", selected.modId());
        assertEquals("com.example.LegacyProvider", selected.providerClass());
    }

    @Test
    void anOverrideFromADependentModWins() {
        SystemReplacementResolver.Result result = resolver.resolve(
                List.of(mod("base", List.of(), true), withReplacement(mod("pack", List.of("base"), false), true)),
                definitions);

        assertEquals(List.of(), result.problems());
        SystemReplacementResolver.SelectedReplacement selected =
                result.replacements().get(GameBrandSystem.ID);
        assertEquals("pack", selected.modId());
        assertEquals(List.of("base"), selected.overriddenModIds());
    }

    @Test
    void anOverrideWithoutDependingOnTheOtherModIsRejected() {
        SystemReplacementResolver.Result result = resolver.resolve(
                List.of(mod("base", List.of(), true), withReplacement(mod("pack", List.of(), false), true)),
                definitions);

        assertTrue(result.replacements().isEmpty());
        assertEquals(2, result.problems().size());
        assertTrue(result.problems().getFirst().startsWith("PLEXUS-SYSTEM-005 mod=pack"));
        assertTrue(result.problems().get(1).startsWith("PLEXUS-SYSTEM-001"));
    }

    @Test
    void unknownSystemsAreReported() {
        ModMetadata mod =
                metadata("ghost", List.of(), List.of(new SystemReplacementDeclaration("poly:no_such", "a.B", false)));

        SystemReplacementResolver.Result result = resolver.resolve(List.of(mod), definitions);

        assertTrue(result.problems().getFirst().startsWith("PLEXUS-SYSTEM-002 mod=ghost"));
    }

    private static ModMetadata mod(String id, List<String> depends, boolean replacesBrand) {
        ModMetadata plain = metadata(id, depends, List.of());
        return replacesBrand ? withReplacement(plain, false) : plain;
    }

    private static ModMetadata withReplacement(ModMetadata mod, boolean override) {
        return metadata(
                mod.id().value(),
                List.copyOf(mod.depends().keySet()),
                List.of(new SystemReplacementDeclaration(
                        GameBrandSystem.ID.value(), "com.example.Provider", override)));
    }

    private static ModMetadata metadata(
            String id, List<String> depends, List<SystemReplacementDeclaration> replacements) {
        Map<String, VersionRange> ranges = new LinkedHashMap<>();
        depends.forEach(
                dependency -> ranges.put(dependency, VersionRange.parse("*").orElseThrow()));
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
                List.of("system.provider.replace"),
                replacements,
                List.of());
    }
}

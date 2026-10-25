package dev.polymodloader.runtime.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.BridgedDependency;
import dev.polymodloader.compat.bridge.BridgedEntrypoint;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.CompatibilityStatus;
import dev.polymodloader.compat.bridge.ModLoaderBridge;
import dev.polymodloader.runtime.discovery.ForeignModResolver.ForeignMod;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossLoaderModResolverTest {

    /** A bridge with Fabric's built-in IDs and no runtime of its own. */
    private static final ModLoaderBridge BRIDGE = new ModLoaderBridge() {
        @Override
        public String loaderName() {
            return "Fabric";
        }

        @Override
        public String metadataPath() {
            return "fabric.mod.json";
        }

        @Override
        public BridgeAssessment assess(String metadata, GameSide side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) {}

        @Override
        public Map<String, String> builtInMods() {
            return Map.of("fabricloader", "0.19.5");
        }

        @Override
        public Map<String, String> unavailableBuiltInMods() {
            return Map.of("mixinextras", "needs Mixin");
        }
    };

    private static BridgedDependency requires(String id, String exactOrStar) {
        return new BridgedDependency(
                id,
                BridgedDependency.Kind.REQUIRED,
                exactOrStar,
                version -> exactOrStar.equals("*") || version.equals(exactOrStar));
    }

    private static BridgedDependency of(BridgedDependency.Kind kind, String id) {
        return new BridgedDependency(id, kind, "*", version -> true);
    }

    private static ForeignMod mod(String id, String version, List<String> provides, BridgedDependency... dependencies) {
        return mod(id, version, provides, List.of(), dependencies);
    }

    private static ForeignMod mod(
            String id,
            String version,
            List<String> provides,
            List<CompatibilityFinding> findings,
            BridgedDependency... dependencies) {
        return new ForeignMod(
                Path.of(id + ".jar"),
                BRIDGE,
                BridgeAssessment.of(
                        "Fabric",
                        id,
                        version,
                        findings,
                        List.of(),
                        provides,
                        List.of(dependencies),
                        CompatibilityStatus.EXPERIMENTAL));
    }

    /** A copy of {@code id} nested in {@code container}'s jar. */
    private static ForeignMod nested(
            ForeignMod container, String id, String version, BridgedDependency... dependencies) {
        ForeignMod mod = mod(id, version, List.of(), dependencies);
        return new ForeignMod(
                Path.of(container.assessment().modId() + "-" + id + ".jar"),
                mod.bridge(),
                mod.assessment(),
                container.jar());
    }

    private static ForeignMod blocked(String id) {
        return mod(id, "1.0.0", List.of(), List.of(new CompatibilityFinding("POLY-COMPAT-101", true, "mixins", "1")));
    }

    private static List<String> statuses(CrossLoaderModResolver.Result result) {
        return result.mods().stream()
                .map(mod -> mod.assessment().modId() + "@" + mod.assessment().version() + "="
                        + mod.assessment().status() + mod.assessment().blockingCodes())
                .toList();
    }

    @Test
    void aNestedModLoadsOnlyWhileItsContainerLoads() {
        ForeignMod container = blocked("bundle");
        ForeignMod lib = nested(container, "lib", "1.0.0");
        CrossLoaderModResolver.Result result =
                resolve(List.of(container, lib, mod("user", "1.0.0", List.of(), requires("lib", "*"))), Map.of());

        assertEquals(
                List.of(
                        "bundle@1.0.0=BLOCKED[POLY-COMPAT-101]",
                        "lib@1.0.0=BLOCKED[POLY-COMPAT-121]",
                        "user@1.0.0=BLOCKED[POLY-COMPAT-114]"),
                statuses(result));
        assertTrue(details(find(result, "lib"), "POLY-COMPAT-121").getFirst().contains("bundle.jar"));
    }

    @Test
    void aContainerWithBlockedNestedModsIsNotedAsPartial() {
        ForeignMod bundle = mod("bundle", "1.0.0", List.of());
        ForeignMod blockedChild = new ForeignMod(
                Path.of("bundle-mixins.jar"), bundle.bridge(), blocked("mixins").assessment(), bundle.jar());
        CrossLoaderModResolver.Result result =
                resolve(List.of(bundle, nested(bundle, "lib", "1.0.0"), blockedChild), Map.of());

        assertEquals(CompatibilityStatus.EXPERIMENTAL, find(result, "bundle").status());
        assertEquals(
                List.of("1 of its 2 nested mods are not loaded, so it works only in part: mixins [POLY-COMPAT-101]"),
                details(find(result, "bundle"), "POLY-COMPAT-124"));
    }

    @Test
    void nestedModsForTheOtherSideOnlyDoNotMakeTheContainerPartial() {
        // Simple Voice Chat's Quilt build on a dedicated server: Fabric API's client modules are left out with 108
        // (305 for a Quilt module), as Fabric Loader and Quilt Loader leave them out there; the mod works in full.
        ForeignMod bundle = mod("bundle", "1.0.0", List.of());
        ForeignMod clientModule = new ForeignMod(
                Path.of("bundle-client.jar"),
                bundle.bridge(),
                mod(
                                "client_module",
                                "1.0.0",
                                List.of(),
                                List.of(new CompatibilityFinding(
                                        "POLY-COMPAT-108",
                                        true,
                                        "environment",
                                        "mod is client-only, process is DEDICATED_SERVER")))
                        .assessment(),
                bundle.jar());
        CrossLoaderModResolver.Result alone =
                resolve(List.of(bundle, nested(bundle, "lib", "1.0.0"), clientModule), Map.of());

        assertEquals(List.of(), details(find(alone, "bundle"), "POLY-COMPAT-124"));

        ForeignMod blockedChild = new ForeignMod(
                Path.of("bundle-mixins.jar"), bundle.bridge(), blocked("mixins").assessment(), bundle.jar());
        CrossLoaderModResolver.Result withBlocked =
                resolve(List.of(bundle, nested(bundle, "lib", "1.0.0"), clientModule, blockedChild), Map.of());

        assertEquals(
                List.of("1 of its 3 nested mods are not loaded, so it works only in part: mixins [POLY-COMPAT-101]"),
                details(find(withBlocked, "bundle"), "POLY-COMPAT-124"));
    }

    @Test
    void aDirectlyInstalledCopyWinsOverNestedOnes() {
        ForeignMod bundle = mod("bundle", "1.0.0", List.of());
        CrossLoaderModResolver.Result result =
                resolve(List.of(bundle, nested(bundle, "lib", "2.0.0"), mod("lib", "1.0.0", List.of())), Map.of());

        assertEquals(
                List.of(
                        "bundle@1.0.0=EXPERIMENTAL[]",
                        "lib@2.0.0=BLOCKED[POLY-COMPAT-122]",
                        "lib@1.0.0=EXPERIMENTAL[]"),
                statuses(result));
        assertEquals("1.0.0", result.loadableVersions().get("lib"));
    }

    @Test
    void theNewestNestedCopyIsUsedAndANextOneIfItCannotLoad() {
        ForeignMod first = mod("first", "1.0.0", List.of());
        ForeignMod second = mod("second", "1.0.0", List.of());
        CrossLoaderModResolver.Result newest = resolve(
                List.of(first, nested(first, "lib", "1.2.0"), second, nested(second, "lib", "1.3.0")), Map.of());

        assertEquals("1.3.0", newest.loadableVersions().get("lib"));
        assertTrue(details(newest.mods().get(1).assessment(), "POLY-COMPAT-122")
                .getFirst()
                .contains("1.3.0 from second-lib.jar"));

        ForeignMod blockedSecond = blocked("second");
        CrossLoaderModResolver.Result fallback = resolve(
                List.of(first, nested(first, "lib", "1.2.0"), blockedSecond, nested(blockedSecond, "lib", "1.3.0")),
                Map.of());

        assertEquals("1.2.0", fallback.loadableVersions().get("lib"));
        assertEquals(
                List.of("POLY-COMPAT-121"), fallback.mods().get(3).assessment().blockingCodes());
    }

    @Test
    void theHostsBuildOfALeftOutModMeetsTheDependenciesOnIt() {
        // Better Shulker Boxes (Quilt) needs cloth-config; NeoForge runs Cloth Config's own build, cloth_config, and
        // the Fabric build is left out (POLY-COMPAT-139, ADR-0026 37). The host's build stands in for it.
        ForeignMod user = mod("bettershulker", "1.5.1", List.of(), requires("cloth-config", "26.2.155"));

        CrossLoaderModResolver.Result without = resolve(List.of(user), Map.of());
        CrossLoaderModResolver.Result with = new CrossLoaderModResolver()
                .resolve(
                        List.of(user),
                        Map.of(),
                        Map.of("cloth-config", "26.2.155", "cloth-config2", "26.2.155"),
                        List.of(),
                        "26.2",
                        "25",
                        null);

        assertEquals(List.of("bettershulker@1.5.1=BLOCKED[POLY-COMPAT-114]"), statuses(without));
        assertEquals(List.of("bettershulker@1.5.1=EXPERIMENTAL[]"), statuses(with));
    }

    @Test
    void aNativeModWinsOverANestedCopyWithItsId() {
        ForeignMod bundle = mod("bundle", "1.0.0", List.of());
        CrossLoaderModResolver.Result result =
                resolve(List.of(bundle, nested(bundle, "native_core", "9.0.0")), Map.of("native_core", "3.1.0"));

        assertEquals(
                List.of("POLY-COMPAT-122"), result.mods().get(1).assessment().blockingCodes());
        assertEquals(
                CompatibilityStatus.EXPERIMENTAL,
                result.mods().get(0).assessment().status());
    }

    private static CrossLoaderModResolver.Result resolve(List<ForeignMod> mods, Map<String, String> natives) {
        return new CrossLoaderModResolver().resolve(mods, natives, List.of(), "26.2", "25", null);
    }

    private static BridgeAssessment find(CrossLoaderModResolver.Result result, String id) {
        return result.mods().stream()
                .map(ForeignMod::assessment)
                .filter(assessment -> assessment.modId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> details(BridgeAssessment assessment, String code) {
        return assessment.findings().stream()
                .filter(finding -> finding.code().equals(code))
                .map(CompatibilityFinding::detail)
                .toList();
    }

    @Test
    void aForeignModMayRequireNativeForeignAndEnvironmentMods() {
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        mod("lib", "1.0.0", List.of("lib_alias")),
                        mod(
                                "addon",
                                "2.0.0",
                                List.of(),
                                requires("lib_alias", "1.0.0"),
                                requires("native_core", "3.1.0"),
                                requires("minecraft", "26.2"),
                                requires("java", "25"),
                                requires("fabricloader", "0.19.5"))),
                Map.of("native_core", "3.1.0"));

        assertEquals(2, result.loadable().size());
        assertEquals(Map.of("lib", "1.0.0", "lib_alias", "1.0.0", "addon", "2.0.0"), result.loadableVersions());
        assertEquals(Map.of(), result.unavailable());
    }

    @Test
    void blockingCascadesAndEveryReasonIsGiven() {
        ForeignMod broken = mod(
                "broken",
                "1.0.0",
                List.of(),
                List.of(new CompatibilityFinding("POLY-COMPAT-101", true, "mixins", "1 config")));
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        // Checked first, while 'middle' is still loadable: it must still list 'middle' at the end.
                        mod("top", "1.0.0", List.of(), requires("absent", "*"), requires("middle", "*")),
                        mod("middle", "1.0.0", List.of(), requires("broken", "*")),
                        broken),
                Map.of());

        assertEquals(List.of(), result.loadable());
        List<String> top = details(find(result, "top"), "POLY-COMPAT-114");
        assertEquals(2, top.size(), top::toString);
        assertTrue(top.contains("requires 'absent' *, which is not installed"), top::toString);
        assertTrue(
                top.contains("requires 'middle' *, which is installed but not loaded: Fabric mod middle 1.0.0"
                        + " (middle.jar) is BLOCKED: [POLY-COMPAT-114]"),
                top::toString);
        assertEquals(
                List.of("requires 'broken' *, which is installed but not loaded: Fabric mod broken 1.0.0 (broken.jar)"
                        + " is BLOCKED: [POLY-COMPAT-101]"),
                details(find(result, "middle"), "POLY-COMPAT-114"));
        assertTrue(result.unavailable().containsKey("top"));
    }

    @Test
    void wrongMinecraftJavaOrDependencyVersionsBlock() {
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        mod("old", "1.0.0", List.of(), requires("minecraft", "1.21.4")),
                        mod("future", "1.0.0", List.of(), requires("java", "26")),
                        mod("picky", "1.0.0", List.of(), requires("native_core", "9.9.9"))),
                Map.of("native_core", "3.1.0"));

        assertEquals(
                List.of("requires Minecraft 1.21.4, but 26.2 is running (the mod was built for another Minecraft"
                        + " version)"),
                details(find(result, "old"), "POLY-COMPAT-114"));
        assertEquals(
                List.of("requires Java 26, but 25 is running"), details(find(result, "future"), "POLY-COMPAT-114"));
        assertEquals(
                List.of("requires 'native_core' 9.9.9, but 3.1.0 is installed"),
                details(find(result, "picky"), "POLY-COMPAT-114"));
    }

    @Test
    void anUnavailableBuiltInModGivesTheBridgesReason() {
        CrossLoaderModResolver.Result result =
                resolve(List.of(mod("fancy", "1.0.0", List.of(), requires("mixinextras", "*"))), Map.of());

        assertEquals(
                List.of("requires 'mixinextras' *, which is installed but not loaded: needs Mixin"),
                details(find(result, "fancy"), "POLY-COMPAT-114"));
    }

    @Test
    void breaksBlocksAndConflictsOnlyWarn() {
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        mod("a", "1.0.0", List.of(), of(BridgedDependency.Kind.BREAKS, "native_core")),
                        mod("b", "1.0.0", List.of(), of(BridgedDependency.Kind.CONFLICTS, "native_core")),
                        mod("c", "1.0.0", List.of(), of(BridgedDependency.Kind.OPTIONAL, "absent"))),
                Map.of("native_core", "3.1.0"));

        assertEquals(CompatibilityStatus.BLOCKED, find(result, "a").status());
        assertEquals(1, details(find(result, "a"), "POLY-COMPAT-115").size());
        assertEquals(CompatibilityStatus.EXPERIMENTAL, find(result, "b").status());
        assertEquals(1, details(find(result, "b"), "POLY-COMPAT-116").size());
        assertEquals(CompatibilityStatus.EXPERIMENTAL, find(result, "c").status());
    }

    @Test
    void duplicateIdsAcrossLoadersBlockTheForeignMods() {
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        mod("same", "1.0.0", List.of()),
                        mod("twin", "1.0.0", List.of("same")),
                        mod("native_core", "1.0.0", List.of()),
                        mod("self_alias", "1.0.0", List.of("self_alias"))),
                Map.of("native_core", "3.1.0"));

        assertEquals(CompatibilityStatus.BLOCKED, find(result, "same").status());
        assertEquals(CompatibilityStatus.BLOCKED, find(result, "twin").status());
        assertEquals(CompatibilityStatus.BLOCKED, find(result, "native_core").status());
        assertTrue(details(find(result, "native_core"), "POLY-COMPAT-117")
                .getFirst()
                .contains("Poly Mod"));
        assertEquals(
                CompatibilityStatus.EXPERIMENTAL, find(result, "self_alias").status(), "an alias of itself");
    }

    @Test
    void aModBlockedByTheLinkCheckBlocksItsDependents() {
        List<String> checked = new ArrayList<>();
        CrossLoaderModResolver.Result result = new CrossLoaderModResolver()
                .resolve(
                        List.of(
                                mod("stale", "1.0.0", List.of()),
                                mod("user", "1.0.0", List.of(), requires("stale", "*"))),
                        Map.of(),
                        List.of(Path.of("native.jar")),
                        "26.2",
                        "25",
                        (mod, others) -> {
                            checked.add(mod.assessment().modId() + " with " + others);
                            return mod.assessment().modId().equals("stale")
                                    ? List.of(new CompatibilityFinding("POLY-COMPAT-112", true, "link check", "gone"))
                                    : List.of(new CompatibilityFinding("POLY-COMPAT-113", false, "link check", "note"));
                        });

        assertEquals(List.of(), result.loadable());
        assertTrue(details(find(result, "user"), "POLY-COMPAT-114").getFirst().contains("[POLY-COMPAT-112]"));
        assertTrue(checked.contains("stale with [native.jar, user.jar]"), checked::toString);
        assertEquals(List.of(), details(find(result, "user"), "POLY-COMPAT-113"), "notes only for mods that load");
    }

    @Test
    void notesOfTheLinkCheckAreKeptForModsThatLoad() {
        CrossLoaderModResolver.Result result = new CrossLoaderModResolver()
                .resolve(
                        List.of(mod("fine", "1.0.0", List.of())),
                        Map.of(),
                        List.of(),
                        "26.2",
                        "25",
                        (mod, others) ->
                                List.of(new CompatibilityFinding("POLY-COMPAT-113", false, "link check", "optional")));

        assertEquals(1, result.loadable().size());
        assertEquals(List.of("optional"), details(find(result, "fine"), "POLY-COMPAT-113"));
    }

    // ---- Quilt relationships (ADR-0027 decision 7) ----

    /** A bridge with Quilt's built-in IDs, nested metadata and a numeric version order (1.10.0 after 1.9.0). */
    private static final ModLoaderBridge QUILT_BRIDGE = new ModLoaderBridge() {
        @Override
        public String loaderName() {
            return "Quilt";
        }

        @Override
        public String metadataPath() {
            return "quilt.mod.json";
        }

        @Override
        public List<String> nestedMetadataPaths() {
            return List.of("quilt.mod.json", "fabric.mod.json");
        }

        @Override
        public BridgeAssessment assess(String metadata, GameSide side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) {}

        @Override
        public Map<String, String> builtInMods() {
            return Map.of("quilt_loader", "0.31.0-beta.4");
        }

        @Override
        public int compareVersions(String first, String second) {
            String[] a = first.split("\\.");
            String[] b = second.split("\\.");
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                int order = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
                if (order != 0) {
                    return order;
                }
            }
            return Integer.compare(a.length, b.length);
        }
    };

    private static ForeignMod quilt(String id, String version, BridgedDependency... dependencies) {
        return new ForeignMod(
                Path.of(id + ".jar"),
                QUILT_BRIDGE,
                BridgeAssessment.of(
                        "Quilt",
                        id,
                        version,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(dependencies),
                        CompatibilityStatus.EXPERIMENTAL));
    }

    private static ForeignMod nestedIn(ForeignMod container, ForeignMod mod) {
        return new ForeignMod(
                Path.of(container.assessment().modId() + "-" + mod.assessment().modId() + ".jar"),
                mod.bridge(),
                mod.assessment(),
                container.jar());
    }

    private static BridgedDependency ifPresent(String id, String exactOrStar) {
        BridgedDependency required = requires(id, exactOrStar);
        return new BridgedDependency(
                id, BridgedDependency.Kind.IF_PRESENT, "optional " + exactOrStar, required.accepts());
    }

    private static BridgedDependency breaks(String id) {
        return of(BridgedDependency.Kind.BREAKS, id);
    }

    @Test
    void anIfPresentDependencyIsCheckedOnlyWhenTheModIsInstalled() {
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        quilt("absent_ok", "1.0.0", ifPresent("nobody", "1.0.0")),
                        quilt("right", "1.0.0", ifPresent("lib", "2.0.0")),
                        quilt("wrong", "1.0.0", ifPresent("lib", "1.0.0")),
                        mod("lib", "2.0.0", List.of())),
                Map.of());

        assertEquals(
                List.of(
                        "absent_ok@1.0.0=EXPERIMENTAL[]",
                        "right@1.0.0=EXPERIMENTAL[]",
                        "wrong@1.0.0=BLOCKED[POLY-COMPAT-114]",
                        "lib@2.0.0=EXPERIMENTAL[]"),
                statuses(result));
        assertTrue(details(find(result, "wrong"), "POLY-COMPAT-114").getFirst().contains("2.0.0 is installed"));
    }

    @Test
    void anIfPresentDependencyOnAMatchingModThatIsNotLoadedBlocks() {
        CrossLoaderModResolver.Result result =
                resolve(List.of(quilt("user", "1.0.0", ifPresent("lib", "*")), blocked("lib")), Map.of());

        assertEquals(List.of("POLY-COMPAT-114"), find(result, "user").blockingCodes());
    }

    @Test
    void aRequiredGroupIsMetByAnyMemberAndABreaksGroupBreaksOnlyWhenAllMatch() {
        BridgedDependency anyMet = BridgedDependency.group(
                BridgedDependency.Kind.REQUIRED,
                "any of [absent_a, lib]",
                List.of(requires("absent_a", "*"), requires("lib", "*")));
        BridgedDependency anyUnmet = BridgedDependency.group(
                BridgedDependency.Kind.REQUIRED,
                "any of [absent_a, absent_b]",
                List.of(requires("absent_a", "*"), requires("absent_b", "*")));
        BridgedDependency allPresent = BridgedDependency.group(
                BridgedDependency.Kind.BREAKS, "all of [lib, other]", List.of(breaks("lib"), breaks("other")));
        BridgedDependency onePresent = BridgedDependency.group(
                BridgedDependency.Kind.BREAKS, "all of [lib, absent_c]", List.of(breaks("lib"), breaks("absent_c")));
        BridgedDependency nested = BridgedDependency.group(
                BridgedDependency.Kind.REQUIRED,
                "any of [[absent_a], [lib]]",
                List.of(
                        BridgedDependency.group(
                                BridgedDependency.Kind.REQUIRED,
                                "any of [absent_a]",
                                List.of(requires("absent_a", "*"))),
                        BridgedDependency.group(
                                BridgedDependency.Kind.REQUIRED, "any of [lib]", List.of(requires("lib", "*")))));
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        quilt("any_met", "1.0.0", anyMet),
                        quilt("any_unmet", "1.0.0", anyUnmet),
                        quilt("all_present", "1.0.0", allPresent),
                        quilt("one_present", "1.0.0", onePresent),
                        quilt("nested_any", "1.0.0", nested),
                        mod("lib", "1.0.0", List.of()),
                        mod("other", "1.0.0", List.of())),
                Map.of());

        assertEquals(
                List.of(
                        "any_met@1.0.0=EXPERIMENTAL[]",
                        "any_unmet@1.0.0=BLOCKED[POLY-COMPAT-114]",
                        "all_present@1.0.0=BLOCKED[POLY-COMPAT-115]",
                        "one_present@1.0.0=EXPERIMENTAL[]",
                        "nested_any@1.0.0=EXPERIMENTAL[]",
                        "lib@1.0.0=EXPERIMENTAL[]",
                        "other@1.0.0=EXPERIMENTAL[]"),
                statuses(result));
        String why = details(find(result, "any_unmet"), "POLY-COMPAT-114").getFirst();
        assertTrue(why.contains("any of [absent_a, absent_b]"), why);
        assertTrue(why.contains("'absent_b'"), why);
    }

    @Test
    void aRelationshipDoesNotApplyWhenAnUnlessConditionIsMet() {
        BridgedDependency needsAbsentUnlessLib = requires("absent", "*").withUnless(List.of(requires("lib", "*")));
        BridgedDependency needsAbsentUnlessGone = requires("absent", "*").withUnless(List.of(requires("gone", "*")));
        BridgedDependency breaksLibUnlessLib = breaks("lib").withUnless(List.of(requires("lib", "*")));
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        quilt("unless_met", "1.0.0", needsAbsentUnlessLib),
                        quilt("unless_unmet", "1.0.0", needsAbsentUnlessGone),
                        quilt("breaks_unless", "1.0.0", breaksLibUnlessLib),
                        mod("lib", "1.0.0", List.of())),
                Map.of());

        assertEquals(
                List.of(
                        "unless_met@1.0.0=EXPERIMENTAL[]",
                        "unless_unmet@1.0.0=BLOCKED[POLY-COMPAT-114]",
                        "breaks_unless@1.0.0=EXPERIMENTAL[]",
                        "lib@1.0.0=EXPERIMENTAL[]"),
                statuses(result));
    }

    @Test
    void aDependencyOnAModLeftOutOnlyForTheOtherSideIsSkippedWhenTheBridgeSaysSo() {
        ForeignMod clientOnly = mod(
                "client_lib",
                "1.0.0",
                List.of(),
                List.of(new CompatibilityFinding("POLY-COMPAT-305", true, "environment", "client only")));
        ForeignMod brokenOtherwise = mod(
                "broken_lib",
                "1.0.0",
                List.of(),
                List.of(
                        new CompatibilityFinding("POLY-COMPAT-305", true, "environment", "client only"),
                        new CompatibilityFinding("POLY-COMPAT-300", true, "metadata", "bad")));
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        clientOnly,
                        brokenOtherwise,
                        quilt("skips", "1.0.0", requires("client_lib", "*").withSkipWhenOtherSideOnly()),
                        quilt("fabric_rules", "1.0.0", requires("client_lib", "*")),
                        quilt(
                                "still_needs",
                                "1.0.0",
                                requires("broken_lib", "*").withSkipWhenOtherSideOnly()),
                        quilt("missing", "1.0.0", requires("nowhere", "*").withSkipWhenOtherSideOnly())),
                Map.of());

        assertEquals(List.of(), find(result, "skips").blockingCodes());
        assertEquals(List.of("POLY-COMPAT-114"), find(result, "fabric_rules").blockingCodes());
        assertEquals(List.of("POLY-COMPAT-114"), find(result, "still_needs").blockingCodes());
        assertEquals(List.of("POLY-COMPAT-114"), find(result, "missing").blockingCodes());
    }

    @Test
    void aProvidedIdWithItsOwnVersionIsCheckedAndReportedWithThatVersion() {
        ForeignMod provider = new ForeignMod(
                Path.of("provider.jar"),
                QUILT_BRIDGE,
                BridgeAssessment.of(
                                "Quilt",
                                "provider",
                                "1.0.0",
                                List.of(),
                                List.of(),
                                List.of("api", "plain"),
                                List.of(),
                                CompatibilityStatus.EXPERIMENTAL)
                        .withProvidedVersions(Map.of("api", "3.0.0")));
        CrossLoaderModResolver.Result result = resolve(
                List.of(
                        provider,
                        quilt("api_user", "1.0.0", requires("api", "3.0.0")),
                        quilt("plain_user", "1.0.0", requires("plain", "1.0.0"))),
                Map.of());

        assertEquals(List.of(), find(result, "api_user").blockingCodes());
        assertEquals(List.of(), find(result, "plain_user").blockingCodes());
        assertEquals("3.0.0", result.loadableVersions().get("api"));
        assertEquals("1.0.0", result.loadableVersions().get("plain"));
    }

    private static ForeignMod ifRequired(ForeignMod mod) {
        return new ForeignMod(
                mod.jar(),
                mod.bridge(),
                mod.assessment().withLoadOnlyIfRequired(true),
                mod.containerJar(),
                mod.nestedEntry());
    }

    @Test
    void aNestedModLoadedOnlyIfRequiredLoadsOnlyWhileALoadedModNeedsIt() {
        ForeignMod outer = quilt("outer", "1.0.0", requires("needed", "*"), ifPresent("optional_hit", "1.0.0"));
        ForeignMod needed = ifRequired(nestedIn(outer, quilt("needed", "1.0.0", requires("transitive", "*"))));
        ForeignMod transitive = ifRequired(nestedIn(outer, quilt("transitive", "1.0.0")));
        ForeignMod optionalHit = ifRequired(nestedIn(outer, quilt("optional_hit", "1.0.0")));
        ForeignMod unused = ifRequired(nestedIn(outer, quilt("unused", "1.0.0", requires("absent", "*"))));
        ForeignMod pairA = ifRequired(nestedIn(outer, quilt("pair_a", "1.0.0", requires("pair_b", "*"))));
        ForeignMod pairB = ifRequired(nestedIn(outer, quilt("pair_b", "1.0.0", requires("pair_a", "*"))));
        ForeignMod always = nestedIn(outer, quilt("always", "1.0.0"));
        ForeignMod direct = ifRequired(quilt("direct", "1.0.0"));
        ForeignMod breaker = quilt("breaker", "1.0.0", breaks("unused"));

        CrossLoaderModResolver.Result result = resolve(
                List.of(outer, needed, transitive, optionalHit, unused, pairA, pairB, always, direct, breaker),
                Map.of());

        assertEquals(
                List.of(
                        "outer@1.0.0=EXPERIMENTAL[]",
                        "needed@1.0.0=EXPERIMENTAL[]",
                        "transitive@1.0.0=EXPERIMENTAL[]",
                        "optional_hit@1.0.0=EXPERIMENTAL[]",
                        "unused@1.0.0=BLOCKED[POLY-COMPAT-307]",
                        "pair_a@1.0.0=BLOCKED[POLY-COMPAT-307]",
                        "pair_b@1.0.0=BLOCKED[POLY-COMPAT-307]",
                        "always@1.0.0=EXPERIMENTAL[]",
                        "direct@1.0.0=EXPERIMENTAL[]",
                        "breaker@1.0.0=EXPERIMENTAL[]"),
                statuses(result));
        assertEquals(List.of(), details(find(result, "outer"), "POLY-COMPAT-124"), "307 is not missing work");
    }

    @Test
    void copiesFromDifferentBridgesAreComparedByTheBridgeThatReadsBothMetadataFiles() {
        ForeignMod first = quilt("first", "1.0.0");
        ForeignMod second = quilt("second", "1.0.0");
        ForeignMod fabricCopy = nestedIn(first, mod("lib", "1.9.0", List.of()));
        ForeignMod quiltCopy = nestedIn(second, quilt("lib", "1.10.0"));

        CrossLoaderModResolver.Result result = resolve(List.of(first, second, fabricCopy, quiltCopy), Map.of());

        assertEquals(
                List.of("POLY-COMPAT-122"), result.mods().get(2).assessment().blockingCodes());
        assertEquals(List.of(), result.mods().get(3).assessment().blockingCodes());
        assertEquals("1.10.0", result.loadableVersions().get("lib"));
    }
}

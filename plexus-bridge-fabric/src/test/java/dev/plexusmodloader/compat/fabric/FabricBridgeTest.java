package dev.plexusmodloader.compat.fabric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeAssessment;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import dev.plexusmodloader.compat.bridge.BridgedAccessWidening;
import dev.plexusmodloader.compat.bridge.BridgedDependency;
import dev.plexusmodloader.compat.bridge.BridgedEntrypoint;
import dev.plexusmodloader.compat.bridge.BridgedInterfaceInjection;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import dev.plexusmodloader.compat.bridge.CompatibilityStatus;
import dev.plexusmodloader.compat.bridge.ModJarContents;
import dev.plexusmodloader.compat.fabric.loader.FabricFamilyMod;
import dev.plexusmodloader.compat.fabric.mixin.FabricMixinBootstrap;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.FabricUtil;

class FabricBridgeTest {

    private final FabricBridge bridge = new FabricBridge();

    private static BridgedDependency dependency(BridgeAssessment assessment, String id) {
        return assessment.dependencies().stream()
                .filter(dependency -> dependency.modId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void entrypointOnlyModsAreLoadableExperimentally() {
        BridgeAssessment assessment = bridge.assess("""
                {"schemaVersion": 1, "id": "hello", "version": "1.0.0", "environment": "*",
                 "entrypoints": {"main": ["a.Main"], "client": [{"value": "a.Client"}], "server": ["a.Server"]},
                 "depends": {"fabricloader": ">=0.19", "minecraft": "~26.2", "java": ">=25"}}
                """, GameSide.CLIENT);

        assertEquals(CompatibilityStatus.EXPERIMENTAL, assessment.status());
        assertEquals(
                List.of(new BridgedEntrypoint("common", "a.Main"), new BridgedEntrypoint("client", "a.Client")),
                assessment.entrypoints());
    }

    @Test
    void fabricApiLikeModsAreBlockedWithOneFindingPerFeature() {
        BridgeAssessment assessment = bridge.assess("""
                {"schemaVersion": 1, "id": "fabric-api", "version": "0.161.0+26.2",
                 "mixins": ["a.mixins.json", "b.mixins.json"], "accessWidener": "api.classtweaker",
                 "jars": [{"file": "META-INF/jars/x.jar"}],
                 "entrypoints": {"main": ["a.Main"], "preLaunch": ["a.Pre"], "modmenu": ["a.Menu"]},
                 "depends": {"fabricloader": "*", "fabric-api-base": "*"}}
                """, GameSide.CLIENT);

        assertEquals(CompatibilityStatus.BLOCKED, assessment.status());
        assertEquals(
                // The class tweaker cannot be read from metadata alone and blocks (125). Mixin configurations that
                // cannot be read are skipped by Mixin, as under Fabric (136); preLaunch runs (PLEXUS-COMPAT-101, 102
                // and
                // 105 are retired).
                List.of("PLEXUS-COMPAT-107", "PLEXUS-COMPAT-125", "PLEXUS-COMPAT-131", "PLEXUS-COMPAT-136"),
                assessment.findings().stream()
                        .map(CompatibilityFinding::code)
                        .sorted()
                        .toList());
        assertEquals(List.of("META-INF/jars/x.jar"), assessment.nestedJars());
    }

    /** Nested jars no longer block by themselves (PLEXUS-COMPAT-103 is retired); the runtime extracts them. */
    @Test
    void nestedJarsAreReportedForTheRuntimeToExtract() {
        BridgeAssessment assessment = bridge.assess("""
                {"schemaVersion": 1, "id": "bundle", "version": "1.0.0",
                 "jars": [{"file": "META-INF/jars/a.jar"}, {"file": "META-INF/jars/b.jar"}]}
                """, GameSide.CLIENT);

        assertEquals(CompatibilityStatus.EXPERIMENTAL, assessment.status());
        assertEquals(List.of("META-INF/jars/a.jar", "META-INF/jars/b.jar"), assessment.nestedJars());
        assertEquals(
                CompatibilityStatus.BLOCKED,
                bridge.assess("{\"schemaVersion\": 1, \"id\": \"a\", \"jars\": [\"x.jar\"]}", GameSide.CLIENT)
                        .status(),
                "an entry without \"file\" cannot be followed");
    }

    @Test
    void versionsAreOrderedLikeFabricLoader() {
        assertTrue(bridge.compareVersions("1.10.0", "1.9.3") > 0);
        assertTrue(bridge.compareVersions("2.0.0-beta.1", "2.0.0") < 0);
        assertEquals(0, bridge.compareVersions("1.0.0+build.5", "1.0.0+build.9"));
    }

    /** Depending on other mods no longer blocks by itself; the runtime checks the dependencies (PLEXUS-COMPAT-114). */
    @Test
    void dependenciesAreReportedForTheRuntimeToCheck() {
        BridgeAssessment assessment = bridge.assess("""
                {"schemaVersion": 1, "id": "addon", "version": "2.0.0",
                 "provides": ["addon_legacy"],
                 "depends": {"base": ">=1.2.0 <2", "minecraft": ["~26.1", "~26.2"]},
                 "recommends": {"extras": "*"}, "breaks": {"old_base": "<1.0.0"}, "conflicts": {"noisy": "*"}}
                """, GameSide.CLIENT);

        assertEquals(CompatibilityStatus.EXPERIMENTAL, assessment.status());
        assertEquals(List.of("addon", "addon_legacy"), assessment.allIds());
        BridgedDependency base = dependency(assessment, "base");
        assertEquals(BridgedDependency.Kind.REQUIRED, base.kind());
        assertTrue(base.accepts().test("1.5.3"));
        assertFalse(base.accepts().test("2.0.0"));
        assertFalse(base.accepts().test("1.1.9"));
        BridgedDependency minecraft = dependency(assessment, "minecraft");
        assertTrue(minecraft.accepts().test("26.2"), "an array of predicates matches if any matches");
        assertFalse(minecraft.accepts().test("26.3"));
        assertEquals(
                BridgedDependency.Kind.OPTIONAL,
                dependency(assessment, "extras").kind());
        assertEquals(
                BridgedDependency.Kind.BREAKS,
                dependency(assessment, "old_base").kind());
        assertEquals(
                BridgedDependency.Kind.CONFLICTS,
                dependency(assessment, "noisy").kind());
    }

    @Test
    void fabricWildcardsAndShortVersionsFollowFabricLoader() {
        BridgeAssessment assessment = bridge.assess("""
                {"schemaVersion": 1, "id": "a", "version": "1",
                 "depends": {"x": "1.21.x", "y": ">=26.2-", "z": "^0.4.0"}}
                """, GameSide.CLIENT);

        assertFalse(dependency(assessment, "x").accepts().test("26.2"));
        assertTrue(dependency(assessment, "y").accepts().test("26.2"));
        assertTrue(dependency(assessment, "z").accepts().test("0.9.1"));
        assertFalse(dependency(assessment, "z").accepts().test("1.0.0"));
    }

    @Test
    void anUnreadableRequirementBlocks() {
        BridgeAssessment assessment = bridge.assess(
                "{\"schemaVersion\": 1, \"id\": \"a\", \"version\": \"1\", \"depends\": {\"b\": \">=1.x\"}}",
                GameSide.CLIENT);

        assertEquals(CompatibilityStatus.BLOCKED, assessment.status());
        assertEquals("PLEXUS-COMPAT-100", assessment.findings().getFirst().code());
    }

    @Test
    void clientOnlyModsAreBlockedOnDedicatedServers() {
        BridgeAssessment assessment = bridge.assess(
                "{\"schemaVersion\": 1, \"id\": \"hud\", \"version\": \"1\", \"environment\": \"client\"}",
                GameSide.DEDICATED_SERVER);

        assertEquals(CompatibilityStatus.BLOCKED, assessment.status());
        assertEquals("PLEXUS-COMPAT-108", assessment.findings().getFirst().code());
    }

    @Test
    void unreadableMetadataIsReportedInsteadOfThrown() {
        assertEquals(
                CompatibilityStatus.BLOCKED,
                bridge.assess("{ broken", GameSide.CLIENT).status());
    }

    /** Values of the wrong JSON type used to throw out of the bridge and stop the launch. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "42",
                "{\"schemaVersion\": \"one\", \"id\": \"a\"}",
                "{\"schemaVersion\": 1, \"id\": 7}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"accessWidener\": {\"x\": 1}}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"entrypoints\": [\"a.Main\"]}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"entrypoints\": {\"main\": \"a.Main\"}}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"entrypoints\": {\"main\": [{\"adapter\": \"default\"}]}}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"entrypoints\": {\"main\": [3]}}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"depends\": [\"b\"]}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"depends\": {\"b\": 3}}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"depends\": {\"b\": [\">=1\", null]}}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"provides\": \"b\", \"mixins\": null, \"jars\": {}}",
                "{\"schemaVersion\": 1, \"id\": \"a\", \"environment\": [\"client\"]}"
            })
    void malformedMetadataNeverThrows(String metadata) {
        for (GameSide side : GameSide.values()) {
            BridgeAssessment assessment = assertDoesNotThrow(() -> bridge.assess(metadata, side), metadata);
            assertTrue(assessment.status() != null, metadata);
        }
    }

    private static final String CLASS_TWEAKER_MOD = """
            {"schemaVersion": 1, "id": "tweaked", "version": "1.0.0", "accessWidener": "tweaked.classtweaker",
             "entrypoints": {"main": ["a.Main"]}}
            """;

    private static ModJarContents jarWith(String path, String text) {
        Map<String, byte[]> files = Map.of(path, text.getBytes(StandardCharsets.UTF_8));
        return entry -> Optional.ofNullable(files.get(entry));
    }

    private static List<String> codes(BridgeAssessment assessment) {
        return assessment.findings().stream().map(CompatibilityFinding::code).toList();
    }

    @Test
    void aClassTweakerWithAccessLinesBecomesAccessWidenings() {
        BridgeAssessment assessment =
                bridge.assess(CLASS_TWEAKER_MOD, GameSide.DEDICATED_SERVER, jarWith("tweaked.classtweaker", """
                        classTweaker v1 official
                        accessible field net/minecraft/util/Mth SIN [F
                        transitive-accessible method net/minecraft/SharedConstants debugFlag (Ljava/lang/String;)Z
                        """));

        assertEquals(CompatibilityStatus.EXPERIMENTAL, assessment.status());
        assertEquals(List.of("PLEXUS-COMPAT-129"), codes(assessment));
        assertEquals(
                "PLEXUS-COMPAT-129 [limited] class tweaker: tweaked.classtweaker (classTweaker, official): 2 line(s), 1"
                        + " of them transitive, become 4 access widening(s); their targets are checked against this"
                        + " game before the mod loads",
                assessment.findings().getFirst().toString());
        assertEquals(
                List.of(
                        "field net/minecraft/util/Mth.SIN:[F",
                        "class net/minecraft/util/Mth",
                        "method net/minecraft/SharedConstants.debugFlag(Ljava/lang/String;)Z",
                        "class net/minecraft/SharedConstants"),
                assessment.accessWidenings().stream()
                        .map(BridgedAccessWidening::describeTarget)
                        .toList());
        assertEquals(
                Set.of(BridgedAccessWidening.Access.ACCESSIBLE),
                assessment.accessWidenings().getFirst().access());
        assertEquals(
                "tweaked.classtweaker:3", assessment.accessWidenings().get(2).source());
    }

    @Test
    void aMissingOrUnreadableClassTweakerBlocks() {
        BridgeAssessment missing = bridge.assess(CLASS_TWEAKER_MOD, GameSide.CLIENT, ModJarContents.NONE);
        assertEquals(CompatibilityStatus.BLOCKED, missing.status());
        assertEquals(
                "PLEXUS-COMPAT-125 [blocking] class tweaker: tweaked.classtweaker is named in fabric.mod.json but not in"
                        + " the jar",
                missing.findings().getFirst().toString());

        BridgeAssessment unreadable = bridge.assess(CLASS_TWEAKER_MOD, GameSide.CLIENT, path -> {
            throw new UncheckedIOException(new IOException("truncated zip entry"));
        });
        assertEquals(List.of("PLEXUS-COMPAT-125"), codes(unreadable));
        assertTrue(unreadable.findings().getFirst().detail().contains("truncated zip entry"));
        assertEquals(List.of(), unreadable.accessWidenings());
    }

    @Test
    void malformedLinesBlockWithTheirLineNumbers() {
        BridgeAssessment assessment = bridge.assess(
                CLASS_TWEAKER_MOD,
                GameSide.CLIENT,
                jarWith(
                        "tweaked.classtweaker",
                        "accessWidener v2 official\naccessible class net.minecraft.A\nmutable method a/B c ()V\n"));

        assertEquals(CompatibilityStatus.BLOCKED, assessment.status());
        assertEquals(
                "PLEXUS-COMPAT-125 [blocking] class tweaker: 2 line(s) Fabric Loader would refuse:"
                        + " tweaked.classtweaker:2 Class-names must be specified as a/b/C, not a.b.C, but found:"
                        + " net.minecraft.A; tweaked.classtweaker:3 Methods cannot be made mutable",
                assessment.findings().getFirst().toString());
        assertEquals(List.of(), assessment.accessWidenings());
    }

    @Test
    void namespacesThatNeedMappingsBlock() {
        BridgeAssessment assessment = bridge.assess(
                CLASS_TWEAKER_MOD,
                GameSide.CLIENT,
                jarWith(
                        "tweaked.classtweaker",
                        "accessWidener v2 intermediary\naccessible class net/minecraft/class_1\n"));

        assertEquals(List.of("PLEXUS-COMPAT-126"), codes(assessment));
        assertTrue(assessment.findings().getFirst().detail().contains("'intermediary' namespace"));
        assertEquals(List.of(), assessment.accessWidenings());
    }

    /** Interface injection and enum extension are applied by the bridge (ADR-0020); PLEXUS-COMPAT-127 is retired. */
    @Test
    void interfaceInjectionAndEnumExtensionAreReported() {
        BridgeAssessment injecting =
                bridge.assess(CLASS_TWEAKER_MOD, GameSide.CLIENT, jarWith("tweaked.classtweaker", """
                        classTweaker v1 official
                        accessible class net/minecraft/A
                        transitive-inject-interface net/minecraft/A net/example/Extra<Lnet/minecraft/B;>
                        """));

        assertEquals(CompatibilityStatus.EXPERIMENTAL, injecting.status());
        assertEquals(
                List.of(new BridgedInterfaceInjection(
                        "tweaked.classtweaker:3", "net/minecraft/A", "net/example/Extra")),
                injecting.injectedInterfaces());
        assertTrue(injecting.findings().getFirst().toString().contains("and 1 interface injection(s)"));

        BridgeAssessment extending =
                bridge.assess(CLASS_TWEAKER_MOD, GameSide.CLIENT, jarWith("tweaked.classtweaker", """
                        classTweaker v2 official
                        extend-enum net/minecraft/C NEW_CONSTANT
                        """));
        assertEquals(CompatibilityStatus.EXPERIMENTAL, extending.status());
        assertTrue(
                extending
                        .findings()
                        .getFirst()
                        .toString()
                        .endsWith(", and 1 enum constant(s) are added; their targets"
                                + " are checked against this game before the mod loads"),
                extending.findings().getFirst().toString());
        assertEquals(List.of(), extending.injectedInterfaces());
    }

    @Test
    void theBundledFabricLoaderVersionIsKnown() {
        assertTrue(
                FabricBridge.FABRIC_LOADER_VERSION.matches("\\d+\\.\\d+\\.\\d+"), FabricBridge.FABRIC_LOADER_VERSION);
        assertEquals(FabricBridge.FABRIC_LOADER_VERSION, bridge.builtInMods().get("fabricloader"));
    }

    @Test
    void aFabricModThatRequiresQuiltLoaderFindsIt() {
        // Quilt builds that ship fabric.mod.json and require quilt_loader (Better Shulker Boxes, MTA NYC Subway Signs,
        // Inventory Search Storage Indexer in the Quilt corpus): Quilt Loader loads them, and Plexus Mod Loader
        // provides
        // Quilt Loader's API, so the requirement is met (Quilt full support Q10).
        assertEquals(BundledVersions.QUILT_LOADER, bridge.builtInMods().get("quilt_loader"));
    }

    @Test
    void aFabricModThatBreaksQuiltLoaderStillRunsAsUnderFabricLoader() {
        // Plexus Mod Loader runs a Fabric mod as Fabric Loader does, where quilt_loader is never present: "breaks" or
        // "conflicts" on quilt_loader must not leave it out now that quilt_loader is built in.
        BridgeAssessment assessment = bridge.assess("""
                {"schemaVersion": 1, "id": "not_on_quilt", "version": "1.0.0",
                 "depends": {"quilt_loader": ">=0.30.0"},
                 "breaks": {"quilt_loader": "*", "other": "*"}, "conflicts": {"quilt_loader": "*"}}
                """, GameSide.CLIENT);

        assertEquals(
                List.of("quilt_loader REQUIRED", "other BREAKS"),
                assessment.dependencies().stream()
                        .map(dependency -> dependency.modId() + " " + dependency.kind())
                        .toList());
    }

    private static final String MIXIN_MOD = """
            {"schemaVersion": 1, "id": "mixer", "version": "1.0.0",
             "mixins": ["mixer.mixins.json", {"config": "mixer.client.mixins.json", "environment": "client"}]}
            """;

    private static ModJarContents jarWithFiles(Map<String, String> files) {
        return path -> Optional.ofNullable(files.get(path)).map(text -> text.getBytes(StandardCharsets.UTF_8));
    }

    /** Mixin configurations no longer block (PLEXUS-COMPAT-101 is retired): Mixin applies them (ADR-0020). */
    @Test
    void mixinConfigurationsAreAppliedByMixinPerSide() {
        ModJarContents jar = jarWithFiles(Map.of(
                "mixer.mixins.json", "{\"package\": \"a.mixin\", \"minVersion\": \"0.8\", \"mixins\": [\"A\"]}",
                "mixer.client.mixins.json", "{\"package\": \"a.mixin.client\", \"client\": [\"B\"]}"));

        BridgeAssessment client = bridge.assess(MIXIN_MOD, GameSide.CLIENT, jar);
        BridgeAssessment server = bridge.assess(MIXIN_MOD, GameSide.DEDICATED_SERVER, jar);

        assertEquals(CompatibilityStatus.EXPERIMENTAL, client.status());
        assertEquals(CompatibilityStatus.EXPERIMENTAL, server.status());
        CompatibilityFinding clientNote = client.findings().getFirst();
        assertEquals("PLEXUS-COMPAT-131", clientNote.code());
        assertFalse(clientNote.blocking());
        assertTrue(
                clientNote
                        .toString()
                        .contains("2 Mixin configuration(s), 2 for CLIENT (mixer.mixins.json; "
                                + "mixer.client.mixins.json)"),
                clientNote.toString());
        assertTrue(
                server.findings()
                        .getFirst()
                        .toString()
                        .contains("2 Mixin configuration(s), 1 for DEDICATED_SERVER (mixer.mixins.json)"),
                server.findings().getFirst().toString());
    }

    /** Mixin stops the game only for a required configuration that needs a newer Mixin; it skips other bad ones. */
    @Test
    void onlyConfigurationsMixinWouldStopTheGameForBlockTheMod() {
        ModJarContents skipped = jarWithFiles(Map.of(
                "mixer.mixins.json", "{\"package\": \"a.mixin\", \"minVersion\": \"0.9.1\"}",
                "mixer.client.mixins.json", "[\"not\", \"an\", \"object\"]"));
        ModJarContents fatal = jarWithFiles(Map.of(
                "mixer.mixins.json", "{\"required\": true, \"package\": \"a.mixin\", \"minVersion\": \"0.9.1\"}",
                "mixer.client.mixins.json", "{\"package\": \"a.mixin.client\"}"));

        BridgeAssessment loads = bridge.assess(MIXIN_MOD, GameSide.CLIENT, skipped);
        BridgeAssessment blocked = bridge.assess(MIXIN_MOD, GameSide.CLIENT, fatal);

        assertEquals(CompatibilityStatus.EXPERIMENTAL, loads.status());
        CompatibilityFinding skip = loads.findings().stream()
                .filter(finding -> finding.code().equals("PLEXUS-COMPAT-136"))
                .findFirst()
                .orElseThrow();
        assertFalse(skip.blocking());
        assertTrue(
                skip.toString().contains("mixer.mixins.json needs Mixin 0.9.1; Plexus Mod Loader has 0.8.7"),
                skip.toString());
        assertTrue(skip.toString().contains("mixer.client.mixins.json is not a JSON object"), skip.toString());
        assertEquals(CompatibilityStatus.BLOCKED, blocked.status());
        assertEquals(
                "PLEXUS-COMPAT-132 [blocking] mixins: 1 required Mixin configuration(s) Mixin would stop the game for:"
                        + " mixer.mixins.json needs Mixin 0.9.1; Plexus Mod Loader has 0.8.7",
                blocked.findings().getFirst().toString());
        assertTrue(bridge.assess(MIXIN_MOD, GameSide.CLIENT, ModJarContents.NONE).findings().stream()
                .anyMatch(finding -> finding.toString()
                        .contains("mixer.mixins.json is named in fabric.mod.json but" + " not in the jar")));
    }

    /** Fabric runs preLaunch after Mixin is ready; the bridge declares it so the link check sees its class. */
    @Test
    void preLaunchAndMemberEntrypointsAreAccepted() {
        BridgeAssessment assessment = bridge.assess("""
                {"schemaVersion": 1, "id": "early", "version": "1.0.0",
                 "entrypoints": {"preLaunch": ["a.Early"], "main": ["a.Main::init"]}}
                """, GameSide.DEDICATED_SERVER);

        assertEquals(CompatibilityStatus.EXPERIMENTAL, assessment.status());
        assertEquals(
                List.of(new BridgedEntrypoint("preLaunch", "a.Early"), new BridgedEntrypoint("common", "a.Main::init")),
                assessment.entrypoints());
    }

    @Test
    void unreadableMixinEntriesAreMetadataErrors() {
        BridgeAssessment assessment = bridge.assess("""
                {"schemaVersion": 1, "id": "odd", "version": "1.0.0",
                 "mixins": [42, {"config": "a.json", "environment": "both"}]}
                """, GameSide.CLIENT, ModJarContents.NONE);

        assertEquals(CompatibilityStatus.BLOCKED, assessment.status());
        assertEquals(
                2,
                assessment.findings().stream()
                        .filter(finding -> finding.code().equals("PLEXUS-COMPAT-100"))
                        .count());
    }

    /** A launch with a Fabric mod and a Quilt mod, each with Mixin configurations and class tweakers. */
    private static BridgeLaunchContext fabricAndQuilt(Path directory, GameSide side, String quiltMixin)
            throws IOException {
        Path fabric = jar(
                directory.resolve("fabric.jar"),
                Map.of(
                        "fabric.mod.json",
                        """
                {"schemaVersion": 1, "id": "fabric_mod", "version": "1.0.0",
                 "depends": {"fabricloader": ">=0.16.0"},
                 "mixins": ["fabric_mod.mixins.json", {"config": "fabric_mod.client.mixins.json", "environment": "client"}],
                 "accessWidener": "fabric_mod.classtweaker"}
                """,
                        "fabric_mod.classtweaker",
                        "classTweaker v1 official\ninject-interface net/minecraft/A a/FabricApi\n"));
        Path quilt = jar(
                directory.resolve("quilt.jar"),
                Map.of(
                        "quilt.mod.json",
                        """
                {"schema_version": 1,
                 "quilt_loader": {"group": "dev.example", "id": "quilt_mod", "version": "1.0.0",
                   "intermediate_mappings": "net.fabricmc:intermediary"},
                 "mixin": [%s, {"config": "quilt_mod.client.mixins.json", "environment": "client"},
                           {"config": "quilt_mod.server.mixins.json", "environment": "dedicated_server"}],
                 "access_widener": ["quilt_mod.accesswidener", "quilt_mod_extra.classtweaker"]}
                """.formatted(quiltMixin),
                        "quilt_mod.accesswidener",
                        "classTweaker v1 official\ninject-interface net/minecraft/B a/QuiltApi\n",
                        "quilt_mod_extra.classtweaker",
                        "classTweaker v1 official\ninject-interface net/minecraft/C a/QuiltExtra\n"));
        return new BridgeLaunchContext(
                side,
                "26.2",
                directory.resolve("game.jar"),
                directory,
                List.of(),
                FabricBridgeTest.class.getClassLoader(),
                List.of(
                        new BridgeLaunchContext.LoadedMod(
                                "Fabric", "fabric_mod", "1.0.0", "fabric_mod", List.of(), fabric, null, null),
                        new BridgeLaunchContext.LoadedMod(
                                "Quilt", "quilt_mod", "1.0.0", "quilt_mod", List.of(), quilt, null, null)));
    }

    private static Path jar(Path path, Map<String, String> entries) throws IOException {
        try (OutputStream file = Files.newOutputStream(path);
                JarOutputStream jar = new JarOutputStream(file)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return path;
    }

    /**
     * Quilt mods' top-level {@code mixin} configurations join the Fabric mods' in the one Mixin, for their side
     * ({@code dedicated_server} is the server), with Quilt Loader's compatibility level 0.10.0 (ADR-0027 decision 9).
     */
    @Test
    void quiltModsMixinConfigurationsShareFabricsMixin(@TempDir Path directory) throws IOException {
        BridgeLaunchContext server = fabricAndQuilt(directory, GameSide.DEDICATED_SERVER, "\"quilt_mod.mixins.json\"");

        Map<String, FabricFamilyMod> configs = FabricMixinBootstrap.configurations(server);

        assertEquals(
                List.of("fabric_mod.mixins.json", "quilt_mod.mixins.json", "quilt_mod.server.mixins.json"),
                List.copyOf(configs.keySet()));
        assertTrue(configs.get("quilt_mod.mixins.json").isQuilt());
        assertEquals(
                FabricUtil.COMPATIBILITY_0_10_0,
                FabricMixinBootstrap.compatibilityLevel(configs.get("quilt_mod.mixins.json")));
        assertEquals(
                FabricUtil.COMPATIBILITY_0_14_0,
                FabricMixinBootstrap.compatibilityLevel(configs.get("fabric_mod.mixins.json")),
                "a Fabric mod's level still comes from the lowest Fabric Loader it accepts");
        assertEquals(
                List.of(
                        "fabric_mod.mixins.json",
                        "fabric_mod.client.mixins.json",
                        "quilt_mod.mixins.json",
                        "quilt_mod.client.mixins.json"),
                List.copyOf(FabricMixinBootstrap.configurations(
                                fabricAndQuilt(directory, GameSide.CLIENT, "\"quilt_mod.mixins.json\""))
                        .keySet()));
    }

    /** One name for two mods is refused, as by Fabric Loader (Quilt Loader would prefix it with the mod's ID). */
    @Test
    void aMixinConfigurationNameOfAFabricAndAQuiltModIsRefused(@TempDir Path directory) throws IOException {
        BridgeLaunchContext context =
                fabricAndQuilt(directory, GameSide.DEDICATED_SERVER, "\"fabric_mod.mixins.json\"");

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> FabricMixinBootstrap.configurations(context));
        assertTrue(refused.getMessage().startsWith("PLEXUS-COMPAT-134"), refused.getMessage());
    }

    /** Every access widener of a Quilt mod is read, after the Fabric mod's one, in load order. */
    @Test
    void everyClassTweakerOfAQuiltModIsApplied(@TempDir Path directory) throws IOException {
        BridgeLaunchContext context = fabricAndQuilt(directory, GameSide.DEDICATED_SERVER, "\"quilt_mod.mixins.json\"");

        List<ClassTweakerFile> files = InterfaceInjector.classTweakers(context);

        assertEquals(
                List.of("net/minecraft/A", "net/minecraft/B", "net/minecraft/C"),
                files.stream()
                        .flatMap(file -> file.injectedInterfaces().stream())
                        .map(ClassTweakerFile.InjectedInterface::target)
                        .toList());
    }

    /** Fabric Loader bundles MixinExtras; Plexus Mod Loader provides it, so mods may require it. */
    @Test
    void mixinExtrasIsBuiltInAndShared() {
        assertTrue(bridge.builtInMods().containsKey("mixinextras"));
        assertTrue(bridge.unavailableBuiltInMods().isEmpty());
        assertTrue(bridge.sharedLibraryPackages().contains("com.llamalad7.mixinextras."));
        assertTrue(bridge.sharedLibraryPackages().contains("org.spongepowered."));
    }
}

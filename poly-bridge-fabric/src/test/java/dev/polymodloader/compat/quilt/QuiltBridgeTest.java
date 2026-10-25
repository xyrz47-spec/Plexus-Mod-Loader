package dev.polymodloader.compat.quilt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.BridgedDependency;
import dev.polymodloader.compat.bridge.BridgedEntrypoint;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.CompatibilityStatus;
import dev.polymodloader.compat.bridge.ModJarContents;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QuiltBridgeTest {

    private final QuiltBridge bridge = new QuiltBridge();

    /** A file Quilt Loader accepts, with {@code loaderExtra} added to {@code quilt_loader} and {@code rootExtra} to the root. */
    private static String quiltModJson(String loaderExtra, String rootExtra) {
        return "{\"schema_version\": 1, \"quilt_loader\": {\"group\": \"dev.polymodloader.test\", \"id\": \"quilt_hello\","
                + " \"version\": \"1.0.0\", \"intermediate_mappings\": \"net.fabricmc:intermediary\""
                + (loaderExtra.isEmpty() ? "" : ", " + loaderExtra) + "}"
                + (rootExtra.isEmpty() ? "" : ", " + rootExtra) + "}";
    }

    private static String withLoader(String loaderExtra) {
        return quiltModJson(loaderExtra, "");
    }

    private static String withRoot(String rootExtra) {
        return quiltModJson("", rootExtra);
    }

    private static ModJarContents jar(Map<String, String> files) {
        Map<String, byte[]> bytes = new HashMap<>();
        files.forEach((path, text) -> bytes.put(path, text.getBytes(StandardCharsets.UTF_8)));
        return path -> Optional.ofNullable(bytes.get(path));
    }

    private BridgeAssessment assess(String json) {
        return bridge.assess(json, GameSide.DEDICATED_SERVER);
    }

    private BridgeAssessment assess(String json, GameSide side, Map<String, String> files) {
        return bridge.assess(json, side, jar(files));
    }

    private static List<String> codes(BridgeAssessment assessment) {
        return assessment.findings().stream().map(CompatibilityFinding::code).toList();
    }

    private static String detail(BridgeAssessment assessment, String code) {
        return assessment.findings().stream()
                .filter(finding -> finding.code().equals(code))
                .map(CompatibilityFinding::detail)
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " not in " + assessment.findings()));
    }

    /** Asserts that Quilt Loader would refuse the file: exactly one blocking 300 whose detail contains {@code why}. */
    private void refused(String json, String why) {
        BridgeAssessment assessment = assess(json);
        assertEquals(List.of("POLY-COMPAT-300"), codes(assessment), json);
        assertEquals(CompatibilityStatus.BLOCKED, assessment.status(), json);
        assertTrue(detail(assessment, "POLY-COMPAT-300").contains(why), detail(assessment, "POLY-COMPAT-300"));
    }

    private void accepted(String json) {
        BridgeAssessment assessment = assess(json);
        assertEquals(List.of(), assessment.blockingCodes(), json + " " + assessment.findings());
    }

    @Test
    void aMinimalFileIsALoadableQuiltMod() {
        BridgeAssessment assessment = assess(withLoader(""));

        assertEquals("Quilt", assessment.loader());
        assertEquals("quilt_hello", assessment.modId());
        assertEquals("1.0.0", assessment.version());
        assertEquals(CompatibilityStatus.EXPERIMENTAL, assessment.status());
        assertEquals(List.of("POLY-COMPAT-309"), codes(assessment), "intermediary has no effect on 26.x");
        assertTrue(assessment.loadOnlyIfRequired(), "load_type defaults to if_required");
    }

    @Test
    void theRootMustBeAVersionOneObject() {
        refused("{", "not valid JSON");
        refused("[]", "object at the root");
        refused("{} {}", "not valid JSON");
        refused("{\"quilt_loader\": {}}", "No schema_version");
        refused("{\"schema_version\": \"1\", \"quilt_loader\": {}}", "must be a number");
        refused("{\"schema_version\": 0, \"quilt_loader\": {}}", "cannot be 0");
        refused("{\"schema_version\": -1, \"quilt_loader\": {}}", "must not be negative");
        refused("{\"schema_version\": 2, \"quilt_loader\": {}}", "unsupported schema_version 2");
        refused("{\"schema_version\": 1, // comment\n \"quilt_loader\": {}}", "not valid JSON");
        accepted(withLoader(""));
    }

    @Test
    void quiltModJson5BesideItIsRefused() {
        BridgeAssessment assessment = assess(withLoader(""), GameSide.CLIENT, Map.of("quilt.mod.json5", "{}"));

        assertEquals(List.of("POLY-COMPAT-300"), codes(assessment));
        assertTrue(detail(assessment, "POLY-COMPAT-300").contains("quilt.mod.json5"));
        assertEquals("quilt_hello", assessment.modId(), "a refused mod is still named");
    }

    @Test
    void quiltLoaderMustBeAnObject() {
        refused("{\"schema_version\": 1}", "quilt_loader field is required");
        refused("{\"schema_version\": 1, \"quilt_loader\": []}", "quilt_loader field must be an object");
    }

    @Test
    void theIdGroupAndVersionAreChecked() {
        String rest = "\"group\": \"g\", \"version\": \"1\", \"intermediate_mappings\": \"net.fabricmc:intermediary\"";
        refused("{\"schema_version\": 1, \"quilt_loader\": {" + rest + "}}", "id is a required field");
        for (String bad : List.of("Quilt", "a", "1abc", "a".repeat(65), "has space")) {
            refused(
                    "{\"schema_version\": 1, \"quilt_loader\": {\"id\": \"" + bad + "\", " + rest + "}}",
                    "Invalid mod id");
        }
        refused("{\"schema_version\": 1, \"quilt_loader\": {\"id\": 5, " + rest + "}}", "id must be a string");
        accepted("{\"schema_version\": 1, \"quilt_loader\": {\"id\": \"" + "a".repeat(64) + "\", " + rest + "}}");

        String id =
                "\"id\": \"quilt_hello\", \"version\": \"1\", \"intermediate_mappings\": \"net.fabricmc:intermediary\"";
        refused("{\"schema_version\": 1, \"quilt_loader\": {" + id + "}}", "group is a required field");
        refused(
                "{\"schema_version\": 1, \"quilt_loader\": {\"group\": \"bad group!\", " + id + "}}",
                "Invalid mod maven group");

        String noVersion =
                "\"id\": \"quilt_hello\", \"group\": \"g\", \"intermediate_mappings\": \"net.fabricmc:intermediary\"";
        refused("{\"schema_version\": 1, \"quilt_loader\": {" + noVersion + "}}", "version is a required field");
        refused(
                "{\"schema_version\": 1, \"quilt_loader\": {\"version\": 1, " + noVersion + "}}",
                "version must be a string");
    }

    @Test
    void entrypointsAreReportedPerSide() {
        String json = withLoader("""
                "entrypoints": {
                  "main": "a.Main",
                  "client": ["a.Client"],
                  "server": {"adapter": "default", "value": "a.Server"},
                  "pre_launch": "a.QuiltPre",
                  "preLaunch": "a.FabricPre"
                }""");

        List<BridgedEntrypoint> server = assess(json).entrypoints();
        List<BridgedEntrypoint> client = assess(json, GameSide.CLIENT, Map.of()).entrypoints();

        assertEquals(
                List.of(
                        new BridgedEntrypoint("common", "a.Main"),
                        new BridgedEntrypoint("server", "a.Server"),
                        new BridgedEntrypoint("preLaunch", "a.QuiltPre"),
                        new BridgedEntrypoint("preLaunch", "a.FabricPre")),
                server);
        assertEquals(
                List.of(
                        new BridgedEntrypoint("common", "a.Main"),
                        new BridgedEntrypoint("client", "a.Client"),
                        new BridgedEntrypoint("preLaunch", "a.QuiltPre"),
                        new BridgedEntrypoint("preLaunch", "a.FabricPre")),
                client);
    }

    @Test
    void malformedEntrypointsAreRefused() {
        refused(withLoader("\"entrypoints\": []"), "entrypoints must be an object");
        refused(withLoader("\"entrypoints\": {\"main\": 5}"), "must be a string or object");
        refused(withLoader("\"entrypoints\": {\"main\": [true]}"), "must be a string or object");
        refused(withLoader("\"entrypoints\": {\"main\": {\"value\": \"a.B\"}}"), "missing the \"adapter\" field");
        refused(withLoader("\"entrypoints\": {\"main\": {\"adapter\": \"default\"}}"), "missing the \"value\" field");
        refused(withLoader("\"entrypoints\": {\"main\": {\"adapter\": 1, \"value\": \"a.B\"}}"), "adapter field");
    }

    @Test
    void languageAdaptersBlock() {
        BridgeAssessment kotlin =
                assess(withLoader("\"entrypoints\": {\"main\": {\"adapter\": \"kotlin\", \"value\": \"a.B\"}}"));
        BridgeAssessment declared = assess(withLoader("\"language_adapters\": {\"kotlin\": \"a.Adapter\"}"));

        assertEquals(List.of("POLY-COMPAT-302"), kotlin.blockingCodes());
        assertTrue(detail(kotlin, "POLY-COMPAT-302").contains("[kotlin]"));
        assertEquals(List.of("POLY-COMPAT-302"), declared.blockingCodes());
        refused(withLoader("\"language_adapters\": []"), "language_adapters must be an object");
        refused(withLoader("\"language_adapters\": {\"kotlin\": 5}"), "must be a string");
    }

    @Test
    void qslEntrypointsAreNotRunAndOtherKeysAreLeftToOtherMods() {
        BridgeAssessment initOnly = assess(withLoader("\"entrypoints\": {\"init\": \"a.Init\"}"));
        BridgeAssessment initAndMain =
                assess(withLoader("\"entrypoints\": {\"init\": \"a.Init\", \"main\": \"a.Main\"}"));
        BridgeAssessment custom = assess(withLoader("\"entrypoints\": {\"modmenu\": \"a.Menu\"}"));

        assertEquals(List.of(), initOnly.blockingCodes());
        assertTrue(detail(initOnly, "POLY-COMPAT-303").contains("initialization code does not run"));
        assertEquals(List.of(), initOnly.entrypoints());
        assertFalse(detail(initAndMain, "POLY-COMPAT-303").contains("initialization code does not run"));
        assertTrue(codes(custom).contains("POLY-COMPAT-304"));
        assertEquals(List.of(), custom.entrypoints());
        assertFalse(codes(custom).contains("POLY-COMPAT-303"));
    }

    @Test
    void nestedJarsAreReportedAndEntriesThatLeaveTheJarAreSkipped() {
        BridgeAssessment assessment = assess(
                withLoader(
                        "\"jars\": [\"META-INF/jars/a.jar\", \"../b.jar\", \"META-INF/jars/../../c.jar\", \"META-INF/jars/x..y.jar\"]"));

        assertEquals(List.of("META-INF/jars/a.jar", "META-INF/jars/x..y.jar"), assessment.nestedJars());
        assertTrue(detail(assessment, "POLY-COMPAT-309").contains("../b.jar")
                || assessment.findings().stream()
                        .anyMatch(finding -> finding.detail().contains("../b.jar")));
        refused(withLoader("\"jars\": \"META-INF/jars/a.jar\""), "jars must be an array");
        refused(withLoader("\"jars\": [{\"file\": \"a.jar\"}]"), "Entry inside jars must be a string");
    }

    @Test
    void dependenciesAndBreaksBecomeBridgedRelationships() {
        BridgeAssessment assessment = assess(withLoader("""
                "depends": [
                  {"id": "quilt_loader", "versions": ">=0.30.0"},
                  {"id": "minecraft", "versions": {"any": ["26.1.x", "26.2.x"]}},
                  {"id": "fabric_lib", "versions": "1.0.0", "optional": true},
                  {"id": "absent_required", "unless": "quilt_loader"},
                  [{"id": "absent_a"}, {"id": "quilt_loader"}],
                  "quilt_resource_loader"
                ],
                "breaks": [[{"id": "fabric_lib"}, {"id": "absent_b"}]]"""));

        List<BridgedDependency> dependencies = assessment.dependencies();
        assertEquals(7, dependencies.size());
        assertTrue(dependencies.get(0).accepts().test("0.31.0-beta.4"));
        assertTrue(dependencies.get(1).accepts().test("26.2"));
        assertFalse(dependencies.get(1).accepts().test("26.3"));
        assertEquals(BridgedDependency.Kind.IF_PRESENT, dependencies.get(2).kind());
        assertTrue(dependencies.get(2).accepts().test("1.2.0"));
        assertEquals("quilt_loader", dependencies.get(3).unless().getFirst().modId());
        assertTrue(dependencies.get(4).isGroup());
        assertEquals(BridgedDependency.Kind.BREAKS, dependencies.get(6).kind());
        assertTrue(dependencies.get(6).isGroup());
        assertEquals(List.of(), assessment.blockingCodes());
        assertTrue(codes(assessment).contains("POLY-COMPAT-308"));
        refused(withLoader("\"depends\": {\"a\": \"*\"}"), "depends must be of type ARRAY");
        refused(withLoader("\"breaks\": \"a\""), "breaks must be of type ARRAY");
        refused(withLoader("\"depends\": [{\"versions\": \"*\"}]"), "has no \"id\"");
        refused(withLoader("\"depends\": [{\"id\": \"a\", \"optional\": 1}]"), "optional must be a boolean");
        refused(withLoader("\"depends\": [{\"id\": \"a\", \"versions\": \"~abc\"}]"), "Unable to parse version range");
        refused(
                withLoader("\"depends\": [{\"id\": \"a\", \"versions\": {\"all\": []}}]"),
                "Unable to parse version range");
    }

    @Test
    void providesAreIdsWithTheirOwnVersions() {
        BridgeAssessment assessment = assess(withLoader("""
                "provides": ["quilt_hello_api", "org.other:grouped_api",
                  {"id": "quilt_hello_versioned", "version": "3.0.0"}, {"id": "same_version"}]"""));

        assertEquals(
                List.of("quilt_hello_api", "grouped_api", "quilt_hello_versioned", "same_version"),
                assessment.providedIds());
        assertEquals(Map.of("quilt_hello_versioned", "3.0.0"), assessment.providedVersions());
        assertEquals("3.0.0", assessment.versionOf("quilt_hello_versioned"));
        assertEquals("1.0.0", assessment.versionOf("quilt_hello_api"));
        refused(withLoader("\"provides\": \"a\""), "provides must be an array");
        refused(withLoader("\"provides\": [5]"), "only objects and/or strings");
        refused(withLoader("\"provides\": [{\"version\": \"1\"}]"), "id is a required field");
        refused(withLoader("\"provides\": [{\"id\": \"a\", \"version\": 1}]"), "version must be a string");
    }

    @Test
    void loadTypes() {
        assertTrue(assess(withLoader("\"load_type\": \"if_required\"")).loadOnlyIfRequired());
        assertFalse(assess(withLoader("\"load_type\": \"always\"")).loadOnlyIfRequired());
        assertFalse(assess(withLoader("\"load_type\": \"if_possible\"")).loadOnlyIfRequired());
        refused(withLoader("\"load_type\": \"sometimes\""), "load_type must be either");
        refused(withLoader("\"load_type\": 1"), "load_type must be a string");
    }

    @Test
    void repositoriesAreReadAndIgnored() {
        BridgeAssessment assessment = assess(withLoader("\"repositories\": [\"https://maven.example\"]"));

        assertTrue(detail(assessment, "POLY-COMPAT-309").contains("repositories")
                || assessment.findings().stream()
                        .anyMatch(finding -> finding.feature().equals("repositories")));
        assertEquals(List.of(), assessment.blockingCodes());
        refused(withLoader("\"repositories\": \"x\""), "repositories must be an array");
        refused(withLoader("\"repositories\": [1]"), "must be a string");
    }

    private static String mappings(String value) {
        return "{\"schema_version\": 1, \"quilt_loader\": {\"group\": \"g\", \"id\": \"quilt_hello\", \"version\":"
                + " \"1.0.0\"" + (value == null ? "" : ", \"intermediate_mappings\": " + value) + "}}";
    }

    @Test
    void intermediateMappingsOnlyIntermediaryIsSupported() {
        refused(mappings(null), "hashed");
        refused(mappings("\"org.quiltmc:hashed\""), "hashed");
        refused(mappings("\"org.quiltmc.experimental:mojang\""), "'mojang' mapping namespace");
        refused(mappings("\"org.example:other\""), "unknown intermediate mappings");
        refused(mappings("\"intermediary\""), "valid maven coordinate");
        refused(mappings("5"), "intermediate_mappings must be a string");
        BridgeAssessment intermediary = assess(mappings("\"net.fabricmc:intermediary\""));
        assertEquals(List.of("POLY-COMPAT-309"), codes(intermediary));
    }

    @Test
    void requiredFeatures() {
        assertEquals(
                List.of("POLY-COMPAT-306"),
                assess(withLoader("\"required_features\": [\"modifiable_filesystem_overlay\"]"))
                        .blockingCodes());
        assertEquals(
                List.of("POLY-COMPAT-306"),
                assess(withLoader("\"required_features\": [\"quilt_loader:modifiable_filesystem_overlay\"]"))
                        .blockingCodes());
        refused(withLoader("\"required_features\": [\"teleportation\"]"), "unknown");
        refused(withLoader("\"required_features\": {}"), "required_features must be an array");
        refused(withLoader("\"required_features\": [1]"), "array of strings");
        accepted(withLoader("\"required_features\": []"));
    }

    @Test
    void metadataValues() {
        accepted(withLoader("""
                "metadata": {"name": "Quilt Hello", "description": "d",
                  "contributors": {"Someone": "Owner", "Other": ["Developer", "Artist"]},
                  "contact": {"homepage": "https://example.org"},
                  "license": "MIT", "icon": "assets/icon.png"}"""));
        accepted(
                withLoader("\"metadata\": {\"license\": {\"name\": \"Mine\", \"id\": \"mine\", \"url\": \"https://x\"},"
                        + " \"icon\": {\"16\": \"a.png\", \"32\": \"b.png\"}}"));
        accepted(withLoader("\"metadata\": {\"license\": [\"MIT\", \"Apache-2.0\"]}"));
        refused(withLoader("\"metadata\": []"), "metadata must be an object");
        refused(withLoader("\"metadata\": {\"name\": 5}"), "name must be a string");
        refused(withLoader("\"metadata\": {\"contributors\": []}"), "contributors must be an object");
        refused(withLoader("\"metadata\": {\"contributors\": {\"a\": 5}}"), "must be a string or array of strings");
        refused(withLoader("\"metadata\": {\"contact\": \"me\"}"), "contact must be an object");
        refused(withLoader("\"metadata\": {\"contact\": {\"email\": 5}}"), "must be a string");
        refused(withLoader("\"metadata\": {\"license\": \"All Rights Reserved\"}"), "valid SPDX identifier");
        refused(
                withLoader("\"metadata\": {\"license\": {\"name\": \"Mine\", \"id\": \"mine\"}}"),
                "url is a required field");
        refused(withLoader("\"metadata\": {\"license\": 5}"), "license field must be");
        refused(withLoader("\"metadata\": {\"icon\": {\"big\": \"a.png\"}}"), "must be an integer");
    }

    private static final String MIXIN_CONFIG = "{\"package\": \"a.mixin\", \"mixins\": []}";

    @Test
    void mixinConfigurationsInEveryForm() {
        Map<String, String> files = Map.of("a.mixins.json", MIXIN_CONFIG, "b.mixins.json", MIXIN_CONFIG);
        BridgeAssessment text = assess(withRoot("\"mixin\": \"a.mixins.json\""), GameSide.DEDICATED_SERVER, files);
        BridgeAssessment clientObject = assess(
                withRoot("\"mixin\": {\"config\": \"a.mixins.json\", \"environment\": \"client\"}"),
                GameSide.DEDICATED_SERVER,
                files);
        BridgeAssessment array = assess(
                withRoot("\"mixin\": [\"a.mixins.json\", {\"config\": \"b.mixins.json\", \"environment\":"
                        + " \"dedicated_server\"}]"),
                GameSide.DEDICATED_SERVER,
                files);

        assertTrue(detail(text, "POLY-COMPAT-131").startsWith("1 Mixin configuration(s), 1 for DEDICATED_SERVER"));
        assertTrue(detail(clientObject, "POLY-COMPAT-131").startsWith("1 Mixin configuration(s), 0 for"));
        assertTrue(detail(array, "POLY-COMPAT-131").startsWith("2 Mixin configuration(s), 2 for"));
        assertEquals(List.of(), array.blockingCodes());
    }

    @Test
    void mixinProblems() {
        refused(withRoot("\"mixin\": {\"environment\": \"client\"}"), "must have a config value");
        refused(
                withRoot("\"mixin\": {\"config\": \"a.mixins.json\", \"environment\": \"server\"}"),
                "'dedicated_server'");
        refused(withRoot("\"mixin\": 5"), "mixin value must be");
        refused(withRoot("\"mixin\": [5]"), "Entry inside mixin");
        BridgeAssessment missing = assess(withRoot("\"mixin\": \"gone.mixins.json\""), GameSide.CLIENT, Map.of());
        BridgeAssessment newer = assess(
                withRoot("\"mixin\": \"a.mixins.json\""),
                GameSide.CLIENT,
                Map.of("a.mixins.json", "{\"required\": true, \"package\": \"a\", \"minVersion\": \"0.9.1\"}"));

        assertTrue(detail(missing, "POLY-COMPAT-136").contains("named in quilt.mod.json but not in the jar"));
        assertTrue(detail(missing, "POLY-COMPAT-136").contains("as under Quilt Loader"));
        assertEquals(List.of(), missing.blockingCodes());
        assertEquals(List.of("POLY-COMPAT-132"), newer.blockingCodes());
    }

    @Test
    void accessWidenersInEveryForm() {
        String first = "accessWidener v2 official\naccessible class net/minecraft/server/MinecraftServer\n";
        String second = "accessWidener v2 official\naccessible class net/minecraft/world/level/Level\n";
        Map<String, String> files = Map.of("a.accesswidener", first, "b.accesswidener", second);
        BridgeAssessment one = assess(withRoot("\"access_widener\": \"a.accesswidener\""), GameSide.CLIENT, files);
        BridgeAssessment two = assess(
                withRoot("\"access_widener\": [\"a.accesswidener\", \"b.accesswidener\"]"), GameSide.CLIENT, files);

        assertEquals(1, one.accessWidenings().size());
        assertEquals(2, two.accessWidenings().size());
        assertEquals(List.of(), two.blockingCodes());
        assertEquals(
                2,
                two.findings().stream()
                        .filter(finding -> finding.code().equals("POLY-COMPAT-129"))
                        .count());
        refused(withRoot("\"access_widener\": 5"), "access_widener must be");
        refused(withRoot("\"access_widener\": [5]"), "must be a string");
        BridgeAssessment missing = assess(withRoot("\"access_widener\": \"gone.aw\""), GameSide.CLIENT, Map.of());
        assertEquals(List.of("POLY-COMPAT-125"), missing.blockingCodes());
        assertTrue(detail(missing, "POLY-COMPAT-125").contains("named in quilt.mod.json"));
        BridgeAssessment namespace = assess(
                withRoot("\"access_widener\": \"a.aw\""),
                GameSide.CLIENT,
                Map.of("a.aw", "accessWidener v2 intermediary\naccessible class net/minecraft/class_1\n"));
        assertEquals(List.of("POLY-COMPAT-126"), namespace.blockingCodes());
        assertTrue(detail(namespace, "POLY-COMPAT-126").contains("Quilt Loader refuses it too"));
    }

    @Test
    void theMinecraftEnvironmentMustMatchTheSide() {
        accepted(withRoot("\"minecraft\": {\"environment\": \"*\"}"));
        accepted(withRoot("\"minecraft\": {\"environment\": \"\"}"));
        accepted(withRoot("\"minecraft\": {}"));
        accepted(withRoot("\"minecraft\": {\"environment\": \"dedicated_server\"}"));
        BridgeAssessment clientOnServer = assess(withRoot("\"minecraft\": {\"environment\": \"client\"}"));
        BridgeAssessment serverOnClient =
                assess(withRoot("\"minecraft\": {\"environment\": \"dedicated_server\"}"), GameSide.CLIENT, Map.of());

        assertEquals(List.of("POLY-COMPAT-305"), clientOnServer.blockingCodes());
        assertEquals(
                "environment",
                clientOnServer.findings().stream()
                        .filter(finding -> finding.code().equals("POLY-COMPAT-305"))
                        .findFirst()
                        .orElseThrow()
                        .feature(),
                "the runtime's skipWhenOtherSideOnly looks for this feature");
        assertEquals(List.of("POLY-COMPAT-305"), serverOnClient.blockingCodes());
        refused(withRoot("\"minecraft\": {\"environment\": \"server\"}"), "is not a valid environment");
        refused(withRoot("\"minecraft\": \"client\""), "minecraft must be an object");
        refused(withRoot("\"minecraft\": {\"environment\": 1}"), "environment must be a string");
    }

    @Test
    void aLoaderPluginBlocks() {
        BridgeAssessment assessment =
                assess(withRoot("\"experimental_quilt_loader_plugin\": {\"class\": \"a.Plugin\", \"packages\": []}"));

        assertEquals(List.of("POLY-COMPAT-301"), assessment.blockingCodes());
    }

    @Test
    void unknownKeysAndATopLevelEnvironmentAreIgnored() {
        BridgeAssessment unknown = assess(withLoader("\"plugins\": [], \"environment\": \"client\""));
        BridgeAssessment environment = assess(withRoot("\"environment\": \"client\""));

        assertEquals(List.of(), unknown.blockingCodes());
        assertTrue(unknown.findings().stream()
                .anyMatch(finding -> finding.code().equals("POLY-COMPAT-309")
                        && finding.detail().contains("[environment, plugins]")));
        assertEquals(List.of(), environment.blockingCodes());
        assertTrue(environment.findings().stream()
                .anyMatch(finding -> finding.code().equals("POLY-COMPAT-309")
                        && finding.feature().equals("environment")));
    }

    @Test
    void builtInModsVersionsAndPackages() {
        assertEquals("0.31.0-beta.4", bridge.builtInMods().get("quilt_loader"));
        assertEquals("0.19.5", bridge.builtInMods().get("fabricloader"));
        assertEquals("0.5.5", bridge.builtInMods().get("mixinextras"));
        assertTrue(bridge.compareVersions("1.10.0", "1.9.0") > 0);
        assertTrue(bridge.compareVersions("1.0.0-beta.2", "1.0.0") < 0);
        assertEquals(List.of("quilt.mod.json", "fabric.mod.json"), bridge.nestedMetadataPaths());
        assertTrue(bridge.providedPackages().contains("org/quiltmc/loader/api/"));
        assertTrue(bridge.providedPackages().contains("net/fabricmc/loader/api/"));
        assertTrue(bridge.runtimePackages().contains("org/quiltmc/loader/"));
        assertEquals(List.of("org.quiltmc.loader."), bridge.sharedLibraryPackages());
    }

    @Test
    void theBridgeNeverThrows() {
        List<String> broken = List.of(
                "",
                "null",
                "\"text\"",
                "{\"schema_version\": 1e400, \"quilt_loader\": {}}",
                "{\"schema_version\": 1, \"quilt_loader\": null}",
                withLoader("\"entrypoints\": null"),
                withLoader("\"depends\": [null]"),
                withLoader("\"depends\": [[[[{\"id\": \"a\", \"unless\": [[{\"id\": 1}]]}]]]]"),
                withLoader("\"provides\": [null]"),
                withLoader("\"metadata\": {\"icon\": null, \"license\": null}"),
                withRoot("\"mixin\": null"),
                withRoot("\"minecraft\": null"),
                withRoot("\"access_widener\": null"),
                withLoader("\"depends\": [{\"id\": \"a\", \"versions\": [\">\", \"<\"]}]"));
        for (String json : broken) {
            BridgeAssessment assessment = assertDoesNotThrow(() -> assess(json), json);
            assertFalse(assessment.findings().isEmpty(), json);
        }
    }
}

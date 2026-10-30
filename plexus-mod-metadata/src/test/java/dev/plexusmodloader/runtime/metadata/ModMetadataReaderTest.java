package dev.plexusmodloader.runtime.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModMetadataReaderTest {

    private static final String VALID = """
            {
              "schemaVersion": 1,
              "id": "example_copper_tools",
              "version": "1.0.0",
              "name": "Example Copper Tools",
              "environment": "both",
              "minecraft": ["26.2"],
              "entrypoints": {
                "common": ["com.example.copper.ExampleMod"],
                "client": ["com.example.copper.client.ExampleClientMod"]
              },
              "depends": { "poly": ">=0.1.0 <0.2.0" },
              "conflicts": [],
              "provides": [],
              "replacesSystems": [],
              "transforms": []
            }
            """;

    private final ModMetadataReader reader = new ModMetadataReader();

    @Test
    void readsTheDesignDocumentExample() {
        ModMetadataReader.Result result = reader.read(VALID);

        assertEquals(List.of(), result.problems());
        ModMetadata metadata = result.metadata().orElseThrow();
        assertEquals("example_copper_tools", metadata.id().value());
        assertEquals("1.0.0", metadata.version().toString());
        assertEquals(ModEnvironment.BOTH, metadata.environment());
        assertEquals(
                List.of("com.example.copper.ExampleMod"), metadata.entrypoints().common());
        assertEquals(
                List.of("com.example.copper.client.ExampleClientMod"),
                metadata.entrypoints().client());
        assertEquals(">=0.1.0 <0.2.0", metadata.depends().get("poly").toString());
    }

    @Test
    void reportsEveryProblemAtOnce() {
        ModMetadataReader.Result result = reader.read("""
                {
                  "schemaVersion": 1,
                  "id": "Bad-Id",
                  "version": "1.0",
                  "environment": "everywhere",
                  "minecraft": ["26.2"],
                  "entrypoints": { "common": ["not a class"], "launcher": [] },
                  "depends": { "poly": "~1.0" }
                }
                """);

        assertTrue(result.metadata().isEmpty());
        assertEquals(
                List.of(
                        MetadataProblemCode.INVALID_MOD_ID,
                        MetadataProblemCode.INVALID_VERSION,
                        MetadataProblemCode.MISSING_FIELD,
                        MetadataProblemCode.INVALID_ENVIRONMENT,
                        MetadataProblemCode.INVALID_ENTRYPOINT,
                        MetadataProblemCode.INVALID_ENTRYPOINT,
                        MetadataProblemCode.INVALID_VERSION_RANGE),
                result.problems().stream().map(MetadataProblem::code).toList());
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        ModMetadataReader.Result result = reader.read(VALID.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));

        assertTrue(result.metadata().isEmpty());
        assertEquals(
                MetadataProblemCode.UNSUPPORTED_SCHEMA_VERSION,
                result.problems().getFirst().code());
    }

    @Test
    void rejectsInvalidJson() {
        ModMetadataReader.Result result = reader.read("{ \"id\": ");

        assertEquals(
                MetadataProblemCode.INVALID_JSON, result.problems().getFirst().code());
    }

    @Test
    void rejectsDeclarationsThatThisVersionCannotHonourInsteadOfIgnoringThem() {
        ModMetadataReader.Result result =
                reader.read(VALID.replace("\"provides\": []", "\"provides\": [\"another_mod\"]"));

        assertTrue(result.metadata().isEmpty());
        MetadataProblem problem = result.problems().getFirst();
        assertEquals(MetadataProblemCode.FEATURE_NOT_SUPPORTED_YET, problem.code());
        assertEquals("$.provides", problem.field());
    }

    @Test
    void readsSystemReplacementsWhenTheCapabilityIsRequested() {
        ModMetadataReader.Result result = reader.read(VALID.replace(
                "\"replacesSystems\": []",
                "\"capabilities\": [\"system.provider.replace\"], \"replacesSystems\": [{\"system\": "
                        + "\"poly:game_brand\", \"provider\": \"com.example.Brand\", \"override\": true}]"));

        assertEquals(List.of(), result.problems());
        assertEquals(
                List.of(new SystemReplacementDeclaration("poly:game_brand", "com.example.Brand", true)),
                result.metadata().orElseThrow().replacesSystems());
    }

    @Test
    void systemReplacementsRequireTheReplaceCapability() {
        ModMetadataReader.Result result = reader.read(VALID.replace(
                "\"replacesSystems\": []",
                "\"replacesSystems\": [{\"system\": \"poly:game_brand\", \"provider\": \"com.example.Brand\"}]"));

        assertTrue(result.metadata().isEmpty());
        assertEquals(
                MetadataProblemCode.MISSING_CAPABILITY,
                result.problems().getFirst().code());
    }

    @Test
    void invalidSystemReplacementEntriesAreRejected() {
        ModMetadataReader.Result result = reader.read(VALID.replace(
                "\"replacesSystems\": []",
                "\"capabilities\": [\"system.provider.replace\"], \"replacesSystems\": [{\"system\": "
                        + "\"Game Brand\", \"provider\": \"com.example.Brand\"}, {\"provider\": \"x.Y\"}]"));

        assertEquals(
                List.of(MetadataProblemCode.INVALID_SYSTEM_REPLACEMENT, MetadataProblemCode.MISSING_FIELD),
                result.problems().stream().map(MetadataProblem::code).toList());
    }

    private static final String TRANSFORM = """
            {"id": "example_copper_tools:storage_name", "minecraft": "26.2", "sides": ["dedicated_server"],
             "target": {"owner": "net/minecraft/world/level/storage/LevelStorageSource", "method": "getName",
                        "descriptor": "()Ljava/lang/String;"},
             "action": {"type": "replace_returned_string_constant", "expected": "Anvil", "replacement": "Anvil+"},
             "onFailure": "abort_startup"}
            """;

    @Test
    void readsTransformDeclarations() {
        ModMetadataReader.Result result =
                reader.read(VALID.replace("\"transforms\": []", "\"transforms\": [" + TRANSFORM + "]")
                        .replace("\"conflicts\": []", "\"capabilities\": [\"internal.bytecode.transform\"]"));

        assertEquals(List.of(), result.problems());
        TransformDeclaration declaration =
                result.metadata().orElseThrow().transforms().getFirst();
        assertEquals("example_copper_tools:storage_name", declaration.id());
        assertEquals("net/minecraft/world/level/storage/LevelStorageSource", declaration.owner());
        assertEquals("Anvil+", declaration.actionArguments().get("replacement"));
        assertTrue(declaration.abortOnFailure());
    }

    @Test
    void transformsRequireTheBytecodeCapability() {
        ModMetadataReader.Result result =
                reader.read(VALID.replace("\"transforms\": []", "\"transforms\": [" + TRANSFORM + "]"));

        assertEquals(
                MetadataProblemCode.MISSING_CAPABILITY,
                result.problems().getFirst().code());
    }

    @Test
    void transformRuleIdsMustUseTheModNamespace() {
        ModMetadataReader.Result result = reader.read(VALID.replace(
                        "\"transforms\": []",
                        "\"transforms\": [" + TRANSFORM.replace("example_copper_tools:", "someone_else:") + "]")
                .replace("\"conflicts\": []", "\"capabilities\": [\"internal.bytecode.transform\"]"));

        assertEquals(
                MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                result.problems().getFirst().code());
        assertEquals("$.transforms[0].id", result.problems().getFirst().field());
    }

    @Test
    void unknownTransformActionsAreRejected() {
        ModMetadataReader.Result result = reader.read(VALID.replace(
                        "\"transforms\": []",
                        "\"transforms\": ["
                                + TRANSFORM.replace("replace_returned_string_constant", "rewrite_everything") + "]")
                .replace("\"conflicts\": []", "\"capabilities\": [\"internal.bytecode.transform\"]"));

        assertEquals(
                MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                result.problems().getFirst().code());
    }

    private static final String REDIRECT = """
            {"id": "example_copper_tools:tick_time", "minecraft": "26.2", "sides": ["client", "dedicated_server"],
             "target": {"owner": "net/minecraft/server/MinecraftServer", "method": "tickServer",
                        "descriptor": "(Ljava/util/function/BooleanSupplier;)V"},
             "action": {"type": "redirect_call", "invokedOwner": "net.minecraft.Util", "invokedName": "getNanos",
                        "invokedDescriptor": "()J", "ordinal": 1,
                        "hookOwner": "com.example.copper.Hooks", "hookMethod": "nanos"},
             "after": ["poly:server_tick_events"]}
            """;

    private static final String WIDENING = """
            {"minecraft": "26.2", "sides": ["client"], "owner": "net.minecraft.client.Minecraft",
             "kind": "field", "name": "fps", "descriptor": "I", "access": ["accessible", "mutable"]}
            """;

    @Test
    void readsRedirectsWithOptionalOrdinalAndDeclaredOrder() {
        ModMetadataReader.Result result = reader.read(
                VALID.replace("\"transforms\": []", "\"transforms\": [" + REDIRECT + "]")
                        .replace(
                                "\"conflicts\": []",
                                "\"capabilities\": [\"internal.bytecode.transform\", \"internal.callsite.intercept\"]"));

        assertEquals(List.of(), result.problems());
        TransformDeclaration declaration =
                result.metadata().orElseThrow().transforms().getFirst();
        assertEquals("1", declaration.actionArguments().get("ordinal"));
        assertEquals("net.minecraft.Util", declaration.actionArguments().get("invokedOwner"));
        assertEquals(List.of("poly:server_tick_events"), declaration.after());
    }

    @Test
    void redirectsAlsoNeedTheCallSiteCapability() {
        ModMetadataReader.Result result =
                reader.read(VALID.replace("\"transforms\": []", "\"transforms\": [" + REDIRECT + "]")
                        .replace("\"conflicts\": []", "\"capabilities\": [\"internal.bytecode.transform\"]"));

        assertTrue(result.metadata().isEmpty());
        assertEquals(
                MetadataProblemCode.MISSING_CAPABILITY,
                result.problems().getFirst().code());
        assertTrue(result.problems().getFirst().message().contains("internal.callsite.intercept"));
    }

    @Test
    void badOrdinalsAndHeadResultsAreRejected() {
        String badOrdinal = REDIRECT.replace("\"ordinal\": 1", "\"ordinal\": \"first\"");
        String badResult = TRANSFORM.replace(
                "\"type\": \"replace_returned_string_constant\", \"expected\": \"Anvil\", \"replacement\": \"Anvil+\"",
                "\"type\": \"invoke_hook_at_head\", \"hookOwner\": \"a.B\", \"hookMethod\": \"c\", \"result\": \"stop\"");
        ModMetadataReader.Result result = reader.read(
                VALID.replace("\"transforms\": []", "\"transforms\": [" + badOrdinal + ", " + badResult + "]")
                        .replace(
                                "\"conflicts\": []",
                                "\"capabilities\": [\"internal.bytecode.transform\", \"internal.callsite.intercept\"]"));

        assertEquals(
                List.of("$.transforms[0].action.ordinal", "$.transforms[1].action.result"),
                result.problems().stream().map(MetadataProblem::field).toList());
    }

    @Test
    void readsAccessWideningsWithTheMemberAccessCapability() {
        ModMetadataReader.Result result = reader.read(
                VALID.replace("\"transforms\": []", "\"transforms\": [], \"accessWidenings\": [" + WIDENING + "]")
                        .replace("\"conflicts\": []", "\"capabilities\": [\"internal.member.access\"]"));

        assertEquals(List.of(), result.problems());
        AccessWideningDeclaration widening =
                result.metadata().orElseThrow().accessWidenings().getFirst();
        assertEquals("net/minecraft/client/Minecraft", widening.owner());
        assertEquals(java.util.Set.of("accessible", "mutable"), widening.access());
        assertTrue(widening.abortOnFailure(), "a missing target stops the launch by default");

        ModMetadataReader.Result withoutCapability = reader.read(
                VALID.replace("\"transforms\": []", "\"transforms\": [], \"accessWidenings\": [" + WIDENING + "]"));
        assertEquals(
                MetadataProblemCode.MISSING_CAPABILITY,
                withoutCapability.problems().getFirst().code());
    }

    @Test
    void invalidAccessWideningsAreRejected() {
        String classWithName = """
                {"minecraft": "26.2", "sides": ["client"], "owner": "a/B", "kind": "class", "name": "x", "access": ["accessible"]}
                """;
        String unknownWords = """
                {"minecraft": "26.2", "sides": ["server"], "owner": "a/B", "kind": "module", "access": ["visible"]}
                """;
        ModMetadataReader.Result result = reader.read(VALID.replace(
                        "\"transforms\": []",
                        "\"transforms\": [], \"accessWidenings\": [" + classWithName + ", " + unknownWords + "]")
                .replace("\"conflicts\": []", "\"capabilities\": [\"internal.member.access\"]"));

        assertEquals(
                List.of(
                        "$.accessWidenings[0]",
                        "$.accessWidenings[1].kind",
                        "$.accessWidenings[1].access",
                        "$.accessWidenings[1].sides"),
                result.problems().stream().map(MetadataProblem::field).toList());
        assertTrue(result.problems().stream()
                .allMatch(problem -> problem.code() == MetadataProblemCode.INVALID_ACCESS_WIDENING));
    }

    @Test
    void unknownCapabilitiesAreWarnings() {
        ModMetadataReader.Result result =
                reader.read(VALID.replace("\"conflicts\": []", "\"capabilities\": [\"teleport.everything\"]"));

        assertTrue(result.metadata().isPresent());
        assertEquals(
                MetadataProblemCode.UNKNOWN_CAPABILITY,
                result.problems().getFirst().code());
    }

    @Test
    void unknownFieldsAreWarningsNotErrors() {
        ModMetadataReader.Result result = reader.read(VALID.replace("\"conflicts\": []", "\"homepage\": \"x\""));

        assertTrue(result.metadata().isPresent());
        assertEquals(
                MetadataProblemCode.UNKNOWN_FIELD, result.problems().getFirst().code());
    }
}

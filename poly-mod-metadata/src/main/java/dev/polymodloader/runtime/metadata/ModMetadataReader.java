package dev.polymodloader.runtime.metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.polymodloader.runtime.metadata.version.MinecraftVersionPattern;
import dev.polymodloader.runtime.metadata.version.SemanticVersion;
import dev.polymodloader.runtime.metadata.version.VersionRange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and validates {@code poly.mod.json} schema version 1. Every problem is collected instead of
 * stopping at the first one, so a mod author sees all mistakes at once. Fields that a later Poly Mod Loader
 * version will support ({@code replacesSystems}, {@code transforms}, {@code provides}) are rejected
 * when non-empty rather than silently ignored (design doc §5.4.1).
 */
public final class ModMetadataReader {

    /** The only schema version this reader understands. */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final Pattern JAVA_BINARY_NAME =
            Pattern.compile("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                    + "(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*");
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "schemaVersion",
            "id",
            "version",
            "name",
            "environment",
            "minecraft",
            "entrypoints",
            "depends",
            "conflicts",
            "capabilities",
            "provides",
            "replacesSystems",
            "transforms",
            "accessWidenings");
    private static final Set<String> NOT_SUPPORTED_YET = Set.of("provides");

    /** Capability that must be requested before declaring {@code transforms}. */
    public static final String TRANSFORM_CAPABILITY = "internal.bytecode.transform";

    /** Capability that must be requested before declaring {@code accessWidenings}. */
    public static final String MEMBER_ACCESS_CAPABILITY = "internal.member.access";

    /** Capability that must be requested before declaring {@code replacesSystems}. */
    public static final String REPLACE_SYSTEMS_CAPABILITY = "system.provider.replace";

    /** Capability IDs defined by design doc §5.4.1. Others are reported as warnings. */
    public static final Set<String> KNOWN_CAPABILITIES = Set.of(
            "content.registry.register",
            "gameplay.rules.extend",
            "worldgen.provider.replace",
            "render.pipeline.extend",
            REPLACE_SYSTEMS_CAPABILITY,
            "internal.member.access",
            "internal.callsite.intercept",
            "internal.bytecode.transform",
            "runtime.bootstrap.modify",
            "network.protocol.extend",
            "world.data.migrate");

    private static final Pattern SYSTEM_ID = Pattern.compile("[a-z][a-z0-9_]*:[a-z0-9_/]+");
    private static final Set<String> ENTRYPOINT_SIDES = Set.of("common", "client", "server");

    /**
     * The outcome of reading one metadata file.
     *
     * @param metadata the validated metadata, present only when there is no error-level problem
     * @param problems every problem found, including warnings
     */
    public record Result(Optional<ModMetadata> metadata, List<MetadataProblem> problems) {
        public Result {
            problems = List.copyOf(problems);
        }
    }

    /** Reads {@code json}, the contents of a {@code poly.mod.json} file. */
    public Result read(String json) {
        List<MetadataProblem> problems = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_JSON, "$", "The document must be a JSON object"));
                return new Result(Optional.empty(), problems);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            problems.add(new MetadataProblem(MetadataProblemCode.INVALID_JSON, "$", e.getMessage()));
            return new Result(Optional.empty(), problems);
        }

        for (String field : root.keySet()) {
            if (!KNOWN_FIELDS.contains(field)) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.UNKNOWN_FIELD, "$." + field, "Unknown field is ignored"));
            }
        }

        Optional<Integer> schemaVersion = requiredInt(root, "schemaVersion", problems);
        if (schemaVersion.isPresent() && schemaVersion.get() != SUPPORTED_SCHEMA_VERSION) {
            problems.add(new MetadataProblem(
                    MetadataProblemCode.UNSUPPORTED_SCHEMA_VERSION,
                    "$.schemaVersion",
                    "Schema version " + schemaVersion.get() + " is not supported; this Poly Mod Loader reads version "
                            + SUPPORTED_SCHEMA_VERSION));
            return new Result(Optional.empty(), problems);
        }

        Optional<ModId> id = requiredString(root, "id", problems).flatMap(value -> {
            Optional<ModId> parsed = ModId.parse(value);
            if (parsed.isEmpty()) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_MOD_ID,
                        "$.id",
                        "'" + value + "' does not match " + ModId.format()));
            }
            return parsed;
        });
        Optional<SemanticVersion> version = requiredString(root, "version", problems)
                .flatMap(value -> {
                    Optional<SemanticVersion> parsed = SemanticVersion.parse(value);
                    if (parsed.isEmpty()) {
                        problems.add(new MetadataProblem(
                                MetadataProblemCode.INVALID_VERSION,
                                "$.version",
                                "'" + value + "' is not a Semantic Versioning 2.0.0 version"));
                    }
                    return parsed;
                });
        Optional<String> name = requiredString(root, "name", problems);
        Optional<ModEnvironment> environment = requiredString(root, "environment", problems)
                .flatMap(value -> {
                    Optional<ModEnvironment> parsed = ModEnvironment.parse(value);
                    if (parsed.isEmpty()) {
                        problems.add(new MetadataProblem(
                                MetadataProblemCode.INVALID_ENVIRONMENT,
                                "$.environment",
                                "'" + value + "' must be one of client, server, both"));
                    }
                    return parsed;
                });
        List<String> minecraft = stringArray(root, "minecraft", true, problems);
        for (int i = 0; i < minecraft.size(); i++) {
            if (MinecraftVersionPattern.parse(minecraft.get(i)).isEmpty()) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_VERSION_RANGE,
                        "$.minecraft[" + i + "]",
                        "'" + minecraft.get(i)
                                + "' is not a Minecraft version, wildcard ('26.*') or range ('>=26.2 <27')"));
            }
        }
        ModEntrypoints entrypoints = entrypoints(root, problems);
        Map<String, VersionRange> depends = depends(root, problems);
        List<String> conflicts = stringArray(root, "conflicts", false, problems);
        List<String> capabilities = stringArray(root, "capabilities", false, problems);
        for (String capability : capabilities) {
            if (!KNOWN_CAPABILITIES.contains(capability)) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.UNKNOWN_CAPABILITY,
                        "$.capabilities",
                        "'" + capability + "' is not a capability this Poly Mod Loader version knows"));
            }
        }
        List<SystemReplacementDeclaration> replacesSystems = replacesSystems(root, problems);
        if (!replacesSystems.isEmpty() && !capabilities.contains(REPLACE_SYSTEMS_CAPABILITY)) {
            problems.add(new MetadataProblem(
                    MetadataProblemCode.MISSING_CAPABILITY,
                    "$.capabilities",
                    "declaring replacesSystems requires the capability '" + REPLACE_SYSTEMS_CAPABILITY + "'"));
        }
        for (String field : NOT_SUPPORTED_YET) {
            if (root.has(field)
                    && !(root.get(field).isJsonArray()
                            && root.getAsJsonArray(field).isEmpty())) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.FEATURE_NOT_SUPPORTED_YET,
                        "$." + field,
                        "'" + field + "' declarations are not supported by this Poly Mod Loader version yet; "
                                + "remove them or use a Poly Mod Loader version that supports them"));
            }
        }

        List<TransformDeclaration> transforms =
                transforms(root, id.map(ModId::value).orElse(null), problems);
        if (!transforms.isEmpty() && !capabilities.contains(TRANSFORM_CAPABILITY)) {
            problems.add(new MetadataProblem(
                    MetadataProblemCode.MISSING_CAPABILITY,
                    "$.capabilities",
                    "declaring transforms requires the capability '" + TRANSFORM_CAPABILITY + "'"));
        }
        for (TransformDeclaration transform : transforms) {
            String extra = TransformDeclaration.EXTRA_CAPABILITY.get(transform.actionType());
            if (extra != null && !capabilities.contains(extra)) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.MISSING_CAPABILITY,
                        "$.capabilities",
                        "transform " + transform.id() + " (" + transform.actionType() + ") requires the capability '"
                                + extra + "'"));
            }
        }
        List<AccessWideningDeclaration> accessWidenings = accessWidenings(root, problems);
        if (!accessWidenings.isEmpty() && !capabilities.contains(MEMBER_ACCESS_CAPABILITY)) {
            problems.add(new MetadataProblem(
                    MetadataProblemCode.MISSING_CAPABILITY,
                    "$.capabilities",
                    "declaring accessWidenings requires the capability '" + MEMBER_ACCESS_CAPABILITY + "'"));
        }

        boolean valid = problems.stream().noneMatch(MetadataProblem::isError);
        if (!valid) {
            return new Result(Optional.empty(), problems);
        }
        return new Result(
                Optional.of(new ModMetadata(
                        schemaVersion.orElseThrow(),
                        id.orElseThrow(),
                        version.orElseThrow(),
                        name.orElseThrow(),
                        environment.orElseThrow(),
                        minecraft,
                        entrypoints,
                        depends,
                        conflicts,
                        capabilities,
                        replacesSystems,
                        transforms,
                        accessWidenings)),
                problems);
    }

    private static List<AccessWideningDeclaration> accessWidenings(JsonObject root, List<MetadataProblem> problems) {
        List<AccessWideningDeclaration> declarations = new ArrayList<>();
        JsonElement element = root.get("accessWidenings");
        if (element == null) {
            return declarations;
        }
        if (!element.isJsonArray()) {
            problems.add(new MetadataProblem(
                    MetadataProblemCode.WRONG_TYPE, "$.accessWidenings", "Expected an array of access widenings"));
            return declarations;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String path = "$.accessWidenings[" + i + "]";
            if (!array.get(i).isJsonObject()) {
                problems.add(new MetadataProblem(MetadataProblemCode.WRONG_TYPE, path, "Expected an object"));
                continue;
            }
            JsonObject entry = array.get(i).getAsJsonObject();
            int before = problems.size();
            Optional<String> minecraft = requiredString(entry, "minecraft", problems, path);
            List<String> sides = stringArray(entry, "sides", true, problems, path + ".sides");
            Optional<String> owner = requiredString(entry, "owner", problems, path);
            Optional<String> kind = requiredString(entry, "kind", problems, path);
            String name = optionalString(entry, "name", problems, path).orElse("");
            String descriptor =
                    optionalString(entry, "descriptor", problems, path).orElse("");
            List<String> access = stringArray(entry, "access", true, problems, path + ".access");
            if (kind.isPresent() && !AccessWideningDeclaration.KINDS.contains(kind.get())) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_ACCESS_WIDENING,
                        path + ".kind",
                        "'" + kind.get() + "' must be class, method or field"));
            }
            if (kind.isPresent() && kind.get().equals("class") && (!name.isEmpty() || !descriptor.isEmpty())) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_ACCESS_WIDENING,
                        path,
                        "a class widening has no name or descriptor"));
            }
            if (kind.isPresent()
                    && AccessWideningDeclaration.KINDS.contains(kind.get())
                    && !kind.get().equals("class")
                    && (name.isEmpty() || descriptor.isEmpty())) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_ACCESS_WIDENING,
                        path,
                        "a " + kind.get() + " widening needs name and descriptor"));
            }
            for (String word : access) {
                if (!AccessWideningDeclaration.ACCESS.contains(word)) {
                    problems.add(new MetadataProblem(
                            MetadataProblemCode.INVALID_ACCESS_WIDENING,
                            path + ".access",
                            "'" + word + "' must be accessible, extendable or mutable"));
                }
            }
            boolean abortOnFailure = true;
            if (entry.has("onFailure")) {
                String policy = entry.get("onFailure").isJsonPrimitive()
                        ? entry.get("onFailure").getAsString()
                        : "";
                switch (policy) {
                    case "abort_startup" -> abortOnFailure = true;
                    case "skip" -> abortOnFailure = false;
                    default ->
                        problems.add(new MetadataProblem(
                                MetadataProblemCode.INVALID_ACCESS_WIDENING,
                                path + ".onFailure",
                                "expected abort_startup or skip"));
                }
            }
            for (String side : sides) {
                if (!side.equals("client") && !side.equals("dedicated_server")) {
                    problems.add(new MetadataProblem(
                            MetadataProblemCode.INVALID_ACCESS_WIDENING,
                            path + ".sides",
                            "'" + side + "' must be client or dedicated_server"));
                }
            }
            if (problems.size() == before) {
                declarations.add(new AccessWideningDeclaration(
                        minecraft.orElseThrow(),
                        sides,
                        owner.orElseThrow().replace('.', '/'),
                        kind.orElseThrow(),
                        name,
                        descriptor,
                        Set.copyOf(access),
                        abortOnFailure));
            }
        }
        return declarations;
    }

    private static Optional<String> optionalString(
            JsonObject object, String field, List<MetadataProblem> problems, String path) {
        if (!object.has(field)) {
            return Optional.empty();
        }
        return requiredString(object, field, problems, path);
    }

    private static List<SystemReplacementDeclaration> replacesSystems(JsonObject root, List<MetadataProblem> problems) {
        List<SystemReplacementDeclaration> declarations = new ArrayList<>();
        JsonElement element = root.get("replacesSystems");
        if (element == null) {
            return declarations;
        }
        if (!element.isJsonArray()) {
            problems.add(new MetadataProblem(
                    MetadataProblemCode.WRONG_TYPE,
                    "$.replacesSystems",
                    "Expected an array of {system, provider, override?} objects"));
            return declarations;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String path = "$.replacesSystems[" + i + "]";
            if (!array.get(i).isJsonObject()) {
                problems.add(new MetadataProblem(MetadataProblemCode.WRONG_TYPE, path, "Expected an object"));
                continue;
            }
            JsonObject entry = array.get(i).getAsJsonObject();
            Optional<String> system = requiredString(entry, "system", problems, path);
            Optional<String> provider = requiredString(entry, "provider", problems, path);
            boolean override = false;
            if (entry.has("override")) {
                JsonElement value = entry.get("override");
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                    override = value.getAsBoolean();
                } else {
                    problems.add(new MetadataProblem(
                            MetadataProblemCode.WRONG_TYPE, path + ".override", "Expected true or false"));
                }
            }
            if (system.isPresent() && !SYSTEM_ID.matcher(system.get()).matches()) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_SYSTEM_REPLACEMENT,
                        path + ".system",
                        "'" + system.get() + "' is not a system ID (namespace:path)"));
                continue;
            }
            if (provider.isPresent()
                    && !JAVA_BINARY_NAME.matcher(provider.get()).matches()) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_SYSTEM_REPLACEMENT,
                        path + ".provider",
                        "'" + provider.get() + "' is not a fully qualified Java class name"));
                continue;
            }
            if (system.isPresent() && provider.isPresent()) {
                declarations.add(new SystemReplacementDeclaration(system.get(), provider.get(), override));
            }
        }
        return declarations;
    }

    private static List<TransformDeclaration> transforms(
            JsonObject root, String modId, List<MetadataProblem> problems) {
        List<TransformDeclaration> declarations = new ArrayList<>();
        JsonElement element = root.get("transforms");
        if (element == null) {
            return declarations;
        }
        if (!element.isJsonArray()) {
            problems.add(new MetadataProblem(
                    MetadataProblemCode.WRONG_TYPE, "$.transforms", "Expected an array of transformation rules"));
            return declarations;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String path = "$.transforms[" + i + "]";
            if (!array.get(i).isJsonObject()) {
                problems.add(new MetadataProblem(MetadataProblemCode.WRONG_TYPE, path, "Expected an object"));
                continue;
            }
            JsonObject entry = array.get(i).getAsJsonObject();
            int before = problems.size();
            Optional<String> id = requiredString(entry, "id", problems, path);
            Optional<String> minecraft = requiredString(entry, "minecraft", problems, path);
            List<String> sides = stringArray(entry, "sides", true, problems, path + ".sides");
            Optional<String> owner = Optional.empty();
            Optional<String> method = Optional.empty();
            Optional<String> descriptor = Optional.empty();
            if (entry.has("target") && entry.get("target").isJsonObject()) {
                JsonObject target = entry.getAsJsonObject("target");
                owner = requiredString(target, "owner", problems, path + ".target");
                method = requiredString(target, "method", problems, path + ".target");
                descriptor = requiredString(target, "descriptor", problems, path + ".target");
            } else {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.MISSING_FIELD, path + ".target", "Expected {owner, method, descriptor}"));
            }
            Optional<String> actionType = Optional.empty();
            Map<String, String> actionArguments = new LinkedHashMap<>();
            if (entry.has("action") && entry.get("action").isJsonObject()) {
                JsonObject action = entry.getAsJsonObject("action");
                actionType = requiredString(action, "type", problems, path + ".action");
                if (actionType.isPresent()) {
                    List<String> required = TransformDeclaration.ACTIONS.get(actionType.get());
                    if (required == null) {
                        problems.add(new MetadataProblem(
                                MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                                path + ".action.type",
                                "unknown action '" + actionType.get() + "'; supported: "
                                        + TransformDeclaration.ACTIONS.keySet()));
                    } else {
                        for (String argument : required) {
                            requiredString(action, argument, problems, path + ".action")
                                    .ifPresent(value -> actionArguments.put(argument, value));
                        }
                        for (String argument :
                                TransformDeclaration.OPTIONAL_ARGUMENTS.getOrDefault(actionType.get(), List.of())) {
                            JsonElement value = action.get(argument);
                            if (value == null) {
                                continue;
                            }
                            if (value.isJsonPrimitive()) {
                                actionArguments.put(argument, value.getAsString());
                            } else {
                                problems.add(new MetadataProblem(
                                        MetadataProblemCode.WRONG_TYPE,
                                        path + ".action." + argument,
                                        "Expected a string or number"));
                            }
                        }
                        String ordinal = actionArguments.get("ordinal");
                        if (ordinal != null && !ordinal.equals("all") && !ordinal.matches("0|[1-9][0-9]{0,8}")) {
                            problems.add(new MetadataProblem(
                                    MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                                    path + ".action.ordinal",
                                    "expected \"all\" or a call number counted from 0, got '" + ordinal + "'"));
                        }
                        String result = actionArguments.get("result");
                        if (actionType.get().equals("invoke_hook_at_head")
                                && result != null
                                && !Set.of("continue", "cancel_if_true", "return_if_non_null")
                                        .contains(result)) {
                            problems.add(new MetadataProblem(
                                    MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                                    path + ".action.result",
                                    "expected continue, cancel_if_true or return_if_non_null"));
                        }
                    }
                }
            } else {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.MISSING_FIELD, path + ".action", "Expected {type, ...arguments}"));
            }
            boolean abortOnFailure = false;
            if (entry.has("onFailure")) {
                String policy = entry.get("onFailure").isJsonPrimitive()
                        ? entry.get("onFailure").getAsString()
                        : "";
                switch (policy) {
                    case "abort_startup" -> abortOnFailure = true;
                    case "disable_rule" -> abortOnFailure = false;
                    default ->
                        problems.add(new MetadataProblem(
                                MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                                path + ".onFailure",
                                "expected abort_startup or disable_rule"));
                }
            }
            for (String side : sides) {
                if (!side.equals("client") && !side.equals("dedicated_server")) {
                    problems.add(new MetadataProblem(
                            MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                            path + ".sides",
                            "'" + side + "' must be client or dedicated_server"));
                }
            }
            if (id.isPresent() && modId != null && !id.get().startsWith(modId + ":")) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                        path + ".id",
                        "rule ID '" + id.get() + "' must start with this mod's ID '" + modId + ":'"));
            }
            List<String> after = stringArray(entry, "after", false, problems, path + ".after");
            for (String earlier : after) {
                if (!SYSTEM_ID.matcher(earlier).matches()) {
                    problems.add(new MetadataProblem(
                            MetadataProblemCode.INVALID_TRANSFORM_DECLARATION,
                            path + ".after",
                            "'" + earlier + "' is not a rule ID (namespace:path)"));
                }
            }
            if (problems.size() == before) {
                declarations.add(new TransformDeclaration(
                        id.orElseThrow(),
                        minecraft.orElseThrow(),
                        sides,
                        owner.orElseThrow(),
                        method.orElseThrow(),
                        descriptor.orElseThrow(),
                        actionType.orElseThrow(),
                        actionArguments,
                        abortOnFailure,
                        after));
            }
        }
        return declarations;
    }

    private static ModEntrypoints entrypoints(JsonObject root, List<MetadataProblem> problems) {
        Map<String, List<String>> bySide = new LinkedHashMap<>();
        ENTRYPOINT_SIDES.forEach(side -> bySide.put(side, new ArrayList<>()));
        if (root.has("entrypoints")) {
            JsonElement element = root.get("entrypoints");
            if (!element.isJsonObject()) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.WRONG_TYPE, "$.entrypoints", "Expected an object of side -> class list"));
            } else {
                JsonObject object = element.getAsJsonObject();
                for (String side : object.keySet()) {
                    String path = "$.entrypoints." + side;
                    if (!ENTRYPOINT_SIDES.contains(side)) {
                        problems.add(new MetadataProblem(
                                MetadataProblemCode.INVALID_ENTRYPOINT,
                                path,
                                "Unknown entrypoint side; expected common, client, or server"));
                        continue;
                    }
                    for (String className : stringArray(object, side, false, problems, path)) {
                        if (JAVA_BINARY_NAME.matcher(className).matches()) {
                            bySide.get(side).add(className);
                        } else {
                            problems.add(new MetadataProblem(
                                    MetadataProblemCode.INVALID_ENTRYPOINT,
                                    path,
                                    "'" + className + "' is not a fully qualified Java class name"));
                        }
                    }
                }
            }
        }
        return new ModEntrypoints(bySide.get("common"), bySide.get("client"), bySide.get("server"));
    }

    private static Map<String, VersionRange> depends(JsonObject root, List<MetadataProblem> problems) {
        Map<String, VersionRange> depends = new LinkedHashMap<>();
        if (!root.has("depends")) {
            return depends;
        }
        if (!root.get("depends").isJsonObject()) {
            problems.add(new MetadataProblem(
                    MetadataProblemCode.WRONG_TYPE, "$.depends", "Expected an object of mod ID -> version range"));
            return depends;
        }
        JsonObject object = root.getAsJsonObject("depends");
        for (String dependency : object.keySet()) {
            String path = "$.depends." + dependency;
            JsonElement range = object.get(dependency);
            if (!range.isJsonPrimitive() || !range.getAsJsonPrimitive().isString()) {
                problems.add(
                        new MetadataProblem(MetadataProblemCode.WRONG_TYPE, path, "Expected a version range string"));
                continue;
            }
            Optional<VersionRange> parsed = VersionRange.parse(range.getAsString());
            if (parsed.isEmpty()) {
                problems.add(new MetadataProblem(
                        MetadataProblemCode.INVALID_VERSION_RANGE,
                        path,
                        "'" + range.getAsString()
                                + "' is not a supported range (use comparators like '>=1.0.0 <2.0.0')"));
                continue;
            }
            depends.put(dependency, parsed.get());
        }
        return depends;
    }

    private static Optional<String> requiredString(JsonObject root, String field, List<MetadataProblem> problems) {
        return requiredString(root, field, problems, "$");
    }

    private static Optional<String> requiredString(
            JsonObject object, String field, List<MetadataProblem> problems, String parentPath) {
        String path = parentPath + "." + field;
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            problems.add(new MetadataProblem(MetadataProblemCode.MISSING_FIELD, path, "Required field is missing"));
            return Optional.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            problems.add(new MetadataProblem(MetadataProblemCode.WRONG_TYPE, path, "Expected a string"));
            return Optional.empty();
        }
        return Optional.of(element.getAsString());
    }

    private static Optional<Integer> requiredInt(JsonObject root, String field, List<MetadataProblem> problems) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            problems.add(
                    new MetadataProblem(MetadataProblemCode.MISSING_FIELD, "$." + field, "Required field is missing"));
            return Optional.empty();
        }
        if (!element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || element.getAsDouble() != Math.rint(element.getAsDouble())) {
            problems.add(new MetadataProblem(MetadataProblemCode.WRONG_TYPE, "$." + field, "Expected an integer"));
            return Optional.empty();
        }
        return Optional.of(element.getAsInt());
    }

    private static List<String> stringArray(
            JsonObject root, String field, boolean required, List<MetadataProblem> problems) {
        return stringArray(root, field, required, problems, "$." + field);
    }

    private static List<String> stringArray(
            JsonObject root, String field, boolean required, List<MetadataProblem> problems, String path) {
        List<String> values = new ArrayList<>();
        JsonElement element = root.get(field);
        if (element == null) {
            if (required) {
                problems.add(new MetadataProblem(MetadataProblemCode.MISSING_FIELD, path, "Required field is missing"));
            }
            return values;
        }
        if (!element.isJsonArray()) {
            problems.add(new MetadataProblem(MetadataProblemCode.WRONG_TYPE, path, "Expected an array of strings"));
            return values;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                values.add(item.getAsString());
            } else {
                problems.add(
                        new MetadataProblem(MetadataProblemCode.WRONG_TYPE, path + "[" + i + "]", "Expected a string"));
            }
        }
        return values;
    }
}

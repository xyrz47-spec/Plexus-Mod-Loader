package dev.plexusmodloader.compat.quilt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.plexusmodloader.compat.bridge.BridgedDependency;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import java.util.ArrayList;
import java.util.List;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.impl.metadata.qmj.ModDependencyIdentifierImpl;

/**
 * Reads the dependency objects of {@code quilt.mod.json} ({@code depends} and {@code breaks} entries) as Quilt Loader's
 * {@code V1ModMetadataReader.readDependencyObject} does (RFC 0002 "Dependency Objects", ADR-0027 decision 7), into two
 * forms: Quilt Loader's own {@link ModDependency} for the Quilt API, and a {@link BridgedDependency} the runtime checks
 * across loaders.
 *
 * <ul>
 *   <li>A text {@code "id"} or {@code "group:id"} is any version of that mod.
 *   <li>An object has {@code id} (required), {@code versions} ({@link QuiltVersionSpecifier}, default any), {@code
 *       reason}, {@code optional} (default {@code false}: in {@code depends}, {@code true} becomes {@link
 *       BridgedDependency.Kind#IF_PRESENT}) and {@code unless} (a dependency object read like a {@code depends} entry).
 *   <li>An array is a group: any member in {@code depends}, all members in {@code breaks}.
 * </ul>
 *
 * <p>The Maven group of {@code "group:id"} stays in the Quilt form only; the runtime decides by ID. Every relationship
 * with one mod is marked {@link BridgedDependency#skipWhenOtherSideOnly()}, as Quilt Loader ignores dependencies whose
 * candidates are all for the other side. A required dependency on a QSL module ({@link QslModules}) gets {@code
 * PLEXUS-COMPAT-308} (not blocking): QSL has no release for Minecraft 26.x. Stateless; used while assessing a mod.
 */
public final class QuiltDependencyReader {

    /**
     * One dependency object in both forms.
     *
     * @param quilt the Quilt API form ({@code ModDependency.Only}, {@code Any} or {@code All}; nested arrays are
     *     flattened, which keeps their meaning because every level of one array joins the same way)
     * @param bridged the form the runtime checks
     * @param findings notes about it ({@code PLEXUS-COMPAT-308})
     */
    public record Read(ModDependency quilt, BridgedDependency bridged, List<CompatibilityFinding> findings) {

        public Read {
            findings = List.copyOf(findings);
        }
    }

    private QuiltDependencyReader() {}

    /** Reads one {@code depends} entry. */
    public static Read depends(JsonElement value) throws QuiltMetadataException {
        List<CompatibilityFinding> findings = new ArrayList<>();
        Node node = read(value, false, findings, true);
        return new Read(node.quilt(), node.bridged(), findings);
    }

    /** Reads one {@code breaks} entry. */
    public static Read breaks(JsonElement value) throws QuiltMetadataException {
        List<CompatibilityFinding> findings = new ArrayList<>();
        Node node = read(value, true, findings, false);
        return new Read(node.quilt(), node.bridged(), findings);
    }

    /** One read dependency: the Quilt form, its flattened {@code Only} members, the bridged form, and a summary. */
    private record Node(
            ModDependency quilt, List<ModDependency.Only> only, BridgedDependency bridged, String summary) {}

    private static Node read(JsonElement value, boolean breaks, List<CompatibilityFinding> findings, boolean noteQsl)
            throws QuiltMetadataException {
        if (isString(value)) {
            ModDependencyIdentifierImpl id = new ModDependencyIdentifierImpl(value.getAsString());
            ModDependency.Only quilt = ModDependency.Only.of(id, VersionRange.ANY, "", null, false);
            if (noteQsl) {
                noteQsl(id.id(), findings);
            }
            BridgedDependency bridged = new BridgedDependency(
                            id.id(),
                            breaks ? BridgedDependency.Kind.BREAKS : BridgedDependency.Kind.REQUIRED,
                            "*",
                            version -> true)
                    .withSkipWhenOtherSideOnly();
            return new Node(quilt, List.of(quilt), bridged, id.id());
        }
        if (value instanceof JsonObject object) {
            return readObject(object, breaks, findings, noteQsl);
        }
        if (value instanceof JsonArray array) {
            List<ModDependency.Only> only = new ArrayList<>();
            List<BridgedDependency> members = new ArrayList<>();
            List<String> summaries = new ArrayList<>();
            for (JsonElement element : array) {
                Node member = read(element, breaks, findings, noteQsl);
                only.addAll(member.only());
                summaries.add(member.summary());
                if (member.bridged().kind() != BridgedDependency.Kind.OPTIONAL) {
                    // A nested empty array is ignored, as at the top.
                    members.add(member.bridged());
                }
            }
            String summary = (breaks ? "all of " : "any of ") + summaries;
            ModDependency quilt = breaks ? ModDependency.All.of(only) : ModDependency.Any.of(only);
            if (members.isEmpty()) {
                // An empty array is ignored by Quilt Loader (ModDependency.Any/All.shouldIgnore of no members).
                BridgedDependency ignored =
                        new BridgedDependency("", BridgedDependency.Kind.OPTIONAL, summary, version -> false);
                return new Node(quilt, only, ignored, summary);
            }
            BridgedDependency bridged = BridgedDependency.group(
                    breaks ? BridgedDependency.Kind.BREAKS : BridgedDependency.Kind.REQUIRED, summary, members);
            return new Node(quilt, only, bridged, summary);
        }
        throw new QuiltMetadataException("Dependency object must be an object or string to represent a single"
                + " dependency or an array to represent any dependency: " + value);
    }

    private static Node readObject(
            JsonObject object, boolean breaks, List<CompatibilityFinding> findings, boolean noteQsl)
            throws QuiltMetadataException {
        JsonElement idValue = object.get("id");
        if (idValue == null) {
            throw new QuiltMetadataException("a dependency object has no \"id\": " + object);
        }
        if (!isString(idValue)) {
            throw new QuiltMetadataException("a dependency object's \"id\" must be a string: " + object);
        }
        ModDependencyIdentifierImpl id = new ModDependencyIdentifierImpl(idValue.getAsString());
        JsonElement versionsValue = object.get("versions");
        VersionRange range;
        try {
            range = QuiltVersionSpecifier.readVersions(versionsValue);
        } catch (VersionFormatException e) {
            throw new QuiltMetadataException(
                    "Unable to parse version range " + versionsValue + " of " + id + ": " + e.getMessage(), e);
        }
        JsonElement reasonValue = object.get("reason");
        if (reasonValue != null && !isString(reasonValue)) {
            throw new QuiltMetadataException("reason must be a string: " + object);
        }
        String reason = reasonValue == null ? "" : reasonValue.getAsString();
        JsonElement optionalValue = object.get("optional");
        if (optionalValue != null
                && !(optionalValue.isJsonPrimitive()
                        && optionalValue.getAsJsonPrimitive().isBoolean())) {
            throw new QuiltMetadataException("optional must be a boolean: " + object);
        }
        boolean optional = optionalValue != null && optionalValue.getAsBoolean();
        Node unless = null;
        if (object.has("unless")) {
            // Read like a depends entry, as Quilt Loader does (readDependencyObject(true, ...)); not a requirement of
            // its own, so no QSL note.
            unless = read(object.get("unless"), false, new ArrayList<>(), false);
        }
        String versionsText = versionsValue == null
                ? "*"
                : isString(versionsValue) ? versionsValue.getAsString() : versionsValue.toString();
        String requirement = versionsText
                + (optional && !breaks ? " (optional)" : "")
                + (unless == null ? "" : " unless " + unless.summary())
                + (reason.isEmpty() ? "" : " (reason: " + reason + ")");
        ModDependency.Only quilt =
                ModDependency.Only.of(id, range, reason, unless == null ? null : unless.quilt(), optional);
        BridgedDependency.Kind kind = breaks
                ? BridgedDependency.Kind.BREAKS
                : optional ? BridgedDependency.Kind.IF_PRESENT : BridgedDependency.Kind.REQUIRED;
        BridgedDependency bridged = new BridgedDependency(
                        id.id(), kind, requirement, QuiltVersionSpecifier.accepts(range))
                .withSkipWhenOtherSideOnly();
        if (unless != null) {
            bridged = bridged.withUnless(List.of(unless.bridged()));
        }
        if (noteQsl && !optional) {
            noteQsl(id.id(), findings);
        }
        return new Node(quilt, List.of(quilt), bridged, id.id() + " " + versionsText);
    }

    private static void noteQsl(String id, List<CompatibilityFinding> findings) {
        if (QslModules.isQslModule(id)) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-308",
                    false,
                    "dependencies",
                    "depends on the QSL module '" + id + "'; the Quilt Standard Libraries have no release for"
                            + " Minecraft 26.x, so Quilt Loader cannot meet this dependency either (PLEXUS-COMPAT-114"
                            + " leaves the mod out unless another installed mod provides '" + id + "')"));
        }
    }

    private static boolean isString(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }
}

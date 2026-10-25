package dev.polymodloader.compat.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgedInterfaceInjection;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.ModJarContents;
import dev.polymodloader.compat.fabric.mixin.MixinConfigEntry;
import dev.polymodloader.compat.fabric.mixin.MixinSupertypes;
import dev.polymodloader.compat.fabric.mixin.MixinTargetSelectors;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.util.VersionNumber;

/**
 * Checks the Mixin configurations a Fabric-family mod names, during assessment (Fabric {@code mixins}, Quilt {@code
 * mixin}). Mixin itself reads and applies them when the game starts (ADR-0020); what can be known from the jar is
 * checked here, with Mixin 0.8.7's own outcome.
 */
public final class MixinConfigCheck {

    private MixinConfigCheck() {}

    /**
     * Checks {@code entries} and adds:
     *
     * <ul>
     *   <li>{@code POLY-COMPAT-132}: a configuration marked {@code required} needs a newer Mixin ({@code minVersion});
     *       Mixin would stop the game, so the mod is left out instead
     *   <li>{@code POLY-COMPAT-136} (not blocking): a configuration that is missing, is not a JSON object, or is not
     *       required and needs a newer Mixin; Mixin logs it and applies the mod's other configurations, as under the
     *       mod's own loader
     *   <li>{@code POLY-COMPAT-140}: an injection point of a mixin for this side whose {@code target} Mixin's selector
     *       parser refuses; blocking for a required configuration (its target class would not load), otherwise
     *       Mixin skips that mixin ({@link MixinTargetSelectors})
     *   <li>{@code POLY-COMPAT-131} (not blocking): which configurations apply on this side
     * </ul>
     *
     * @param metadataFile the metadata file that names them, for messages ({@code fabric.mod.json})
     * @param loaderName the loader whose behaviour the messages compare with ({@code Fabric Loader})
     * @return the interfaces the mixins for this side add to their targets, for the link check ({@link
     *     MixinSupertypes})
     */
    public static List<BridgedInterfaceInjection> check(
            List<MixinConfigEntry> entries,
            String metadataFile,
            String loaderName,
            GameSide side,
            ModJarContents jar,
            List<CompatibilityFinding> findings) {
        List<String> fatal = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (MixinConfigEntry entry : entries) {
            problem(entry.config(), metadataFile, jar)
                    .ifPresent(problem -> (problem.fatal() ? fatal : skipped).add(problem.text()));
        }
        if (!fatal.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-132",
                    true,
                    "mixins",
                    fatal.size() + " required Mixin configuration(s) Mixin would stop the game for: "
                            + ClassTweakerCheck.examples(fatal)));
        }
        if (!skipped.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-136",
                    false,
                    "mixins",
                    skipped.size() + " Mixin configuration(s) Mixin will skip with an error, as under " + loaderName
                            + ": " + ClassTweakerCheck.examples(skipped)));
        }
        List<String> stopping = new ArrayList<>();
        List<String> skippedMixins = new ArrayList<>();
        for (MixinConfigEntry entry : entries) {
            if (!entry.appliesTo(side)) {
                continue;
            }
            List<String> refused = MixinTargetSelectors.refused(entry.config(), jar, side);
            if (!refused.isEmpty()) {
                (MixinTargetSelectors.required(entry.config(), jar) ? stopping : skippedMixins)
                        .addAll(refused.stream()
                                .map(line -> entry.config() + ":" + line)
                                .toList());
            }
        }
        if (!stopping.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-140",
                    true,
                    "mixins",
                    stopping.size() + " injection point(s) of a required Mixin configuration whose target Mixin "
                            + MixinBootstrap.VERSION + " refuses, so the target class would not load (as under "
                            + loaderName + ", which uses the same Mixin): " + ClassTweakerCheck.examples(stopping)));
        }
        if (!skippedMixins.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-140",
                    false,
                    "mixins",
                    skippedMixins.size() + " injection point(s) whose target Mixin " + MixinBootstrap.VERSION
                            + " refuses; Mixin skips that mixin with an error, as under " + loaderName + ": "
                            + ClassTweakerCheck.examples(skippedMixins)));
        }
        List<String> forThisSide = entries.stream()
                .filter(entry -> entry.appliesTo(side))
                .map(MixinConfigEntry::config)
                .toList();
        findings.add(new CompatibilityFinding(
                "POLY-COMPAT-131",
                false,
                "mixins",
                entries.size() + " Mixin configuration(s), " + forThisSide.size() + " for " + side
                        + (forThisSide.isEmpty() ? "" : " (" + ClassTweakerCheck.examples(forThisSide) + ")")
                        + "; Mixin " + MixinBootstrap.VERSION + " applies them as classes load"));
        List<BridgedInterfaceInjection> supertypes = new ArrayList<>();
        entries.stream()
                .filter(entry -> entry.appliesTo(side))
                .forEach(entry -> supertypes.addAll(MixinSupertypes.of(entry.config(), jar, side)));
        return supertypes;
    }

    /** Why Mixin would not apply a configuration, and whether it would stop the game for it. */
    private record Problem(String text, boolean fatal) {}

    /** What Mixin would do with the configuration at {@code path}, if it would not simply apply it. */
    private static Optional<Problem> problem(String path, String metadataFile, ModJarContents jar) {
        Optional<byte[]> bytes;
        try {
            bytes = jar.read(path);
        } catch (UncheckedIOException e) {
            return Optional.of(new Problem(path + " cannot be read: " + e.getCause(), false));
        }
        if (bytes.isEmpty()) {
            return Optional.of(new Problem(path + " is named in " + metadataFile + " but not in the jar", false));
        }
        JsonElement config;
        try {
            config = JsonParser.parseString(new String(bytes.get(), StandardCharsets.UTF_8));
        } catch (JsonParseException e) {
            return Optional.of(new Problem(path + " is not valid JSON: " + e.getMessage(), false));
        }
        if (!(config instanceof JsonObject object)) {
            return Optional.of(new Problem(path + " is not a JSON object", false));
        }
        if (isString(object.get("minVersion"))) {
            String minVersion = object.get("minVersion").getAsString();
            if (VersionNumber.parse(minVersion).compareTo(VersionNumber.parse(MixinBootstrap.VERSION)) > 0) {
                boolean required = object.get("required") instanceof JsonElement value
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isBoolean()
                        && value.getAsBoolean();
                return Optional.of(new Problem(
                        path + " needs Mixin " + minVersion + "; Poly Mod Loader has " + MixinBootstrap.VERSION,
                        required));
            }
        }
        return Optional.empty();
    }

    private static boolean isString(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }
}

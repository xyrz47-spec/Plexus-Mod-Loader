package dev.polymodloader.compat.forge;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.BridgedEntrypoint;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.CompatibilityStatus;
import dev.polymodloader.compat.bridge.ModLoaderBridge;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Diagnosis-only bridge for FML-based loaders (NeoForge and MinecraftForge). FML mods are classes
 * annotated with {@code @Mod} that receive an event bus and depend on FML's lifecycle, registries,
 * and class processors, none of which Poly Mod Loader provides yet, so every such mod is {@code BLOCKED}; the
 * findings explain exactly which FML features it needs. Every {@code [[mods]]} ID of the jar is reported,
 * so a mod of another loader that requires one of them is told that it is installed but blocked.
 *
 * <p>Values of an unexpected TOML type are reported as {@code POLY-COMPAT-200}, never thrown.
 */
abstract class FmlModsTomlBridge implements ModLoaderBridge {

    @Override
    public BridgeAssessment assess(String metadata, GameSide side) {
        TomlParseResult toml = Toml.parse(metadata);
        List<CompatibilityFinding> findings = new ArrayList<>();
        if (toml.hasErrors()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-200",
                    true,
                    "metadata",
                    "unreadable TOML: " + toml.errors().getFirst()));
            return BridgeAssessment.of(loaderName(), "?", "?", findings, List.of(), CompatibilityStatus.BLOCKED);
        }
        List<String> ids = new ArrayList<>();
        String version = "?";
        if (toml.get("mods") instanceof TomlArray mods) {
            for (int i = 0; i < mods.size(); i++) {
                if (mods.get(i) instanceof TomlTable mod && mod.get("modId") instanceof String modId) {
                    ids.add(modId);
                    if (i == 0 && mod.get("version") instanceof String text) {
                        version = text;
                    }
                }
            }
        }
        if (ids.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-200", true, "metadata", "no [[mods]] entry with a text modId"));
        }
        findings.add(new CompatibilityFinding(
                "POLY-COMPAT-201",
                true,
                "FML language provider",
                "modLoader '" + text(toml.get("modLoader")) + "' needs FML to construct @Mod classes with "
                        + "the mod event bus; Poly Mod Loader has no FML runtime"));
        if (toml.get("mixins") instanceof TomlArray mixins && !mixins.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-202", true, "mixins", mixins.size() + " Mixin configuration(s)"));
        }
        if (toml.get("accessTransformers") instanceof TomlArray transformers && !transformers.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-203", true, "access transformers", transformers.size() + " file(s) declared"));
        }
        if (toml.get("dependencies") instanceof TomlTable dependencies) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-204",
                    false,
                    "dependencies",
                    "declares dependencies for " + dependencies.keySet() + "; not evaluated"));
        }
        String modId = ids.isEmpty() ? "?" : ids.getFirst();
        List<String> provided = ids.size() > 1 ? ids.subList(1, ids.size()) : List.of();
        return BridgeAssessment.of(
                loaderName(), modId, version, findings, List.of(), provided, List.of(), CompatibilityStatus.BLOCKED);
    }

    private static String text(Object value) {
        return value instanceof String text ? text : "?";
    }

    @Override
    public void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) {
        throw new UnsupportedOperationException(loaderName() + " mods are never loaded by this bridge");
    }
}

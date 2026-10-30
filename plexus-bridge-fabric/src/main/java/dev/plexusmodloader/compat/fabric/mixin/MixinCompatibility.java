/*
 * The version table and the rule are from Fabric Loader 0.19.5 (net.fabricmc.loader.impl.launch.FabricMixinVersions
 * and FabricMixinBootstrap.MixinConfigDecorator.getMixinCompat). Copyright 2016 FabricMC. Licensed under the Apache
 * License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0). See THIRD_PARTY_NOTICES.md.
 */
package dev.plexusmodloader.compat.fabric.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.spongepowered.asm.mixin.FabricUtil;

/**
 * The Mixin compatibility level of a Fabric mod, decided as Fabric Loader decides it: from the lowest Fabric Loader
 * version the mod accepts ({@code depends} and {@code breaks} on {@code fabricloader}). Mixin keeps older behaviour
 * for mods that accept older loaders, so a mod sees the Mixin it was tested with.
 */
final class MixinCompatibility {

    /** Lowest Fabric Loader version of each Mixin compatibility level, newest first. */
    private static final List<Map.Entry<SemanticVersion, Integer>> LEVELS = List.of(
            level("0.19.4", FabricUtil.COMPATIBILITY_0_17_4),
            level("0.19.0", FabricUtil.COMPATIBILITY_0_17_1),
            level("0.18.4", FabricUtil.COMPATIBILITY_0_17_0),
            level("0.17.3", FabricUtil.COMPATIBILITY_0_16_5),
            level("0.16.0", FabricUtil.COMPATIBILITY_0_14_0),
            level("0.12.0-", FabricUtil.COMPATIBILITY_0_10_0));

    private MixinCompatibility() {}

    /**
     * The compatibility level for the mod whose {@code fabric.mod.json} is {@code root}.
     *
     * @throws IllegalArgumentException when its {@code fabricloader} requirements accept no version at all, or
     *     cannot be read (the bridge has already blocked such a mod)
     */
    static int of(JsonObject root) {
        List<VersionInterval> accepted = List.of(VersionInterval.INFINITE);
        accepted = restrict(accepted, root.get("depends"), false);
        accepted = restrict(accepted, root.get("breaks"), true);
        if (accepted.isEmpty()) {
            throw new IllegalArgumentException(
                    "the mod's fabricloader requirements accept no Fabric Loader version at all");
        }
        Version lowest = accepted.getFirst().getMin();
        if (lowest != null) {
            for (Map.Entry<SemanticVersion, Integer> level : LEVELS) {
                if (lowest.compareTo(level.getKey()) >= 0) {
                    return level.getValue();
                }
            }
        }
        return FabricUtil.COMPATIBILITY_0_9_2;
    }

    /** The lowest Fabric Loader version whose Mixin has {@code level}, for messages. */
    static Optional<String> minimumLoaderVersion(int level) {
        return LEVELS.stream()
                .filter(entry -> entry.getValue() == level)
                .map(entry -> entry.getKey().getFriendlyString())
                .findFirst();
    }

    private static List<VersionInterval> restrict(
            List<VersionInterval> accepted, JsonElement relationships, boolean breaks) {
        if (!(relationships instanceof JsonObject object)) {
            return accepted;
        }
        List<VersionInterval> result = accepted;
        for (String id : List.of("fabricloader", "fabric-loader")) {
            JsonElement value = object.get(id);
            if (value == null) {
                continue;
            }
            List<VersionInterval> intervals = intervals(value);
            result = VersionInterval.and(result, breaks ? VersionInterval.not(intervals) : intervals);
        }
        return result;
    }

    /** The versions one requirement accepts: any of its predicates (an array means "any of"). */
    private static List<VersionInterval> intervals(JsonElement value) {
        List<String> texts = new ArrayList<>();
        if (value instanceof JsonArray array) {
            array.forEach(element -> texts.add(element.getAsString()));
        } else {
            texts.add(value.getAsString());
        }
        List<VersionInterval> union = new ArrayList<>();
        try {
            Collection<VersionPredicate> predicates = VersionPredicate.parse(texts);
            for (VersionPredicate predicate : predicates) {
                union = VersionInterval.or(union, predicate.getInterval());
            }
        } catch (VersionParsingException e) {
            throw new IllegalArgumentException("unreadable fabricloader requirement " + texts, e);
        }
        return union;
    }

    private static Map.Entry<SemanticVersion, Integer> level(String loaderVersion, int mixinLevel) {
        try {
            return Map.entry(SemanticVersion.parse(loaderVersion), mixinLevel);
        } catch (VersionParsingException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}

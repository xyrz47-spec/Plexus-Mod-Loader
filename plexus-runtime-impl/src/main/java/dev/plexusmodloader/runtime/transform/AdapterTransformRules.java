package dev.plexusmodloader.runtime.transform;

import dev.plexusmodloader.game.version.GameVersionAdapter;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import java.util.List;
import java.util.Set;

/**
 * The built-in rules of the version adapter that runs the game, for the exact game version that runs.
 *
 * <p>An adapter may cover several version IDs ({@link GameVersionAdapter#gameVersions()}, ADR-0011):
 * hotfixes whose transformation targets did not change. Its rules name one of them, but the planner
 * accepts a rule only for the version it names. A rule that names one of the adapter's own versions is
 * therefore planned for the running version, which the adapter vouches for; a rule that names any other
 * version is left as it is, so the planner still rejects it.
 */
public final class AdapterTransformRules {

    private AdapterTransformRules() {}

    /**
     * Returns {@code adapter}'s built-in rules for {@code gameVersion}.
     *
     * @param adapter the adapter selected for the game, so {@code gameVersions()} contains {@code gameVersion}
     * @param gameVersion the version ID of the game that runs
     * @throws IllegalArgumentException when {@code adapter} does not cover {@code gameVersion}
     */
    public static List<TransformRuleDefinition> forGameVersion(GameVersionAdapter adapter, String gameVersion) {
        Set<String> covered = adapter.gameVersions();
        if (!covered.contains(gameVersion)) {
            throw new IllegalArgumentException(
                    "adapter " + adapter.adapterId() + " covers " + covered + ", not Minecraft " + gameVersion);
        }
        return adapter.builtInTransformRules().stream()
                .map(rule -> covered.contains(rule.gameVersion())
                                && !rule.gameVersion().equals(gameVersion)
                        ? new TransformRuleDefinition(
                                rule.ruleId(),
                                rule.ownerModId(),
                                gameVersion,
                                rule.sides(),
                                rule.target(),
                                rule.action(),
                                rule.failurePolicy(),
                                rule.after())
                        : rule)
                .toList();
    }
}

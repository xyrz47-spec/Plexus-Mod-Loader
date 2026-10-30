package dev.polymodloader.api.content.rule;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * A declared game rule, set per world with {@code /gamerule <id> <value>} and read with
 * {@code WorldView.gameRule(key)}.
 *
 * @param <T> {@link Boolean} or {@link Integer}
 * @param id rule ID, {@code <mod id>:<path>}
 * @param type value class
 * @param defaultValue value in new worlds
 * @param min smallest allowed integer (ignored for booleans)
 * @param max largest allowed integer (ignored for booleans)
 * @param category rule screen section
 */
@ApiStability(StabilityLevel.PREVIEW)
public record GameRuleKey<T>(
        ResourceId id, Class<T> type, T defaultValue, int min, int max, GameRuleCategory category) {

    public GameRuleKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(category, "category");
        if (type != Boolean.class && type != Integer.class) {
            throw new IllegalArgumentException("Game rules hold Boolean or Integer values");
        }
        if (type == Integer.class && ((Integer) defaultValue < min || (Integer) defaultValue > max || min > max)) {
            throw new IllegalArgumentException("Default " + defaultValue + " is outside " + min + ".." + max);
        }
    }
}

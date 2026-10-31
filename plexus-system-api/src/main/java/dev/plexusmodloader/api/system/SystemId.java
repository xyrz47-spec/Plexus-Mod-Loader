package dev.plexusmodloader.api.system;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Identifier of a replaceable Plexus Mod Loader system in {@code namespace:path} form, e.g. {@code plexus:game_brand}.
 *
 * @param value the identifier text
 */
@ApiStability(StabilityLevel.PREVIEW)
public record SystemId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]*:[a-z0-9_/]+");

    public SystemId {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("System ID must look like namespace:path, got '" + value + "'");
        }
    }

    /** Parses {@code value}, or returns empty when it is not a valid system ID. */
    public static Optional<SystemId> parse(String value) {
        return FORMAT.matcher(value).matches() ? Optional.of(new SystemId(value)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}

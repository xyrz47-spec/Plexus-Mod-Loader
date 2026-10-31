package dev.plexusmodloader.runtime.system;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.system.brand.GameBrandSystem;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The systems Plexus Mod Loader defines. New systems are added here together with their default provider. */
public final class BuiltInSystems {

    private static final Pattern BRAND = Pattern.compile("[\\x21-\\x7e](?:[\\x20-\\x7e]{0,62}[\\x21-\\x7e])?");

    private BuiltInSystems() {}

    /** All built-in system definitions. */
    public static List<SystemDefinition<?>> definitions() {
        return List.of(gameBrand());
    }

    static SystemDefinition<GameBrandSystem> gameBrand() {
        return new SystemDefinition<>(
                GameBrandSystem.ID,
                "Game Brand",
                GameBrandSystem.class,
                true,
                new PlexusGameBrandProvider(),
                BuiltInSystems::validateBrand);
    }

    private static Optional<String> validateBrand(GameBrandSystem system) {
        for (GameSide side : GameSide.values()) {
            String brand = system.brand(side);
            if (brand == null || !BRAND.matcher(brand).matches()) {
                return Optional.of("brand(" + side + ") returned " + (brand == null ? "null" : "'" + brand + "'")
                        + "; expected 1-64 printable ASCII characters without leading or trailing spaces");
            }
        }
        return Optional.empty();
    }
}

package dev.plexusmodloader.runtime.system;

import dev.plexusmodloader.api.system.PlexusSystem;
import dev.plexusmodloader.api.system.SystemId;
import dev.plexusmodloader.api.system.SystemProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Plexus Mod Loader's definition of one system: its contract, default provider, whether mods may replace it, and
 * how an implementation is validated before use (design doc §4.3, §5.6).
 *
 * @param id system ID
 * @param displayName human-readable name for diagnostics
 * @param contract interface every implementation must implement
 * @param replaceable whether mods may replace the default
 * @param defaultProvider Plexus Mod Loader's own implementation
 * @param validator returns a problem description when an implementation breaks the contract
 * @param <T> the system contract
 */
public record SystemDefinition<T extends PlexusSystem>(
        SystemId id,
        String displayName,
        Class<T> contract,
        boolean replaceable,
        SystemProvider<T> defaultProvider,
        Function<T, Optional<String>> validator) {

    public SystemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(defaultProvider, "defaultProvider");
        Objects.requireNonNull(validator, "validator");
        if (!contract.isInterface()) {
            throw new IllegalArgumentException("System contract must be an interface: " + contract.getName());
        }
    }
}

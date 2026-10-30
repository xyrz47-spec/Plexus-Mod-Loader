package dev.polymodloader.runtime.metadata;

import java.util.Locale;
import java.util.Optional;

/** Where a mod may be loaded, from the {@code environment} field of {@code poly.mod.json}. */
public enum ModEnvironment {
    CLIENT,
    SERVER,
    BOTH;

    /** Parses the JSON spelling ({@code client}, {@code server}, {@code both}). */
    public static Optional<ModEnvironment> parse(String value) {
        for (ModEnvironment environment : values()) {
            if (environment.jsonName().equals(value)) {
                return Optional.of(environment);
            }
        }
        return Optional.empty();
    }

    /** The spelling used in {@code poly.mod.json}. */
    public String jsonName() {
        return name().toLowerCase(Locale.ROOT);
    }
}

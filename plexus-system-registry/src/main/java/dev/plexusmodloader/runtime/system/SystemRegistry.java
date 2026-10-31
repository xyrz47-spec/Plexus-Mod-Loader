package dev.plexusmodloader.runtime.system;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.system.PlexusSystem;
import dev.plexusmodloader.api.system.SystemContext;
import dev.plexusmodloader.api.system.SystemId;
import dev.plexusmodloader.api.system.SystemLookup;
import dev.plexusmodloader.api.system.SystemProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the one active implementation of every system after replacement resolution. Creating the
 * registry instantiates every provider, checks the result against the system contract, and runs the
 * system's validator, so a broken replacement stops the launch instead of failing later inside the
 * game.
 */
public final class SystemRegistry implements SystemLookup {

    /**
     * One active system, for diagnostics (System Explorer, design doc §5.10).
     *
     * @param systemId the system
     * @param displayName human-readable name
     * @param providerModId {@code plexus} for the default, otherwise the replacing mod
     * @param providerClass provider class name
     * @param overriddenModIds mods whose replacement was overridden
     */
    public record ActiveSystem(
            SystemId systemId,
            String displayName,
            String providerModId,
            String providerClass,
            List<String> overriddenModIds) {
        public ActiveSystem {
            overriddenModIds = List.copyOf(overriddenModIds);
        }

        /** Whether Plexus Mod Loader's own default provider is active. */
        public boolean isDefault() {
            return providerModId.equals(DEFAULT_PROVIDER_MOD_ID)
                    || providerModId.equals(LEGACY_DEFAULT_PROVIDER_MOD_ID);
        }
    }

    /** Thrown when one or more providers could not be created or validated. */
    public static final class SystemSetupException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient List<String> problems;

        SystemSetupException(List<String> problems) {
            super(String.join(System.lineSeparator(), problems));
            this.problems = List.copyOf(problems);
        }

        /** One line per problem, each starting with its error code. */
        public List<String> problems() {
            return problems;
        }
    }

    static final String DEFAULT_PROVIDER_MOD_ID = "plexus";
    static final String LEGACY_DEFAULT_PROVIDER_MOD_ID = "plexus";

    private final Map<Class<?>, PlexusSystem> instances;
    private final List<ActiveSystem> activeSystems;

    private SystemRegistry(Map<Class<?>, PlexusSystem> instances, List<ActiveSystem> activeSystems) {
        this.instances = Map.copyOf(instances);
        this.activeSystems = List.copyOf(activeSystems);
    }

    /**
     * Creates every system.
     *
     * @param definitions built-in system definitions
     * @param replacements resolved replacements
     * @param side the process side
     * @param modClassLoader loader that can load mod provider classes
     * @throws SystemSetupException listing every provider that failed
     */
    public static SystemRegistry create(
            List<SystemDefinition<?>> definitions,
            SystemReplacementResolver.Result replacements,
            GameSide side,
            ClassLoader modClassLoader) {
        Map<Class<?>, PlexusSystem> instances = new LinkedHashMap<>();
        List<ActiveSystem> active = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (SystemDefinition<?> definition : definitions) {
            SystemReplacementResolver.SelectedReplacement replacement =
                    replacements.replacements().get(definition.id());
            try {
                PlexusSystem instance = replacement == null
                        ? createDefault(definition, side)
                        : createReplacement(definition, replacement, side, modClassLoader);
                instances.put(definition.contract(), instance);
                active.add(
                        replacement == null
                                ? new ActiveSystem(
                                        definition.id(),
                                        definition.displayName(),
                                        DEFAULT_PROVIDER_MOD_ID,
                                        definition.defaultProvider().getClass().getName(),
                                        List.of())
                                : new ActiveSystem(
                                        definition.id(),
                                        definition.displayName(),
                                        replacement.modId(),
                                        replacement.providerClass(),
                                        replacement.overriddenModIds()));
            } catch (ProviderFailure failure) {
                problems.add(SystemProblemCode.PROVIDER_FAILED.code() + " system=" + definition.id() + " provider="
                        + failure.provider + " mod=" + failure.modId + ": " + failure.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new SystemSetupException(problems);
        }
        return new SystemRegistry(instances, active);
    }

    @Override
    public <T extends PlexusSystem> T get(Class<T> contract) {
        PlexusSystem instance = instances.get(contract);
        if (instance == null) {
            throw new IllegalArgumentException("No system registered for contract " + contract.getName());
        }
        return contract.cast(instance);
    }

    /** Every active system and who provides it, in definition order. */
    public List<ActiveSystem> activeSystems() {
        return activeSystems;
    }

    private static <T extends PlexusSystem> T createDefault(SystemDefinition<T> definition, GameSide side) {
        SystemContext context = new SystemContext(definition.id(), side, DEFAULT_PROVIDER_MOD_ID);
        return checked(
                definition,
                definition.defaultProvider().create(context),
                DEFAULT_PROVIDER_MOD_ID,
                definition.defaultProvider().getClass().getName());
    }

    private static <T extends PlexusSystem> T createReplacement(
            SystemDefinition<T> definition,
            SystemReplacementResolver.SelectedReplacement replacement,
            GameSide side,
            ClassLoader modClassLoader) {
        String providerName = replacement.providerClass();
        String modId = replacement.modId();
        Object provider;
        try {
            Class<?> providerType = Class.forName(providerName, true, modClassLoader);
            if (!SystemProvider.class.isAssignableFrom(providerType)) {
                throw new ProviderFailure(modId, providerName, "does not implement " + SystemProvider.class.getName());
            }
            provider = providerType.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new ProviderFailure(modId, providerName, "could not be instantiated: " + e);
        }
        Object created;
        try {
            created = ((SystemProvider<?>) provider).create(new SystemContext(definition.id(), side, modId));
        } catch (RuntimeException e) {
            throw new ProviderFailure(modId, providerName, "create() threw " + e);
        }
        if (!definition.contract().isInstance(created)) {
            throw new ProviderFailure(
                    modId,
                    providerName,
                    "create() returned "
                            + (created == null ? "null" : created.getClass().getName()) + ", which does not implement "
                            + definition.contract().getName());
        }
        return checked(definition, definition.contract().cast(created), modId, providerName);
    }

    private static <T extends PlexusSystem> T checked(
            SystemDefinition<T> definition, T instance, String modId, String provider) {
        if (instance == null) {
            throw new ProviderFailure(modId, provider, "create() returned null");
        }
        Optional<String> problem;
        try {
            problem = definition.validator().apply(instance);
        } catch (RuntimeException e) {
            throw new ProviderFailure(modId, provider, "implementation threw during validation: " + e);
        }
        if (problem.isPresent()) {
            throw new ProviderFailure(modId, provider, "breaks the system contract: " + problem.get());
        }
        return instance;
    }

    private static final class ProviderFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String modId;
        private final String provider;

        ProviderFailure(String modId, String provider, String message) {
            super(message);
            this.modId = modId;
            this.provider = provider;
        }
    }
}

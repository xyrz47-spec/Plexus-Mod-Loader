package dev.plexusmodloader.runtime.lifecycle;

import dev.plexusmodloader.api.client.ClientRegistrar;
import dev.plexusmodloader.api.command.CommandRegistrar;
import dev.plexusmodloader.api.content.ContentRegistrar;
import dev.plexusmodloader.api.devtools.gametest.GameTestRegistrar;
import dev.plexusmodloader.api.event.EventRegistry;
import dev.plexusmodloader.api.lifecycle.ClientModInitializer;
import dev.plexusmodloader.api.lifecycle.CommonModInitializer;
import dev.plexusmodloader.api.lifecycle.DedicatedServerModInitializer;
import dev.plexusmodloader.api.lifecycle.LoadedMods;
import dev.plexusmodloader.api.lifecycle.ModContext;
import dev.plexusmodloader.api.network.NetworkRegistrar;
import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.system.SystemLookup;
import dev.plexusmodloader.api.worldgen.WorldgenRegistrar;
import dev.plexusmodloader.runtime.diagnostics.PolyLaunchException;
import dev.plexusmodloader.runtime.event.EventBus;
import dev.plexusmodloader.runtime.metadata.ModMetadata;
import dev.plexusmodloader.runtime.nativeapi.NativeRegistrations;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Instantiates and calls mod entrypoints: all common entrypoints in dependency order, then all
 * side-specific ones in dependency order. Entrypoint classes are loaded through the game class
 * loader so they can reference game classes.
 */
public final class EntrypointInvoker {

    private final ClassLoader gameClassLoader;
    private final GameSide side;
    private final ShutdownCoordinator shutdown;
    private final SystemLookup systems;
    private final EventBus events;
    private final NativeRegistrations registrations;
    private final LoadedMods loadedMods;

    public EntrypointInvoker(
            ClassLoader gameClassLoader,
            GameSide side,
            ShutdownCoordinator shutdown,
            SystemLookup systems,
            EventBus events,
            NativeRegistrations registrations,
            LoadedMods loadedMods) {
        this.events = events;
        this.loadedMods = loadedMods;
        this.registrations = registrations;
        this.gameClassLoader = gameClassLoader;
        this.side = side;
        this.shutdown = shutdown;
        this.systems = systems;
    }

    /** Runs every entrypoint of {@code mods}, which must already be in dependency order. */
    public void invokeAll(List<ModMetadata> mods) {
        for (ModMetadata mod : mods) {
            invokeCommon(mod);
        }
        for (ModMetadata mod : mods) {
            invokeSide(mod);
        }
    }

    /** Runs the common entrypoints of one mod. */
    public void invokeCommon(ModMetadata mod) {
        invoke(mod, mod.entrypoints().common(), CommonModInitializer.class, CommonModInitializer::onInitializeCommon);
    }

    /** Runs the client or dedicated-server entrypoints of one mod, for this process's side. */
    public void invokeSide(ModMetadata mod) {
        switch (side) {
            case CLIENT ->
                invoke(
                        mod,
                        mod.entrypoints().client(),
                        ClientModInitializer.class,
                        ClientModInitializer::onInitializeClient);
            case DEDICATED_SERVER ->
                invoke(
                        mod,
                        mod.entrypoints().server(),
                        DedicatedServerModInitializer.class,
                        DedicatedServerModInitializer::onInitializeDedicatedServer);
        }
    }

    private <T> void invoke(
            ModMetadata mod, List<String> classNames, Class<T> contract, BiConsumer<T, ModContext> call) {
        ModContext context = new RuntimeModContext(
                mod.id().value(),
                mod.version().toString(),
                side,
                loadedMods,
                shutdown,
                systems,
                events.registryFor(mod.id().value()),
                registrations.forMod(mod.id().value()));
        for (String className : classNames) {
            T entrypoint = instantiate(mod, className, contract);
            try {
                call.accept(entrypoint, context);
            } catch (RuntimeException | LinkageError e) {
                throw new PolyLaunchException(
                        RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                        "PLEXUS-ENTRYPOINT-003 mod=" + mod.id() + " entrypoint=" + className + " threw "
                                + e.getClass().getName() + ": " + e.getMessage(),
                        e);
            }
        }
    }

    private <T> T instantiate(ModMetadata mod, String className, Class<T> contract) {
        Class<?> type;
        try {
            type = Class.forName(className, true, gameClassLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new PolyLaunchException(
                    RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                    "PLEXUS-ENTRYPOINT-001 mod=" + mod.id() + " entrypoint class " + className
                            + " could not be loaded: " + e,
                    e);
        }
        if (!contract.isAssignableFrom(type)) {
            throw new PolyLaunchException(
                    RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                    List.of("PLEXUS-ENTRYPOINT-002 mod=" + mod.id() + " entrypoint " + className + " must implement "
                            + contract.getName()));
        }
        try {
            return contract.cast(type.getConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            throw new PolyLaunchException(
                    RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                    "PLEXUS-ENTRYPOINT-002 mod=" + mod.id() + " entrypoint " + className
                            + " needs a public no-argument constructor",
                    e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            Throwable cause = e instanceof InvocationTargetException ite ? ite.getCause() : e;
            throw new PolyLaunchException(
                    RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                    "PLEXUS-ENTRYPOINT-003 mod=" + mod.id() + " entrypoint " + className + " constructor failed: "
                            + cause,
                    cause);
        }
    }

    private record RuntimeModContext(
            String modId,
            String modVersion,
            GameSide side,
            LoadedMods mods,
            ShutdownCoordinator shutdown,
            SystemLookup systems,
            EventRegistry events,
            NativeRegistrations.ModRegistrars registrars)
            implements ModContext {

        @Override
        public void onShutdown(String description, Runnable task) {
            shutdown.register(modId, description, task);
        }

        @Override
        public java.nio.file.Path configDirectory() {
            java.nio.file.Path directory =
                    java.nio.file.Path.of("config", modId).toAbsolutePath();
            try {
                java.nio.file.Files.createDirectories(directory);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException("Cannot create " + directory, e);
            }
            return directory;
        }

        @Override
        public ContentRegistrar content() {
            return registrars;
        }

        @Override
        public dev.plexusmodloader.api.resource.ResourceRegistrar resources() {
            return registrars;
        }

        @Override
        public dev.plexusmodloader.api.permission.PermissionRegistrar permissions() {
            return registrars;
        }

        @Override
        public CommandRegistrar commands() {
            return registrars;
        }

        @Override
        public NetworkRegistrar network() {
            return registrars;
        }

        @Override
        public WorldgenRegistrar worldgen() {
            return registrars;
        }

        @Override
        public GameTestRegistrar gameTests() {
            return registrars;
        }

        @Override
        public ClientRegistrar client() {
            return registrars.client();
        }
    }
}

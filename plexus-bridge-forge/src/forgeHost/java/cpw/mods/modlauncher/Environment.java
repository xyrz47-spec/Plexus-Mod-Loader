package cpw.mods.modlauncher;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.INameMappingService;
import cpw.mods.modlauncher.api.TypesafeMap;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Plexus Mod Loader's implementation of ModLauncher's environment (ADR-0021), written from the public {@link IEnvironment}
 * contract: properties in a {@link TypesafeMap} owned by {@code IEnvironment}, and lookups that Plexus Mod Loader's {@code
 * PlexusModLauncher} answers. FML (`fmlloader`) and the access transformer library reach it as {@code
 * Launcher.INSTANCE.environment()}, so it keeps ModLauncher's class name and members. Not ModLauncher's code.
 */
public final class Environment implements IEnvironment {

    private final TypesafeMap properties = new TypesafeMap(IEnvironment.class);
    private final Function<String, Optional<ILaunchPluginService>> launchPlugins;
    private final Function<String, Optional<ILaunchHandlerService>> launchHandlers;
    private final Supplier<Optional<IModuleLayerManager>> layerManager;
    private final Function<String, Optional<BiFunction<INameMappingService.Domain, String, String>>> nameMappings;

    public Environment(
            Function<String, Optional<ILaunchPluginService>> launchPlugins,
            Function<String, Optional<ILaunchHandlerService>> launchHandlers,
            Supplier<Optional<IModuleLayerManager>> layerManager,
            Function<String, Optional<BiFunction<INameMappingService.Domain, String, String>>> nameMappings) {
        this.launchPlugins = launchPlugins;
        this.launchHandlers = launchHandlers;
        this.layerManager = layerManager;
        this.nameMappings = nameMappings;
    }

    @Override
    public <T> Optional<T> getProperty(TypesafeMap.Key<T> key) {
        return properties.get(key);
    }

    @Override
    public <T> T computePropertyIfAbsent(
            TypesafeMap.Key<T> key, Function<? super TypesafeMap.Key<T>, ? extends T> valueFunction) {
        return properties.computeIfAbsent(key, valueFunction);
    }

    @Override
    public Optional<ILaunchPluginService> findLaunchPlugin(String name) {
        return launchPlugins.apply(name);
    }

    @Override
    public Optional<ILaunchHandlerService> findLaunchHandler(String name) {
        return launchHandlers.apply(name);
    }

    @Override
    public Optional<IModuleLayerManager> findModuleLayerManager() {
        return layerManager.get();
    }

    @Override
    public Optional<BiFunction<INameMappingService.Domain, String, String>> findNameMapping(String targetMapping) {
        return nameMappings.apply(targetMapping);
    }
}

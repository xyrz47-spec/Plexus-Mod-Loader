package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What the bridges learn about a host loader that patched the game and starts it (ADR-0021: MinecraftForge; ADR-0026:
 * NeoForge), when mods of other loaders run in the same launch (G8).
 *
 * @param hostName the host for logs ({@link GameHost#name()}), empty when no host runs
 * @param sharedMixinConfigs the host's mods' Mixin configurations, for the bridge whose Mixin serves every loader; for a
 *     host that learns them only while it starts its loader ({@link GameHost#sharesMixin()} with an empty {@link
 *     GameHost#sharedMixinConfigs()} before {@link GameHost#prepare}), the runtime hands the bridges a new launch with
 *     them before {@link ModLoaderBridge#beforeGame}
 * @param injectionPointReplacements injection points the host's patch moved ({@link GameHost#injectionPointReplacements()})
 * @param vanillaGameJar the game jar before the host's patch, to compare with the patched one; {@code null} without a
 *     host
 * @param sharesMixin whether the host's mods use the Mixin of the bridge that runs one ({@link GameHost#sharesMixin()}),
 *     so that bridge starts its Mixin even when its own mods have no configuration
 */
@ApiStability(StabilityLevel.INTERNAL)
public record HostedLaunch(
        String hostName,
        List<SharedMixinConfig> sharedMixinConfigs,
        List<InjectionPointReplacement> injectionPointReplacements,
        Path vanillaGameJar,
        boolean sharesMixin) {

    /** A launch without a host: the vanilla game. */
    public static final HostedLaunch NONE = new HostedLaunch("", List.of(), List.of(), null, false);

    public HostedLaunch {
        Objects.requireNonNull(hostName, "hostName");
        sharedMixinConfigs = List.copyOf(sharedMixinConfigs);
        injectionPointReplacements = List.copyOf(injectionPointReplacements);
    }

    /** A host whose mods share the Mixin exactly when it reports configurations for it (MinecraftForge). */
    public HostedLaunch(
            String hostName,
            List<SharedMixinConfig> sharedMixinConfigs,
            List<InjectionPointReplacement> injectionPointReplacements,
            Path vanillaGameJar) {
        this(hostName, sharedMixinConfigs, injectionPointReplacements, vanillaGameJar, !sharedMixinConfigs.isEmpty());
    }

    /** Whether a host patched the game: other loaders' mods run against its classes, not vanilla's. */
    public boolean isHosted() {
        return !hostName.isEmpty();
    }

    /** This launch with {@code configs} as the host's shared Mixin configurations (known once the host started). */
    public HostedLaunch withSharedMixinConfigs(List<SharedMixinConfig> configs) {
        return new HostedLaunch(
                hostName, configs, injectionPointReplacements, vanillaGameJar, sharesMixin || !configs.isEmpty());
    }
}

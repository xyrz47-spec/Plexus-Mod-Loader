package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A Mixin configuration of a mod that another loader runs (a Forge mod's {@code MixinConfigs}, a NeoForge mod's {@code
 * [[mixins]]}), applied by the one Mixin that serves every loader in a launch where several loaders' mods use Mixin
 * (ADR-0021 G8, ADR-0026). A class loader can hold only one Mixin, so the bridge whose Mixin runs ({@link
 * BridgeLaunchContext#sharedMixinConfigs()}) adds these next to its own mods' configurations.
 *
 * @param loaderName the loader that runs the mod, such as {@code MinecraftForge}
 * @param modId the mod's ID, or the jar's file name when the jar declares none
 * @param config the configuration's resource name, such as {@code example.mixins.json}
 * @param compatibility the Mixin behaviour the mod's loader gives the configuration (FabricMC Mixin's {@code
 *     fabric-compat} decoration, such as NeoForge's {@code behaviorVersion}); empty for the newest behaviour
 */
@ApiStability(StabilityLevel.INTERNAL)
public record SharedMixinConfig(String loaderName, String modId, String config, OptionalInt compatibility) {

    public SharedMixinConfig {
        Objects.requireNonNull(loaderName, "loaderName");
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(compatibility, "compatibility");
    }

    /** A configuration with the newest Mixin behaviour, as upstream Mixin applies it (Forge mods). */
    public SharedMixinConfig(String loaderName, String modId, String config) {
        this(loaderName, modId, config, OptionalInt.empty());
    }
}

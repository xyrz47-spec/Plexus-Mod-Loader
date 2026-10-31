package dev.plexusmodloader.compat.forge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.compat.bridge.BridgeClassTransformer;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import dev.plexusmodloader.compat.bridge.GameModuleLayers;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * FML set up by Plexus Mod Loader's implementation of ModLauncher's contracts (ADR-0021), ready to start the game. The
 * implementation runs inside the boot layer's {@code cpw.mods.modlauncher} module ({@code PlexusModLauncher}, built from
 * the {@code forgeHost} source set); this side and that one meet only through this interface, JDK types and the bridge
 * API, which both see from Plexus Mod Loader's class path.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface ForgeLaunch {

    /**
     * What {@code PlexusModLauncher.start} needs.
     *
     * @param launchTarget Forge's launch handler: {@code forge_server}, {@code forge_client}, or Plexus Mod Loader's {@code
     *     plexusmodloader_server_gametest}
     * @param minecraftVersion the game version, ModLauncher's {@code --version}
     * @param modJars the Forge mods Plexus Mod Loader found and FML will accept; those that provide a ModLauncher or FML
     *     service go to the SERVICE layer, in place of FML's own scan of the mods folder
     * @param otherMods the Plexus Mods and other loaders' mods in this launch, which FML's mod list answers for
     *     (ADR-0025)
     * @param bootLayer the layer with FML's loader libraries and the {@code cpw.mods.modlauncher} module
     * @param layers defines the SERVICE, PLUGIN and GAME layers in the game class loader
     * @param classes the game class loader's classes, for launch plugins that read other classes
     * @param layerModules applied to each module of the SERVICE, PLUGIN and GAME layers before the layer is defined;
     *     Plexus Mod Loader's checked patches of FML's own classes ({@link ForgeModListPatch}, ADR-0025)
     * @param sharedMixin whether the Mixin on Plexus Mod Loader's class path serves the Forge mods too (Fabric mods run in
     *     the same launch, ADR-0021 G8); the GAME layer then leaves out the MixinExtras jars FML selected, as MixinExtras
     *     comes from that class path
     */
    record Request(
            String launchTarget,
            String minecraftVersion,
            Path gameDirectory,
            List<String> gameArguments,
            List<Path> modJars,
            ForeignModIndex otherMods,
            ModuleLayer bootLayer,
            GameModuleLayers layers,
            GameClassBytes classes,
            UnaryOperator<ModuleReference> layerModules,
            boolean sharedMixin,
            Consumer<String> log) {
        public Request {
            gameArguments = List.copyOf(gameArguments);
            modJars = List.copyOf(modJars);
            Objects.requireNonNull(otherMods, "otherMods");
            Objects.requireNonNull(layerModules, "layerModules");
        }
    }

    /** The GAME layer: Minecraft, Forge, FML's game libraries and the Forge mods FML found. */
    ModuleLayer gameLayer();

    /** ModLauncher's class transformation: launch plugins (access transformers, …) and transformation services. */
    BridgeClassTransformer classTransformer();

    /** Runs Forge's launch handler, which starts Minecraft from the GAME layer. */
    void launch() throws Throwable;
}

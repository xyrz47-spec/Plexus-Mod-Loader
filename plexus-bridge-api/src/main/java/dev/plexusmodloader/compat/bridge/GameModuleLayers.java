package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.lang.module.Configuration;
import java.util.List;

/**
 * Defines JPMS module layers whose modules belong to the game class loader (ADR-0021), for a loader whose code and
 * mods expect modules (Forge). The game class loader stays the one owner of every class it defines.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface GameModuleLayers {

    /**
     * Defines {@code configuration}'s modules in the game class loader.
     *
     * <p>Call it before any class of those modules' packages is loaded; the layer's classes are found by package
     * from then on.
     *
     * @param parents the parent layers, such as the boot layer or a layer defined earlier
     * @param transformed whether the classes go through Plexus Mod Loader's transformer and the bridges' stages (game and
     *     mod classes) or are defined as they are (a loader's own code)
     * @return the layer's controller, for adding reads, exports and opens the descriptors do not declare
     * @throws java.lang.LayerInstantiationException when the JDK refuses the layer
     */
    ModuleLayer.Controller define(Configuration configuration, List<ModuleLayer> parents, boolean transformed);

    /**
     * Adds a class loader the game class loader asks last, after its own jars and modules and its parent, for classes
     * and resources it does not have, as NeoForge's FML has its own class loader ask the loader of FML's plugin
     * libraries (ADR-0026). Classes found there are that loader's, defined as it defines them (not transformed). A
     * lookup that {@code fallback} hands back to the game class loader (its parent) is answered without the fallbacks,
     * so the two do not loop.
     *
     * <p>Call it before the game layer's classes need it, from the launching thread.
     *
     * @throws UnsupportedOperationException when this game class loader has no fallbacks
     */
    default void addFallback(ClassLoader fallback) {
        throw new UnsupportedOperationException("This game class loader has no fallback class loaders");
    }
}

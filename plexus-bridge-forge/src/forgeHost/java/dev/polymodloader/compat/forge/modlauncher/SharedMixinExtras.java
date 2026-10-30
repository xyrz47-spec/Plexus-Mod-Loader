package dev.polymodloader.compat.forge.modlauncher;

import cpw.mods.jarhandling.SecureJar;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * FML's GAME layer for a launch where one Mixin serves Forge and Fabric mods (ADR-0021 G8): MixinExtras then comes from
 * Poly Mod Loader's class path for every mod, and defines the classes it generates there ({@code LocalRef}
 * implementations, in the package of its {@code GeneratedImplDummy}). A MixinExtras module in the GAME layer would hold
 * MixinExtras' packages in the game class loader, which resolves a module's packages before its parent, so game classes
 * a mixin made use those classes would not find them ({@code NoClassDefFoundError: ...LocalRefImpl}).
 *
 * <p>Forge's own {@code mixinextras-forge} is replaced before FML starts ({@code MixinExtrasStub}), but FML's JarJar
 * selection takes the newest MixinExtras of all Forge mods: a mod that nests a newer {@code mixinextras-forge} (Carry On
 * 2.12.0 for 26.3 has 0.5.5, Forge 66.1.0 0.5.4) brings its MixinExtras jar into the GAME layer. NeoForge's host leaves
 * such jars out the same way ({@code FmlSharedMixin.withoutMixinExtras}).
 *
 * <p>Thread: FML's startup thread.
 */
final class SharedMixinExtras {

    /** The class every MixinExtras jar has; Poly Mod Loader's class path has it when Fabric's Mixin is shared. */
    static final String MIXIN_EXTRAS = "com/llamalad7/mixinextras/MixinExtrasBootstrap.class";

    private SharedMixinExtras() {}

    /**
     * {@code gameJars} without the jars that hold MixinExtras itself, when Poly Mod Loader's class path has MixinExtras
     * ({@code classPathHasMixinExtras}); otherwise {@code gameJars} as they are. MixinExtras' platform jar ({@code
     * mixinextras-forge}, whose classes Poly Mod Loader's class path does not have) stays.
     *
     * @param log gets a line for each jar left out
     */
    static List<SecureJar> withoutMixinExtras(
            List<SecureJar> gameJars, boolean classPathHasMixinExtras, Consumer<String> log) {
        if (!classPathHasMixinExtras) {
            return gameJars;
        }
        List<SecureJar> kept = new ArrayList<>();
        for (SecureJar jar : gameJars) {
            if (jar.moduleDataProvider().findFile(MIXIN_EXTRAS).isPresent()) {
                log.accept("FML's game library " + jar.name() + " (" + fileOf(jar)
                        + ") is left out of the game layer: Forge's mods use the MixinExtras of the Mixin that Fabric"
                        + " mods share (ADR-0021 G8)");
            } else {
                kept.add(jar);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * The jar's file name. A jar FML's JarJar selection opened inside another jar is the root of its own file system;
     * its name is the last part of its URI ({@code jar:roimfs:jar-jar-8/MixinExtras-0.5.5.jar!/}).
     */
    private static String fileOf(SecureJar jar) {
        Path file = jar.getPrimaryPath().getFileName();
        if (file != null) {
            return file.toString();
        }
        String uri = String.valueOf(jar.moduleDataProvider().uri());
        String inner = uri.endsWith("!/") ? uri.substring(0, uri.length() - 2) : uri;
        return inner.substring(inner.lastIndexOf('/') + 1);
    }
}

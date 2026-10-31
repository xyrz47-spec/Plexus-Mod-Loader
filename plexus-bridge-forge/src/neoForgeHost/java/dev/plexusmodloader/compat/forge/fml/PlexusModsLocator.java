package dev.plexusmodloader.compat.forge.fml;

import java.nio.file.Path;
import java.util.List;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * FML's mods folder, as Plexus Mod Loader sees it (ADR-0026): the NeoForge mods it found there and left in. The Fabric
 * and Plexus Mods in the same folder are not FML's, so FML never reports them as files it cannot read.
 */
final class PlexusModsLocator implements IModFileCandidateLocator {

    private final List<Path> modJars;

    PlexusModsLocator(List<Path> modJars) {
        this.modJars = List.copyOf(modJars);
    }

    /**
     * Each jar is a NeoForge mod Plexus Mod Loader read, so a jar FML cannot read is an error, as from FML's folder. A jar
     * FML already located is left to FML, as its own mods folder locator does: an early service jar (FML loaded it
     * before discovery and takes the mods nested in it from its JarJar), which FML would otherwise also see as a library
     * that hides the mod nested in it.
     */
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        for (Path jar : modJars) {
            Path path = jar.toAbsolutePath().normalize();
            if (context.isLocated(path)) {
                continue;
            }
            pipeline.addPath(path, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        }
    }

    @Override
    public String toString() {
        return "{Plexus Mod Loader's NeoForge mods (" + modJars.size() + ")}";
    }
}

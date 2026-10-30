package dev.polymodloader.runtime.compat;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.GameClassBytes;
import dev.polymodloader.compat.bridge.HostedLaunch;
import dev.polymodloader.compat.bridge.ModLoaderBridge;
import dev.polymodloader.runtime.discovery.ForeignModResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Keeps out, before the runtime decides which mods load, the other loaders' mods that cannot work as written on the
 * game a host loader patched (ADR-0030): a Fabric mod whose mixin injects at a call NeoForge's patch removed would
 * otherwise run with that part missing, and its users would report the breakage to the mod's author, whose mixin is
 * correct for the game it was written for. Each bridge says which of its mods do not fit ({@link
 * ModLoaderBridge#hostIncompatibilities}); each such mod gets a blocking {@code PLEXUS-COMPAT-224} finding that names
 * Poly Mod Loader as the one to fix it, and the dependency check then leaves out the mods that need it ({@code 114}).
 *
 * <p>The host's classes are read from its jars (the patched game, the jars its loader defines later), and the JDK's and
 * libraries' from Poly Mod Loader's class path: no class is defined. Not thread-safe; the runtime calls it once, on the
 * launch thread.
 */
public final class HostPatchCheck {

    /** The code of a mod left out because it does not fit the host's patch. */
    public static final String CODE = "PLEXUS-COMPAT-224";

    private HostPatchCheck() {}

    /**
     * {@code mods} with a blocking {@link #CODE} finding on each loadable mod a bridge found not to fit {@code host}'s
     * patched game; the others as they are.
     *
     * @param hostJars the jars that hold the host's patched game and its loader's classes
     * @param fallback where the JDK's and the libraries' classes are read from
     */
    public static List<ForeignModResolver.ForeignMod> apply(
            GameSide side,
            HostedLaunch host,
            List<Path> hostJars,
            ClassLoader fallback,
            List<ModLoaderBridge> bridges,
            List<ForeignModResolver.ForeignMod> mods) {
        if (!host.isHosted()) {
            return mods;
        }
        List<BridgeLaunchContext.LoadedMod> candidates = new ArrayList<>();
        for (ForeignModResolver.ForeignMod mod : mods) {
            if (mod.assessment().status().loadable()) {
                candidates.add(new BridgeLaunchContext.LoadedMod(
                        mod.assessment().loader(),
                        mod.assessment().modId(),
                        mod.assessment().version(),
                        mod.assessment().modId(),
                        mod.assessment().providedIds(),
                        mod.jar(),
                        mod.containerJar(),
                        mod.nestedEntry()));
            }
        }
        if (candidates.isEmpty()) {
            return mods;
        }
        Map<Path, List<String>> reasons = new LinkedHashMap<>();
        try (JarClasses classes = new JarClasses(hostJars, fallback)) {
            for (ModLoaderBridge bridge : bridges) {
                bridge.hostIncompatibilities(side, host, candidates, classes)
                        .forEach((jar, lines) -> reasons.computeIfAbsent(jar, key -> new ArrayList<>())
                                .addAll(lines));
            }
        }
        if (reasons.isEmpty()) {
            return mods;
        }
        List<ForeignModResolver.ForeignMod> checked = new ArrayList<>();
        for (ForeignModResolver.ForeignMod mod : mods) {
            List<String> lines = reasons.get(mod.jar());
            checked.add(
                    lines == null || !mod.assessment().status().loadable()
                            ? mod
                            : mod.withFindings(List.of(finding(host.hostName(), lines))));
        }
        return checked;
    }

    /** The finding for a mod whose code does not fit, with the bridge's {@code lines}. */
    static CompatibilityFinding finding(String hostName, List<String> lines) {
        return new CompatibilityFinding(
                CODE,
                true,
                "mixins",
                "Poly Mod Loader cannot run " + (lines.size() == 1 ? "a part" : lines.size() + " parts")
                        + " of this mod as written on the game as " + hostName + " patches it, and does not run the"
                        + " mod with them left out. This is a limitation of Poly Mod Loader, not a bug in the mod:"
                        + " report it to Poly Mod Loader, not to the mod's author. " + String.join(" | ", lines));
    }

    /** Class files from jars, then from a class loader's resources. Never defines a class. */
    private static final class JarClasses implements GameClassBytes, AutoCloseable {

        private final List<JarFile> jars = new ArrayList<>();
        private final ClassLoader fallback;

        JarClasses(List<Path> paths, ClassLoader fallback) {
            this.fallback = fallback;
            try {
                for (Path path : paths) {
                    jars.add(new JarFile(path.toFile()));
                }
            } catch (IOException e) {
                close();
                throw new UncheckedIOException("Failed to open the host's jar to check other loaders' mods", e);
            }
        }

        @Override
        public Optional<byte[]> classBytes(String className) {
            String entryName = className.replace('.', '/') + ".class";
            try {
                for (JarFile jar : jars) {
                    ZipEntry entry = jar.getEntry(entryName);
                    if (entry != null) {
                        try (InputStream input = jar.getInputStream(entry)) {
                            return Optional.of(input.readAllBytes());
                        }
                    }
                }
                try (InputStream input = fallback.getResourceAsStream(entryName)) {
                    return input == null ? Optional.empty() : Optional.of(input.readAllBytes());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + entryName + " to check other loaders' mods", e);
            }
        }

        @Override
        public boolean isDefined(String className) {
            return false;
        }

        @Override
        public void close() {
            for (JarFile jar : jars) {
                try {
                    jar.close();
                } catch (IOException e) {
                    // Read-only jars opened for this check; nothing was written to them.
                }
            }
        }
    }
}

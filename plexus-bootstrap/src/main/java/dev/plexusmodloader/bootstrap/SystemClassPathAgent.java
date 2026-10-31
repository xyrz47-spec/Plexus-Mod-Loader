package dev.plexusmodloader.bootstrap;

import dev.plexusmodloader.compat.bridge.JdkModuleAccess;
import dev.plexusmodloader.compat.bridge.SystemClassPath;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Plexus Mod Loader's Java agent (ADR-0024). It only keeps the {@link Instrumentation} the JVM hands it, so the launcher can
 * append jars to the JVM's class path later (Forge's file systems, which Java finds only through the system class
 * loader, when Forge mods are present) and open JDK packages to the modules of NeoForge's loader (ADR-0026), as
 * NeoForge's own launcher profile does with {@code --add-opens}. It registers no class transformer and does nothing
 * else; without Forge or NeoForge mods it is never used.
 *
 * <p>The JVM starts it before {@link PlexusLauncherMain#main}: with {@code -javaagent:<this jar>} (the launcher profile's
 * JVM argument; this jar's manifest names it as {@code Premain-Class}), or as the {@code Launcher-Agent-Class} of an
 * executable jar (the server's {@code plexusmodloader-server.jar}, whose {@code Class-Path} has this jar).
 *
 * <p>Thread: the JVM calls {@link #premain}/{@link #agentmain} on the main thread before the main method.
 */
public final class SystemClassPathAgent {

    private static volatile Instrumentation instrumentation;

    private SystemClassPathAgent() {}

    /** Called by the JVM for {@code -javaagent}. */
    public static void premain(String arguments, Instrumentation instrumentation) {
        SystemClassPathAgent.instrumentation = instrumentation;
    }

    /** Called by the JVM for an executable jar's {@code Launcher-Agent-Class}. */
    public static void agentmain(String arguments, Instrumentation instrumentation) {
        SystemClassPathAgent.instrumentation = instrumentation;
    }

    /** Opens JDK packages to modules; empty when this JVM was started without the agent. */
    static Optional<JdkModuleAccess> jdkModuleAccess() {
        Instrumentation current = instrumentation;
        if (current == null) {
            return Optional.empty();
        }
        return Optional.of((jdkModule, packageName, targets) -> {
            if (!jdkModule.getPackages().contains(packageName)) {
                throw new IllegalArgumentException(jdkModule.getName() + " has no package " + packageName);
            }
            current.redefineModule(
                    jdkModule, Set.of(), Map.of(), Map.of(packageName, Set.copyOf(targets)), Set.of(), Map.of());
        });
    }

    /** Appends to the JVM's class path; empty when this JVM was started without the agent (development runs). */
    static Optional<SystemClassPath> systemClassPath() {
        Instrumentation current = instrumentation;
        if (current == null) {
            return Optional.empty();
        }
        return Optional.of(jar -> {
            // The JDK keeps only the jar's name; the file is opened here to fail early on a broken jar.
            try (JarFile file = new JarFile(jar.toFile())) {
                current.appendToSystemClassLoaderSearch(file);
            } catch (UnsupportedOperationException e) {
                throw new IOException(
                        "the JVM's system class loader does not take more jars (java.system.class.loader is set)", e);
            }
        });
    }
}

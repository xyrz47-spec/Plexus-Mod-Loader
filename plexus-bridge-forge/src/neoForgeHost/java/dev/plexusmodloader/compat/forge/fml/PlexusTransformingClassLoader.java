package dev.plexusmodloader.compat.forge.fml;

import dev.plexusmodloader.compat.bridge.GameClassBytes;
import java.lang.module.Configuration;
import java.util.List;
import net.neoforged.fml.classloading.transformation.ClassProcessorAuditLog;
import net.neoforged.fml.classloading.transformation.ClassProcessorSet;
import net.neoforged.fml.classloading.transformation.TransformingClassLoader;

/**
 * The class loader FML gets in place of its own (ADR-0026): a {@code TransformingClassLoader} without modules of its
 * own, so that every class and resource comes from the game class loader (its fallback), which defines FML's game
 * layer. It still runs FML's class processors: for the game class loader's bridge stage ({@link #processAll}), and for
 * processors that ask for another class "up to" a processor (Mixin, frame computation), from that class's bytes after
 * Poly Mod Loader's transformer.
 *
 * <p>Thread: as FML's own; parallel capable.
 */
final class PlexusTransformingClassLoader extends TransformingClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final GameClassBytes classes;

    PlexusTransformingClassLoader(
            ClassProcessorSet processors,
            ClassProcessorAuditLog auditLog,
            ClassLoader gameClassLoader,
            GameClassBytes classes) {
        super(processors, auditLog, Configuration.empty(), List.of(ModuleLayer.boot()), gameClassLoader);
        this.classes = classes;
    }

    /**
     * FML reads a class it does not define as empty bytes; this loader defines none, so the class's bytes come from the
     * game class loader instead, before FML's processors run on them.
     */
    @Override
    protected byte[] maybeTransformClassBytes(byte[] bytes, String name, String upToTransformer) {
        byte[] input = bytes.length > 0 || classes == null
                ? bytes
                : classes.classBytes(name).orElse(bytes);
        return super.maybeTransformClassBytes(input, name, upToTransformer);
    }

    /** {@code bytes} after every FML class processor, as FML's own loader defines a class of its game layer. */
    byte[] processAll(String name, byte[] bytes) {
        return super.maybeTransformClassBytes(bytes, name, null);
    }
}

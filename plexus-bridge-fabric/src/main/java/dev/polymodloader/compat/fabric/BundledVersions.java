package dev.polymodloader.compat.fabric;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Versions of the libraries this bridge jar carries, written into {@code fabric-bridge.properties} by the build from
 * {@code gradle/libs.versions.toml}. Mods that require the matching built-in IDs are checked against them.
 */
public final class BundledVersions {

    /** Fabric Loader whose public API the jar carries: the built-in {@code fabricloader}. */
    public static final String FABRIC_LOADER = read("fabricLoaderVersion");

    /** MixinExtras on Poly Mod Loader's class path: the built-in {@code mixinextras}. */
    public static final String MIXIN_EXTRAS = read("mixinExtrasVersion");

    /** Quilt Loader whose public API the jar carries: the built-in {@code quilt_loader} (ADR-0027). */
    public static final String QUILT_LOADER = read("quiltLoaderVersion");

    private BundledVersions() {}

    private static String read(String key) {
        Properties properties = new Properties();
        try (InputStream input = BundledVersions.class.getResourceAsStream("fabric-bridge.properties")) {
            if (input == null) {
                throw new IllegalStateException("fabric-bridge.properties is missing from the Fabric bridge jar");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException(key + " is missing from fabric-bridge.properties");
        }
        return value;
    }
}

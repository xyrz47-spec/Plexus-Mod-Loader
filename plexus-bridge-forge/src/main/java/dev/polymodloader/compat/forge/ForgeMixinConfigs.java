package dev.polymodloader.compat.forge;

import dev.polymodloader.compat.bridge.SharedMixinConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * The Mixin configurations of Forge mods, as Mixin's own ModLauncher service finds them: the manifest attribute {@code
 * MixinConfigs} (comma-separated resource names) of each mod jar and of the jars nested in it ({@code
 * META-INF/jarjar/}, which FML adds as mods of their own). Read for a launch where one Mixin serves Forge and Fabric
 * mods (ADR-0021 G8); in a Forge-only launch Forge's own Mixin reads them.
 */
final class ForgeMixinConfigs {

    private static final Attributes.Name MIXIN_CONFIGS = new Attributes.Name("MixinConfigs");
    private static final String NESTED = "META-INF/jarjar/";

    private ForgeMixinConfigs() {}

    /** Every configuration of {@code jars}, once each, in jar order. */
    static List<SharedMixinConfig> read(List<Path> jars) throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        List<SharedMixinConfig> configs = new ArrayList<>();
        for (Path jar : jars) {
            try (JarFile file = new JarFile(jar.toFile())) {
                String modId = modId(jar);
                add(configs, seen, modId, file.getManifest());
                Enumeration<? extends ZipEntry> entries = file.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().startsWith(NESTED) || !entry.getName().endsWith(".jar")) {
                        continue;
                    }
                    try (InputStream input = file.getInputStream(entry);
                            JarInputStream nested = new JarInputStream(input)) {
                        // Mixin names generated members after the mod ID, so the configurations of nested jars carry
                        // the ID of the mod that ships them.
                        add(configs, seen, modId, nested.getManifest());
                    }
                }
            }
        }
        return List.copyOf(configs);
    }

    /**
     * The configurations Mixin 0.8.7 registers from {@code manifest}'s {@code MixinConfigs}: each comma-separated name,
     * trimmed, that ends in {@code .json} ({@code MixinPlatformAgentDefault.prepare}, {@code
     * MixinPlatformManager.addConfig}). Mixin ignores the other names, an empty one included.
     */
    static List<String> registered(Manifest manifest) {
        if (manifest == null) {
            return List.of();
        }
        String value = manifest.getMainAttributes().getValue(MIXIN_CONFIGS);
        if (value == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String config : value.split(",")) {
            String name = config.trim();
            if (name.endsWith(".json")) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static void add(List<SharedMixinConfig> configs, Set<String> seen, String modId, Manifest manifest) {
        for (String name : registered(manifest)) {
            if (seen.add(name)) {
                configs.add(new SharedMixinConfig("MinecraftForge", modId, name));
            }
        }
    }

    /** The first mod ID in the jar's {@code mods.toml}, or its file name reduced to the characters of a mod ID. */
    private static String modId(Path jar) {
        try {
            ForgeModRequirements.ModsToml toml = ForgeModRequirements.ModsToml.read(jar);
            if (toml != null && !toml.versions().isEmpty()) {
                return toml.versions().keySet().iterator().next();
            }
        } catch (IOException | RuntimeException e) {
            // The requirements check already reported an unreadable mods.toml; the file name still names the jar.
        }
        return jar.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}

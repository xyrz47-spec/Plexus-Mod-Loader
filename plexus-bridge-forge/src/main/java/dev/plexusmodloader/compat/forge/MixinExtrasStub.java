package dev.plexusmodloader.compat.forge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Forge's {@code mixinextras-forge} for a launch where one Mixin serves Forge and Fabric mods (ADR-0021 G8): the same jar,
 * but the MixinExtras it carries ({@code META-INF/jars/}) is an empty jar with that jar's manifest, and its Mixin
 * configuration ({@code MixinConfigs}) is gone.
 *
 * <p>MixinExtras then comes from Poly Mod Loader's class path for every mod: its injectors run there with the shared Mixin,
 * and it defines the classes it generates there ({@code LocalRef} implementations), which game classes must find under
 * the same package. A MixinExtras module would hold that package in the game class loader. FML still finds the jar and
 * the nested jar its jar-in-jar list names, which it requires as the MixinExtras that mods' own copies are matched
 * against.
 */
final class MixinExtrasStub {

    private static final Attributes.Name MIXIN_CONFIGS = new Attributes.Name("MixinConfigs");
    private static final String NESTED = "META-INF/jars/";

    private MixinExtrasStub() {}

    /** Writes the copy of {@code jar} to {@code directory} (replacing an older one) and returns it. */
    static Path write(Path jar, Path directory) throws IOException {
        Path target = directory.resolve("polymodloader-shared-mixin-" + jar.getFileName());
        Path partial = directory.resolve(target.getFileName() + ".partial");
        try (JarFile in = new JarFile(jar.toFile());
                OutputStream file = Files.newOutputStream(partial)) {
            Manifest manifest = new Manifest(in.getManifest());
            String configs = manifest.getMainAttributes().getValue(MIXIN_CONFIGS);
            manifest.getMainAttributes().remove(MIXIN_CONFIGS);
            try (JarOutputStream out = new JarOutputStream(file, manifest)) {
                Enumeration<JarEntry> entries = in.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.equalsIgnoreCase(JarFile.MANIFEST_NAME) || isConfig(name, configs)) {
                        continue;
                    }
                    out.putNextEntry(new JarEntry(name));
                    try (InputStream input = in.getInputStream(entry)) {
                        if (name.startsWith(NESTED) && name.endsWith(".jar")) {
                            out.write(emptied(input));
                        } else {
                            input.transferTo(out);
                        }
                    }
                    out.closeEntry();
                }
            }
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    /** A jar with only the manifest of {@code nested} (its module name and version stay). */
    private static byte[] emptied(InputStream nested) throws IOException {
        Manifest manifest;
        try (JarInputStream in = new JarInputStream(nested)) {
            manifest = in.getManifest() == null ? new Manifest() : in.getManifest();
        }
        manifest.getMainAttributes().putIfAbsent(Attributes.Name.MANIFEST_VERSION, "1.0");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new JarOutputStream(bytes, manifest).close();
        return bytes.toByteArray();
    }

    private static boolean isConfig(String name, String configs) {
        if (configs == null) {
            return false;
        }
        for (String config : configs.split(",")) {
            if (name.equals(config.strip())) {
                return true;
            }
        }
        return false;
    }
}

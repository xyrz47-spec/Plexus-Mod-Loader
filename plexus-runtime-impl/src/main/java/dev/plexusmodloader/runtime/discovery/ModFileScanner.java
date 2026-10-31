package dev.plexusmodloader.runtime.discovery;

import dev.plexusmodloader.runtime.metadata.ModMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Finds mod jars and classifies them by loader. Only the jars' metadata files are read; no class is
 * loaded during discovery.
 */
public final class ModFileScanner {

    private static final String MODLAUNCHER_SERVICE =
            "META-INF/services/cpw.mods.modlauncher.api.ITransformationService";

    /**
     * Scans {@code modsDirectory} (non-recursive, {@code *.jar} only, sorted by file name for a
     * deterministic order) and then {@code extraJars}.
     */
    public List<DiscoveredModFile> scan(Path modsDirectory, List<Path> extraJars) {
        List<Path> jars = new ArrayList<>();
        if (Files.isDirectory(modsDirectory)) {
            try (Stream<Path> files = Files.list(modsDirectory)) {
                files.filter(file -> file.getFileName().toString().endsWith(".jar"))
                        .filter(Files::isRegularFile)
                        .sorted()
                        .forEach(jars::add);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to list mods directory " + modsDirectory, e);
            }
        }
        jars.addAll(extraJars);
        return jars.stream().map(ModFileScanner::classify).toList();
    }

    /**
     * Classifies one jar. Never throws for the jar's contents: a file that is not a readable jar is {@link
     * ModFileKind#UNREADABLE}, so one damaged download cannot stop the others from being reported.
     */
    static DiscoveredModFile classify(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            ModFileKind found = null;
            Optional<String> nativeMetadata = Optional.empty();
            Set<ModFileKind> alsoFor = EnumSet.noneOf(ModFileKind.class);
            for (ModFileKind kind : ModFileKind.values()) {
                if (kind.metadataPath().isEmpty()) {
                    continue;
                }
                ZipEntry entry = jarFile.getEntry(kind.metadataPath());
                if (entry == null && kind == ModFileKind.PLEXUS_NATIVE) {
                    entry = jarFile.getEntry(ModMetadata.LEGACY_FILE_NAME);
                }
                if (entry == null) {
                    continue;
                }
                if (found != null) {
                    alsoFor.add(kind);
                    continue;
                }
                found = kind;
                if (kind == ModFileKind.PLEXUS_NATIVE) {
                    try (InputStream input = jarFile.getInputStream(entry)) {
                        nativeMetadata = Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
            // A ModLauncher transformation service is Forge's too: Forge finds it in mods/ (CustomSkinLoader's jar for
            // every loader has fabric.mod.json and such a service, but no mods.toml).
            if (found != null && found != ModFileKind.FORGE && jarFile.getEntry(MODLAUNCHER_SERVICE) != null) {
                alsoFor.add(ModFileKind.FORGE);
            }
            if (found != null) {
                return new DiscoveredModFile(jar, found, nativeMetadata, Optional.empty(), alsoFor);
            }
            Manifest manifest = jarFile.getManifest();
            String fmlType =
                    manifest == null ? null : manifest.getMainAttributes().getValue("FMLModType");
            if (fmlType != null) {
                return new DiscoveredModFile(jar, ModFileKind.FML_LIBRARY, Optional.empty(), Optional.of(fmlType));
            }
            return new DiscoveredModFile(jar, ModFileKind.UNKNOWN, Optional.empty());
        } catch (IOException | RuntimeException e) {
            return new DiscoveredModFile(jar, ModFileKind.UNREADABLE, Optional.empty(), Optional.of(String.valueOf(e)));
        }
    }
}

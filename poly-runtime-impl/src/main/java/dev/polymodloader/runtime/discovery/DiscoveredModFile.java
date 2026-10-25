package dev.polymodloader.runtime.discovery;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * A jar found during discovery.
 *
 * @param path location of the jar
 * @param kind the loader it targets
 * @param nativeMetadataJson contents of {@code poly.mod.json}, present only for {@link ModFileKind#POLY_NATIVE}
 * @param detail why the jar was classified as it was, for {@link ModFileKind#UNREADABLE} (the read error) and
 *     {@link ModFileKind#FML_LIBRARY} (the {@code FMLModType}); empty otherwise
 * @param alsoFor the other loaders whose metadata the jar also has (one jar built for several loaders); {@code kind} is
 *     the first in {@link ModFileKind}'s order
 */
public record DiscoveredModFile(
        Path path,
        ModFileKind kind,
        Optional<String> nativeMetadataJson,
        Optional<String> detail,
        Set<ModFileKind> alsoFor) {

    public DiscoveredModFile {
        alsoFor = Set.copyOf(alsoFor);
    }

    public DiscoveredModFile(
            Path path, ModFileKind kind, Optional<String> nativeMetadataJson, Optional<String> detail) {
        this(path, kind, nativeMetadataJson, detail, Set.of());
    }

    public DiscoveredModFile(Path path, ModFileKind kind, Optional<String> nativeMetadataJson) {
        this(path, kind, nativeMetadataJson, Optional.empty());
    }

    /** Whether the jar has {@code loader}'s metadata, as its kind or besides it. */
    public boolean isFor(ModFileKind loader) {
        return kind == loader || alsoFor.contains(loader);
    }
}

package dev.polymodloader.runtime.metadata;

import dev.polymodloader.runtime.metadata.version.SemanticVersion;
import dev.polymodloader.runtime.metadata.version.VersionRange;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A validated {@code poly.mod.json} (schema version 1). Only produced by {@link ModMetadataReader}
 * when no error-level problem was found.
 *
 * @param schemaVersion metadata schema version, independent of the Poly Mod Loader API version
 * @param id technical mod ID
 * @param version mod version
 * @param name human-readable name
 * @param environment where the mod may load
 * @param minecraftVersions Minecraft versions the mod declares support for: exact IDs, wildcards or
 *     ranges ({@link dev.polymodloader.runtime.metadata.version.MinecraftVersionPattern})
 * @param entrypoints entrypoint classes per side
 * @param depends required mod IDs (including {@code poly}) and their version ranges
 * @param conflicts mod IDs that must not be loaded together with this mod
 * @param capabilities capability IDs the mod requests, e.g. {@code system.provider.replace} (design doc §5.4.1)
 * @param replacesSystems systems this mod replaces
 * @param transforms class transformation rules this mod declares
 * @param accessWidenings game classes and members this mod needs wider access to
 */
public record ModMetadata(
        int schemaVersion,
        ModId id,
        SemanticVersion version,
        String name,
        ModEnvironment environment,
        List<String> minecraftVersions,
        ModEntrypoints entrypoints,
        Map<String, VersionRange> depends,
        List<String> conflicts,
        List<String> capabilities,
        List<SystemReplacementDeclaration> replacesSystems,
        List<TransformDeclaration> transforms,
        List<AccessWideningDeclaration> accessWidenings) {

    /** File name Plexus Mod Loader looks for at the root of a mod jar. */
    public static final String FILE_NAME = "plexus.mod.json";

    /** Legacy file name Poly Mod Loader looked for, supported for backward compatibility. */
    public static final String LEGACY_FILE_NAME = "poly.mod.json";

    public ModMetadata {
        minecraftVersions = List.copyOf(minecraftVersions);
        // Keep declaration order so diagnostics list problems in the order the author wrote them.
        depends = Collections.unmodifiableMap(new LinkedHashMap<>(depends));
        conflicts = List.copyOf(conflicts);
        capabilities = List.copyOf(capabilities);
        replacesSystems = List.copyOf(replacesSystems);
        transforms = List.copyOf(transforms);
        accessWidenings = List.copyOf(accessWidenings);
    }

    /** Metadata without access widenings. */
    public ModMetadata(
            int schemaVersion,
            ModId id,
            SemanticVersion version,
            String name,
            ModEnvironment environment,
            List<String> minecraftVersions,
            ModEntrypoints entrypoints,
            Map<String, VersionRange> depends,
            List<String> conflicts,
            List<String> capabilities,
            List<SystemReplacementDeclaration> replacesSystems,
            List<TransformDeclaration> transforms) {
        this(
                schemaVersion,
                id,
                version,
                name,
                environment,
                minecraftVersions,
                entrypoints,
                depends,
                conflicts,
                capabilities,
                replacesSystems,
                transforms,
                List.of());
    }
}

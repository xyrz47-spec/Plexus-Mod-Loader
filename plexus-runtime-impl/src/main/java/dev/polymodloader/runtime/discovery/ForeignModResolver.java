package dev.polymodloader.runtime.discovery;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.CompatibilityStatus;
import dev.polymodloader.compat.bridge.ModJarContents;
import dev.polymodloader.compat.bridge.ModLoaderBridge;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Hands jars built for other loaders to the matching {@link ModLoaderBridge} (design doc §11 step 5).
 * Bridges are optional: without one, the jar is reported as blocked by the caller.
 */
public final class ForeignModResolver {

    /**
     * A foreign mod and the bridge that assessed it.
     *
     * @param jar the mod jar (for a nested mod, its extracted copy)
     * @param bridge the bridge for its loader
     * @param assessment the bridge's verdict
     * @param containerJar for a mod nested in another mod's jar, that jar; {@code null} for a jar that was
     *     installed directly
     * @param nestedEntry for a nested mod, its path inside {@code containerJar}; {@code null} otherwise
     */
    public record ForeignMod(
            Path jar, ModLoaderBridge bridge, BridgeAssessment assessment, Path containerJar, String nestedEntry) {

        /** A mod installed directly (in the mods folder or as a development mod). */
        public ForeignMod(Path jar, ModLoaderBridge bridge, BridgeAssessment assessment) {
            this(jar, bridge, assessment, null, null);
        }

        /** A mod nested in {@code containerJar} at an entry named like the extracted file. */
        public ForeignMod(Path jar, ModLoaderBridge bridge, BridgeAssessment assessment, Path containerJar) {
            this(
                    jar,
                    bridge,
                    assessment,
                    containerJar,
                    containerJar == null ? null : "META-INF/jars/" + jar.getFileName());
        }

        /** This mod with more findings, such as dependency or link problems found later. */
        public ForeignMod withFindings(List<CompatibilityFinding> added) {
            return new ForeignMod(jar, bridge, assessment.withFindings(added), containerJar, nestedEntry);
        }

        /** Whether this mod came out of another mod's jar. */
        public boolean nested() {
            return containerJar != null;
        }
    }

    private final List<ModLoaderBridge> bridges = new ArrayList<>();

    public ForeignModResolver(ClassLoader bridgeClassLoader) {
        ServiceLoader.load(ModLoaderBridge.class, bridgeClassLoader).forEach(bridges::add);
    }

    /** The installed bridges. */
    public List<ModLoaderBridge> bridges() {
        return List.copyOf(bridges);
    }

    /** Loader names of the installed bridges, for diagnostics. */
    public List<String> bridgeNames() {
        return bridges.stream().map(ModLoaderBridge::loaderName).toList();
    }

    /** Assesses {@code file}, or returns empty when no installed bridge handles its loader. */
    public Optional<ForeignMod> assess(DiscoveredModFile file, GameSide side) {
        for (ModLoaderBridge bridge : bridges) {
            if (!file.kind().metadataPath().isEmpty()
                    && bridge.metadataPath().equals(file.kind().metadataPath())) {
                BridgeAssessment assessment;
                try (JarFile jar = new JarFile(file.path().toFile())) {
                    String metadata = new String(
                            readEntry(jar, bridge.metadataPath())
                                    .orElseThrow(() -> new UncheckedIOException(new IOException(
                                            bridge.metadataPath() + " is missing from " + file.path()))),
                            StandardCharsets.UTF_8);
                    // Files the metadata points to (a Fabric class tweaker) are read from the same jar.
                    ModJarContents contents = path -> readEntry(jar, path);
                    assessment = bridge.assess(metadata, side, contents);
                } catch (IOException | RuntimeException | LinkageError e) {
                    // A bridge must report, not throw; if it throws anyway, the jar is blocked with the reason.
                    assessment = BridgeAssessment.of(
                            bridge.loaderName(),
                            "?",
                            "?",
                            List.of(new CompatibilityFinding(
                                    "PLEXUS-COMPAT-002",
                                    true,
                                    "assessment",
                                    "the " + bridge.loaderName() + " bridge could not read " + bridge.metadataPath()
                                            + ": " + e)),
                            List.of(),
                            CompatibilityStatus.BLOCKED);
                }
                if (file.kind() == ModFileKind.FABRIC && file.isFor(ModFileKind.QUILT)) {
                    assessment = assessment.withFindings(List.of(new CompatibilityFinding(
                            "PLEXUS-COMPAT-310",
                            false,
                            "metadata",
                            "the jar has both fabric.mod.json and quilt.mod.json; it runs as a Fabric mod from"
                                    + " fabric.mod.json, as it does under Fabric Loader (Quilt Loader would read"
                                    + " quilt.mod.json instead)")));
                }
                return Optional.of(new ForeignMod(file.path(), bridge, assessment));
            }
        }
        return Optional.empty();
    }

    private static Optional<byte[]> readEntry(JarFile jar, String entryName) {
        ZipEntry entry = jar.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            return Optional.empty();
        }
        try (InputStream input = jar.getInputStream(entry)) {
            return Optional.of(input.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + entryName + " from " + jar.getName(), e);
        }
    }
}

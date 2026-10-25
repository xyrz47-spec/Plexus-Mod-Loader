package dev.polymodloader.compat.fabric.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * A loaded mod as Fabric Loader's {@link ModContainer}. Its root path is the root of its jar, opened as a zip
 * file system the first time it is asked for and kept open for the rest of the game, as in Fabric.
 */
final class FabricModContainer implements ModContainer {

    private final ModMetadata metadata;
    private final Path jar;
    private final ModOrigin origin;
    private FabricModContainer containing;
    private final List<ModContainer> contained = new ArrayList<>();
    private List<Path> roots;
    /** A host loader's mod: the jar {@link #findPath} reads, without root paths (ADR-0026 36). */
    private final Path lookupJar;

    private Path lookupRoot;

    /**
     * @param jar the mod's jar, {@code null} for a built-in mod without one
     * @param origin where it came from
     */
    FabricModContainer(ModMetadata metadata, Path jar, ModOrigin origin) {
        this(metadata, jar, origin, null);
    }

    /**
     * @param lookupJar for a mod the host loader runs, its jar: {@link #findPath} finds its files there, while {@link
     *     #getRootPaths()} stays empty (the host serves its resources, ADR-0025); {@code null} otherwise
     */
    FabricModContainer(ModMetadata metadata, Path jar, ModOrigin origin, Path lookupJar) {
        this.metadata = metadata;
        this.jar = jar;
        this.origin = origin;
        this.lookupJar = lookupJar;
    }

    /**
     * A file of the mod. For a mod the host loader runs, from its jar although it has no root paths: multi-loader
     * libraries that pick Fabric Loader's API whenever it is there find a host mod's own files through it (Yumi in
     * Lambda Dynamic Lights for NeoForge reads its default configuration so).
     */
    @Override
    public synchronized Optional<Path> findPath(String file) {
        if (lookupJar == null) {
            return ModContainer.super.findPath(file);
        }
        if (lookupRoot == null) {
            try {
                lookupRoot = FileSystems.newFileSystem(lookupJar)
                        .getRootDirectories()
                        .iterator()
                        .next();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot open " + lookupJar + " for mod " + metadata.getId(), e);
            }
        }
        Path path =
                lookupRoot.resolve(file.replace("/", lookupRoot.getFileSystem().getSeparator()));
        return java.nio.file.Files.exists(path) ? Optional.of(path) : Optional.empty();
    }

    Path jar() {
        return jar;
    }

    void containedIn(FabricModContainer parent) {
        this.containing = parent;
        parent.contained.add(this);
    }

    @Override
    public ModMetadata getMetadata() {
        return metadata;
    }

    @Override
    public synchronized List<Path> getRootPaths() {
        if (roots == null) {
            if (jar == null) {
                roots = List.of();
            } else {
                try {
                    FileSystem fileSystem = FileSystems.newFileSystem(jar);
                    roots = List.of(fileSystem.getRootDirectories().iterator().next());
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot open " + jar + " for mod " + metadata.getId(), e);
                }
            }
        }
        return roots;
    }

    @Override
    public ModOrigin getOrigin() {
        return origin;
    }

    @Override
    public Optional<ModContainer> getContainingMod() {
        return Optional.ofNullable(containing);
    }

    @Override
    public Collection<ModContainer> getContainedMods() {
        return List.copyOf(contained);
    }

    @Override
    @Deprecated
    public Path getRootPath() {
        List<Path> paths = getRootPaths();
        if (paths.isEmpty()) {
            throw new UnsupportedOperationException(metadata.getId() + " has no root path");
        }
        return paths.getFirst();
    }

    @Override
    @Deprecated
    public Path getPath(String file) {
        return findPath(file)
                .orElseGet(() -> getRootPath()
                        .resolve(file.replace("/", getRootPath().getFileSystem().getSeparator())));
    }

    /** Where a mod came from. */
    record Origin(Kind kind, List<Path> paths, String parentModId, String parentSubLocation) implements ModOrigin {

        static Origin path(Path jar) {
            return new Origin(Kind.PATH, List.of(jar), null, null);
        }

        static Origin nested(String parentModId, String entry) {
            return new Origin(Kind.NESTED, List.of(), parentModId, entry);
        }

        static Origin unknown() {
            return new Origin(Kind.UNKNOWN, List.of(), null, null);
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public List<Path> getPaths() {
            if (kind != Kind.PATH) {
                throw new UnsupportedOperationException("kind " + kind + " doesn't have paths");
            }
            return paths;
        }

        @Override
        public String getParentModId() {
            if (kind != Kind.NESTED) {
                throw new UnsupportedOperationException("kind " + kind + " doesn't have a parent mod");
            }
            return parentModId;
        }

        @Override
        public String getParentSubLocation() {
            if (kind != Kind.NESTED) {
                throw new UnsupportedOperationException("kind " + kind + " doesn't have a parent sub-location");
            }
            return parentSubLocation;
        }
    }
}

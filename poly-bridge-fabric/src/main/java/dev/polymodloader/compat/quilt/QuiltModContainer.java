package dev.polymodloader.compat.quilt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModMetadata;

/**
 * A loaded mod as Quilt Loader's {@link ModContainer} (ADR-0027 decision 4).
 *
 * <ul>
 *   <li>{@link #rootPath()} is the root of the mod's jar, opened as a zip file system the first time it is asked for and
 *       kept open for the rest of the game, as the Fabric bridge's containers do. Mods the host loader runs get their
 *       jar root too: 26.x has no Quilt resource loader (QSL) that would make a second resource pack of it. A built-in
 *       mod without a jar ({@code java}, {@code quilt_loader}) has an empty directory.
 *   <li>{@link #getSourcePaths()} is {@code [[jar]]} for a mod installed directly and {@code [[outer jar, entry inside
 *       it]]} for a nested one.
 *   <li>{@link #getClassLoader()} is the game class loader.
 * </ul>
 *
 * <p>Thread: safe from any thread.
 */
public final class QuiltModContainer implements ModContainer {

    private final ModMetadata metadata;
    private final BasicSourceType sourceType;
    private final Path jar;
    private final Path containerJar;
    private final String nestedEntry;
    private final ClassLoader classLoader;
    private final Supplier<Path> emptyRoot;
    private Path root;
    private List<List<Path>> sourcePaths;

    /**
     * @param jar the mod's jar, {@code null} for a built-in mod without one
     * @param containerJar the jar it is nested in, {@code null} when installed directly
     * @param nestedEntry its path inside {@code containerJar}
     * @param emptyRoot the empty directory a mod without a jar has as its root
     */
    QuiltModContainer(
            ModMetadata metadata,
            BasicSourceType sourceType,
            Path jar,
            Path containerJar,
            String nestedEntry,
            ClassLoader classLoader,
            Supplier<Path> emptyRoot) {
        this.metadata = metadata;
        this.sourceType = sourceType;
        this.jar = jar;
        this.containerJar = containerJar;
        this.nestedEntry = nestedEntry;
        this.classLoader = classLoader;
        this.emptyRoot = emptyRoot;
    }

    /** The mod's jar, or {@code null}. */
    Path jar() {
        return jar;
    }

    @Override
    public ModMetadata metadata() {
        return metadata;
    }

    @Override
    public synchronized Path rootPath() {
        if (root == null) {
            root = jar == null
                    ? emptyRoot.get()
                    : open(jar).getRootDirectories().iterator().next();
        }
        return root;
    }

    @Override
    public synchronized List<List<Path>> getSourcePaths() {
        if (sourcePaths == null) {
            if (jar == null) {
                sourcePaths = List.of();
            } else if (containerJar == null) {
                sourcePaths = List.of(List.of(jar));
            } else {
                Path inside = open(containerJar)
                        .getRootDirectories()
                        .iterator()
                        .next()
                        .resolve(nestedEntry);
                sourcePaths = List.of(List.of(containerJar, inside));
            }
        }
        return sourcePaths;
    }

    @Override
    public BasicSourceType getSourceType() {
        return sourceType;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    private FileSystem open(Path file) {
        try {
            return FileSystems.newFileSystem(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open " + file + " for mod " + metadata.id(), e);
        }
    }

    @Override
    public String toString() {
        return metadata.id() + " (" + sourceType + ")";
    }
}

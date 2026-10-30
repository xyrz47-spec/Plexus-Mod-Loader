package dev.polymodloader.runtime.discovery;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.runtime.discovery.ForeignModResolver.ForeignMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Takes the jars nested inside foreign mods (Fabric and Quilt {@code jars}) out of their container and assesses each
 * one, recursively, the way Fabric Loader and Quilt Loader treat jar-in-jar: every nested jar with metadata the
 * container's loader reads ({@link dev.polymodloader.compat.bridge.ModLoaderBridge#nestedMetadataPaths()}) is a mod of
 * its own, assessed by the bridge of that metadata. Whether one may load is decided later with the other mods ({@code CrossLoaderModResolver}): a
 * nested mod loads only while the mod it came from loads, and of several copies of one mod the newest is used.
 *
 * <p>Extracted jars are stored by content hash in the cache directory, so a launch reuses them and two
 * containers that carry the same jar share one file.
 *
 * <p>Findings added here: {@code PLEXUS-COMPAT-120} (not blocking) on each nested mod, naming its container;
 * {@code PLEXUS-COMPAT-123} (blocking) on a container whose {@code jars} entry is missing or unreadable, or that
 * nests deeper than {@value #MAX_DEPTH} levels.
 */
public final class NestedJarExtractor {

    /** Deepest nesting followed; Fabric mods in practice nest one or two levels. */
    static final int MAX_DEPTH = 8;

    /**
     * A nested jar that is not a mod of its container's loader (no metadata that loader reads, or unreadable).
     *
     * @param jar the extracted copy
     * @param container file name of the jar it came from
     * @param entry its path inside the container
     * @param kind what discovery made of it
     */
    public record NestedFile(Path jar, String container, String entry, ModFileKind kind) {}

    /**
     * @param mods the input mods, each followed by the mods nested in it (depth first)
     * @param otherFiles nested jars that are not mods
     */
    public record Result(List<ForeignMod> mods, List<NestedFile> otherFiles) {}

    private final ForeignModResolver bridges;
    private final Path cacheDirectory;
    private final GameSide side;

    public NestedJarExtractor(ForeignModResolver bridges, Path cacheDirectory, GameSide side) {
        this.bridges = bridges;
        this.cacheDirectory = cacheDirectory;
        this.side = side;
    }

    /** Expands every mod in {@code mods}. Never throws for the contents of a jar. */
    public Result expand(List<ForeignMod> mods) {
        List<ForeignMod> out = new ArrayList<>();
        List<NestedFile> others = new ArrayList<>();
        for (ForeignMod mod : mods) {
            expand(mod, 0, new HashSet<>(), out, others);
        }
        return new Result(List.copyOf(out), List.copyOf(others));
    }

    private void expand(ForeignMod mod, int depth, Set<String> chain, List<ForeignMod> out, List<NestedFile> others) {
        List<String> entries = mod.assessment().nestedJars();
        if (entries.isEmpty()) {
            out.add(mod);
            return;
        }
        List<CompatibilityFinding> problems = new ArrayList<>();
        List<ForeignMod> children = new ArrayList<>();
        List<Integer> childDepths = new ArrayList<>();
        List<Set<String>> childChains = new ArrayList<>();
        String containerName = mod.jar().getFileName().toString();
        if (depth >= MAX_DEPTH) {
            problems.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-123", true, "nested jars", "nested more than " + MAX_DEPTH + " levels deep"));
        } else {
            try (JarFile jar = new JarFile(mod.jar().toFile())) {
                for (String entryName : entries) {
                    ZipEntry entry = jar.getEntry(entryName);
                    if (entry == null) {
                        problems.add(new CompatibilityFinding(
                                "PLEXUS-COMPAT-123",
                                true,
                                "nested jars",
                                entryName + " is listed in jars but not in the jar"));
                        continue;
                    }
                    byte[] bytes;
                    try (InputStream input = jar.getInputStream(entry)) {
                        bytes = input.readAllBytes();
                    }
                    String hash = sha256(bytes);
                    if (chain.contains(hash)) {
                        problems.add(new CompatibilityFinding(
                                "PLEXUS-COMPAT-123", true, "nested jars", entryName + " contains itself"));
                        continue;
                    }
                    Path extracted = store(hash, fileName(entryName), bytes);
                    DiscoveredModFile file = ModFileScanner.classify(extracted);
                    // Which nested jars are mods follows the container's loader (Quilt Loader also reads nested
                    // Fabric mods); each is then assessed by the bridge of its own metadata (ADR-0027).
                    Optional<ForeignMod> nested = !file.kind().metadataPath().isEmpty()
                                    && mod.bridge()
                                            .nestedMetadataPaths()
                                            .contains(file.kind().metadataPath())
                            ? bridges.assess(file, side)
                            : Optional.empty();
                    if (nested.isEmpty()) {
                        others.add(new NestedFile(extracted, containerName, entryName, file.kind()));
                        continue;
                    }
                    ForeignMod child = new ForeignMod(
                                    extracted,
                                    nested.get().bridge(),
                                    nested.get().assessment(),
                                    mod.jar(),
                                    entryName)
                            .withFindings(List.of(new CompatibilityFinding(
                                    "PLEXUS-COMPAT-120",
                                    false,
                                    "nested jar",
                                    "inside " + containerName + " as " + entryName)));
                    Set<String> childChain = new HashSet<>(chain);
                    childChain.add(hash);
                    children.add(child);
                    childDepths.add(depth + 1);
                    childChains.add(childChain);
                }
            } catch (IOException | RuntimeException e) {
                problems.add(new CompatibilityFinding(
                        "PLEXUS-COMPAT-123", true, "nested jars", "the nested jars cannot be read: " + e));
            }
        }
        out.add(mod.withFindings(problems));
        for (int i = 0; i < children.size(); i++) {
            expand(children.get(i), childDepths.get(i), childChains.get(i), out, others);
        }
    }

    /** Writes {@code bytes} to {@code <cache>/<hash prefix>/<name>} unless it is already there. */
    private Path store(String hash, String name, byte[] bytes) throws IOException {
        Path target = cacheDirectory.resolve(hash.substring(0, 16)).resolve(name);
        if (Files.isRegularFile(target) && Files.size(target) == bytes.length) {
            return target;
        }
        Files.createDirectories(target.getParent());
        Path partial = Files.createTempFile(target.getParent(), name, ".partial");
        Files.write(partial, bytes);
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    private static String fileName(String entryName) {
        String name = entryName.substring(entryName.lastIndexOf('/') + 1);
        return name.isEmpty() || !name.matches("[A-Za-z0-9._+\\-]+") ? "nested.jar" : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}

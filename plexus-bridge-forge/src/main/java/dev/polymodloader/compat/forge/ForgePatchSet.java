package dev.polymodloader.compat.forge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.tukaani.xz.LZMAInputStream;

/**
 * Forge's binary patches for one side ({@code data/client.lzma} or {@code data/server.lzma} of the Forge installer):
 * an LZMA-compressed zip with one {@link ForgeBinaryPatch} per class (ADR-0021 G2). Builds the Forge-patched game jar
 * on the player's PC, as Forge's installer does with {@code binarypatcher}; Poly Mod Loader never ships patched classes.
 *
 * <p>Thread: immutable after {@link #read}; {@link #patch} may run on any thread.
 */
final class ForgePatchSet {

    private final Map<String, ForgeBinaryPatch> byPatchedEntry;

    private ForgePatchSet(Map<String, ForgeBinaryPatch> byPatchedEntry) {
        this.byPatchedEntry = byPatchedEntry;
    }

    /** Reads a {@code <side>.lzma} stream; the caller closes {@code lzma}. */
    static ForgePatchSet read(InputStream lzma) throws IOException {
        Map<String, ForgeBinaryPatch> patches = new LinkedHashMap<>();
        ZipInputStream zip = new ZipInputStream(new LZMAInputStream(lzma));
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            if (entry.isDirectory() || !entry.getName().endsWith(".binpatch")) {
                continue;
            }
            ForgeBinaryPatch patch = ForgeBinaryPatch.read(zip.readAllBytes());
            // A patch of an existing class applies to that class's entry, found by its original name, as Forge's
            // binarypatcher does; Forge 26.1's patches name some patched classes differently (Transformation$...).
            String entryName = patch.exists()
                    ? patch.originalName().replace('.', '/') + ".class"
                    : patch.patchedInternalName() + ".class";
            ForgeBinaryPatch previous = patches.put(entryName, patch);
            if (previous != null) {
                throw new IOException("Two Forge patches produce " + patch.patchedName());
            }
        }
        return new ForgePatchSet(Collections.unmodifiableMap(patches));
    }

    /** Number of patches, new classes included. */
    int size() {
        return byPatchedEntry.size();
    }

    /** Number of classes Forge adds that the game does not have. */
    long newClasses() {
        return byPatchedEntry.values().stream().filter(patch -> !patch.exists()).count();
    }

    /**
     * Writes {@code gameJar} with every patch applied and Forge's new classes added, to a temporary file that is then
     * moved to {@code output}. Other entries are copied unchanged, except the signature: Mojang signs its jars, and
     * patched classes no longer match it, so the signature files are left out and the manifest keeps only its main
     * attributes, as in Forge's own patched jar.
     *
     * @param marker an empty entry to add, as Forge's installer adds {@code .forge_patched_minecraft} to the client jar
     *     so that FML finds it on any class path
     * @return the entries that were patched or added, in jar order and then patch order
     * @throws IOException when a patch targets a class the jar does not have, or a delta does not apply
     */
    List<String> patch(Path gameJar, Path output, Optional<String> marker) throws IOException {
        List<String> changed = new ArrayList<>();
        Path partial = output.resolveSibling(output.getFileName() + ".partial");
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (JarFile in = new JarFile(gameJar.toFile());
                OutputStream file = Files.newOutputStream(partial);
                ZipOutputStream out = new ZipOutputStream(file)) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            java.util.Set<String> seen = new java.util.HashSet<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                seen.add(entry.getName());
                if (isSignatureFile(entry.getName())) {
                    continue;
                }
                byte[] bytes;
                try (InputStream data = in.getInputStream(entry)) {
                    bytes = data.readAllBytes();
                }
                if (entry.getName().equals(JarFile.MANIFEST_NAME)) {
                    bytes = mainAttributesOnly(bytes);
                }
                ForgeBinaryPatch patch = byPatchedEntry.get(entry.getName());
                if (patch != null && patch.exists()) {
                    bytes = patch.apply(bytes);
                    changed.add(entry.getName());
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(bytes);
                out.closeEntry();
            }
            for (Map.Entry<String, ForgeBinaryPatch> patch : byPatchedEntry.entrySet()) {
                if (seen.contains(patch.getKey())) {
                    continue;
                }
                if (patch.getValue().exists()) {
                    throw new IOException("Forge patches " + patch.getValue().originalName() + ", which " + gameJar
                            + " does not have; the patches are for another Minecraft version");
                }
                out.putNextEntry(new ZipEntry(patch.getKey()));
                out.write(patch.getValue().apply(null));
                out.closeEntry();
                changed.add(patch.getKey());
            }
            if (marker.isPresent() && !seen.contains(marker.get())) {
                out.putNextEntry(new ZipEntry(marker.get()));
                out.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return changed;
    }

    /** {@link #patch(Path, Path, Optional)} without a marker entry, as for the dedicated server. */
    List<String> patch(Path gameJar, Path output) throws IOException {
        return patch(gameJar, output, Optional.empty());
    }

    private static boolean isSignatureFile(String name) {
        if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length()) >= 0) {
            return false;
        }
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
    }

    /** The manifest without its per-entry sections (the signature's digests). */
    private static byte[] mainAttributesOnly(byte[] manifest) throws IOException {
        java.util.jar.Manifest original = new java.util.jar.Manifest(new java.io.ByteArrayInputStream(manifest));
        java.util.jar.Manifest kept = new java.util.jar.Manifest();
        kept.getMainAttributes().putAll(original.getMainAttributes());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        kept.write(out);
        return out.toByteArray();
    }
}

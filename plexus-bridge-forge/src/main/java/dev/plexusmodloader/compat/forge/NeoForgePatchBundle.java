package dev.plexusmodloader.compat.forge;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.tukaani.xz.LZMAInputStream;

/**
 * NeoForge's binary patches ({@code data/client.lzma} of the NeoForge installer, for client and server alike): an
 * LZMA-compressed {@code NFPATCHBUNDLE001} bundle (ADR-0026). Builds the NeoForge-patched game jar on the player's PC,
 * as the installer's {@code PROCESS_MINECRAFT_JAR} task does; Plexus Mod Loader never ships patched classes.
 *
 * <p>The bundle, after LZMA: the signature, an {@code int} entry count and a byte of the sides it targets, then per
 * entry a flags byte (operation and sides), an ASCII path with an {@code unsigned short} length, for a modification
 * the original's Adler32 as an {@code int}, and an {@code int} length and that many bytes: a GDIFF delta for a
 * modification, the file for a new entry, nothing for a removal.
 *
 * <p>Thread: immutable after {@link #read}; {@link #patch} may run on any thread.
 */
final class NeoForgePatchBundle {

    /** The jar a patch applies to; the bundle names the sides of each entry. */
    enum Side {
        CLIENT(0x01),
        SERVER(0x02),
        JOINED(0x04);

        private final int bit;

        Side(int bit) {
            this.bit = bit;
        }
    }

    private enum Operation {
        CREATE,
        MODIFY,
        REMOVE
    }

    private record Patch(Operation operation, String path, int sides, long checksum, byte[] data) {

        boolean appliesTo(Side side) {
            return (sides & side.bit) != 0;
        }
    }

    private static final byte[] SIGNATURE = "NFPATCHBUNDLE001".getBytes(StandardCharsets.US_ASCII);
    private static final int OPERATION_MASK = 0x18;
    private static final int SIDES_MASK = 0x07;
    /** The time NeoForge's installer gives entries the patches add (1989-11-25, a fixed value for stable jars). */
    private static final long NEW_ENTRY_TIME = 628041600000L;

    private final List<Patch> patches;

    private NeoForgePatchBundle(List<Patch> patches) {
        this.patches = patches;
    }

    /**
     * Reads a bundle stream; the caller closes {@code lzma}.
     *
     * @throws IOException when the stream is not a {@code NFPATCHBUNDLE001} bundle, or an entry is malformed
     */
    static NeoForgePatchBundle read(InputStream lzma) throws IOException {
        DataInputStream in = new DataInputStream(new LZMAInputStream(lzma));
        byte[] signature = new byte[SIGNATURE.length];
        try {
            in.readFully(signature);
        } catch (EOFException e) {
            throw new IOException("Not a NeoForge patch bundle: too short", e);
        }
        if (!Arrays.equals(signature, SIGNATURE)) {
            throw new IOException("Not a NeoForge patch bundle: the signature is "
                    + new String(signature, StandardCharsets.US_ASCII) + ", not NFPATCHBUNDLE001");
        }
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("NeoForge patch bundle with " + count + " entries");
        }
        int bundleSides = in.readUnsignedByte() & SIDES_MASK;
        List<Patch> patches = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int flags = in.readUnsignedByte();
            Operation operation = switch (flags & OPERATION_MASK) {
                case 0x00 -> Operation.CREATE;
                case 0x08 -> Operation.MODIFY;
                case 0x10 -> Operation.REMOVE;
                default -> throw new IOException("NeoForge patch entry " + i + " has unknown flags " + flags);
            };
            int sides = flags & SIDES_MASK;
            if ((sides & ~bundleSides) != 0) {
                throw new IOException("NeoForge patch entry " + i + " targets sides " + sides
                        + " the bundle does not declare (" + bundleSides + ")");
            }
            String path = readPath(in, i);
            long checksum = operation == Operation.MODIFY ? in.readInt() & 0xFFFFFFFFL : -1;
            int length = in.readInt();
            if (length < 0 || (operation == Operation.REMOVE && length != 0)) {
                throw new IOException("NeoForge patch of " + path + " has data length " + length);
            }
            patches.add(new Patch(operation, path, sides, checksum, in.readNBytes(length)));
        }
        return new NeoForgePatchBundle(Collections.unmodifiableList(patches));
    }

    private static String readPath(DataInputStream in, int index) throws IOException {
        byte[] bytes = in.readNBytes(in.readUnsignedShort());
        for (byte b : bytes) {
            if (b < 0x20 || b > 0x7E) {
                throw new IOException("NeoForge patch entry " + index + " has a path with byte " + (b & 0xFF));
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /** Number of entries, for every side. */
    int size() {
        return patches.size();
    }

    /**
     * Writes {@code gameJar} with the entries for {@code side} applied, to a temporary file that is then moved to
     * {@code output}, as NeoForge's installer does: the entries stay in the jar's order, new entries follow in bundle
     * order, and Mojang's signature files are left out because patched classes no longer match them. The manifest is
     * kept unchanged, as in NeoForge's own jar.
     *
     * @param gameJar the vanilla client jar, or the server classes (the jar inside the server bundler)
     * @return the entries that were patched, removed or added, in jar order and then bundle order
     * @throws IOException when an entry patches or removes a file the jar does not have, adds one it has, the original
     *     does not have the checksum NeoForge recorded (the patches are for another Minecraft version), or a delta does
     *     not apply
     */
    List<String> patch(Path gameJar, Path output, Side side) throws IOException {
        Map<String, Patch> bySide = new LinkedHashMap<>();
        for (Patch patch : patches) {
            if (patch.appliesTo(side) && bySide.put(patch.path(), patch) != null) {
                throw new IOException("Two NeoForge patches for " + side + " change " + patch.path());
            }
        }
        List<String> changed = new ArrayList<>();
        Path partial = output.resolveSibling(output.getFileName() + ".partial");
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (ZipFile in = new ZipFile(gameJar.toFile());
                OutputStream file = Files.newOutputStream(partial);
                ZipOutputStream out = new ZipOutputStream(file)) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || isSignatureFile(entry.getName())) {
                    continue;
                }
                Patch patch = bySide.remove(entry.getName());
                byte[] bytes;
                try (InputStream data = in.getInputStream(entry)) {
                    bytes = data.readAllBytes();
                }
                if (patch != null) {
                    changed.add(entry.getName());
                    switch (patch.operation()) {
                        case REMOVE -> {
                            continue;
                        }
                        case MODIFY -> bytes = modify(patch, bytes);
                        case CREATE ->
                            throw new IOException("NeoForge adds " + entry.getName() + ", which " + gameJar
                                    + " already has; the patches are for another Minecraft version");
                    }
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
            for (Patch patch : bySide.values()) {
                if (patch.operation() != Operation.CREATE) {
                    throw new IOException("NeoForge patches " + patch.path() + ", which " + gameJar
                            + " does not have; the patches are for another Minecraft version");
                }
                ZipEntry added = new ZipEntry(patch.path());
                added.setTime(NEW_ENTRY_TIME);
                out.putNextEntry(added);
                out.write(patch.data());
                out.closeEntry();
                changed.add(patch.path());
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return changed;
    }

    private static byte[] modify(Patch patch, byte[] original) throws IOException {
        Adler32 adler = new Adler32();
        adler.update(original);
        if (adler.getValue() != patch.checksum()) {
            throw new IOException("NeoForge expects " + patch.path() + " to have the checksum "
                    + Long.toHexString(patch.checksum()) + ", but it has " + Long.toHexString(adler.getValue())
                    + "; the patches are for another Minecraft version");
        }
        return ForgeBinaryPatch.gdiff(original, patch.data());
    }

    /** Mojang's signature in {@code META-INF/}, as NeoForge's installer recognizes it (case-sensitive). */
    private static boolean isSignatureFile(String name) {
        if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length()) >= 0) {
            return false;
        }
        return name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC");
    }
}

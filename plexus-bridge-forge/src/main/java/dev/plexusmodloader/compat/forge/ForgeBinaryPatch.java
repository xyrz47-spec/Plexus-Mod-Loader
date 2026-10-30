package dev.plexusmodloader.compat.forge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * One Forge binary patch ({@code <class>.binpatch} inside the installer's {@code data/<side>.lzma}), in the format of
 * {@code net.minecraftforge:binarypatcher} 1.3.4 ({@code Patch.from}, checked with {@code javap}; ADR-0021 G2):
 *
 * <ol>
 *   <li>format byte {@code 1}
 *   <li>{@code UTF} original name, {@code UTF} patched name (binary names with dots)
 *   <li>{@code boolean} whether the class exists in the game; if so an {@code int} checksum of the original
 *   <li>{@code int} length and that many bytes: a GDIFF delta (version 4) against the original class bytes, or
 *       against nothing for a class Forge adds
 * </ol>
 *
 * <p>Applying every patch of Forge 26.2-65.1.5's {@code server.lzma} this way to the vanilla server classes gives
 * byte-identical classes to Forge's installer output (1394 of 1394; {@code docs/plans/FORGE_SUPPORT.md}).
 *
 * @param originalName the class before patching, as a binary name
 * @param patchedName the class after patching, as a binary name
 * @param exists whether the game has the class; {@code false} for classes Forge adds
 * @param checksum the original's checksum as Forge recorded it, or 0 when {@code exists} is false
 * @param delta the GDIFF delta
 */
record ForgeBinaryPatch(String originalName, String patchedName, boolean exists, int checksum, byte[] delta) {

    private static final int FORMAT = 1;
    private static final int GDIFF_MAGIC = 0xD1FFD1FF;
    private static final int GDIFF_VERSION = 4;

    ForgeBinaryPatch {
        delta = delta.clone();
    }

    /** Reads one {@code .binpatch} entry. */
    static ForgeBinaryPatch read(byte[] entry) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(entry));
        int format = in.readUnsignedByte();
        if (format != FORMAT) {
            throw new IOException("Unsupported Forge binary patch format " + format + " (expected " + FORMAT + ")");
        }
        String original = in.readUTF();
        String patched = in.readUTF();
        boolean exists = in.readBoolean();
        int checksum = exists ? in.readInt() : 0;
        byte[] delta = new byte[in.readInt()];
        in.readFully(delta);
        return new ForgeBinaryPatch(original, patched, exists, checksum, delta);
    }

    /** The JVM internal name of the patched class, the jar entry is this plus {@code .class}. */
    String patchedInternalName() {
        return patchedName.replace('.', '/');
    }

    /**
     * Applies the delta.
     *
     * @param original the class bytes before patching; ignored (may be {@code null}) when the class is new
     * @throws IOException when the delta is not GDIFF version 4, or reads outside the original
     */
    byte[] apply(byte[] original) throws IOException {
        byte[] source = exists ? original : new byte[0];
        if (source == null) {
            throw new IOException("Forge patches " + originalName + ", which the game jar does not have");
        }
        return gdiff(source, delta);
    }

    /** Applies a GDIFF (version 4) delta, the format {@code com.nothome.delta.GDiffPatcher} reads. */
    static byte[] gdiff(byte[] source, byte[] delta) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(delta));
        if (in.readInt() != GDIFF_MAGIC || in.readUnsignedByte() != GDIFF_VERSION) {
            throw new IOException("Not a GDIFF version 4 delta");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(source.length + delta.length);
        while (true) {
            int command = in.readUnsignedByte();
            if (command == 0) {
                return out.toByteArray();
            }
            if (command <= 246) {
                out.write(in.readNBytes(command));
                continue;
            }
            switch (command) {
                case 247 -> out.write(in.readNBytes(in.readUnsignedShort()));
                case 248 -> out.write(in.readNBytes(in.readInt()));
                case 249 -> copy(source, in.readUnsignedShort(), in.readUnsignedByte(), out);
                case 250 -> copy(source, in.readUnsignedShort(), in.readUnsignedShort(), out);
                case 251 -> copy(source, in.readUnsignedShort(), in.readInt(), out);
                case 252 -> copy(source, in.readInt(), in.readUnsignedByte(), out);
                case 253 -> copy(source, in.readInt(), in.readUnsignedShort(), out);
                case 254 -> copy(source, in.readInt(), in.readInt(), out);
                default -> copy(source, in.readLong(), in.readInt(), out);
            }
        }
    }

    private static void copy(byte[] source, long position, int length, ByteArrayOutputStream out) throws IOException {
        if (position < 0 || length < 0 || position + length > source.length) {
            throw new IOException("GDIFF copy of " + length + " byte(s) at " + position + " is outside the original ("
                    + source.length + " bytes)");
        }
        out.write(Arrays.copyOfRange(source, (int) position, (int) position + length));
    }
}

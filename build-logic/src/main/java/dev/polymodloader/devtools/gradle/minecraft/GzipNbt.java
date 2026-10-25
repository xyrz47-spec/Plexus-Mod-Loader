package dev.polymodloader.devtools.gradle.minecraft;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A gzip-compressed NBT file as Minecraft writes its {@code .dat} files: one named compound tag, read and written
 * whole. Tags keep their type, so a file read and written back without changes has the same content. Used by
 * verification tasks to adjust saved test worlds; not a general NBT library.
 */
final class GzipNbt {

    private static final byte END = 0;
    private static final byte BYTE = 1;
    private static final byte SHORT = 2;
    private static final byte INT = 3;
    private static final byte LONG = 4;
    private static final byte FLOAT = 5;
    private static final byte DOUBLE = 6;
    private static final byte BYTE_ARRAY = 7;
    static final byte STRING = 8;
    private static final byte LIST = 9;
    static final byte COMPOUND = 10;
    private static final byte INT_ARRAY = 11;
    private static final byte LONG_ARRAY = 12;

    private GzipNbt() {}

    /** A tag: a value of one of the simple types, a compound or a list. */
    sealed interface Tag permits Value, Compound, ListTag {}

    /** A tag of type 1 to 8, 11 or 12 with its Java value (Byte, Short, …, byte[], String, int[], long[]). */
    record Value(byte type, Object value) implements Tag {}

    /** A compound tag; entries keep their order. */
    record Compound(Map<String, Tag> entries) implements Tag {

        Compound() {
            this(new LinkedHashMap<>());
        }
    }

    /** A list tag of elements of one type ({@code END} when empty). */
    record ListTag(byte elementType, List<Tag> elements) implements Tag {}

    /** The file's root: a named compound. */
    record Root(String name, Compound compound) {}

    static Value string(String value) {
        return new Value(STRING, value);
    }

    static Root read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            byte type = in.readByte();
            if (type != COMPOUND) {
                throw new IOException(file + " does not start with a compound tag (type " + type + ")");
            }
            String name = in.readUTF();
            return new Root(name, (Compound) readPayload(in, COMPOUND));
        }
    }

    static void write(Path file, Root root) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
            out.writeByte(COMPOUND);
            out.writeUTF(root.name());
            writePayload(out, root.compound());
        }
    }

    private static Tag readPayload(DataInputStream in, byte type) throws IOException {
        return switch (type) {
            case BYTE -> new Value(type, in.readByte());
            case SHORT -> new Value(type, in.readShort());
            case INT -> new Value(type, in.readInt());
            case LONG -> new Value(type, in.readLong());
            case FLOAT -> new Value(type, in.readFloat());
            case DOUBLE -> new Value(type, in.readDouble());
            case BYTE_ARRAY -> {
                byte[] bytes = new byte[in.readInt()];
                in.readFully(bytes);
                yield new Value(type, bytes);
            }
            case STRING -> new Value(type, in.readUTF());
            case LIST -> {
                byte elementType = in.readByte();
                int size = in.readInt();
                List<Tag> elements = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    elements.add(readPayload(in, elementType));
                }
                yield new ListTag(elementType, elements);
            }
            case COMPOUND -> {
                Compound compound = new Compound();
                for (byte entryType = in.readByte(); entryType != END; entryType = in.readByte()) {
                    String name = in.readUTF();
                    compound.entries().put(name, readPayload(in, entryType));
                }
                yield compound;
            }
            case INT_ARRAY -> {
                int[] ints = new int[in.readInt()];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = in.readInt();
                }
                yield new Value(type, ints);
            }
            case LONG_ARRAY -> {
                long[] longs = new long[in.readInt()];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = in.readLong();
                }
                yield new Value(type, longs);
            }
            default -> throw new IOException("unknown NBT tag type " + type);
        };
    }

    private static void writePayload(DataOutputStream out, Tag tag) throws IOException {
        switch (tag) {
            case Compound compound -> {
                for (Map.Entry<String, Tag> entry : compound.entries().entrySet()) {
                    out.writeByte(typeOf(entry.getValue()));
                    out.writeUTF(entry.getKey());
                    writePayload(out, entry.getValue());
                }
                out.writeByte(END);
            }
            case ListTag list -> {
                out.writeByte(list.elementType());
                out.writeInt(list.elements().size());
                for (Tag element : list.elements()) {
                    writePayload(out, element);
                }
            }
            case Value value -> {
                switch (value.type()) {
                    case BYTE -> out.writeByte((Byte) value.value());
                    case SHORT -> out.writeShort((Short) value.value());
                    case INT -> out.writeInt((Integer) value.value());
                    case LONG -> out.writeLong((Long) value.value());
                    case FLOAT -> out.writeFloat((Float) value.value());
                    case DOUBLE -> out.writeDouble((Double) value.value());
                    case BYTE_ARRAY -> {
                        byte[] bytes = (byte[]) value.value();
                        out.writeInt(bytes.length);
                        out.write(bytes);
                    }
                    case STRING -> out.writeUTF((String) value.value());
                    case INT_ARRAY -> {
                        int[] ints = (int[]) value.value();
                        out.writeInt(ints.length);
                        for (int i : ints) {
                            out.writeInt(i);
                        }
                    }
                    case LONG_ARRAY -> {
                        long[] longs = (long[]) value.value();
                        out.writeInt(longs.length);
                        for (long l : longs) {
                            out.writeLong(l);
                        }
                    }
                    default -> throw new IOException("unknown NBT tag type " + value.type());
                }
            }
        }
    }

    private static byte typeOf(Tag tag) {
        return switch (tag) {
            case Value value -> value.type();
            case Compound compound -> COMPOUND;
            case ListTag list -> LIST;
        };
    }
}

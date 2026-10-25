package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAOutputStream;

class NeoForgePatchBundleTest {

    private static final int CLIENT = 1;
    private static final int SERVER = 2;
    private static final int JOINED = 4;

    @Test
    void appliesTheEntriesOfItsSide(@TempDir Path directory) throws IOException {
        byte[] main = "hello world".getBytes(StandardCharsets.US_ASCII);
        Path game = jar(
                directory.resolve("server.jar"),
                Map.of(
                        "net/minecraft/server/Main.class", main,
                        "net/minecraft/Old.class", new byte[] {1},
                        "META-INF/MOJANGCS.SF", new byte[] {2},
                        "data/kept.json", new byte[] {3}));
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.modify(CLIENT | SERVER | JOINED, "net/minecraft/server/Main.class", main, newWorld()));
        entries.add(Entry.create(SERVER | JOINED, "net/neoforged/Added.class", new byte[] {9}));
        entries.add(Entry.remove(SERVER, "net/minecraft/Old.class"));
        entries.add(Entry.create(CLIENT, "net/minecraft/client/OnlyClient.class", new byte[] {7}));
        NeoForgePatchBundle bundle = NeoForgePatchBundle.read(bundle(CLIENT | SERVER | JOINED, entries));

        Path patched = directory.resolve("patched.jar");
        List<String> changed = bundle.patch(game, patched, NeoForgePatchBundle.Side.SERVER);

        assertEquals(
                List.of("net/minecraft/Old.class", "net/minecraft/server/Main.class", "net/neoforged/Added.class"),
                changed);
        try (ZipFile out = new ZipFile(patched.toFile())) {
            assertEquals(
                    "new world", new String(read(out, "net/minecraft/server/Main.class"), StandardCharsets.US_ASCII));
            assertArrayEquals(new byte[] {9}, read(out, "net/neoforged/Added.class"));
            assertArrayEquals(new byte[] {3}, read(out, "data/kept.json"));
            assertNull(out.getEntry("net/minecraft/Old.class"));
            assertNull(out.getEntry("net/minecraft/client/OnlyClient.class"), "a client entry is not for the server");
            assertNull(out.getEntry("META-INF/MOJANGCS.SF"), "the signature no longer matches the patched classes");
        }
    }

    @Test
    void refusesAChangedOriginal(@TempDir Path directory) throws IOException {
        byte[] main = "hello world".getBytes(StandardCharsets.US_ASCII);
        Path game = jar(directory.resolve("server.jar"), Map.of("net/minecraft/server/Main.class", main));
        NeoForgePatchBundle bundle = NeoForgePatchBundle.read(bundle(
                SERVER,
                List.of(Entry.modify(
                        SERVER,
                        "net/minecraft/server/Main.class",
                        "other".getBytes(StandardCharsets.US_ASCII),
                        newWorld()))));
        Path patched = directory.resolve("patched.jar");

        IOException failure =
                assertThrows(IOException.class, () -> bundle.patch(game, patched, NeoForgePatchBundle.Side.SERVER));

        assertTrue(failure.getMessage().contains("checksum"), failure.getMessage());
        assertFalse(Files.exists(patched));
        assertFalse(Files.exists(directory.resolve("patched.jar.partial")));
    }

    @Test
    void refusesOtherFormatsAndMissingTargets(@TempDir Path directory) throws IOException {
        byte[] notABundle = lzma("NFPATCHBUNDLE999".getBytes(StandardCharsets.US_ASCII));
        assertThrows(IOException.class, () -> NeoForgePatchBundle.read(new ByteArrayInputStream(notABundle)));

        NeoForgePatchBundle bundle = NeoForgePatchBundle.read(bundle(
                SERVER, List.of(Entry.modify(SERVER, "net/minecraft/Missing.class", new byte[] {1}, newWorld()))));
        Path game = jar(directory.resolve("server.jar"), Map.of("data/kept.json", new byte[] {3}));
        IOException missing = assertThrows(
                IOException.class,
                () -> bundle.patch(game, directory.resolve("patched.jar"), NeoForgePatchBundle.Side.SERVER));
        assertTrue(missing.getMessage().contains("net/minecraft/Missing.class"), missing.getMessage());
    }

    @Test
    void refusesAnEntryForASideTheBundleDoesNotDeclare() throws IOException {
        InputStream bundle = bundle(SERVER, List.of(Entry.create(CLIENT, "a/B.class", new byte[] {1})));

        assertThrows(IOException.class, () -> NeoForgePatchBundle.read(bundle));
    }

    /**
     * The real patches give NeoForge's own result: every entry of the jar NeoForge's installer made, with the same
     * bytes. Runs when given the NeoForge installer, the vanilla client jar or server classes, and that jar ({@code
     * -PneoForgeInstaller=... -PneoForgeVanillaJar=... -PneoForgePatchedJar=...}, with {@code -PneoForgeSide=client}
     * for the client).
     */
    @Test
    @EnabledIfSystemProperty(named = "poly.neoForgeInstaller", matches = ".+")
    void realPatchesMatchNeoForgesInstaller(@TempDir Path directory) throws IOException {
        NeoForgePatchBundle bundle;
        try (JarFile installer = new JarFile(System.getProperty("poly.neoForgeInstaller"));
                InputStream lzma = installer.getInputStream(installer.getEntry("data/client.lzma"))) {
            bundle = NeoForgePatchBundle.read(lzma);
        }
        Path patched = directory.resolve("patched.jar");
        NeoForgePatchBundle.Side side = NeoForgePatchBundle.Side.valueOf(
                System.getProperty("poly.neoForgeSide", "server").toUpperCase(java.util.Locale.ROOT));
        List<String> changed = bundle.patch(Path.of(System.getProperty("poly.neoForgeVanillaJar")), patched, side);

        assertFalse(changed.isEmpty());
        Map<String, byte[]> ours = files(patched);
        Map<String, byte[]> neoForge = files(Path.of(System.getProperty("poly.neoForgePatchedJar")));
        assertEquals(neoForge.keySet(), ours.keySet());
        for (Map.Entry<String, byte[]> expected : neoForge.entrySet()) {
            assertArrayEquals(expected.getValue(), ours.get(expected.getKey()), expected.getKey());
        }
    }

    private static Map<String, byte[]> files(Path jar) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), read(zip, entry.getName()));
                }
            }
        }
        return files;
    }

    private static byte[] newWorld() throws IOException {
        ByteArrayOutputStream delta = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(delta);
        out.writeInt(0xD1FFD1FF);
        out.writeByte(4);
        out.writeByte(3); // DATA, 3 bytes
        out.writeBytes("new");
        out.writeByte(249); // COPY ushort position, ubyte length
        out.writeShort(5);
        out.writeByte(6);
        out.writeByte(0); // EOF
        return delta.toByteArray();
    }

    private record Entry(int flags, String path, Long checksum, byte[] data) {

        static Entry create(int sides, String path, byte[] data) {
            return new Entry(sides, path, null, data);
        }

        static Entry modify(int sides, String path, byte[] original, byte[] delta) {
            Adler32 adler = new Adler32();
            adler.update(original);
            return new Entry(0x08 | sides, path, adler.getValue(), delta);
        }

        static Entry remove(int sides, String path) {
            return new Entry(0x10 | sides, path, null, new byte[0]);
        }
    }

    private static InputStream bundle(int sides, List<Entry> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("NFPATCHBUNDLE001");
        out.writeInt(entries.size());
        out.writeByte(sides);
        for (Entry entry : entries) {
            out.writeByte(entry.flags());
            out.writeShort(entry.path().length());
            out.writeBytes(entry.path());
            if (entry.checksum() != null) {
                out.writeInt((int) (long) entry.checksum());
            }
            out.writeInt(entry.data().length);
            out.write(entry.data());
        }
        return new ByteArrayInputStream(lzma(bytes.toByteArray()));
    }

    private static byte[] lzma(byte[] data) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream out = new LZMAOutputStream(compressed, new LZMA2Options(), -1)) {
            out.write(data);
        }
        return compressed.toByteArray();
    }

    private static Path jar(Path path, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(entries).entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return path;
    }

    private static byte[] read(ZipFile zip, String name) throws IOException {
        try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
            return in.readAllBytes();
        }
    }
}

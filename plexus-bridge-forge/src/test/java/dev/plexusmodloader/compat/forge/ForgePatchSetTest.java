package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class ForgePatchSetTest {

    @Test
    void appliesGdiffDataAndCopyCommands() throws IOException {
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

        byte[] result = ForgeBinaryPatch.gdiff("hello world".getBytes(StandardCharsets.US_ASCII), delta.toByteArray());

        assertEquals("new world", new String(result, StandardCharsets.US_ASCII));
    }

    @Test
    void readsTheBinpatchHeader() throws IOException {
        byte[] entry = binpatch(true, new byte[] {(byte) 0xD1, (byte) 0xFF, (byte) 0xD1, (byte) 0xFF, 4, 0});

        ForgeBinaryPatch patch = ForgeBinaryPatch.read(entry);

        assertEquals("net.minecraft.server.Main", patch.originalName());
        assertEquals("net/minecraft/server/Main", patch.patchedInternalName());
        assertTrue(patch.exists());
        assertEquals(1234, patch.checksum());
        assertArrayEquals(new byte[0], patch.apply(new byte[] {1, 2, 3}), "an empty delta gives an empty class");
    }

    @Test
    void refusesOtherFormatsAndCopiesOutsideTheOriginal() throws IOException {
        byte[] entry = binpatch(true, new byte[0]);
        entry[0] = 2;
        assertThrows(IOException.class, () -> ForgeBinaryPatch.read(entry));

        ByteArrayOutputStream delta = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(delta);
        out.writeInt(0xD1FFD1FF);
        out.writeByte(4);
        out.writeByte(249);
        out.writeShort(10);
        out.writeByte(5);
        out.writeByte(0);
        IOException outside =
                assertThrows(IOException.class, () -> ForgeBinaryPatch.gdiff(new byte[4], delta.toByteArray()));
        assertTrue(outside.getMessage().contains("outside the original"), outside.getMessage());
    }

    /**
     * The real patches give Forge's own result. Runs when given the Forge installer, the vanilla server classes and the
     * patched server jar Forge's installer made ({@code -PforgeInstaller=... -PforgeVanillaServer=...
     * -PforgePatchedServer=...}).
     */
    @Test
    @EnabledIfSystemProperty(named = "poly.forgeInstaller", matches = ".+")
    void realPatchesMatchForgesInstaller(@org.junit.jupiter.api.io.TempDir Path directory) throws IOException {
        ForgePatchSet patches;
        try (JarFile installer = new JarFile(System.getProperty("poly.forgeInstaller"));
                InputStream lzma = installer.getInputStream(installer.getEntry("data/server.lzma"))) {
            patches = ForgePatchSet.read(lzma);
        }
        Path patched = directory.resolve("patched.jar");
        List<String> changed = patches.patch(Path.of(System.getProperty("poly.forgeVanillaServer")), patched);

        assertEquals(patches.size(), changed.size());
        assertFalse(changed.isEmpty());
        try (JarFile ours = new JarFile(patched.toFile())) {
            assertTrue(
                    ours.stream().noneMatch(entry -> entry.getName().endsWith(".SF")),
                    "Mojang's signature is left out, patched classes no longer match it");
        }
        try (JarFile ours = new JarFile(patched.toFile());
                JarFile forge = new JarFile(System.getProperty("poly.forgePatchedServer"))) {
            for (String name : changed) {
                ZipEntry expected = forge.getEntry(name);
                assertTrue(expected != null, name + " is not in Forge's jar");
                assertArrayEquals(read(forge, expected), read(ours, ours.getEntry(name)), name);
            }
        }
    }

    private static byte[] read(JarFile jar, ZipEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static byte[] binpatch(boolean exists, byte[] delta) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeUTF("net.minecraft.server.Main");
        out.writeUTF("net.minecraft.server.Main");
        out.writeBoolean(exists);
        if (exists) {
            out.writeInt(1234);
        }
        out.writeInt(delta.length);
        out.write(delta);
        return bytes.toByteArray();
    }
}

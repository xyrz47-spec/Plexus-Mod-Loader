package dev.plexusmodloader.runtime.classloading;

import java.nio.charset.StandardCharsets;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Keeps mods built before the rename working: a class file that still refers to the former {@code dev.polymodloader}
 * packages is rewritten to {@code dev.plexusmodloader} before it is defined.
 */
final class LegacyPackageRemapper {

    private static final String OLD_PREFIX = "dev/polymodloader/";
    private static final String NEW_PREFIX = "dev/plexusmodloader/";
    private static final byte[] OLD_BYTES = OLD_PREFIX.getBytes(StandardCharsets.UTF_8);

    private LegacyPackageRemapper() {}

    /** Returns {@code classBytes} untouched unless it mentions a former package. */
    static byte[] remap(byte[] classBytes) {
        if (!contains(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(
                new ClassRemapper(writer, new Remapper(Opcodes.ASM9) {
                    @Override
                    public String map(String internalName) {
                        return internalName.startsWith(OLD_PREFIX)
                                ? NEW_PREFIX + internalName.substring(OLD_PREFIX.length())
                                : internalName;
                    }
                }),
                0);
        return writer.toByteArray();
    }

    private static boolean contains(byte[] haystack) {
        outer:
        for (int i = 0; i <= haystack.length - OLD_BYTES.length; i++) {
            for (int j = 0; j < OLD_BYTES.length; j++) {
                if (haystack[i + j] != OLD_BYTES[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}

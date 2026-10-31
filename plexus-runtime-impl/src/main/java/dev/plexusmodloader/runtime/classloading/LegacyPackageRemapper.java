package dev.plexusmodloader.runtime.classloading;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Keeps mods built before the rename working: a class file that still refers to the former {@code dev.polymodloader}
 * packages is rewritten to {@code dev.plexusmodloader} before it is defined, and references to API types renamed in
 * 1.5.0 (such as {@code PolySystem}) are rewritten to their current names.
 */
final class LegacyPackageRemapper {

    private static final String OLD_PREFIX = "dev/polymodloader/";
    private static final String NEW_PREFIX = "dev/plexusmodloader/";
    private static final byte[] OLD_BYTES = OLD_PREFIX.getBytes(StandardCharsets.UTF_8);
    /** API types renamed in 1.5.0, by their 1.4.2 internal name. */
    private static final Map<String, String> RENAMED_TYPES =
            Map.of("dev/plexusmodloader/api/system/PolySystem", "dev/plexusmodloader/api/system/PlexusSystem");

    private static final byte[] RENAMED_BYTES = "api/system/PolySystem".getBytes(StandardCharsets.UTF_8);

    private LegacyPackageRemapper() {}

    /** Returns {@code classBytes} untouched unless it mentions a former package or a renamed type. */
    static byte[] remap(byte[] classBytes) {
        if (!contains(classBytes, OLD_BYTES) && !contains(classBytes, RENAMED_BYTES)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(
                new ClassRemapper(writer, new Remapper(Opcodes.ASM9) {
                    @Override
                    public String map(String internalName) {
                        String current = internalName.startsWith(OLD_PREFIX)
                                ? NEW_PREFIX + internalName.substring(OLD_PREFIX.length())
                                : internalName;
                        return RENAMED_TYPES.getOrDefault(current, current);
                    }
                }),
                0);
        return writer.toByteArray();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}

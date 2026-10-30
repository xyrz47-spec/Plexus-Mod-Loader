package dev.plexusmodloader.runtime.classloading;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class LegacyPackageRemapperTest {

    private static byte[] classExtending(String superName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "mod/Sample", null, superName, null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void rewritesFormerPackages() {
        byte[] remapped = LegacyPackageRemapper.remap(classExtending("dev/polymodloader/api/Thing"));
        assertEquals("dev/plexusmodloader/api/Thing", new ClassReader(remapped).getSuperName());
    }

    @Test
    void leavesOtherClassesUntouched() {
        byte[] plain = classExtending("java/lang/Object");
        assertArrayEquals(plain, LegacyPackageRemapper.remap(plain));
    }
}

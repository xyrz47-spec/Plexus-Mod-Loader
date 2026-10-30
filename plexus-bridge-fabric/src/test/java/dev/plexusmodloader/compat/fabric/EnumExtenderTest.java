package dev.plexusmodloader.compat.fabric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

class EnumExtenderTest {

    /** An enum with a constant, a non-constant field and methods, to see where the added constant goes. */
    enum Shade {
        LIGHT,
        DARK;

        static final String LABEL = "shade";

        String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final String SHADE = Shade.class.getName().replace('.', '/');

    @Test
    void addsAStubConstantAfterTheExistingOnes() throws Exception {
        EnumExtender extender = EnumExtender.of(List.of(
                new ClassTweakerFile.EnumExtension(2, SHADE, "POLY_DIM"),
                new ClassTweakerFile.EnumExtension(3, SHADE, "LIGHT")));

        ClassNode node = node(extender.apply(SHADE, bytes(Shade.class)));

        List<String> fields = node.fields.stream().map(field -> field.name).toList();
        assertEquals(List.of("LIGHT", "DARK", "POLY_DIM"), fields.subList(0, 3), "after the existing constants");
        assertTrue(fields.indexOf("LABEL") > 2, "other fields come after the added constant");
        FieldNode added = node.fields.get(2);
        assertEquals("L" + SHADE + ";", added.desc);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, added.access);
        assertEquals(
                List.of("org.spongepowered.asm.mixin.StubEnumConstant"),
                added.attrs.stream().map(attribute -> attribute.type).toList());
        assertEquals(1, fields.stream().filter("LIGHT"::equals).count(), "an existing name is not added again");
        assertEquals(2, extender.count());
    }

    @Test
    void theExtendedEnumStillLoadsAndTheStubIsNull() throws Exception {
        EnumExtender extender = EnumExtender.of(List.of(new ClassTweakerFile.EnumExtension(2, SHADE, "POLY_DIM")));
        byte[] changed = extender.apply(SHADE, bytes(Shade.class));

        Class<?> loaded = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(Shade.class.getName())) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> done = findLoadedClass(name);
                        return done != null ? done : defineClass(name, changed, 0, changed.length);
                    }
                }
                return super.loadClass(name, resolve);
            }
        }.loadClass(Shade.class.getName());

        Field stub = loaded.getField("POLY_DIM");
        stub.setAccessible(true); // the test enum is package-private, and this copy is in another class loader
        assertTrue(stub.isEnumConstant());
        assertNull(stub.get(null), "no mixin assigned it, as under Fabric Loader");
        assertArrayEquals(
                new String[] {"LIGHT", "DARK"},
                Arrays.stream(loaded.getEnumConstants()).map(Object::toString).toArray(String[]::new));
    }

    @Test
    void otherClassesAreUntouched() throws Exception {
        EnumExtender extender = EnumExtender.of(List.of(new ClassTweakerFile.EnumExtension(2, SHADE, "POLY_DIM")));
        byte[] other = bytes(EnumExtenderTest.class);
        assertSame(other, extender.apply(EnumExtenderTest.class.getName(), other));
        assertTrue(EnumExtender.of(List.of()).isEmpty());
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        Attribute stub = new Attribute("org.spongepowered.asm.mixin.StubEnumConstant") {};
        new ClassReader(bytes).accept(node, new Attribute[] {stub}, 0);
        return node;
    }

    private static byte[] bytes(Class<?> type) throws Exception {
        try (var in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            return in.readAllBytes();
        }
    }
}

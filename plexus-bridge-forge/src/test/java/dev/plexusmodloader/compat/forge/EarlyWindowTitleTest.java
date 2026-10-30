package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** The early loading window's title names Poly Mod Loader. */
class EarlyWindowTitleTest {

    private static final ClassDesc WINDOW = ClassDesc.of("t.DisplayWindow");

    @Test
    void theTitleConstantIsRewrittenAndTheClassStillRuns() throws Exception {
        byte[] original = window(EarlyWindowTitle.NEOFORGE_TITLE, "Minecraft: NeoForge");

        byte[] patched = EarlyWindowTitle.replaced(original, EarlyWindowTitle.NEOFORGE_TITLE, "Minecraft* Plexus 26.3");

        Class<?> type = define(patched);
        Method title = type.getMethod("title");
        Method dialog = type.getMethod("dialog");
        assertEquals("Minecraft* Plexus 26.3", title.invoke(null));
        assertEquals("Minecraft: NeoForge", dialog.invoke(null));
    }

    @Test
    void forgesTitlePrefixGetsPolysName() throws Exception {
        byte[] patched = EarlyWindowTitle.replaced(
                window(EarlyWindowTitle.FORGE_TITLE, "Minecraft: Forge"),
                EarlyWindowTitle.FORGE_TITLE,
                "Minecraft* Plexus ");

        assertEquals("Minecraft* Plexus ", define(patched).getMethod("title").invoke(null));
    }

    /** A constant loaded more than once is not the shape checked: the class stays as it is. */
    @Test
    void aConstantLoadedTwiceIsLeftAlone() {
        byte[] original = window(EarlyWindowTitle.FORGE_TITLE, EarlyWindowTitle.FORGE_TITLE);

        assertSame(original, EarlyWindowTitle.replaced(original, EarlyWindowTitle.FORGE_TITLE, "Minecraft* Plexus"));
    }

    /** {@code t.DisplayWindow} with {@code static String title()} and {@code static String dialog()}. */
    private static byte[] window(String title, String dialog) {
        return ClassFile.of().build(WINDOW, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC);
            builder.withMethodBody(
                    "title",
                    MethodTypeDesc.of(ConstantDescs.CD_String),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    code -> code.ldc(title).areturn());
            builder.withMethodBody(
                    "dialog",
                    MethodTypeDesc.of(ConstantDescs.CD_String),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    code -> code.ldc(dialog).areturn());
        });
    }

    private static Class<?> define(byte[] bytes) throws ClassNotFoundException {
        ClassLoader loader = new ClassLoader(EarlyWindowTitleTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!name.equals("t.DisplayWindow")) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return loader.loadClass("t.DisplayWindow");
    }
}

package dev.polymodloader.compat.fabric.mixin;

import static java.lang.constant.ConstantDescs.CD_String;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * ADR-0026 42: NeoForge's patch gives {@code BlockStateModel} its {@code BlockStateModelExtension} and Fabric API's class
 * tweaker injects {@code FabricBlockStateModel}; both have a default {@code particleMaterial(...)}, and the client crashed
 * with {@code IncompatibleClassChangeError: Conflicting default methods} in {@code SingleVariant.particleMaterial}. The
 * interface now gets its own default, which calls the host's.
 */
class HostInterfaceDefaultsTest {

    private static final ClassDesc MODEL = ClassDesc.of("t.Model");
    private static final ClassDesc HOST = ClassDesc.of("t.HostExtension");
    private static final ClassDesc MOD = ClassDesc.of("m.FabricModel");
    private static final ClassDesc VARIANT = ClassDesc.of("t.Variant");
    private static final MethodTypeDesc PARTICLE = MethodTypeDesc.of(CD_String);

    @Test
    void anInterfaceWithTheHostsAndAModsDefaultKeepsTheHostsAndItsClassesNoLongerThrow() throws Exception {
        Map<String, byte[]> bytes = new HashMap<>();
        bytes.put("t/HostExtension", defaultInterface(HOST, "host"));
        bytes.put("m/FabricModel", defaultInterface(MOD, "mod"));
        byte[] patched = model(List.of(HOST));
        byte[] withMods = model(List.of(HOST, MOD));
        bytes.put("t/Variant", variant());
        List<HostInterfaceDefaults.Resolution> reported = new ArrayList<>();
        HostInterfaceDefaults defaults = new HostInterfaceDefaults(
                HostInterfaceDefaults.lookup(name -> Optional.ofNullable(bytes.get(name))), reported::add);

        // Before: the variant's call of particle() throws, as on the client.
        bytes.put("t/Model", withMods);
        Throwable before = assertThrows(InvocationTargetException.class, () -> particle(bytes));
        assertTrue(
                before.getCause() instanceof IncompatibleClassChangeError,
                before.getCause().toString());

        byte[] resolved = defaults.apply("t.Model", patched, withMods);
        bytes.put("t/Model", resolved);
        assertEquals("host", particle(bytes));
        assertEquals(1, reported.size());
        assertEquals("t/HostExtension", reported.getFirst().kept());
        assertEquals("m/FabricModel", reported.getFirst().left());
        assertEquals("particle()Ljava/lang/String;", reported.getFirst().method());

        // Nothing added by the stage, or a class: unchanged.
        assertSame(patched, defaults.apply("t.Model", patched, patched));
        assertSame(bytes.get("t/Variant"), defaults.apply("t.Variant", variant(), bytes.get("t/Variant")));
    }

    private static Object particle(Map<String, byte[]> bytes) throws Exception {
        ClassLoader loader = new ClassLoader(HostInterfaceDefaultsTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] found = bytes.get(name.replace('.', '/'));
                if (found == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, found, 0, found.length);
            }
        };
        Object variant = loader.loadClass("t.Variant").getConstructor().newInstance();
        return loader.loadClass("t.Model").getMethod("particle").invoke(variant);
    }

    private static byte[] defaultInterface(ClassDesc name, String value) {
        return ClassFile.of().build(name, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            builder.withMethodBody("particle", PARTICLE, ClassFile.ACC_PUBLIC, code -> {
                code.ldc(value);
                code.areturn();
            });
        });
    }

    private static byte[] model(List<ClassDesc> interfaces) {
        return ClassFile.of().build(MODEL, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            builder.withInterfaceSymbols(interfaces);
        });
    }

    private static byte[] variant() {
        return ClassFile.of().build(VARIANT, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC);
            builder.withInterfaceSymbols(MODEL);
            builder.withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> {
                code.aload(0);
                code.invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                code.return_();
            });
        });
    }
}

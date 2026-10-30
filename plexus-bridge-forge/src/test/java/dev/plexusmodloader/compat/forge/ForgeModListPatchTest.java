package dev.plexusmodloader.compat.forge;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-0025: FML's {@code ModList} lookups ask Poly Mod Loader only when FML has no answer. The class under test has
 * {@code ModList}'s name and the method bodies {@code javap} shows in fmlcore 26.2-65.1.5 and 26.1-62.0.9.
 */
class ForgeModListPatchTest {

    /** Stands in for forge-host's {@code ForeignModList}. */
    public static final class Answers {
        static final Map<String, IModFileInfo> FILES = new HashMap<>();

        public static IModFileInfo getModFileById(String id) {
            return FILES.get(id);
        }

        public static boolean isLoaded(String id) {
            return FILES.containsKey(id);
        }
    }

    private static final ClassDesc MOD_LIST = ClassDesc.of(ForgeModListPatch.CLASS_NAME);
    private static final ClassDesc MOD_FILE_INFO = ClassDesc.of(IModFileInfo.class.getName());
    private static final ClassDesc MAP = ClassDesc.of(Map.class.getName());
    private static final ClassDesc ANSWERS = ClassDesc.of(Answers.class.getName());

    @BeforeEach
    void clear() {
        Answers.FILES.clear();
    }

    @Test
    void fmlAnswersFirstAndPolyModLoaderOnlyWhenFmlHasNone() throws Exception {
        IModFileInfo forgeFile = file();
        IModFileInfo fabricFile = file();
        IModFileInfo shadowed = file();
        Answers.FILES.put("sodium", fabricFile);
        Answers.FILES.put("geckolib", shadowed);

        ForgeModListPatch.Result result = ForgeModListPatch.apply(modList(ForgeModListPatchTest::fmlLookups), ANSWERS);
        assertEquals(List.of("getModFileById", "isLoaded"), result.changed());
        assertEquals(List.of(), result.unchanged());
        Class<?> modList = define(result.bytes());
        modList.getField("FILE_BY_ID").set(null, Map.of("geckolib", forgeFile));
        modList.getField("indexedMods").set(null, Map.of("geckolib", "container"));

        assertSame(forgeFile, call(modList, "getModFileById", "geckolib"), "a Forge mod keeps its ID");
        assertSame(fabricFile, call(modList, "getModFileById", "sodium"));
        assertNull(call(modList, "getModFileById", "lithium"));
        assertEquals(true, call(modList, "isLoaded", "geckolib"));
        assertEquals(true, call(modList, "isLoaded", "sodium"));
        assertEquals(false, call(modList, "isLoaded", "lithium"));
    }

    @Test
    void aMethodWhoseCodeIsNotTheCheckedOneIsLeftAsItIs() throws Exception {
        Answers.FILES.put("sodium", file());
        // isLoaded reads another map than FML 65.1.5's: not the code Poly Mod Loader checked.
        byte[] original = modList(code -> {
            byIdBody(code);
            code.withMethodBody(
                    "isLoaded",
                    MethodTypeDesc.of(CD_boolean, CD_String),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    body -> body.getstatic(MOD_LIST, "FILE_BY_ID", MAP)
                            .aload(0)
                            .invokeinterface(MAP, "containsKey", MethodTypeDesc.of(CD_boolean, CD_Object))
                            .ireturn());
        });

        ForgeModListPatch.Result result = ForgeModListPatch.apply(original, ANSWERS);

        assertEquals(List.of("getModFileById"), result.changed());
        assertEquals(List.of("isLoaded"), result.unchanged());
        Class<?> modList = define(result.bytes());
        modList.getField("FILE_BY_ID").set(null, Map.of());
        modList.getField("indexedMods").set(null, Map.of());
        assertEquals(false, call(modList, "isLoaded", "sodium"));
        assertNotNull(call(modList, "getModFileById", "sodium"));
    }

    @Test
    void nothingChangesWhenNoMethodIsTheCheckedOneOrTheClassIsAnother() {
        byte[] noLookups = modList(code -> {});
        ForgeModListPatch.Result none = ForgeModListPatch.apply(noLookups, ANSWERS);
        assertArrayEquals(noLookups, none.bytes());
        assertEquals(List.of("getModFileById", "isLoaded"), none.unchanged());

        byte[] other = ClassFile.of().build(ClassDesc.of("net.minecraftforge.fml.Other"), builder -> {});
        ForgeModListPatch.Result untouched = ForgeModListPatch.apply(other, ANSWERS);
        assertSame(other, untouched.bytes());
        assertEquals(List.of(), untouched.changed());
        assertEquals(List.of(), untouched.unchanged());
    }

    /** fmlcore is an FML library in the untransformed PLUGIN layer: ModList is patched as its module reader serves it. */
    @Test
    void theModuleWithModListServesItPatchedAndOtherModulesAreLeftAlone(@TempDir Path directory) throws Exception {
        Path fmlcore = directory.resolve("fmlcore.jar");
        Path other = directory.resolve("other.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(fmlcore))) {
            jar.putNextEntry(new JarEntry("net/minecraftforge/fml/ModList.class"));
            jar.write(modList(ForgeModListPatchTest::fmlLookups));
            jar.closeEntry();
        }
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(other))) {
            jar.putNextEntry(new JarEntry("net/minecraftforge/other/Thing.class"));
            jar.write(ClassFile.of().build(ClassDesc.of("net.minecraftforge.other.Thing"), builder -> {}));
            jar.closeEntry();
        }
        List<ForgeModListPatch.Result> reports = new ArrayList<>();
        ModuleReference otherModule =
                ModuleFinder.of(other).findAll().iterator().next();
        assertSame(otherModule, ForgeModListPatch.patching(otherModule, ANSWERS, reports::add));

        ModuleReference patched = ForgeModListPatch.patching(
                ModuleFinder.of(fmlcore).findAll().iterator().next(), ANSWERS, reports::add);
        byte[] served;
        try (ModuleReader reader = patched.open();
                InputStream input =
                        reader.open("net/minecraftforge/fml/ModList.class").orElseThrow()) {
            served = input.readAllBytes();
        }

        assertEquals(1, reports.size());
        assertEquals(List.of("getModFileById", "isLoaded"), reports.getFirst().changed());
        Answers.FILES.put("sodium", file());
        Class<?> modList = define(served);
        modList.getField("indexedMods").set(null, Map.of());
        assertEquals(true, call(modList, "isLoaded", "sodium"));
    }

    /**
     * NeoForge's FML 11 (ADR-0026): the real {@code ModList} of {@code loader} 11.0.16 has the checked instance-method
     * bodies; both lookups now ask {@code neoforge-host.jar}'s {@code ForeignModList}, and the class still verifies.
     */
    @Test
    void neoForgesRealModListIsPatchedAndVerifies() throws Exception {
        byte[] original;
        try (InputStream input = ForgeModListPatchTest.class
                .getClassLoader()
                .getResourceAsStream(ForgeModListPatch.Shape.NEOFORGE.classFile())) {
            assertNotNull(input, "FML's loader jar is on the test class path");
            original = input.readAllBytes();
        }

        ForgeModListPatch.Result result = ForgeModListPatch.apply(
                original, ForgeModListPatch.Shape.NEOFORGE, ForgeModListPatch.Shape.NEOFORGE.foreignModList());

        assertEquals(List.of("getModFileById", "isLoaded"), result.changed());
        assertEquals(List.of(), result.unchanged());
        assertEquals(List.of(), ClassFile.of().verify(result.bytes()));
    }

    /** The NeoForge shape reads instance fields: a static {@code ModList} like Forge's is left unchanged with it. */
    @Test
    void theNeoForgeShapeLeavesStaticLookupsUnchanged() {
        byte[] forgeLike = ClassFile.of().build(ClassDesc.of(ForgeModListPatch.Shape.NEOFORGE.className()), builder -> {
            builder.withField("indexedMods", MAP, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
            builder.withMethodBody(
                    "isLoaded",
                    MethodTypeDesc.of(CD_boolean, CD_String),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    code -> code.getstatic(
                                    ClassDesc.of(ForgeModListPatch.Shape.NEOFORGE.className()), "indexedMods", MAP)
                            .aload(0)
                            .invokeinterface(MAP, "containsKey", MethodTypeDesc.of(CD_boolean, CD_Object))
                            .ireturn());
        });

        ForgeModListPatch.Result result = ForgeModListPatch.apply(forgeLike, ForgeModListPatch.Shape.NEOFORGE, ANSWERS);

        assertEquals(List.of(), result.changed());
        assertEquals(List.of("getModFileById", "isLoaded"), result.unchanged());
        assertArrayEquals(forgeLike, result.bytes());
    }

    /** {@code ModList} with public fields for the test and the given methods. */
    private static byte[] modList(Consumer<java.lang.classfile.ClassBuilder> methods) {
        return ClassFile.of().build(MOD_LIST, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            builder.withField("FILE_BY_ID", MAP, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
            builder.withField("indexedMods", MAP, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
            methods.accept(builder);
        });
    }

    /** As in fmlcore: {@code return (IModFileInfo) FILE_BY_ID.get(id);}, {@code return indexedMods.containsKey(id);}. */
    private static void fmlLookups(java.lang.classfile.ClassBuilder builder) {
        byIdBody(builder);
        builder.withMethodBody(
                "isLoaded",
                MethodTypeDesc.of(CD_boolean, CD_String),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code.getstatic(MOD_LIST, "indexedMods", MAP)
                        .aload(0)
                        .invokeinterface(MAP, "containsKey", MethodTypeDesc.of(CD_boolean, CD_Object))
                        .ireturn());
    }

    private static void byIdBody(java.lang.classfile.ClassBuilder builder) {
        builder.withMethodBody(
                "getModFileById",
                MethodTypeDesc.of(MOD_FILE_INFO, CD_String),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code.getstatic(MOD_LIST, "FILE_BY_ID", MAP)
                        .aload(0)
                        .invokeinterface(MAP, "get", MethodTypeDesc.of(CD_Object, CD_Object))
                        .checkcast(MOD_FILE_INFO)
                        .areturn());
    }

    private static Class<?> define(byte[] bytes) {
        return new ClassLoader(ForgeModListPatchTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(ForgeModListPatch.CLASS_NAME, bytes, 0, bytes.length);
            }
        }.define();
    }

    private static Object call(Class<?> modList, String name, String id) throws Exception {
        Method method = modList.getMethod(name, String.class);
        try {
            return method.invoke(null, id);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private static IModFileInfo file() {
        return (IModFileInfo) Proxy.newProxyInstance(
                IModFileInfo.class.getClassLoader(), new Class<?>[] {IModFileInfo.class}, (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "a mod file";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}

package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

/**
 * Against FML's own {@code FMLLoader} and {@code MixinFacade} from {@code loader} 11.0.16 (NeoForge 26.2.0.93, on the
 * test class path) and 12.0.12 (NeoForge 26.3.0.98, read from the jar the build names in {@code poly.fml12Loader}).
 */
class NeoForgeSharedMixinPatchTest {

    private static final ClassHierarchyResolver HIERARCHY = ClassHierarchyResolver.ofResourceParsing(
                    NeoForgeSharedMixinPatchTest.class.getClassLoader())
            .orElse(ClassHierarchyResolver.defaultResolver());

    private static final ClassDesc FACADE = ClassDesc.of("net.neoforged.fml.loading.mixin.MixinFacade");

    private static Map<String, byte[]> fml12;

    @Test
    void fmlHasTheCheckedShapes() throws IOException {
        assertEquals(
                List.of(),
                NeoForgeSharedMixinPatch.missing(
                        classFile(NeoForgeLoaderPatch.CLASS_FILE),
                        classFile(NeoForgeSharedMixinPatch.FACADE_CLASS_FILE)));
    }

    @Test
    void theProcessorSetGetsThePlaceholderAndStillVerifies() throws IOException {
        NeoForgeSharedMixinPatch.Result result =
                NeoForgeSharedMixinPatch.applyToLoader(classFile(NeoForgeLoaderPatch.CLASS_FILE), HIERARCHY);

        assertTrue(result.missing().isEmpty(), () -> "missing: " + result.missing());
        assertEquals(2, result.changed().size(), () -> "changed: " + result.changed());
        // FML's plugin files pass Poly Mod Loader's filter first (another Fabric Loader is left out).
        assertEquals(
                1,
                hostCalls(
                        instructions(ClassFile.of().parse(result.bytes()), "loadPlugins"),
                        NeoForgeSharedMixinPatch.WITHOUT_FOREIGN_FABRIC_LOADER));
        List<Instruction> code = instructions(ClassFile.of().parse(result.bytes()), "createClassProcessorSet");
        assertEquals(1, hostCalls(code, NeoForgeSharedMixinPatch.MIXIN_CLASS_PROCESSOR));
        assertEquals(0, hostCalls(code, NeoForgeSharedMixinPatch.MIXIN_GENERATING_CLASS_PROCESSOR));
        assertEquals(0, facadeCalls(code));
        // Both patches of FMLLoader together, as the host reads the class.
        NeoForgeLoaderPatch.Result both = NeoForgeLoaderPatch.apply(result.bytes(), HIERARCHY);
        assertTrue(both.complete(), () -> "missing: " + both.missing());
        List<VerifyError> errors = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(HIERARCHY))
                .verify(both.bytes());
        assertTrue(errors.isEmpty(), () -> errors.toString());
    }

    @Test
    void theFacadeStartsNoMixinAndHandsTheModListToTheHost() throws IOException {
        NeoForgeSharedMixinPatch.Result result =
                NeoForgeSharedMixinPatch.applyToFacade(classFile(NeoForgeSharedMixinPatch.FACADE_CLASS_FILE));

        assertTrue(result.missing().isEmpty(), () -> "missing: " + result.missing());
        ClassModel patched = ClassFile.of().parse(result.bytes());
        List<Instruction> constructor = instructions(patched, "<init>");
        assertTrue(constructor.stream()
                .noneMatch(i -> i instanceof InvokeInstruction call
                        && call.owner().asInternalName().startsWith("org/spongepowered/")));
        List<Instruction> finish = instructions(patched, "finishInitialization");
        assertEquals(1, hostCalls(finish, NeoForgeSharedMixinPatch.FINISH));
        assertTrue(finish.stream()
                .noneMatch(
                        i -> i instanceof InvokeInstruction call && call.name().equalsString("addMixins")));
        List<VerifyError> errors = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(HIERARCHY))
                .verify(result.bytes());
        assertTrue(errors.isEmpty(), () -> errors.toString());
    }

    @Test
    void leavesAnotherFacadeAlone() throws IOException {
        byte[] original = classFile(NeoForgeSharedMixinPatch.FACADE_CLASS_FILE);
        ClassModel model = ClassFile.of().parse(original);
        // A MixinFacade whose finishInitialization does not add the mods' configurations itself.
        byte[] other = ClassFile.of().transformClass(model, (builder, element) -> {
            if (element instanceof MethodModel method && method.methodName().equalsString("finishInitialization")) {
                builder.withMethodBody(
                        method.methodName(), method.methodType(), method.flags().flagsMask(), code -> code.return_());
            } else {
                builder.with(element);
            }
        });

        NeoForgeSharedMixinPatch.Result result = NeoForgeSharedMixinPatch.applyToFacade(other);

        assertFalse(result.missing().isEmpty());
        assertTrue(result.changed().isEmpty());
        assertArrayEquals(other, result.bytes());
        assertFalse(NeoForgeSharedMixinPatch.missing(classFile(NeoForgeLoaderPatch.CLASS_FILE), other)
                .isEmpty());
    }

    /**
     * A field the patch does not know is a processor or service the replacement constructor would leave {@code null}
     * for {@code FMLLoader} to use, as FML 12's {@code generatingClassProcessor} was.
     */
    @Test
    void refusesAFacadeWithAFieldItDoesNotKnow() throws IOException {
        byte[] original = classFile(NeoForgeSharedMixinPatch.FACADE_CLASS_FILE);
        byte[] other = ClassFile.of()
                .transformClass(
                        ClassFile.of().parse(original),
                        ClassTransform.endHandler(builder -> builder.withField(
                                "anotherClassProcessor",
                                ClassDesc.of("net.neoforged.neoforgespi.transformation.ClassProcessor"),
                                ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL)));

        NeoForgeSharedMixinPatch.Result result = NeoForgeSharedMixinPatch.applyToFacade(other);

        assertFalse(result.missing().isEmpty());
        assertTrue(result.changed().isEmpty());
        assertArrayEquals(other, result.bytes());
        assertFalse(NeoForgeSharedMixinPatch.missing(classFile(NeoForgeLoaderPatch.CLASS_FILE), other)
                .isEmpty());
    }

    @Test
    void fml12HasTheCheckedShapes() {
        assertEquals(
                List.of(),
                NeoForgeSharedMixinPatch.missing(
                        fml12(NeoForgeLoaderPatch.CLASS_FILE), fml12(NeoForgeSharedMixinPatch.FACADE_CLASS_FILE)));
    }

    /** FML 12 adds Mixin's generating processor to the set too; FMLLoader must not add a null one (PLEXUS-COMPAT-208). */
    @Test
    void fml12ProcessorSetGetsPlaceholdersForBothMixinProcessorsAndStillVerifies() {
        ClassHierarchyResolver hierarchy = fml12Hierarchy();
        NeoForgeSharedMixinPatch.Result result =
                NeoForgeSharedMixinPatch.applyToLoader(fml12(NeoForgeLoaderPatch.CLASS_FILE), hierarchy);

        assertTrue(result.missing().isEmpty(), () -> "missing: " + result.missing());
        assertEquals(3, result.changed().size(), () -> "changed: " + result.changed());
        assertEquals(
                1,
                hostCalls(
                        instructions(ClassFile.of().parse(result.bytes()), "loadPlugins"),
                        NeoForgeSharedMixinPatch.WITHOUT_FOREIGN_FABRIC_LOADER));
        List<Instruction> code = instructions(ClassFile.of().parse(result.bytes()), "createClassProcessorSet");
        assertEquals(1, hostCalls(code, NeoForgeSharedMixinPatch.MIXIN_CLASS_PROCESSOR));
        assertEquals(1, hostCalls(code, NeoForgeSharedMixinPatch.MIXIN_GENERATING_CLASS_PROCESSOR));
        assertEquals(0, facadeCalls(code));
        NeoForgeLoaderPatch.Result both = NeoForgeLoaderPatch.apply(result.bytes(), hierarchy);
        assertTrue(both.complete(), () -> "missing: " + both.missing());
        List<VerifyError> errors = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(hierarchy))
                .verify(both.bytes());
        assertTrue(errors.isEmpty(), () -> errors.toString());
    }

    @Test
    void fml12FacadeCreatesNeitherMixinProcessor() {
        NeoForgeSharedMixinPatch.Result result =
                NeoForgeSharedMixinPatch.applyToFacade(fml12(NeoForgeSharedMixinPatch.FACADE_CLASS_FILE));

        assertTrue(result.missing().isEmpty(), () -> "missing: " + result.missing());
        ClassModel patched = ClassFile.of().parse(result.bytes());
        List<Instruction> constructor = instructions(patched, "<init>");
        assertTrue(constructor.stream()
                .noneMatch(i -> i instanceof InvokeInstruction call
                        && call.owner().asInternalName().startsWith("org/spongepowered/")));
        assertTrue(constructor.stream().noneMatch(i -> i instanceof NewObjectInstruction));
        // Every instance field the constructor sets, the generating processor too.
        List<String> fields = patched.fields().stream()
                .filter(f -> !f.flags().has(AccessFlag.STATIC))
                .map(f -> f.fieldName().stringValue())
                .sorted()
                .toList();
        assertEquals(List.of("classProcessor", "generatingClassProcessor", "service"), fields);
        List<String> set = constructor.stream()
                .filter(i -> i instanceof FieldInstruction f && f.opcode() == Opcode.PUTFIELD)
                .map(i -> ((FieldInstruction) i).name().stringValue())
                .sorted()
                .toList();
        assertEquals(fields, set);
        List<Instruction> finish = instructions(patched, "finishInitialization");
        assertEquals(1, hostCalls(finish, NeoForgeSharedMixinPatch.FINISH));
        List<VerifyError> errors = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(fml12Hierarchy()))
                .verify(result.bytes());
        assertTrue(errors.isEmpty(), () -> errors.toString());
    }

    /** One FML's processor set with the other's facade: the set would get a processor the facade does not have. */
    @Test
    void refusesAProcessorSetAndFacadeOfDifferentFmls() throws IOException {
        assertFalse(NeoForgeSharedMixinPatch.missing(
                        classFile(NeoForgeLoaderPatch.CLASS_FILE), fml12(NeoForgeSharedMixinPatch.FACADE_CLASS_FILE))
                .isEmpty());
        assertFalse(NeoForgeSharedMixinPatch.missing(
                        fml12(NeoForgeLoaderPatch.CLASS_FILE), classFile(NeoForgeSharedMixinPatch.FACADE_CLASS_FILE))
                .isEmpty());
    }

    /** A processor set that takes something else from the facade, which the patch would leave to a facade of nulls. */
    @Test
    void refusesAProcessorSetWithAnotherFacadeCall() {
        ClassHierarchyResolver hierarchy = fml12Hierarchy();
        byte[] original = fml12(NeoForgeLoaderPatch.CLASS_FILE);
        byte[] other = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(hierarchy))
                .transformClass(ClassFile.of().parse(original), (builder, element) -> {
                    if (element instanceof MethodModel method
                            && method.methodName().equalsString("createClassProcessorSet")) {
                        builder.transformMethod(method, MethodTransform.transformingCode((code, e) -> {
                            if (e instanceof InvokeInstruction call
                                    && call.name().equalsString("getGeneratingClassProcessor")) {
                                code.invokevirtual(FACADE, "getOtherClassProcessor", call.typeSymbol());
                            } else {
                                code.with(e);
                            }
                        }));
                    } else {
                        builder.with(element);
                    }
                });

        NeoForgeSharedMixinPatch.Result result = NeoForgeSharedMixinPatch.applyToLoader(other, hierarchy);

        assertFalse(result.missing().isEmpty());
        assertTrue(result.changed().isEmpty());
        assertArrayEquals(other, result.bytes());
    }

    private static long hostCalls(List<Instruction> code, String name) {
        return code.stream()
                .filter(i -> i instanceof InvokeInstruction call
                        && call.owner().asSymbol().equals(NeoForgeLoaderPatch.HOST)
                        && call.name().equalsString(name))
                .count();
    }

    private static long facadeCalls(List<Instruction> code) {
        return code.stream()
                .filter(i -> i instanceof InvokeInstruction call
                        && call.owner().asSymbol().equals(FACADE))
                .count();
    }

    private static List<Instruction> instructions(ClassModel model, String name) {
        return model.methods().stream()
                .filter(m -> m.methodName().equalsString(name))
                .findFirst()
                .orElseThrow()
                .code()
                .orElseThrow()
                .elementStream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList();
    }

    private static byte[] classFile(String name) throws IOException {
        try (InputStream in =
                NeoForgeSharedMixinPatchTest.class.getClassLoader().getResourceAsStream(name)) {
            return in.readAllBytes();
        }
    }

    /** A class file of FML 12's {@code loader} jar. */
    private static byte[] fml12(String name) {
        byte[] bytes = fml12Classes().get(name);
        assertNotNull(bytes, () -> "FML 12 has no " + name);
        return bytes;
    }

    /** FML 12's classes first (its own FMLMixinGeneratingClassProcessor), then the test class path's. */
    private static ClassHierarchyResolver fml12Hierarchy() {
        Map<String, byte[]> classes = fml12Classes();
        return ClassHierarchyResolver.ofResourceParsing(desc -> {
                    byte[] bytes = classes.get(desc.descriptorString()
                                    .substring(1, desc.descriptorString().length() - 1)
                            + ".class");
                    return bytes == null ? null : new ByteArrayInputStream(bytes);
                })
                .orElse(HIERARCHY);
    }

    private static synchronized Map<String, byte[]> fml12Classes() {
        if (fml12 == null) {
            String jar = System.getProperty("poly.fml12Loader");
            assertNotNull(jar, "the build names FML 12's loader jar in poly.fml12Loader");
            Map<String, byte[]> classes = new HashMap<>();
            try (JarFile file = new JarFile(Path.of(jar).toFile())) {
                for (var entry : file.stream().toList()) {
                    if (entry.getName().endsWith(".class")) {
                        try (InputStream in = file.getInputStream(entry)) {
                            classes.put(entry.getName(), in.readAllBytes());
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assertTrue(classes.containsKey("net/neoforged/fml/loading/mixin/FMLMixinGeneratingClassProcessor.class"));
            fml12 = classes;
        }
        return fml12;
    }
}

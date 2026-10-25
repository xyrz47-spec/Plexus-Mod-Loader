package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Against FML's own {@code FMLLoader} and {@code MixinFacade} from {@code loader} 11.0.16 (NeoForge 26.2.0.93). */
class NeoForgeSharedMixinPatchTest {

    private static final ClassHierarchyResolver HIERARCHY = ClassHierarchyResolver.ofResourceParsing(
                    NeoForgeSharedMixinPatchTest.class.getClassLoader())
            .orElse(ClassHierarchyResolver.defaultResolver());

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
        assertEquals(1, result.changed().size());
        List<Instruction> code = instructions(ClassFile.of().parse(result.bytes()), "createClassProcessorSet");
        assertEquals(1, hostCalls(code, NeoForgeSharedMixinPatch.MIXIN_CLASS_PROCESSOR));
        assertTrue(code.stream()
                .noneMatch(
                        i -> i instanceof InvokeInstruction call && call.name().equalsString("getClassProcessor")));
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

    private static long hostCalls(List<Instruction> code, String name) {
        return code.stream()
                .filter(i -> i instanceof InvokeInstruction call
                        && call.owner().asSymbol().equals(NeoForgeLoaderPatch.HOST)
                        && call.name().equalsString(name))
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
}

package dev.plexusmodloader.compat.forge;

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
import java.lang.classfile.instruction.NewObjectInstruction;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Against FML's own {@code FMLLoader} from {@code loader} 11.0.16, the version NeoForge 26.2.0.93 runs. */
class NeoForgeLoaderPatchTest {

    private static final ClassHierarchyResolver HIERARCHY = ClassHierarchyResolver.ofResourceParsing(
                    NeoForgeLoaderPatchTest.class.getClassLoader())
            .orElse(ClassHierarchyResolver.defaultResolver());

    @Test
    void replacesBothCallsAndStillVerifies() throws IOException {
        byte[] original = fmlLoader();

        NeoForgeLoaderPatch.Result result = NeoForgeLoaderPatch.apply(original, HIERARCHY);

        assertTrue(result.complete(), () -> "missing: " + result.missing());
        ClassModel patched = ClassFile.of().parse(result.bytes());
        List<Instruction> create = instructions(patched, "create", "(Ljava/lang/instrument/Instrumentation;");
        assertEquals(1, hostCalls(create, NeoForgeLoaderPatch.BUILD));
        assertTrue(create.stream()
                .noneMatch(i -> i instanceof InvokeInstruction call
                        && call.name().equalsString(NeoForgeLoaderPatch.BUILD)
                        && call.owner().asInternalName().equals("net/neoforged/fml/loading/FMLLoader")));
        List<Instruction> discovery = instructions(patched, "runDiscovery", "()");
        assertEquals(1, hostCalls(discovery, NeoForgeLoaderPatch.MODS_LOCATOR));
        assertTrue(discovery.stream()
                .noneMatch(i -> i instanceof NewObjectInstruction n
                        && n.className().asInternalName().endsWith("/ModsFolderLocator")));
        List<VerifyError> errors = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(HIERARCHY))
                .verify(result.bytes());
        assertTrue(errors.isEmpty(), () -> errors.toString());
    }

    @Test
    void leavesAnotherShapeAlone() throws IOException {
        byte[] original = fmlLoader();
        ClassModel model = ClassFile.of().parse(original);
        // FMLLoader without its runDiscovery: the patch must change nothing, not just the create half.
        byte[] withoutDiscovery = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(HIERARCHY))
                .transformClass(model, (builder, element) -> {
                    if (!(element instanceof MethodModel method
                            && method.methodName().equalsString("runDiscovery"))) {
                        builder.with(element);
                    }
                });

        NeoForgeLoaderPatch.Result result = NeoForgeLoaderPatch.apply(withoutDiscovery, HIERARCHY);

        assertFalse(result.complete());
        assertTrue(result.changed().isEmpty());
        assertEquals(1, result.missing().size(), result.missing().toString());
        assertArrayEquals(withoutDiscovery, result.bytes());
    }

    @Test
    void ignoresOtherClasses() throws IOException {
        byte[] other;
        try (InputStream in = NeoForgeLoaderPatchTest.class.getResourceAsStream("NeoForgeLoaderPatchTest.class")) {
            other = in.readAllBytes();
        }

        NeoForgeLoaderPatch.Result result = NeoForgeLoaderPatch.apply(other, HIERARCHY);

        assertArrayEquals(other, result.bytes());
        assertTrue(result.missing().isEmpty() && result.changed().isEmpty());
    }

    private static long hostCalls(List<Instruction> code, String name) {
        return code.stream()
                .filter(i -> i instanceof InvokeInstruction call
                        && call.owner().asSymbol().equals(NeoForgeLoaderPatch.HOST)
                        && call.name().equalsString(name))
                .count();
    }

    private static List<Instruction> instructions(ClassModel model, String name, String descriptorStart) {
        MethodModel method = model.methods().stream()
                .filter(m -> m.methodName().equalsString(name)
                        && m.methodType().stringValue().startsWith(descriptorStart))
                .findFirst()
                .orElseThrow();
        return method.code()
                .orElseThrow()
                .elementStream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList();
    }

    private static byte[] fmlLoader() throws IOException {
        try (InputStream in =
                NeoForgeLoaderPatchTest.class.getClassLoader().getResourceAsStream(NeoForgeLoaderPatch.CLASS_FILE)) {
            return in.readAllBytes();
        }
    }
}

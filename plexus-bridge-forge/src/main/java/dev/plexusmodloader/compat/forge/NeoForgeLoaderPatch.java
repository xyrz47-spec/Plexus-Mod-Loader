package dev.plexusmodloader.compat.forge;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Poly Mod Loader's two changes to FML 11's {@code FMLLoader} (ADR-0026), in its own startup and nowhere else:
 *
 * <ul>
 *   <li>In {@code create(Instrumentation, StartupArgs)}, the one call {@code this.buildTransformingLoader(processors,
 *       auditLog, gameContent)} becomes {@code PolyFml.buildTransformingLoader(this, processors, auditLog,
 *       gameContent)}: the game class loader defines the game layer FML computed, and FML gets a class loader that
 *       hands everything to it.
 *   <li>In {@code runDiscovery()}, {@code new ModsFolderLocator()} becomes {@code PolyFml.modsLocator()}: FML loads the
 *       NeoForge mods Poly Mod Loader found and left in, not every jar of the mods folder.
 * </ul>
 *
 * <p>Checked with {@code javap} against {@code loader} 11.0.16 (NeoForge 26.2.0.93 and 26.1.2.122). Each change is
 * made only when its method has exactly one such call, with the descriptors above, and {@code buildTransformingLoader}
 * is FML's private method that defines the layer ({@code ModuleLayer.defineModules}), creates the {@code
 * TransformingClassLoader} and makes it the context class loader. Otherwise nothing changes and the result names what
 * was not found; the host stops the launch then ({@code PLEXUS-COMPAT-215}), because FML's own class loader would own
 * the game.
 */
final class NeoForgeLoaderPatch {

    /** FML's loader, in the module {@code fml_loader}. */
    static final String CLASS_FILE = "net/neoforged/fml/loading/FMLLoader.class";

    static final String PACKAGE = "net.neoforged.fml.loading";

    /** Poly Mod Loader's host in {@code neoforge-host.jar}, a module of the same layer. */
    static final ClassDesc HOST = ClassDesc.of("dev.plexusmodloader.compat.forge.fml.PolyFml");

    private static final ClassDesc FML_LOADER = ClassDesc.of("net.neoforged.fml.loading.FMLLoader");
    private static final ClassDesc PROCESSOR_SET =
            ClassDesc.of("net.neoforged.fml.classloading.transformation.ClassProcessorSet");
    private static final ClassDesc AUDIT_LOG =
            ClassDesc.of("net.neoforged.fml.classloading.transformation.ClassProcessorAuditLog");
    private static final ClassDesc TRANSFORMING_LOADER =
            ClassDesc.of("net.neoforged.fml.classloading.transformation.TransformingClassLoader");
    private static final ClassDesc MODS_FOLDER_LOCATOR =
            ClassDesc.of("net.neoforged.fml.loading.moddiscovery.locators.ModsFolderLocator");
    private static final ClassDesc CANDIDATE_LOCATOR =
            ClassDesc.of("net.neoforged.neoforgespi.locating.IModFileCandidateLocator");
    private static final ClassDesc LIST = ClassDesc.of("java.util.List");

    static final String BUILD = "buildTransformingLoader";
    static final String MODS_LOCATOR = "modsLocator";
    private static final MethodTypeDesc BUILD_TYPE =
            MethodTypeDesc.of(TRANSFORMING_LOADER, PROCESSOR_SET, AUDIT_LOG, LIST);
    private static final MethodTypeDesc HOST_BUILD_TYPE =
            MethodTypeDesc.of(TRANSFORMING_LOADER, FML_LOADER, PROCESSOR_SET, AUDIT_LOG, LIST);
    private static final MethodTypeDesc HOST_LOCATOR_TYPE = MethodTypeDesc.of(CANDIDATE_LOCATOR);
    private static final MethodTypeDesc CREATE_TYPE = MethodTypeDesc.of(
            FML_LOADER,
            ClassDesc.of("java.lang.instrument.Instrumentation"),
            ClassDesc.of("net.neoforged.fml.startup.StartupArgs"));
    private static final MethodTypeDesc RUN_DISCOVERY_TYPE =
            MethodTypeDesc.of(ClassDesc.of("net.neoforged.fml.loading.FMLLoader$DiscoveryResult"));
    private static final MethodTypeDesc NO_ARGUMENTS = MethodTypeDesc.of(ClassDesc.ofDescriptor("V"));

    /**
     * The result of {@link #apply}.
     *
     * @param bytes the class file to define ({@code original} when nothing changed)
     * @param changed the calls replaced, as {@code method: call}
     * @param missing the checked shapes that were not found
     */
    record Result(byte[] bytes, List<String> changed, List<String> missing) {
        Result {
            changed = List.copyOf(changed);
            missing = List.copyOf(missing);
        }

        boolean complete() {
            return missing.isEmpty() && changed.size() == 2;
        }
    }

    private NeoForgeLoaderPatch() {}

    /**
     * {@code reference} as it is, or, when it is the module with {@code FMLLoader} ({@code fml_loader}), reading that
     * class through {@link #apply}.
     *
     * @param hierarchy answers the class hierarchy of FML's and the JDK's types, for the patched methods' stack maps
     * @param report gets the result, for the log and the launch's check
     */
    static ModuleReference patching(
            ModuleReference reference, ClassHierarchyResolver hierarchy, Consumer<Result> report) {
        if (!reference.descriptor().packages().contains(PACKAGE)) {
            return reference;
        }
        return PatchedClassReference.of(reference, CLASS_FILE, bytes -> {
            Result result = apply(bytes, hierarchy);
            report.accept(result);
            return result.bytes();
        });
    }

    /**
     * {@code FMLLoader}'s class file with both calls replaced, or unchanged when either shape is not the checked one
     * (both or neither, so FML never runs half on its own class loader).
     *
     * @param original the class file of {@code FMLLoader}; any other class is returned unchanged
     */
    static Result apply(byte[] original, ClassHierarchyResolver hierarchy) {
        ClassFile classFile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(hierarchy));
        ClassModel model = classFile.parse(original);
        if (!model.thisClass().asSymbol().equals(FML_LOADER)) {
            return new Result(original, List.of(), List.of());
        }
        List<String> missing = new ArrayList<>();
        if (!buildsTheLayer(model)) {
            missing.add("private " + BUILD + BUILD_TYPE.descriptorString()
                    + " that defines the layer and its TransformingClassLoader");
        }
        if (buildCalls(code(model, "create", CREATE_TYPE)) != 1) {
            missing.add("one call of " + BUILD + " in create" + CREATE_TYPE.descriptorString());
        }
        if (modsFolderLocators(code(model, "runDiscovery", RUN_DISCOVERY_TYPE)) != 1) {
            missing.add("one new ModsFolderLocator() in runDiscovery" + RUN_DISCOVERY_TYPE.descriptorString());
        }
        if (!missing.isEmpty()) {
            return new Result(original, List.of(), missing);
        }
        ClassTransform transform = (builder, element) -> {
            if (element instanceof MethodModel method && is(method, "create", CREATE_TYPE)) {
                builder.transformMethod(method, MethodTransform.transformingCode(redirectBuild()));
            } else if (element instanceof MethodModel method && is(method, "runDiscovery", RUN_DISCOVERY_TYPE)) {
                builder.transformMethod(method, MethodTransform.transformingCode(replaceModsFolderLocator()));
            } else {
                builder.with(element);
            }
        };
        return new Result(
                classFile.transformClass(model, transform),
                List.of(
                        "create: " + BUILD + " -> PolyFml." + BUILD,
                        "runDiscovery: new ModsFolderLocator() -> PolyFml." + MODS_LOCATOR + "()"),
                List.of());
    }

    private static CodeTransform redirectBuild() {
        return (code, element) -> {
            if (element instanceof InvokeInstruction call && callsBuild(call)) {
                code.invokestatic(HOST, BUILD, HOST_BUILD_TYPE);
            } else {
                code.with(element);
            }
        };
    }

    /** Drops {@code new ModsFolderLocator; dup} and puts the host's locator where the constructor call was. */
    private static CodeTransform replaceModsFolderLocator() {
        return new CodeTransform() {
            private boolean droppedNew;
            private boolean droppedDup;

            @Override
            public void accept(CodeBuilder code, CodeElement element) {
                if (element instanceof NewObjectInstruction create
                        && create.className().asSymbol().equals(MODS_FOLDER_LOCATOR)) {
                    droppedNew = true;
                    return;
                }
                if (droppedNew
                        && !droppedDup
                        && element instanceof StackInstruction dup
                        && dup.opcode() == Opcode.DUP) {
                    droppedDup = true;
                    return;
                }
                if (droppedDup && element instanceof InvokeInstruction call && constructsModsFolderLocator(call)) {
                    code.invokestatic(HOST, MODS_LOCATOR, HOST_LOCATOR_TYPE);
                    droppedNew = false;
                    droppedDup = false;
                    return;
                }
                code.with(element);
            }
        };
    }

    /** FML's own {@code buildTransformingLoader}: private, with the layer, the loader and the context class loader. */
    private static boolean buildsTheLayer(ClassModel model) {
        List<MethodModel> found = model.methods().stream()
                .filter(method ->
                        is(method, BUILD, BUILD_TYPE) && (method.flags().flagsMask() & ClassFile.ACC_PRIVATE) != 0)
                .toList();
        if (found.size() != 1) {
            return false;
        }
        List<Instruction> code =
                found.getFirst().code().map(NeoForgeLoaderPatch::instructions).orElse(List.of());
        return code.stream().anyMatch(i -> invokes(i, "java.lang.ModuleLayer", "defineModules"))
                && code.stream()
                        .anyMatch(i -> i instanceof NewObjectInstruction n
                                && n.className().asSymbol().equals(TRANSFORMING_LOADER))
                && code.stream().anyMatch(i -> invokes(i, "java.lang.Thread", "setContextClassLoader"));
    }

    private static boolean invokes(Instruction instruction, String owner, String name) {
        return instruction instanceof InvokeInstruction call
                && call.owner().asSymbol().equals(ClassDesc.of(owner))
                && call.name().equalsString(name);
    }

    private static boolean callsBuild(Instruction instruction) {
        return instruction instanceof InvokeInstruction call
                && call.opcode() == Opcode.INVOKEVIRTUAL
                && call.owner().asSymbol().equals(FML_LOADER)
                && call.name().equalsString(BUILD)
                && call.typeSymbol().equals(BUILD_TYPE);
    }

    private static boolean constructsModsFolderLocator(Instruction instruction) {
        return instruction instanceof InvokeInstruction call
                && call.opcode() == Opcode.INVOKESPECIAL
                && call.owner().asSymbol().equals(MODS_FOLDER_LOCATOR)
                && call.name().equalsString("<init>")
                && call.typeSymbol().equals(NO_ARGUMENTS);
    }

    /** The instructions of the one method {@code name type}, or {@code null} when there is not exactly one. */
    private static List<Instruction> code(ClassModel model, String name, MethodTypeDesc type) {
        List<MethodModel> found = model.methods().stream()
                .filter(method -> is(method, name, type))
                .toList();
        return found.size() == 1
                ? found.getFirst().code().map(NeoForgeLoaderPatch::instructions).orElse(null)
                : null;
    }

    /** How many calls of FML's {@code buildTransformingLoader} {@code code} has; -1 without code. */
    private static int buildCalls(List<Instruction> code) {
        return code == null
                ? -1
                : (int) code.stream().filter(NeoForgeLoaderPatch::callsBuild).count();
    }

    /**
     * How many {@code new ModsFolderLocator; dup; invokespecial <init>()V} sequences {@code code} has; -1 without code
     * or when a {@code ModsFolderLocator} is created any other way.
     */
    private static int modsFolderLocators(List<Instruction> code) {
        if (code == null) {
            return -1;
        }
        int news = 0;
        int sequences = 0;
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i) instanceof NewObjectInstruction n
                    && n.className().asSymbol().equals(MODS_FOLDER_LOCATOR)) {
                news++;
                if (i + 2 < code.size()
                        && code.get(i + 1) instanceof StackInstruction dup
                        && dup.opcode() == Opcode.DUP
                        && constructsModsFolderLocator(code.get(i + 2))) {
                    sequences++;
                }
            }
        }
        return news == sequences ? sequences : -1;
    }

    private static boolean is(MethodModel method, String name, MethodTypeDesc type) {
        return method.methodName().equalsString(name)
                && method.methodTypeSymbol().equals(type);
    }

    private static List<Instruction> instructions(CodeModel code) {
        return code.elementStream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList();
    }
}

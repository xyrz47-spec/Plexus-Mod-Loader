package dev.polymodloader.compat.forge;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Poly Mod Loader's change to FML 11's own Mixin start for a launch where Fabric or Quilt mods run next to NeoForge
 * mods (ADR-0026 30): one class loader holds one Mixin, and Fabric's (Poly Mod Loader's service, ADR-0020) serves
 * every loader, as it serves Forge mods (ADR-0021 G8). FML has no setting that leaves its Mixin out: {@code
 * FMLLoader.create} always creates a {@code MixinFacade}, whose constructor names FML's Mixin service and starts Mixin,
 * and whose {@code finishInitialization} adds the mods' configurations and moves Mixin to its default phase.
 *
 * <ul>
 *   <li>{@code MixinFacade}: the constructor only sets its two fields to {@code null} (Mixin is already running);
 *       {@code finishInitialization(LoadingModList, TransformingClassLoader)} hands the mod list to {@code
 *       PolyFml.finishSharedMixin}, which finds the configurations as FML's {@code addMixins} does and reports them to
 *       the shared Mixin; {@code close()} does nothing (it only detaches FML's service).
 *   <li>{@code FMLLoader.createClassProcessorSet}: the one {@code mixinFacade.getClassProcessor()} becomes {@code
 *       PolyFml.mixinClassProcessor(mixinFacade)}, a processor with FML's Mixin name that changes nothing, so that the
 *       other processors keep their order; the shared Mixin applies in Poly Mod Loader's bridge stage.
 * </ul>
 *
 * <p>Checked with {@code javap} against {@code loader} 11.0.5 (NeoForge 26.1.0.19-beta), 11.0.15 (26.1.2.122) and
 * 11.0.16 (26.2.0.93). Each change is made only when the method has the checked shape: the constructor sets {@code
 * mixin.service} and calls {@code MixinBootstrap.init()}, {@code finishInitialization} calls {@code addMixins}, and
 * {@code createClassProcessorSet} calls {@code getClassProcessor} once and passes the result straight to {@code
 * ArrayList.add}. Otherwise nothing changes; the host checks this before NeoForge starts ({@link #missing}) and leaves
 * NeoForge's mods out with {@code POLY-COMPAT-216} instead, so that two Mixins never start.
 */
final class NeoForgeSharedMixinPatch {

    static final String FACADE_CLASS_FILE = "net/neoforged/fml/loading/mixin/MixinFacade.class";

    static final String MIXIN_CLASS_PROCESSOR = "mixinClassProcessor";
    static final String FINISH = "finishSharedMixin";

    private static final ClassDesc FACADE = ClassDesc.of("net.neoforged.fml.loading.mixin.MixinFacade");
    private static final ClassDesc FML_PROCESSOR =
            ClassDesc.of("net.neoforged.fml.loading.mixin.FMLMixinClassProcessor");
    private static final ClassDesc FML_SERVICE = ClassDesc.of("net.neoforged.fml.loading.mixin.FMLMixinService");
    private static final ClassDesc CLASS_PROCESSOR =
            ClassDesc.of("net.neoforged.neoforgespi.transformation.ClassProcessor");
    private static final ClassDesc LOADING_MOD_LIST = ClassDesc.of("net.neoforged.fml.loading.LoadingModList");
    private static final ClassDesc TRANSFORMING_LOADER =
            ClassDesc.of("net.neoforged.fml.classloading.transformation.TransformingClassLoader");
    private static final ClassDesc FML_LOADER = ClassDesc.of("net.neoforged.fml.loading.FMLLoader");
    private static final ClassDesc MIXIN_BOOTSTRAP = ClassDesc.of("org.spongepowered.asm.launch.MixinBootstrap");

    private static final MethodTypeDesc NO_ARGUMENTS = MethodTypeDesc.of(ConstantDescs.CD_void);
    private static final MethodTypeDesc FINISH_TYPE =
            MethodTypeDesc.of(ConstantDescs.CD_void, LOADING_MOD_LIST, TRANSFORMING_LOADER);
    private static final MethodTypeDesc GET_PROCESSOR_TYPE = MethodTypeDesc.of(FML_PROCESSOR);
    private static final MethodTypeDesc HOST_PROCESSOR_TYPE = MethodTypeDesc.of(CLASS_PROCESSOR, FACADE);
    private static final MethodTypeDesc CREATE_PROCESSOR_SET_TYPE = MethodTypeDesc.of(
            ClassDesc.of("net.neoforged.fml.classloading.transformation.ClassProcessorSet"),
            ClassDesc.of("net.neoforged.fml.startup.StartupArgs"),
            ClassDesc.of("net.neoforged.fml.loading.FMLLoader$LaunchContextAdapter"),
            ClassDesc.of("net.neoforged.fml.loading.FMLLoader$DiscoveryResult"),
            FACADE);

    /**
     * The result of patching one class.
     *
     * @param bytes the class file to define ({@code original} when nothing changed)
     * @param changed what was replaced
     * @param missing the checked shapes that were not found
     */
    record Result(byte[] bytes, List<String> changed, List<String> missing) {
        Result {
            changed = List.copyOf(changed);
            missing = List.copyOf(missing);
        }
    }

    private NeoForgeSharedMixinPatch() {}

    /**
     * {@code reference} as it is, or, when it is FML's {@code loader} module, reading {@code FMLLoader} and {@code
     * MixinFacade} through this patch.
     */
    static ModuleReference patching(
            ModuleReference reference, ClassHierarchyResolver hierarchy, Consumer<Result> report) {
        if (!reference.descriptor().packages().contains(NeoForgeLoaderPatch.PACKAGE)) {
            return reference;
        }
        ModuleReference loader = PatchedClassReference.of(reference, NeoForgeLoaderPatch.CLASS_FILE, bytes -> {
            Result result = applyToLoader(bytes, hierarchy);
            report.accept(result);
            return result.bytes();
        });
        return PatchedClassReference.of(loader, FACADE_CLASS_FILE, bytes -> {
            Result result = applyToFacade(bytes);
            report.accept(result);
            return result.bytes();
        });
    }

    /**
     * What of the checked shapes {@code fmlLoader} and {@code mixinFacade} (the class files of FML's {@code loader}
     * jar) do not have; empty when both patches apply.
     */
    static List<String> missing(byte[] fmlLoader, byte[] mixinFacade) {
        List<String> missing = new ArrayList<>(loaderMissing(ClassFile.of().parse(fmlLoader)));
        missing.addAll(facadeMissing(ClassFile.of().parse(mixinFacade)));
        return List.copyOf(missing);
    }

    /** {@code FMLLoader} with its one {@code getClassProcessor} call replaced, or unchanged when the shape differs. */
    static Result applyToLoader(byte[] original, ClassHierarchyResolver hierarchy) {
        ClassFile classFile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(hierarchy));
        ClassModel model = classFile.parse(original);
        if (!model.thisClass().asSymbol().equals(FML_LOADER)) {
            return new Result(original, List.of(), List.of());
        }
        List<String> missing = loaderMissing(model);
        if (!missing.isEmpty()) {
            return new Result(original, List.of(), missing);
        }
        CodeTransform redirect = (code, element) -> {
            if (element instanceof InvokeInstruction call && getsProcessor(call)) {
                code.invokestatic(NeoForgeLoaderPatch.HOST, MIXIN_CLASS_PROCESSOR, HOST_PROCESSOR_TYPE);
            } else {
                code.with(element);
            }
        };
        ClassTransform transform = (builder, element) -> {
            if (element instanceof MethodModel method
                    && is(method, "createClassProcessorSet", CREATE_PROCESSOR_SET_TYPE)) {
                builder.transformMethod(method, MethodTransform.transformingCode(redirect));
            } else {
                builder.with(element);
            }
        };
        return new Result(
                classFile.transformClass(model, transform),
                List.of("createClassProcessorSet: MixinFacade.getClassProcessor() -> PolyFml." + MIXIN_CLASS_PROCESSOR),
                List.of());
    }

    /** {@code MixinFacade} without FML's own Mixin start, or unchanged when the shape differs. */
    static Result applyToFacade(byte[] original) {
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(original);
        if (!model.thisClass().asSymbol().equals(FACADE)) {
            return new Result(original, List.of(), List.of());
        }
        List<String> missing = facadeMissing(model);
        if (!missing.isEmpty()) {
            return new Result(original, List.of(), missing);
        }
        ClassTransform transform = (builder, element) -> {
            if (element instanceof MethodModel method && is(method, "<init>", NO_ARGUMENTS)) {
                builder.withMethodBody(
                        method.methodName(),
                        method.methodType(),
                        method.flags().flagsMask(),
                        code -> code.aload(0)
                                .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, NO_ARGUMENTS)
                                .aload(0)
                                .aconst_null()
                                .putfield(FACADE, "classProcessor", FML_PROCESSOR)
                                .aload(0)
                                .aconst_null()
                                .putfield(FACADE, "service", FML_SERVICE)
                                .return_());
            } else if (element instanceof MethodModel method && is(method, "finishInitialization", FINISH_TYPE)) {
                builder.withMethodBody(
                        method.methodName(),
                        method.methodType(),
                        method.flags().flagsMask(),
                        code -> code.aload(1)
                                .aload(2)
                                .invokestatic(NeoForgeLoaderPatch.HOST, FINISH, FINISH_TYPE)
                                .return_());
            } else if (element instanceof MethodModel method && is(method, "close", NO_ARGUMENTS)) {
                builder.withMethodBody(
                        method.methodName(), method.methodType(), method.flags().flagsMask(), code -> code.return_());
            } else {
                builder.with(element);
            }
        };
        return new Result(
                classFile.transformClass(model, transform),
                List.of(
                        "MixinFacade.<init>: no Mixin start of its own",
                        "MixinFacade.finishInitialization -> PolyFml." + FINISH,
                        "MixinFacade.close: nothing to detach"),
                List.of());
    }

    private static List<String> loaderMissing(ClassModel model) {
        List<Instruction> code = instructions(model, "createClassProcessorSet", CREATE_PROCESSOR_SET_TYPE);
        int calls = 0;
        boolean addedAsIs = true;
        if (code != null) {
            for (int i = 0; i < code.size(); i++) {
                if (code.get(i) instanceof InvokeInstruction call && getsProcessor(call)) {
                    calls++;
                    addedAsIs &= i + 1 < code.size()
                            && code.get(i + 1) instanceof InvokeInstruction add
                            && add.opcode() == Opcode.INVOKEVIRTUAL
                            && add.owner().asSymbol().equals(ClassDesc.of("java.util.ArrayList"))
                            && add.name().equalsString("add")
                            && add.typeSymbol()
                                    .equals(MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object));
                }
            }
        }
        if (calls != 1 || !addedAsIs) {
            return List.of("one MixinFacade.getClassProcessor() in createClassProcessorSet"
                    + CREATE_PROCESSOR_SET_TYPE.descriptorString() + " whose result goes straight to ArrayList.add");
        }
        return List.of();
    }

    private static List<String> facadeMissing(ClassModel model) {
        List<String> missing = new ArrayList<>();
        List<Instruction> constructor = instructions(model, "<init>", NO_ARGUMENTS);
        if (constructor == null
                || constructor.stream().noneMatch(i -> invokes(i, MIXIN_BOOTSTRAP, "init"))
                || constructor.stream()
                        .noneMatch(
                                i -> i instanceof ConstantInstruction c && "mixin.service".equals(c.constantValue()))) {
            missing.add("MixinFacade() that sets mixin.service and calls MixinBootstrap.init()");
        }
        List<Instruction> finish = instructions(model, "finishInitialization", FINISH_TYPE);
        if (finish == null || finish.stream().noneMatch(i -> invokes(i, FACADE, "addMixins"))) {
            missing.add("MixinFacade.finishInitialization" + FINISH_TYPE.descriptorString() + " that calls addMixins");
        }
        if (instructions(model, "close", NO_ARGUMENTS) == null) {
            missing.add("MixinFacade.close()V");
        }
        boolean fields = model.fields().stream()
                        .anyMatch(f -> f.fieldName().equalsString("classProcessor")
                                && f.fieldTypeSymbol().equals(FML_PROCESSOR))
                && model.fields().stream()
                        .anyMatch(f -> f.fieldName().equalsString("service")
                                && f.fieldTypeSymbol().equals(FML_SERVICE));
        if (!fields) {
            missing.add("MixinFacade fields classProcessor and service");
        }
        return missing;
    }

    private static boolean getsProcessor(InvokeInstruction call) {
        return call.opcode() == Opcode.INVOKEVIRTUAL
                && call.owner().asSymbol().equals(FACADE)
                && call.name().equalsString("getClassProcessor")
                && call.typeSymbol().equals(GET_PROCESSOR_TYPE);
    }

    private static boolean invokes(Instruction instruction, ClassDesc owner, String name) {
        return instruction instanceof InvokeInstruction call
                && call.owner().asSymbol().equals(owner)
                && call.name().equalsString(name);
    }

    private static boolean is(MethodModel method, String name, MethodTypeDesc type) {
        return method.methodName().equalsString(name)
                && method.methodTypeSymbol().equals(type);
    }

    /** The instructions of the one method {@code name type}, or {@code null} when there is not exactly one. */
    private static List<Instruction> instructions(ClassModel model, String name, MethodTypeDesc type) {
        List<MethodModel> found = model.methods().stream()
                .filter(method -> is(method, name, type))
                .toList();
        if (found.size() != 1 || found.getFirst().code().isEmpty()) {
            return null;
        }
        return found.getFirst()
                .code()
                .orElseThrow()
                .elementStream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList();
    }
}

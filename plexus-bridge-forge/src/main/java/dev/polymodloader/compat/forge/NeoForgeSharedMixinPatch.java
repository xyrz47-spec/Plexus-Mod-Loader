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
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Poly Mod Loader's change to FML's own Mixin start for a launch where Fabric or Quilt mods run next to NeoForge
 * mods (ADR-0026 30): one class loader holds one Mixin, and Fabric's (Poly Mod Loader's service, ADR-0020) serves
 * every loader, as it serves Forge mods (ADR-0021 G8). FML has no setting that leaves its Mixin out: {@code
 * FMLLoader.create} always creates a {@code MixinFacade}, whose constructor names FML's Mixin service and starts Mixin,
 * and whose {@code finishInitialization} adds the mods' configurations and moves Mixin to its default phase.
 *
 * <ul>
 *   <li>{@code MixinFacade}: the constructor only sets its fields to {@code null} (Mixin is already running);
 *       {@code finishInitialization(LoadingModList, TransformingClassLoader)} hands the mod list to {@code
 *       PolyFml.finishSharedMixin}, which finds the configurations as FML's {@code addMixins} does and reports them to
 *       the shared Mixin; {@code close()} does nothing (it only detaches FML's service).
 *   <li>{@code FMLLoader.createClassProcessorSet}: the one {@code mixinFacade.getClassProcessor()} becomes {@code
 *       PolyFml.mixinClassProcessor(mixinFacade)}, a processor with FML's Mixin name that changes nothing, so that the
 *       other processors keep their order; the shared Mixin applies in Poly Mod Loader's bridge stage. FML 12 adds
 *       {@code mixinFacade.getGeneratingClassProcessor()} to the set too, the processor that makes Mixin's synthetic
 *       classes ({@code org.spongepowered.asm.synthetic.args}, for {@code @ModifyArgs}) with FML's Mixin; it becomes
 *       {@code PolyFml.mixinGeneratingClassProcessor(mixinFacade)}, a processor with its name and order that changes
 *       nothing, as the shared Mixin's synthetic classes come from Poly Mod Loader's bridge stage too ({@code
 *       BridgeClassTransformer.generate}). Left as it is, FML 12 would add the {@code null} the facade now holds, and
 *       its {@code ClassProcessorSet} would fail ({@code PLEXUS-COMPAT-208}).
 * </ul>
 *
 * <p>Checked with {@code javap} against {@code loader} 11.0.5 (NeoForge 26.1.0.19-beta), 11.0.15 (26.1.2.122),
 * 11.0.16 (26.2.0.93) and 12.0.12 (26.3.0.98). Each change is made only when the method has the checked shape: the
 * constructor sets {@code mixin.service} and calls {@code MixinBootstrap.init()}, the facade's instance fields are
 * FML 11's ({@code classProcessor}, {@code service}) or FML 12's (also {@code generatingClassProcessor}), {@code
 * finishInitialization} calls {@code addMixins}, and {@code createClassProcessorSet} calls {@code getClassProcessor}
 * once, {@code getGeneratingClassProcessor} once exactly when the facade has that field, no other method of the
 * facade, and passes each result straight to {@code ArrayList.add}. Otherwise nothing changes; the host checks this
 * before NeoForge starts ({@link #missing}) and leaves NeoForge's mods out with {@code PLEXUS-COMPAT-216} instead, so that
 * two Mixins never start and no processor of FML's Mixin is left {@code null}.
 */
final class NeoForgeSharedMixinPatch {

    static final String FACADE_CLASS_FILE = "net/neoforged/fml/loading/mixin/MixinFacade.class";

    static final String MIXIN_CLASS_PROCESSOR = "mixinClassProcessor";
    static final String MIXIN_GENERATING_CLASS_PROCESSOR = "mixinGeneratingClassProcessor";
    static final String FINISH = "finishSharedMixin";
    static final String WITHOUT_FOREIGN_FABRIC_LOADER = "withoutForeignFabricLoader";

    private static final MethodTypeDesc PLUGIN_FILTER_TYPE =
            MethodTypeDesc.of(ConstantDescs.CD_List, ConstantDescs.CD_List);
    private static final MethodTypeDesc LOAD_PLUGINS_TYPE =
            MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_List);

    private static final ClassDesc FACADE = ClassDesc.of("net.neoforged.fml.loading.mixin.MixinFacade");
    private static final ClassDesc FML_PROCESSOR =
            ClassDesc.of("net.neoforged.fml.loading.mixin.FMLMixinClassProcessor");
    private static final ClassDesc FML_GENERATING_PROCESSOR =
            ClassDesc.of("net.neoforged.fml.loading.mixin.FMLMixinGeneratingClassProcessor");
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
    private static final MethodTypeDesc GET_GENERATING_PROCESSOR_TYPE = MethodTypeDesc.of(FML_GENERATING_PROCESSOR);
    private static final MethodTypeDesc HOST_PROCESSOR_TYPE = MethodTypeDesc.of(CLASS_PROCESSOR, FACADE);
    /** FML 11's instance fields of {@code MixinFacade}; FML 12 adds {@link #GENERATING_PROCESSOR_FIELD}. */
    private static final Map<String, ClassDesc> FACADE_FIELDS =
            Map.of("classProcessor", FML_PROCESSOR, "service", FML_SERVICE);

    private static final String GENERATING_PROCESSOR_FIELD = "generatingClassProcessor";
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
        ClassModel loader = ClassFile.of().parse(fmlLoader);
        ClassModel facade = ClassFile.of().parse(mixinFacade);
        List<String> missing = new ArrayList<>(loaderMissing(loader));
        missing.addAll(facadeMissing(facade));
        if (missing.isEmpty() && (generatingCalls(loader) == 1) != hasGeneratingProcessor(facade)) {
            missing.add("createClassProcessorSet that calls getGeneratingClassProcessor() exactly when MixinFacade has "
                    + GENERATING_PROCESSOR_FIELD + " (FML 12; FML 11 has neither)");
        }
        return List.copyOf(missing);
    }

    /**
     * {@code FMLLoader} with its {@code getClassProcessor} call (and FML 12's {@code getGeneratingClassProcessor} call)
     * replaced, or unchanged when the shape differs.
     */
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
            } else if (element instanceof InvokeInstruction call && getsGeneratingProcessor(call)) {
                code.invokestatic(NeoForgeLoaderPatch.HOST, MIXIN_GENERATING_CLASS_PROCESSOR, HOST_PROCESSOR_TYPE);
            } else {
                code.with(element);
            }
        };
        ClassTransform transform = (builder, element) -> {
            if (element instanceof MethodModel method
                    && is(method, "createClassProcessorSet", CREATE_PROCESSOR_SET_TYPE)) {
                builder.transformMethod(method, MethodTransform.transformingCode(redirect));
            } else if (element instanceof MethodModel method && is(method, "loadPlugins", LOAD_PLUGINS_TYPE)) {
                // The plugin files, first: without another Fabric Loader (PolyFml.withoutForeignFabricLoader).
                builder.transformMethod(method, MethodTransform.transformingCode(CodeTransform.ofStateful(() -> {
                    boolean[] started = {false};
                    return (code, codeElement) -> {
                        if (!started[0] && codeElement instanceof java.lang.classfile.Instruction) {
                            started[0] = true;
                            code.aload(1);
                            code.invokestatic(
                                    NeoForgeLoaderPatch.HOST, WITHOUT_FOREIGN_FABRIC_LOADER, PLUGIN_FILTER_TYPE);
                            code.astore(1);
                        }
                        code.with(codeElement);
                    };
                })));
            } else {
                builder.with(element);
            }
        };
        List<String> changed = new ArrayList<>();
        changed.add("createClassProcessorSet: MixinFacade.getClassProcessor() -> PolyFml." + MIXIN_CLASS_PROCESSOR);
        if (generatingCalls(model) == 1) {
            changed.add("createClassProcessorSet: MixinFacade.getGeneratingClassProcessor() -> PolyFml."
                    + MIXIN_GENERATING_CLASS_PROCESSOR);
        }
        if (model.methods().stream().anyMatch(method -> is(method, "loadPlugins", LOAD_PLUGINS_TYPE))) {
            changed.add("loadPlugins: its plugin files through PolyFml." + WITHOUT_FOREIGN_FABRIC_LOADER);
        }
        return new Result(classFile.transformClass(model, transform), changed, List.of());
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
        Map<String, ClassDesc> fields = new TreeMap<>(FACADE_FIELDS);
        if (hasGeneratingProcessor(model)) {
            fields.put(GENERATING_PROCESSOR_FIELD, FML_GENERATING_PROCESSOR);
        }
        ClassTransform transform = (builder, element) -> {
            if (element instanceof MethodModel method && is(method, "<init>", NO_ARGUMENTS)) {
                builder.withMethodBody(
                        method.methodName(), method.methodType(), method.flags().flagsMask(), code -> {
                            code.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, NO_ARGUMENTS);
                            // Every instance field (facadeMissing checked there are no others): none holds FML's Mixin.
                            fields.forEach(
                                    (name, type) -> code.aload(0).aconst_null().putfield(FACADE, name, type));
                            code.return_();
                        });
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
        int generatingCalls = 0;
        boolean otherCalls = false;
        boolean addedAsIs = true;
        if (code != null) {
            for (int i = 0; i < code.size(); i++) {
                if (!(code.get(i) instanceof InvokeInstruction call)
                        || !call.owner().asSymbol().equals(FACADE)) {
                    continue;
                }
                if (getsProcessor(call)) {
                    calls++;
                } else if (getsGeneratingProcessor(call)) {
                    generatingCalls++;
                } else {
                    otherCalls = true;
                }
                addedAsIs &= i + 1 < code.size()
                        && code.get(i + 1) instanceof InvokeInstruction add
                        && add.opcode() == Opcode.INVOKEVIRTUAL
                        && add.owner().asSymbol().equals(ClassDesc.of("java.util.ArrayList"))
                        && add.name().equalsString("add")
                        && add.typeSymbol()
                                .equals(MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object));
            }
        }
        if (code == null || calls != 1 || generatingCalls > 1 || otherCalls || !addedAsIs) {
            return List.of("one MixinFacade.getClassProcessor() and at most one getGeneratingClassProcessor() (FML 12)"
                    + " in createClassProcessorSet" + CREATE_PROCESSOR_SET_TYPE.descriptorString()
                    + ", no other MixinFacade call, each result going straight to ArrayList.add");
        }
        return List.of();
    }

    /** How many {@code getGeneratingClassProcessor} calls {@code createClassProcessorSet} has (FML 12). */
    private static long generatingCalls(ClassModel loader) {
        List<Instruction> code = instructions(loader, "createClassProcessorSet", CREATE_PROCESSOR_SET_TYPE);
        return code == null
                ? 0
                : code.stream()
                        .filter(i -> i instanceof InvokeInstruction call && getsGeneratingProcessor(call))
                        .count();
    }

    /** Whether {@code facade} has FML 12's {@code generatingClassProcessor} field. */
    private static boolean hasGeneratingProcessor(ClassModel facade) {
        return facade.fields().stream()
                .anyMatch(f -> !f.flags().has(AccessFlag.STATIC)
                        && f.fieldName().equalsString(GENERATING_PROCESSOR_FIELD)
                        && f.fieldTypeSymbol().equals(FML_GENERATING_PROCESSOR));
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
        // Exactly the instance fields the replacement constructor sets: one it does not know would stay null.
        Map<String, ClassDesc> expected = new HashMap<>(FACADE_FIELDS);
        if (hasGeneratingProcessor(model)) {
            expected.put(GENERATING_PROCESSOR_FIELD, FML_GENERATING_PROCESSOR);
            if (instructions(model, "getGeneratingClassProcessor", GET_GENERATING_PROCESSOR_TYPE) == null) {
                missing.add(
                        "MixinFacade.getGeneratingClassProcessor()" + GET_GENERATING_PROCESSOR_TYPE.descriptorString()
                                + " next to its field " + GENERATING_PROCESSOR_FIELD);
            }
        }
        Map<String, ClassDesc> fields = new HashMap<>();
        model.fields().stream()
                .filter(f -> !f.flags().has(AccessFlag.STATIC))
                .forEach(f -> fields.put(f.fieldName().stringValue(), f.fieldTypeSymbol()));
        if (!fields.equals(expected)) {
            missing.add("MixinFacade instance fields classProcessor and service (FML 11), and generatingClassProcessor"
                    + " (FML 12), and no other; found " + new TreeMap<>(fields).keySet());
        }
        return missing;
    }

    private static boolean getsProcessor(InvokeInstruction call) {
        return call.opcode() == Opcode.INVOKEVIRTUAL
                && call.owner().asSymbol().equals(FACADE)
                && call.name().equalsString("getClassProcessor")
                && call.typeSymbol().equals(GET_PROCESSOR_TYPE);
    }

    private static boolean getsGeneratingProcessor(InvokeInstruction call) {
        return call.opcode() == Opcode.INVOKEVIRTUAL
                && call.owner().asSymbol().equals(FACADE)
                && call.name().equalsString("getGeneratingClassProcessor")
                && call.typeSymbol().equals(GET_GENERATING_PROCESSOR_TYPE);
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

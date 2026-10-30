package dev.plexusmodloader.compat.fabric.mixin;

import dev.plexusmodloader.compat.bridge.GameClassBytes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A game interface that gets both a default method of the host's patch and one of a Fabric mod's interface (ADR-0026
 * 42). NeoForge's patch gives {@code BlockStateModel} its {@code BlockStateModelExtension} and Fabric API's class tweaker
 * injects {@code FabricBlockStateModel}; both have a default {@code particleMaterial(BlockAndTintGetter, BlockPos,
 * BlockState)}, so every model class that does not override it threw {@code IncompatibleClassChangeError: Conflicting
 * default methods} when called. The JVM refuses such a call; javac would refuse the interface.
 *
 * <p>After the Fabric stage (interface injection, Mixin), an interface that has a direct superinterface it did not have
 * before that stage (a mod's) and whose inherited defaults of the same name and descriptor then come from unrelated
 * interfaces, one from a mod's and one from those it had (the host's patch or vanilla), gets a default method of its own
 * that calls the one it had ({@code Original.super.m(...)}), as {@link HostPatchAdjustments} does for a class a mixin gives
 * an interface: the class keeps the host's default and overrides still win. A call through the mod's interface then runs
 * the host's default; when the two defaults' code differs, that is reported ({@code PLEXUS-COMPAT-223} limited), otherwise
 * noted ({@code PLEXUS-COMPAT-223}). Classes are left to {@link HostPatchAdjustments} (mixins) and to the audit.
 *
 * <p>Thread: any class loading thread; the interfaces read are cached.
 */
public final class HostInterfaceDefaults {

    /** A resolved conflict, for the log: the interface, the method, the default kept and the one left. */
    public record Resolution(String type, String method, String kept, String left, boolean sameCode) {}

    private final GameClassBytes classes;
    private final Consumer<Resolution> report;
    private final Map<String, Optional<ClassModel>> models = new ConcurrentHashMap<>();

    public HostInterfaceDefaults(GameClassBytes classes, Consumer<Resolution> report) {
        this.classes = Objects.requireNonNull(classes, "classes");
        this.report = Objects.requireNonNull(report, "report");
    }

    /**
     * {@code after}, the class {@code className} after the Fabric stage, with a default method for each conflict between
     * a default of an interface the stage added and one of the interfaces it had in {@code before}.
     */
    public byte[] apply(String className, byte[] before, byte[] after) {
        if (before == after) {
            return after;
        }
        ClassModel model = ClassFile.of().parse(after);
        if (!model.flags().has(AccessFlag.INTERFACE)) {
            return after;
        }
        Set<String> had = new HashSet<>();
        ClassFile.of().parse(before).interfaces().forEach(type -> had.add(type.asInternalName()));
        List<String> original = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (ClassEntry type : model.interfaces()) {
            (had.contains(type.asInternalName()) ? original : added).add(type.asInternalName());
        }
        if (added.isEmpty() || original.isEmpty()) {
            return after;
        }
        Set<String> declared = new HashSet<>();
        model.methods()
                .forEach(method -> declared.add(
                        method.methodName().stringValue() + method.methodType().stringValue()));
        List<String[]> resolved = new ArrayList<>();
        for (String mine : added) {
            for (Map.Entry<String, String> modDefault :
                    defaults(mine, new HashSet<>()).entrySet()) {
                String method = modDefault.getKey();
                if (declared.contains(method)) {
                    continue;
                }
                for (String kept : original) {
                    String hostDeclaring = defaults(kept, new HashSet<>()).get(method);
                    if (hostDeclaring == null
                            || hostDeclaring.equals(modDefault.getValue())
                            || superinterfaces(hostDeclaring).contains(modDefault.getValue())
                            || superinterfaces(modDefault.getValue()).contains(hostDeclaring)) {
                        continue;
                    }
                    declared.add(method);
                    resolved.add(new String[] {method, kept});
                    report.accept(new Resolution(
                            className.replace('.', '/'),
                            method,
                            hostDeclaring,
                            modDefault.getValue(),
                            sameCode(hostDeclaring, modDefault.getValue(), method)));
                    break;
                }
            }
        }
        if (resolved.isEmpty()) {
            return after;
        }
        return ClassFile.of().transformClass(model, java.lang.classfile.ClassTransform.endHandler(builder -> {
            for (String[] conflict : resolved) {
                int split = conflict[0].indexOf('(');
                String name = conflict[0].substring(0, split);
                MethodTypeDesc type = MethodTypeDesc.ofDescriptor(conflict[0].substring(split));
                ClassDesc kept = ClassDesc.ofInternalName(conflict[1]);
                builder.withMethodBody(name, type, ClassFile.ACC_PUBLIC, code -> {
                    code.aload(0);
                    int slot = 1;
                    for (ClassDesc parameter : type.parameterList()) {
                        code.loadLocal(TypeKind.from(parameter), slot);
                        slot += TypeKind.from(parameter).slotSize();
                    }
                    code.invokespecial(kept, name, type, true);
                    code.return_(TypeKind.from(type.returnType()));
                });
            }
        }));
    }

    /** The default methods ({@code name + descriptor}) interface {@code name} has or inherits, to the declaring one. */
    private Map<String, String> defaults(String name, Set<String> seen) {
        Map<String, String> found = new LinkedHashMap<>();
        Optional<ClassModel> model = model(name);
        if (!seen.add(name) || model.isEmpty() || !model.get().flags().has(AccessFlag.INTERFACE)) {
            return found;
        }
        Set<String> abstracts = new HashSet<>();
        for (MethodModel method : model.get().methods()) {
            String key = method.methodName().stringValue() + method.methodType().stringValue();
            int flags = method.flags().flagsMask();
            if ((flags & (ClassFile.ACC_STATIC | ClassFile.ACC_PRIVATE)) != 0
                    || method.methodName().equalsString(ConstantDescs.CLASS_INIT_NAME)) {
                continue;
            }
            if (method.code().isPresent()) {
                found.put(key, name);
            } else {
                // A redeclared abstract method hides the inherited defaults.
                abstracts.add(key);
            }
        }
        for (ClassEntry parent : model.get().interfaces()) {
            defaults(parent.asInternalName(), seen).forEach((key, declaring) -> {
                if (!abstracts.contains(key)) {
                    found.putIfAbsent(key, declaring);
                }
            });
        }
        return found;
    }

    /** Every superinterface of interface {@code name}. */
    private Set<String> superinterfaces(String name) {
        Set<String> found = new HashSet<>();
        List<String> queue = new ArrayList<>(List.of(name));
        while (!queue.isEmpty()) {
            model(queue.removeLast())
                    .ifPresent(model -> model.interfaces().forEach(parent -> {
                        if (found.add(parent.asInternalName())) {
                            queue.add(parent.asInternalName());
                        }
                    }));
        }
        return found;
    }

    /** Whether the two defaults {@code method} have the same instructions. */
    private boolean sameCode(String first, String second, String method) {
        return code(first, method).equals(code(second, method));
    }

    private List<String> code(String owner, String method) {
        List<String> instructions = new ArrayList<>();
        model(owner)
                .ifPresent(model -> model.methods().stream()
                        .filter(candidate -> (candidate.methodName().stringValue()
                                        + candidate.methodType().stringValue())
                                .equals(method))
                        .findFirst()
                        .flatMap(MethodModel::code)
                        .ifPresent(code -> {
                            for (CodeElement element : code) {
                                if (element instanceof java.lang.classfile.Instruction instruction) {
                                    instructions.add(instruction.toString());
                                }
                            }
                        }));
        return instructions;
    }

    private Optional<ClassModel> model(String name) {
        return models.computeIfAbsent(
                name, key -> classes.classBytes(key).map(bytes -> ClassFile.of().parse(bytes)));
    }

    /** {@link GameClassBytes} of a plain lookup, for tests. */
    static GameClassBytes lookup(Function<String, Optional<byte[]>> bytes) {
        return new GameClassBytes() {
            @Override
            public Optional<byte[]> classBytes(String className) {
                return bytes.apply(className.replace('.', '/'));
            }

            @Override
            public boolean isDefined(String className) {
                return false;
            }
        };
    }
}

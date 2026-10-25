package dev.polymodloader.compat.fabric.mixin;

import dev.polymodloader.compat.bridge.HostedLaunch;
import dev.polymodloader.compat.fabric.mixin.HostPatchAdjustments.Finding;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fabric mixin members that meet a member the host loader's patch added or changed (ADR-0021 G8). Mixin fails a whole
 * mixin when one of its members collides with the patched class: an {@code @Accessor} or {@code @Invoker} whose method
 * the patch added itself, a method the mixin adds that the patch also added, or an {@code @Accessor} of a field whose
 * type the patch changed. The target then does not implement the mixin's interfaces, and the mod's casts to them fail
 * at run time. Decided from the vanilla and the patched class, one member at a time:
 *
 * <ul>
 *   <li>The patch added a public method with the code the accessor or invoker would have (reads or writes that field,
 *       calls that method): Mixin does not see the accessor, and the patched method implements it ({@code
 *       POLY-COMPAT-212}).
 *   <li>The patch added the method the mixin adds, with the same code: the patched class keeps its own ({@code
 *       POLY-COMPAT-212}).
 *   <li>The patch added it with other code: the mixin's method gets its own name ({@code poly$<mixin>$<name>}), and the
 *       calls to it in the mod's mixins follow, so both methods work; other callers reach the patched one ({@code
 *       POLY-COMPAT-213}).
 *   <li>The patch changed the type of the field an accessor reads or writes: no accessor of the old type can exist.
 *       Mixin does not see it, so the rest of the mixin applies; as the game loads the mixin's interface, that method
 *       throws {@code POLY-COMPAT-214}, which names the change ({@code POLY-COMPAT-138}).
 *   <li>The patch added an accessor's or invoker's method with other code: when the mixin's configuration requires
 *       {@code @Overwrite} ({@code overwrites.requireAnnotations}), Mixin would fail the mixin, so it is left out, as
 *       when the patch removed a member it uses ({@code POLY-COMPAT-138}); otherwise Mixin replaces the patched method
 *       with the generated one, as it does for any method of such a configuration, and that is reported ({@code
 *       POLY-COMPAT-213}).
 * </ul>
 *
 * <p>Only single-target mixins are checked (Mixin allows accessors only there); a member Mixin itself resolves
 * ({@code @Unique}, {@code @Shadow}, {@code @Overwrite}, injectors) is left to Mixin. Calls outside the mod's mixins are
 * not changed.
 */
final class HostAddedMembers {

    static final HostAddedMembers NONE = new HostAddedMembers(Map.of(), Map.of(), Map.of());

    private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
    private static final String RENAME_PREFIX = "poly$";

    /** A mixin method Mixin does not see ({@code newName} null), or sees under {@code newName}. */
    private record Change(String name, String descriptor, String newName) {}

    /** Calls to {@code name descriptor} on one of {@code owners} that go to {@code newName}. */
    private record Call(Set<String> owners, String name, String descriptor, String newName) {}

    /** An accessor of the mixin's interface that throws {@code message} in the game. */
    private record Unavailable(String name, String descriptor, String message) {}

    private final Map<String, List<Change>> changes;
    private final Map<String, List<Call>> calls;
    private final Map<String, List<Unavailable>> unavailable;

    private HostAddedMembers(
            Map<String, List<Change>> changes,
            Map<String, List<Call>> calls,
            Map<String, List<Unavailable>> unavailable) {
        this.changes = changes;
        this.calls = calls;
        this.unavailable = unavailable;
    }

    /** Collects the changes while {@link HostPatchAdjustments#plan} goes through the mixins. Not thread-safe. */
    static final class Planner {

        private final HostedLaunch host;
        private final HostPatchAdjustments.Classes classes;
        private final Map<String, List<Change>> changes = new HashMap<>();
        private final Map<String, List<Unavailable>> unavailable = new HashMap<>();
        private final Map<String, List<Call>> modCalls = new HashMap<>();
        private final Map<String, Set<String>> modMixins = new HashMap<>();

        Planner(HostedLaunch host, HostPatchAdjustments.Classes classes) {
            this.host = host;
            this.classes = classes;
        }

        /**
         * The findings for {@code mixin} of mod {@code modId}, from configuration {@code config}; {@code
         * overwriteNeedsAnnotation} is the configuration's {@code overwrites.requireAnnotations}. When one of them is
         * {@link Finding#leftOut}, it is the only one, nothing is planned for the mixin, and the caller leaves it out of
         * its configuration.
         */
        List<Finding> check(String modId, String config, ClassModel mixin, boolean overwriteNeedsAnnotation) {
            String mixinName = mixin.thisClass().asInternalName();
            List<String> targets = HostPatchAdjustments.mixinTargets(mixin);
            if (targets.size() != 1) {
                modMixins.computeIfAbsent(modId, id -> new HashSet<>()).add(mixinName);
                return List.of();
            }
            String targetName = targets.getFirst();
            Optional<ClassModel> vanilla = classes.vanilla(targetName);
            Optional<ClassModel> patched = classes.patched(targetName);
            if (vanilla.isEmpty() || patched.isEmpty()) {
                modMixins.computeIfAbsent(modId, id -> new HashSet<>()).add(mixinName);
                return List.of();
            }
            List<Finding> findings = new ArrayList<>();
            List<Change> ownChanges = new ArrayList<>();
            List<Unavailable> ownUnavailable = new ArrayList<>();
            List<Call> ownCalls = new ArrayList<>();
            for (MethodModel method : mixin.methods()) {
                String name = method.methodName().stringValue();
                String descriptor = method.methodType().stringValue();
                String member = name + descriptor;
                boolean isStatic = method.flags().has(java.lang.reflect.AccessFlag.STATIC);
                boolean added = HostPatchAdjustments.methods(vanilla.get(), name, descriptor)
                                .isEmpty()
                        && !HostPatchAdjustments.methods(patched.get(), name, descriptor)
                                .isEmpty();
                Optional<Annotation> accessor =
                        HostPatchAdjustments.annotation(method.attributes(), HostPatchAdjustments.ACCESSOR);
                Optional<Annotation> invoker =
                        HostPatchAdjustments.annotation(method.attributes(), HostPatchAdjustments.INVOKER);
                if (accessor.isPresent() || invoker.isPresent()) {
                    if (isStatic) {
                        continue;
                    }
                    String use = accessor.isPresent() ? "@Accessor" : "@Invoker";
                    if (added) {
                        MethodModel own = HostPatchAdjustments.methods(patched.get(), name, descriptor)
                                .getFirst();
                        boolean same = own.flags().has(java.lang.reflect.AccessFlag.PUBLIC)
                                && !own.flags().has(java.lang.reflect.AccessFlag.STATIC)
                                && (accessor.isPresent()
                                        ? isFieldAccessor(own, targetName, field(accessor.get(), name), method)
                                        : isInvoker(own, targetName, invoked(invoker.get(), name), method));
                        if (!same && overwriteNeedsAnnotation) {
                            // Mixin would fail the whole mixin ("@Overwrite is required").
                            return List.of(new Finding(
                                    modId,
                                    config,
                                    mixinName,
                                    "its " + use + " " + member + " is also a method " + host.hostName()
                                            + "'s patch added to " + targetName
                                            + ", with other code, so this mixin is left out",
                                    true));
                        }
                        if (!same) {
                            // Mixin replaces the patched method with the generated one, as it did before this check.
                            findings.add(new Finding(
                                    modId,
                                    config,
                                    mixinName,
                                    "its " + use + " " + member + " is also a method " + host.hostName()
                                            + "'s patch added to " + targetName + ", with other code; the mod's"
                                            + " configuration lets Mixin replace that method with the " + use
                                            + ", so " + host.hostName() + "'s code calling it gets the "
                                            + use.substring(1).toLowerCase(java.util.Locale.ROOT) + "'s result",
                                    false,
                                    "POLY-COMPAT-213",
                                    true));
                            continue;
                        }
                        ownChanges.add(new Change(name, descriptor, null));
                        findings.add(new Finding(
                                modId,
                                config,
                                mixinName,
                                "its " + use + " " + member + " is a method " + host.hostName()
                                        + "'s patch added to " + targetName + " with the same code; that method"
                                        + " serves it and the rest of the mixin applies",
                                false,
                                "POLY-COMPAT-212",
                                false));
                    } else if (accessor.isPresent()) {
                        String field = field(accessor.get(), name);
                        Optional<String> type = accessedType(method.methodTypeSymbol());
                        Optional<String> patchedType = type.flatMap(
                                accessed -> changedFieldType(vanilla.get(), patched.get(), field, accessed));
                        if (patchedType.isPresent()) {
                            String message = "POLY-COMPAT-214 Fabric mod " + modId + " called " + mixinName + "."
                                    + member + ", its @Accessor of field " + field + " of " + targetName + ", which "
                                    + host.hostName() + "'s patch changed from " + type.get() + " to "
                                    + patchedType.get() + "; this accessor cannot work on that game, so that part"
                                    + " of the mod does not run";
                            ownChanges.add(new Change(name, descriptor, null));
                            ownUnavailable.add(new Unavailable(name, descriptor, message));
                            findings.add(new Finding(
                                    modId,
                                    config,
                                    mixinName,
                                    "its @Accessor " + member + " uses field " + field + " of " + targetName + " as "
                                            + type.get() + ", which " + host.hostName() + "'s patch changed to "
                                            + patchedType.get() + "; the rest of the mixin applies, and a call to "
                                            + name + " throws POLY-COMPAT-214",
                                    false));
                        }
                    }
                    continue;
                }
                if (!added
                        || name.startsWith("<")
                        || method.flags().has(java.lang.reflect.AccessFlag.SYNTHETIC)
                        || method.flags().has(java.lang.reflect.AccessFlag.BRIDGE)
                        || method.code().isEmpty()
                        || resolvedByMixin(method)
                        || inVanillaSupertypes(vanilla.get(), name, descriptor, new HashSet<>())) {
                    continue;
                }
                MethodModel own = HostPatchAdjustments.methods(patched.get(), name, descriptor)
                        .getFirst();
                if (sameCode(method, mixinName, own, targetName)) {
                    ownChanges.add(new Change(name, descriptor, null));
                    findings.add(new Finding(
                            modId,
                            config,
                            mixinName,
                            "its method " + member + " is a method " + host.hostName() + "'s patch added to "
                                    + targetName + " with the same code; " + targetName
                                    + " keeps its own and the rest of the mixin applies",
                            false,
                            "POLY-COMPAT-212",
                            false));
                    continue;
                }
                String newName = RENAME_PREFIX + simpleName(mixinName) + "$" + name;
                ownChanges.add(new Change(name, descriptor, newName));
                ownCalls.add(new Call(Set.of(targetName, mixinName), name, descriptor, newName));
                findings.add(new Finding(
                        modId,
                        config,
                        mixinName,
                        "its method " + member + " is also a method " + host.hostName() + "'s patch added to "
                                + targetName + ", with other code; the mixin's method becomes " + newName
                                + " and the calls in mod " + modId + "'s mixins follow it, while other code calling "
                                + member + " on " + targetName + " (other classes, other mods, calls through an"
                                + " interface) reaches " + host.hostName() + "'s",
                        false,
                        "POLY-COMPAT-213",
                        true));
            }
            modMixins.computeIfAbsent(modId, id -> new HashSet<>()).add(mixinName);
            if (!ownChanges.isEmpty()) {
                changes.put(mixinName, List.copyOf(ownChanges));
            }
            if (!ownUnavailable.isEmpty()) {
                unavailable.put(mixinName, List.copyOf(ownUnavailable));
            }
            if (!ownCalls.isEmpty()) {
                modCalls.computeIfAbsent(modId, id -> new ArrayList<>()).addAll(ownCalls);
            }
            return findings;
        }

        HostAddedMembers build() {
            if (changes.isEmpty() && unavailable.isEmpty()) {
                return NONE;
            }
            Map<String, List<Call>> calls = new HashMap<>();
            modCalls.forEach((modId, own) -> {
                for (String mixin : modMixins.getOrDefault(modId, Set.of())) {
                    calls.put(mixin, List.copyOf(own));
                }
            });
            return new HostAddedMembers(Map.copyOf(changes), Map.copyOf(calls), Map.copyOf(unavailable));
        }

        /**
         * Whether a vanilla supertype of {@code type} declares {@code name descriptor}: the mixin's method then
         * overrides it and is not the mixin's own.
         */
        private boolean inVanillaSupertypes(ClassModel type, String name, String descriptor, Set<String> seen) {
            List<String> supertypes = new ArrayList<>();
            type.superclass().ifPresent(superclass -> supertypes.add(superclass.asInternalName()));
            type.interfaces().forEach(entry -> supertypes.add(entry.asInternalName()));
            for (String supertype : supertypes) {
                if (!seen.add(supertype)) {
                    continue;
                }
                Optional<ClassModel> model = classes.vanilla(supertype);
                if (model.isPresent()
                        && (!HostPatchAdjustments.methods(model.get(), name, descriptor)
                                        .isEmpty()
                                || inVanillaSupertypes(model.get(), name, descriptor, seen))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** The mixin {@code mixin} as Mixin reads it: without its left-out methods, renamed ones and calls following. */
    byte[] apply(String mixin, byte[] bytes) {
        List<Change> own = changes.getOrDefault(mixin, List.of());
        List<Call> ownCalls = calls.getOrDefault(mixin, List.of());
        if (own.isEmpty() && ownCalls.isEmpty()) {
            return bytes;
        }
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(bytes);
        CodeTransform rewrite = (code, element) -> rewrite(code, element, ownCalls);
        return classFile.transformClass(model, (builder, element) -> {
            if (!(element instanceof MethodModel method)) {
                builder.with(element);
                return;
            }
            Optional<Change> change = own.stream()
                    .filter(candidate -> method.methodName().equalsString(candidate.name())
                            && method.methodType().equalsString(candidate.descriptor()))
                    .findFirst();
            if (change.isPresent() && change.get().newName() == null) {
                return;
            }
            String name = change.map(Change::newName).orElse(method.methodName().stringValue());
            builder.withMethod(name, method.methodTypeSymbol(), method.flags().flagsMask(), methodBuilder -> {
                for (MethodElement methodElement : method) {
                    if (methodElement instanceof CodeModel code) {
                        methodBuilder.transformCode(code, rewrite);
                    } else {
                        methodBuilder.with(methodElement);
                    }
                }
            });
        });
    }

    /**
     * {@code bytes} of class {@code className} as the game loads it: a mixin interface whose accessor cannot work gets
     * a body for it that throws {@code POLY-COMPAT-214}.
     */
    byte[] runtime(String className, byte[] bytes) {
        List<Unavailable> own = unavailable.get(className.replace('.', '/'));
        if (own == null || bytes == null) {
            return bytes;
        }
        ClassFile classFile = ClassFile.of();
        return classFile.transformClass(classFile.parse(bytes), (builder, element) -> {
            Optional<Unavailable> match = element instanceof MethodModel method
                    ? own.stream()
                            .filter(candidate -> method.methodName().equalsString(candidate.name())
                                    && method.methodType().equalsString(candidate.descriptor()))
                            .findFirst()
                    : Optional.empty();
            if (match.isEmpty()) {
                builder.with(element);
                return;
            }
            MethodModel method = (MethodModel) element;
            int flags = method.flags().flagsMask() & ~ClassFile.ACC_ABSTRACT;
            ClassDesc error = ClassDesc.of("java.lang.UnsupportedOperationException");
            builder.withMethod(method.methodName(), method.methodType(), flags, methodBuilder -> {
                for (MethodElement methodElement : method) {
                    if (!(methodElement instanceof CodeModel) && !(methodElement instanceof AccessFlags)) {
                        methodBuilder.with(methodElement);
                    }
                }
                methodBuilder.withCode(code -> code.new_(error)
                        .dup()
                        .ldc(match.get().message())
                        .invokespecial(
                                error,
                                "<init>",
                                MethodTypeDesc.of(
                                        java.lang.constant.ConstantDescs.CD_void,
                                        java.lang.constant.ConstantDescs.CD_String))
                        .athrow());
            });
        });
    }

    /** {@code element}, with a call, method handle constant or bootstrap argument in {@code calls} renamed. */
    private static void rewrite(CodeBuilder code, CodeElement element, List<Call> calls) {
        if (element instanceof InvokeInstruction call) {
            for (Call candidate : calls) {
                if (candidate.owners().contains(call.owner().asInternalName())
                        && call.name().equalsString(candidate.name())
                        && call.type().equalsString(candidate.descriptor())) {
                    code.invoke(
                            call.opcode(),
                            call.owner().asSymbol(),
                            candidate.newName(),
                            call.typeSymbol(),
                            call.isInterface());
                    return;
                }
            }
        } else if (element instanceof ConstantInstruction.LoadConstantInstruction constant
                && constant.constantValue() instanceof DirectMethodHandleDesc handle) {
            DirectMethodHandleDesc renamed = renamed(handle, calls);
            if (renamed != handle) {
                code.ldc(renamed);
                return;
            }
        } else if (element instanceof InvokeDynamicInstruction dynamic) {
            List<ConstantDesc> arguments = new ArrayList<>();
            boolean changed = false;
            for (ConstantDesc argument : dynamic.bootstrapArgs()) {
                ConstantDesc renamed =
                        argument instanceof DirectMethodHandleDesc handle ? renamed(handle, calls) : argument;
                changed |= renamed != argument;
                arguments.add(renamed);
            }
            if (changed) {
                code.invokedynamic(DynamicCallSiteDesc.of(
                        dynamic.bootstrapMethod(),
                        dynamic.name().stringValue(),
                        dynamic.typeSymbol(),
                        arguments.toArray(ConstantDesc[]::new)));
                return;
            }
        }
        code.with(element);
    }

    private static DirectMethodHandleDesc renamed(DirectMethodHandleDesc handle, List<Call> calls) {
        for (Call candidate : calls) {
            if (candidate.owners().contains(handle.owner().descriptorString().replaceAll("^L|;$", ""))
                    && handle.methodName().equals(candidate.name())
                    && handle.lookupDescriptor().equals(candidate.descriptor())) {
                return MethodHandleDesc.ofMethod(
                        handle.kind(),
                        handle.owner(),
                        candidate.newName(),
                        MethodTypeDesc.ofDescriptor(handle.lookupDescriptor()));
            }
        }
        return handle;
    }

    /** Members Mixin itself places: shadows, overwrites, injectors, {@code @Unique} (renamed or dropped by Mixin). */
    private static boolean resolvedByMixin(MethodModel method) {
        if (HostPatchAdjustments.has(method.attributes(), HostPatchAdjustments.SHADOW)
                || HostPatchAdjustments.has(method.attributes(), HostPatchAdjustments.OVERWRITE)
                || HostPatchAdjustments.has(method.attributes(), UNIQUE)) {
            return true;
        }
        return HostPatchAdjustments.INJECTORS.stream()
                .anyMatch(type -> HostPatchAdjustments.has(method.attributes(), type));
    }

    private static String field(Annotation accessor, String method) {
        return HostPatchAdjustments.value(accessor).orElse(HostPatchAdjustments.accessorName(method));
    }

    private static String invoked(Annotation invoker, String method) {
        return HostPatchAdjustments.value(invoker).orElse(HostPatchAdjustments.invokerName(method));
    }

    /** The field type an accessor of {@code type} reads ({@code ()T}) or writes ({@code (T)V}). */
    private static Optional<String> accessedType(MethodTypeDesc type) {
        if (type.parameterCount() == 0 && !type.returnType().equals(java.lang.constant.ConstantDescs.CD_void)) {
            return Optional.of(type.returnType().descriptorString());
        }
        if (type.parameterCount() == 1 && type.returnType().equals(java.lang.constant.ConstantDescs.CD_void)) {
            return Optional.of(type.parameterType(0).descriptorString());
        }
        return Optional.empty();
    }

    /** The patched type of field {@code name}, when vanilla has it as {@code type} and the patch only as another type. */
    private static Optional<String> changedFieldType(ClassModel vanilla, ClassModel patched, String name, String type) {
        boolean inVanilla = vanilla.fields().stream()
                .anyMatch(field -> field.fieldName().equalsString(name)
                        && field.fieldType().equalsString(type));
        if (!inVanilla) {
            return Optional.empty();
        }
        List<FieldModel> after = patched.fields().stream()
                .filter(field -> field.fieldName().equalsString(name))
                .toList();
        if (after.isEmpty()
                || after.stream().anyMatch(field -> field.fieldType().equalsString(type))) {
            return Optional.empty();
        }
        return Optional.of(after.getFirst().fieldType().stringValue());
    }

    /**
     * Whether {@code method} of {@code owner} is exactly the accessor {@code accessor} of field {@code field}: {@code
     * this.field} returned, or set from the one parameter.
     */
    private static boolean isFieldAccessor(MethodModel method, String owner, String field, MethodModel accessor) {
        MethodTypeDesc type = accessor.methodTypeSymbol();
        List<Instruction> code = instructions(method);
        if (code.size() == 3 && type.parameterCount() == 0) {
            return isThis(code.get(0))
                    && code.get(1) instanceof FieldInstruction get
                    && get.opcode() == Opcode.GETFIELD
                    && isMember(get.owner().asInternalName(), get.name().stringValue(), owner, field)
                    && get.typeSymbol().equals(type.returnType())
                    && code.get(2) instanceof ReturnInstruction result
                    && result.typeKind() == TypeKind.from(type.returnType());
        }
        if (code.size() == 4 && type.parameterCount() == 1) {
            return isThis(code.get(0))
                    && code.get(1) instanceof LoadInstruction value
                    && value.slot() == 1
                    && value.typeKind() == TypeKind.from(type.parameterType(0))
                    && code.get(2) instanceof FieldInstruction put
                    && put.opcode() == Opcode.PUTFIELD
                    && isMember(put.owner().asInternalName(), put.name().stringValue(), owner, field)
                    && put.typeSymbol().equals(type.parameterType(0))
                    && code.get(3) instanceof ReturnInstruction result
                    && result.typeKind() == TypeKind.VOID;
        }
        return false;
    }

    /** Whether {@code method} of {@code owner} only calls {@code owner.target} with its parameters and returns. */
    private static boolean isInvoker(MethodModel method, String owner, String target, MethodModel invoker) {
        MethodTypeDesc type = invoker.methodTypeSymbol();
        List<Instruction> code = instructions(method);
        if (code.size() != type.parameterCount() + 3 || !isThis(code.getFirst())) {
            return false;
        }
        int slot = 1;
        for (int index = 0; index < type.parameterCount(); index++) {
            TypeKind kind = TypeKind.from(type.parameterType(index));
            if (!(code.get(index + 1) instanceof LoadInstruction load)
                    || load.slot() != slot
                    || load.typeKind() != kind) {
                return false;
            }
            slot += kind.slotSize();
        }
        return code.get(code.size() - 2) instanceof InvokeInstruction call
                && (call.opcode() == Opcode.INVOKEVIRTUAL || call.opcode() == Opcode.INVOKESPECIAL)
                && isMember(call.owner().asInternalName(), call.name().stringValue(), owner, target)
                && call.typeSymbol().equals(type)
                && code.getLast() instanceof ReturnInstruction result
                && result.typeKind() == TypeKind.from(type.returnType());
    }

    private static boolean isThis(Instruction instruction) {
        return instruction instanceof LoadInstruction load && load.slot() == 0 && load.typeKind() == TypeKind.REFERENCE;
    }

    private static boolean isMember(String owner, String name, String expectedOwner, String expectedName) {
        return owner.equals(expectedOwner) && name.equals(expectedName);
    }

    /**
     * Whether the mixin's {@code method} and the patched {@code own} have the same instructions, the mixin's class
     * standing for the target. Code with branches, switches, dynamic calls or exception handlers never counts as the
     * same.
     */
    static boolean sameCode(MethodModel method, String mixinName, MethodModel own, String targetName) {
        if (method.flags().has(java.lang.reflect.AccessFlag.STATIC)
                        != own.flags().has(java.lang.reflect.AccessFlag.STATIC)
                || method.code().isEmpty()
                || own.code().isEmpty()
                || !method.code().get().exceptionHandlers().isEmpty()
                || !own.code().get().exceptionHandlers().isEmpty()) {
            return false;
        }
        List<Instruction> mixinCode = instructions(method);
        List<Instruction> ownCode = instructions(own);
        if (mixinCode.size() != ownCode.size()) {
            return false;
        }
        for (int index = 0; index < mixinCode.size(); index++) {
            if (!sameInstruction(mixinCode.get(index), ownCode.get(index), mixinName, targetName)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameInstruction(Instruction mixin, Instruction own, String mixinName, String targetName) {
        if (mixin.opcode() != own.opcode()) {
            return false;
        }
        return switch (mixin) {
            case FieldInstruction field
            when own instanceof FieldInstruction other ->
                sameOwner(field.owner().asInternalName(), other.owner().asInternalName(), mixinName, targetName)
                        && field.name().stringValue().equals(other.name().stringValue())
                        && field.type().stringValue().equals(other.type().stringValue());
            case InvokeInstruction call
            when own instanceof InvokeInstruction other ->
                sameOwner(call.owner().asInternalName(), other.owner().asInternalName(), mixinName, targetName)
                        && call.name().stringValue().equals(other.name().stringValue())
                        && call.type().stringValue().equals(other.type().stringValue())
                        && call.isInterface() == other.isInterface();
            case LoadInstruction load when own instanceof LoadInstruction other -> load.slot() == other.slot();
            case StoreInstruction store when own instanceof StoreInstruction other -> store.slot() == other.slot();
            case IncrementInstruction increment
            when own instanceof IncrementInstruction other ->
                increment.slot() == other.slot() && increment.constant() == other.constant();
            case ConstantInstruction constant
            when own instanceof ConstantInstruction other ->
                Objects.equals(constant.constantValue(), other.constantValue());
            case NewObjectInstruction created
            when own instanceof NewObjectInstruction other ->
                sameOwner(
                        created.className().asInternalName(),
                        other.className().asInternalName(),
                        mixinName,
                        targetName);
            case TypeCheckInstruction check
            when own instanceof TypeCheckInstruction other ->
                sameOwner(check.type().asInternalName(), other.type().asInternalName(), mixinName, targetName);
            case NewReferenceArrayInstruction array
            when own instanceof NewReferenceArrayInstruction other ->
                sameOwner(
                        array.componentType().asInternalName(),
                        other.componentType().asInternalName(),
                        mixinName,
                        targetName);
            case OperatorInstruction _,
                    ReturnInstruction _,
                    StackInstruction _,
                    ConvertInstruction _,
                    ThrowInstruction _,
                    ArrayLoadInstruction _,
                    ArrayStoreInstruction _,
                    NopInstruction _,
                    MonitorInstruction _ -> true;
            default -> false;
        };
    }

    private static boolean sameOwner(String mixinOwner, String ownOwner, String mixinName, String targetName) {
        return (mixinOwner.equals(mixinName) ? targetName : mixinOwner).equals(ownOwner);
    }

    private static List<Instruction> instructions(MethodModel method) {
        return method.code()
                .map(code -> code.elementList().stream()
                        .filter(Instruction.class::isInstance)
                        .map(Instruction.class::cast)
                        .toList())
                .orElse(List.of());
    }

    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }
}

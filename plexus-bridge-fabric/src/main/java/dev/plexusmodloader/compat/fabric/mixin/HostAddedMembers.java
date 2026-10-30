package dev.plexusmodloader.compat.fabric.mixin;

import dev.plexusmodloader.compat.bridge.HostedLaunch;
import dev.plexusmodloader.compat.fabric.mixin.HostPatchAdjustments.Finding;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
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
 * type the patch changed. Decided from the vanilla and the patched class, one member at a time:
 *
 * <ul>
 *   <li>The patch added a public method with the code the accessor or invoker would have (reads or writes that field,
 *       calls that method): Mixin does not see the accessor, and the patched method implements it ({@code
 *       PLEXUS-COMPAT-212}). The mixin works as written.
 *   <li>The patch added the method the mixin adds, with the same code: the patched class keeps its own ({@code
 *       PLEXUS-COMPAT-212}). The mixin works as written.
 *   <li>The patch added it, or an accessor's or invoker's method, with other code, or changed the type of the field an
 *       accessor reads or writes: one class cannot hold both, so the mixin cannot work as written on that game. The mod
 *       is incompatible ({@code PLEXUS-COMPAT-224}, ADR-0030) and is not loaded; Poly Mod Loader does not rename or
 *       replace either method to make the rest of the mod run.
 * </ul>
 *
 * <p>Only single-target mixins are checked (Mixin allows accessors only there); a member Mixin itself resolves
 * ({@code @Unique}, {@code @Shadow}, {@code @Overwrite}, injectors) is left to Mixin.
 */
final class HostAddedMembers {

    static final HostAddedMembers NONE = new HostAddedMembers(Map.of());

    private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";

    /** A mixin method Mixin does not see, because the patched class has it with the same code. */
    private record Change(String name, String descriptor) {}

    private final Map<String, List<Change>> changes;

    private HostAddedMembers(Map<String, List<Change>> changes) {
        this.changes = changes;
    }

    /** Collects the changes while {@link HostPatchAdjustments#plan} goes through the mixins. Not thread-safe. */
    static final class Planner {

        private final HostedLaunch host;
        private final HostPatchAdjustments.Classes classes;
        private final Map<String, List<Change>> changes = new HashMap<>();

        Planner(HostedLaunch host, HostPatchAdjustments.Classes classes) {
            this.host = host;
            this.classes = classes;
        }

        /**
         * The findings for {@code mixin} of mod {@code modId}, from configuration {@code config}; {@code
         * overwriteNeedsAnnotation} is the configuration's {@code overwrites.requireAnnotations}. When one of them is
         * {@link Finding#leftOut} (Mixin would fail the whole mixin), it is the only one and nothing is planned for the
         * mixin.
         */
        List<Finding> check(String modId, String config, ClassModel mixin, boolean overwriteNeedsAnnotation) {
            String mixinName = mixin.thisClass().asInternalName();
            List<String> targets = HostPatchAdjustments.mixinTargets(mixin);
            if (targets.size() != 1) {
                return List.of();
            }
            String targetName = targets.getFirst();
            Optional<ClassModel> vanilla = classes.vanilla(targetName);
            Optional<ClassModel> patched = classes.patched(targetName);
            if (vanilla.isEmpty() || patched.isEmpty()) {
                return List.of();
            }
            List<Finding> findings = new ArrayList<>();
            List<Change> ownChanges = new ArrayList<>();
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
                                        ? (isFieldAccessor(own, targetName, field(accessor.get(), name), method)
                                                || isAttributeBuilderBridgeMethod(
                                                        own, targetName, field(accessor.get(), name)))
                                        : isInvoker(own, targetName, invoked(invoker.get(), name), method));
                        if (!same) {
                            // With @Overwrite required Mixin fails the whole mixin; otherwise it replaces the patched
                            // method, and the host's code calling it gets the accessor's result.
                            Finding finding = Finding.incompatible(
                                    modId,
                                    config,
                                    mixinName,
                                    "its " + use + " " + member + " is also a method " + host.hostName()
                                            + "'s patch added to " + targetName + ", with other code; one class cannot"
                                            + " hold both",
                                    overwriteNeedsAnnotation);
                            if (overwriteNeedsAnnotation) {
                                return List.of(finding);
                            }
                            findings.add(finding);
                            continue;
                        }
                        ownChanges.add(new Change(name, descriptor));
                        findings.add(new Finding(
                                modId,
                                config,
                                mixinName,
                                "its " + use + " " + member + " is a method " + host.hostName()
                                        + "'s patch added to " + targetName + " with the same code; that method"
                                        + " serves it and the rest of the mixin applies",
                                false,
                                "PLEXUS-COMPAT-212",
                                false));
                    } else if (accessor.isPresent()) {
                        String field = field(accessor.get(), name);
                        Optional<String> type = accessedType(method.methodTypeSymbol());
                        Optional<String> patchedType = type.flatMap(
                                accessed -> changedFieldType(vanilla.get(), patched.get(), field, accessed));
                        if (patchedType.isPresent()) {
                            if (isAttributeSupplierBuilder(targetName, field, type.get(), patchedType.get())) {
                                ownChanges.add(new Change(name, descriptor));
                                findings.add(new Finding(
                                        modId,
                                        config,
                                        mixinName,
                                        "its @Accessor " + member + " uses field " + field + " of " + targetName
                                                + " as "
                                                + type.get() + ", which " + host.hostName() + "'s patch changed to "
                                                + patchedType.get() + "; adapted via AttributeBuilderBridge",
                                        false,
                                        "PLEXUS-COMPAT-212",
                                        false));
                            } else {
                                findings.add(Finding.incompatible(
                                        modId,
                                        config,
                                        mixinName,
                                        "its @Accessor " + member + " uses field " + field + " of " + targetName
                                                + " as "
                                                + type.get() + ", which " + host.hostName() + "'s patch changed to "
                                                + patchedType.get()
                                                + "; no accessor of that type can work on that game",
                                        false));
                            }
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
                    ownChanges.add(new Change(name, descriptor));
                    findings.add(new Finding(
                            modId,
                            config,
                            mixinName,
                            "its method " + member + " is a method " + host.hostName() + "'s patch added to "
                                    + targetName + " with the same code; " + targetName
                                    + " keeps its own and the rest of the mixin applies",
                            false,
                            "PLEXUS-COMPAT-212",
                            false));
                    continue;
                }
                findings.add(Finding.incompatible(
                        modId,
                        config,
                        mixinName,
                        "its method " + member + " is also a method " + host.hostName() + "'s patch added to "
                                + targetName + ", with other code; one class cannot hold both",
                        false));
            }
            if (!ownChanges.isEmpty()) {
                changes.put(mixinName, List.copyOf(ownChanges));
            }
            return findings;
        }

        HostAddedMembers build() {
            return changes.isEmpty() ? NONE : new HostAddedMembers(Map.copyOf(changes));
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

    /** The mixin {@code mixin} as Mixin reads it: without the methods the patched class has with the same code. */
    byte[] apply(String mixin, byte[] bytes) {
        List<Change> own = changes.getOrDefault(mixin, List.of());
        if (own.isEmpty()) {
            return bytes;
        }
        ClassFile classFile = ClassFile.of();
        return classFile.transformClass(classFile.parse(bytes), (builder, element) -> {
            if (element instanceof MethodModel method
                    && own.stream()
                            .anyMatch(candidate -> method.methodName().equalsString(candidate.name())
                                    && method.methodType().equalsString(candidate.descriptor()))) {
                return;
            }
            builder.with(element);
        });
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
            return isEquivalentGetter(mixinCode, ownCode, mixinName, targetName);
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

    private static boolean isEquivalentGetter(List<Instruction> a, List<Instruction> b, String aOwner, String bOwner) {
        return isUnmodifiableCollectionGetter(a, b, aOwner, bOwner)
                || isUnmodifiableCollectionGetter(b, a, bOwner, aOwner);
    }

    private static boolean isUnmodifiableCollectionGetter(
            List<Instruction> wrapped, List<Instruction> direct, String wrappedOwner, String directOwner) {
        if (wrapped.size() != 4 || direct.size() != 3) {
            return false;
        }
        if (!(wrapped.get(0) instanceof LoadInstruction wl
                && wl.slot() == 0
                && direct.get(0) instanceof LoadInstruction dl
                && dl.slot() == 0)) {
            return false;
        }
        if (!(wrapped.get(1) instanceof FieldInstruction wf
                && wf.opcode() == Opcode.GETFIELD
                && direct.get(1) instanceof FieldInstruction df
                && df.opcode() == Opcode.GETFIELD)) {
            return false;
        }
        if (!sameInstruction(wf, df, wrappedOwner, directOwner)) {
            return false;
        }
        if (!(wrapped.get(2) instanceof InvokeInstruction call && call.opcode() == Opcode.INVOKESTATIC)) {
            return false;
        }
        String callOwner = call.owner().asInternalName();
        String callName = call.name().stringValue();
        boolean isUnmodifiableWrapper = (callOwner.equals("java/util/Collections")
                        && (callName.equals("unmodifiableList")
                                || callName.equals("unmodifiableSet")
                                || callName.equals("unmodifiableMap")
                                || callName.equals("unmodifiableCollection")))
                || ((callOwner.equals("java/util/List")
                                || callOwner.equals("java/util/Set")
                                || callOwner.equals("java/util/Map"))
                        && callName.equals("copyOf"));
        if (!isUnmodifiableWrapper) {
            return false;
        }
        return wrapped.get(3) instanceof ReturnInstruction && direct.get(2) instanceof ReturnInstruction;
    }

    private static boolean isAttributeSupplierBuilder(
            String targetName, String field, String type, String patchedType) {
        return (targetName.equals("net/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder")
                        || targetName.endsWith("/AttributeSupplier$Builder")
                        || targetName.equals("AttributeSupplier$Builder"))
                && field.equals("builder")
                && type.contains("ImmutableMap$Builder")
                && patchedType.equals("Ljava/util/Map;");
    }

    private static boolean isAttributeBuilderBridgeMethod(MethodModel method, String targetName, String field) {
        if (!targetName.endsWith("AttributeSupplier$Builder") || !field.equals("builder")) {
            return false;
        }
        List<Instruction> code = instructions(method);
        return code.size() == 4
                && isThis(code.get(0))
                && code.get(1) instanceof FieldInstruction get
                && get.opcode() == Opcode.GETFIELD
                && get.name().stringValue().equals(field)
                && code.get(2) instanceof InvokeInstruction call
                && call.name().stringValue().equals("wrap")
                && call.owner().asInternalName().endsWith("AttributeBuilderBridge")
                && code.get(3) instanceof ReturnInstruction;
    }

    private static List<Instruction> instructions(MethodModel method) {
        return method.code()
                .map(code -> code.elementList().stream()
                        .filter(Instruction.class::isInstance)
                        .map(Instruction.class::cast)
                        .toList())
                .orElse(List.of());
    }
}

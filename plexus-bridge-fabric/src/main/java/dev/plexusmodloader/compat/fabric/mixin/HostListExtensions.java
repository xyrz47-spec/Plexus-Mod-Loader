package dev.plexusmodloader.compat.fabric.mixin;

import dev.plexusmodloader.compat.bridge.InjectionPointReplacement;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Static {@code List} fields whose reads a host's patch replaced with a call that returns the field's elements followed
 * by the host's own ({@link InjectionPointReplacement.Placement#FIELD_THEN_HOST_ENTRIES}). MinecraftForge 26.3-66.1.0
 * calls {@code RegistryDataLoader.getWorldAndDimensionRegistries()} ({@code WORLD_REGISTRIES} followed by {@code
 * DIMENSION_REGISTRIES}) where the vanilla data generators read {@code WORLD_REGISTRIES}, which Fabric API's registry
 * sync redirects to its own world registries ({@code DynamicRegistries.getWorldRegistries()}: vanilla's and the ones
 * mods add).
 *
 * <p>A {@code @Redirect} of such a read, whose static handler takes nothing and returns a {@code List}, moves to the
 * host's call through a bridge ({@link #bridgeMethod}): it makes the host's call, calls the handler, and returns the
 * handler's list followed by the host's own elements ({@link ExtendedHostList}). Before the launch it is checked that
 * the host's method is static, takes nothing, returns a {@code List} or a {@code Stream} and reads the field; at run
 * time, that its result starts with the field's elements. Other injectors there are not adapted.
 *
 * <p>Thread: any; no state.
 */
final class HostListExtensions {

    static final String PREFIX = "plexus$hostList$";

    /**
     * The {@code replacement} of a {@code HostList} whose {@code replaced} is a host-filled static list field that a
     * {@code @ModifyArg} handler's list replaces ({@link #hostFilledArgument}).
     */
    static final String HOST_FILLED_ARGUMENT = "plexus:host-filled-argument";

    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";

    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String LIST = "Ljava/util/List;";
    private static final Set<String> RESULTS = Set.of(LIST, "Ljava/util/stream/Stream;");
    private static final ClassDesc EXTENDED_HOST_LIST = ClassDesc.of(ExtendedHostList.class.getName());

    private HostListExtensions() {}

    /**
     * Whether the injection {@code type} of {@code handler} at {@code kind} {@code target} fits {@code replacement}'s
     * bridge: a {@code @Redirect} of the field, a static handler {@code ()Ljava/util/List;}, the host's call in one of
     * {@code patchedMethods}, and the host's method of that shape reading the field in {@code patchedClass}.
     */
    static boolean fits(
            MethodModel handler,
            String type,
            String kind,
            String target,
            InjectionPointReplacement replacement,
            List<MethodModel> patchedMethods,
            Function<String, Optional<ClassModel>> patchedClass) {
        if (replacement.placement() != InjectionPointReplacement.Placement.FIELD_THEN_HOST_ENTRIES
                || !type.equals(REDIRECT)
                || !kind.equals("FIELD")
                || !target.equals(replacement.replaced())
                || !handler.flags().has(AccessFlag.STATIC)
                || !handler.methodType().equalsString("()" + LIST)) {
            return false;
        }
        Optional<Field> field = Field.parse(replacement.replaced());
        Optional<Call> call = Call.parse(replacement.replacement());
        if (field.isEmpty()
                || call.isEmpty()
                || !field.get().type().equals(LIST)
                || !field.get().owner().equals(call.get().owner())) {
            return false;
        }
        boolean called = patchedMethods.stream()
                .flatMap(method -> method.code().stream())
                .flatMap(code -> code.elementStream())
                .anyMatch(element -> HostPatchAdjustments.matches(element, replacement.replacement()));
        return called && readsField(patchedClass.apply(call.get().owner()), call.get(), field.get());
    }

    /** Whether {@code owner} has the static method {@code call} that reads {@code field}. */
    private static boolean readsField(Optional<ClassModel> owner, Call call, Field field) {
        if (owner.isEmpty()) {
            return false;
        }
        for (MethodModel method : owner.get().methods()) {
            if (!method.methodName().equalsString(call.name())
                    || !method.methodType().equalsString(call.descriptor())
                    || !method.flags().has(AccessFlag.STATIC)
                    || method.code().isEmpty()) {
                continue;
            }
            for (CodeElement element : method.code().get()) {
                if (element instanceof FieldInstruction read
                        && read.opcode() == Opcode.GETSTATIC
                        && read.owner().asInternalName().equals(field.owner())
                        && read.name().equalsString(field.name())
                        && read.type().equalsString(field.type())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds the bridge of {@code handler} to {@code mixin}: {@code redirect} (the handler's, with its other injection
     * points already moved) at the host's call {@code replacement} in place of the field {@code replaced}; the body
     * returns {@link ExtendedHostList#extend} of the host's result, the field and the handler's list. The handler stays
     * as a plain method.
     */
    static void bridgeMethod(
            ClassBuilder builder,
            ClassModel mixin,
            MethodModel handler,
            Annotation redirect,
            String replaced,
            String replacement) {
        Field field = Field.parse(replaced).orElseThrow();
        Call call = Call.parse(replacement).orElseThrow();
        ClassDesc result = ClassDesc.ofDescriptor(call.descriptor().substring(2));
        ClassDesc list = ClassDesc.ofDescriptor(LIST);
        List<AnnotationElement> elements = new ArrayList<>();
        for (AnnotationElement element : redirect.elements()) {
            if (!element.name().equalsString("at")) {
                elements.add(element);
            }
        }
        elements.add(AnnotationElement.ofAnnotation(
                "at",
                Annotation.of(
                        ClassDesc.ofDescriptor(AT),
                        AnnotationElement.ofString("value", "INVOKE"),
                        AnnotationElement.ofString("target", replacement))));
        Annotation moved = Annotation.of(redirect.classSymbol(), elements);
        ClassDesc owner = mixin.thisClass().asSymbol();
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        String name = handler.methodName().stringValue();
        builder.withMethod(
                PREFIX + name,
                MethodTypeDesc.of(result),
                handler.flags().flagsMask() | ClassFile.ACC_STATIC,
                method -> {
                    method.with(RuntimeVisibleAnnotationsAttribute.of(moved));
                    method.withCode(code -> {
                        code.invokestatic(
                                ClassDesc.ofInternalName(call.owner()), call.name(), MethodTypeDesc.of(result));
                        code.getstatic(ClassDesc.ofInternalName(field.owner()), field.name(), list);
                        code.invokestatic(owner, name, MethodTypeDesc.of(list), isInterface);
                        code.invokestatic(
                                EXTENDED_HOST_LIST,
                                "extend",
                                MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object, list, list));
                        code.checkcast(result);
                        code.areturn();
                    });
                });
    }

    /**
     * The static {@code List} field, in Mixin's notation, that {@code patchedMethods} read and whose class's static
     * initializer takes from the host's own code ({@code hostOwned}), when {@code handler} is a static {@code
     * @ModifyArg} handler {@code (List)List}: MinecraftForge 26.2-65.1.5 sets {@code
     * RegistryDataLoader.WORLDGEN_REGISTRIES} to a view of its list ({@code
     * DataPackRegistriesHooks.grabWorldgenRegistries}) and adds its own registries to that list later.
     */
    static Optional<String> hostFilledArgument(
            MethodModel handler,
            String type,
            List<MethodModel> patchedMethods,
            Predicate<String> hostOwned,
            Function<String, Optional<ClassModel>> patchedClass) {
        if (!type.equals(MODIFY_ARG)
                || !handler.flags().has(AccessFlag.STATIC)
                || !handler.methodType().equalsString("(" + LIST + ")" + LIST)) {
            return Optional.empty();
        }
        for (MethodModel method : patchedMethods) {
            if (method.code().isEmpty()) {
                continue;
            }
            for (CodeElement element : method.code().get()) {
                if (element instanceof FieldInstruction read
                        && read.opcode() == Opcode.GETSTATIC
                        && read.type().equalsString(LIST)
                        && hostFilled(
                                patchedClass.apply(read.owner().asInternalName()),
                                read.name().stringValue(),
                                hostOwned)) {
                    return Optional.of("L" + read.owner().asInternalName() + ";"
                            + read.name().stringValue() + ":" + LIST);
                }
            }
        }
        return Optional.empty();
    }

    /** Whether {@code owner}'s static initializer stores the result of a host method's call in its field {@code name}. */
    private static boolean hostFilled(Optional<ClassModel> owner, String name, Predicate<String> hostOwned) {
        if (owner.isEmpty()) {
            return false;
        }
        for (MethodModel method : owner.get().methods()) {
            if (!method.methodName().equalsString(ConstantDescs.CLASS_INIT_NAME)
                    || method.code().isEmpty()) {
                continue;
            }
            Instruction previous = null;
            for (CodeElement element : method.code().get()) {
                if (!(element instanceof Instruction instruction)) {
                    continue;
                }
                if (instruction instanceof FieldInstruction store
                        && store.opcode() == Opcode.PUTSTATIC
                        && store.owner().equals(owner.get().thisClass())
                        && store.name().equalsString(name)
                        && previous instanceof InvokeInstruction call
                        && call.opcode() == Opcode.INVOKESTATIC
                        && hostOwned.test(call.owner().asInternalName())) {
                    return true;
                }
                previous = instruction;
            }
        }
        return false;
    }

    /**
     * Adds the bridge of {@code handler} to {@code mixin}: {@code modifyArg} (the handler's) on a method {@code
     * (List)List} whose body returns {@link ExtendedHostList#withHostEntries} of the handler's list, the argument and
     * the host-filled {@code field}. The handler stays as a plain method.
     */
    static void modifyArgBridge(
            ClassBuilder builder, ClassModel mixin, MethodModel handler, Annotation modifyArg, String field) {
        Field parsed = Field.parse(field).orElseThrow();
        ClassDesc list = ClassDesc.ofDescriptor(LIST);
        MethodTypeDesc listToList = MethodTypeDesc.of(list, list);
        ClassDesc owner = mixin.thisClass().asSymbol();
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        String name = handler.methodName().stringValue();
        builder.withMethod(PREFIX + name, listToList, handler.flags().flagsMask(), method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(modifyArg));
            method.withCode(code -> {
                code.aload(0);
                code.invokestatic(owner, name, listToList, isInterface);
                code.aload(0);
                code.getstatic(ClassDesc.ofInternalName(parsed.owner()), parsed.name(), list);
                code.invokestatic(EXTENDED_HOST_LIST, "withHostEntries", MethodTypeDesc.of(list, list, list, list));
                code.areturn();
            });
        });
    }

    /** A field in Mixin's notation {@code Lowner;name:type}. */
    private record Field(String owner, String name, String type) {
        static Optional<Field> parse(String target) {
            int end = target.indexOf(';');
            int colon = target.indexOf(':');
            if (!target.startsWith("L") || end < 0 || colon < end) {
                return Optional.empty();
            }
            return Optional.of(
                    new Field(target.substring(1, end), target.substring(end + 1, colon), target.substring(colon + 1)));
        }
    }

    /** A call without parameters in Mixin's notation {@code Lowner;name()result}, returning a list or a stream. */
    private record Call(String owner, String name, String descriptor) {
        static Optional<Call> parse(String target) {
            int end = target.indexOf(';');
            int open = target.indexOf("()");
            if (!target.startsWith("L") || end < 0 || open < end) {
                return Optional.empty();
            }
            String descriptor = target.substring(open);
            if (!RESULTS.contains(descriptor.substring(2))) {
                return Optional.empty();
            }
            return Optional.of(new Call(target.substring(1, end), target.substring(end + 1, open), descriptor));
        }
    }
}

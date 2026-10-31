package dev.plexusmodloader.compat.fabric.mixin;

import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@code @Inject} after a hopper's container lookup that captures the container with {@code @Local}, on NeoForge
 * (26.2.0.93 and 26.3.0.98, checked with {@code javap}). Vanilla's {@code HopperBlockEntity.ejectItems} and {@code
 * suckInItems} look up a {@code Container} ({@code getAttachedContainer}, {@code getSourceContainer}); NeoForge looks up
 * a {@code ContainerOrHandler} there ({@code getContainerOrHandlerAt}, {@code getSourceContainerOrHandler}): the
 * container, or else NeoForge's item handler of the block, and moves items through whichever it found. Fabric API's
 * transfer API ({@code hookInsert}, {@code hookExtract}) returns when the container is there, and otherwise moves items
 * through the block's Fabric {@code Storage} and ends the method.
 *
 * <p>The bridge ({@link #bridgeMethod}) injects after NeoForge's lookup, captures the {@code ContainerOrHandler}, and,
 * unless NeoForge found its own item handler, calls the handler with the container NeoForge found (or none). So a block
 * with a vanilla container or a NeoForge item handler moves items as NeoForge does, and a block with neither reaches
 * Fabric's storage as on vanilla. A block with both a NeoForge item handler and a Fabric storage uses NeoForge's.
 *
 * <p>Thread: any; no state.
 */
final class HostHopperTransfer {

    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String SUGAR_PACKAGE = "Lcom/llamalad7/mixinextras/sugar/";
    private static final ClassDesc CONTAINER = ClassDesc.of("net.minecraft.world.Container");
    private static final ClassDesc CONTAINER_OR_HANDLER =
            ClassDesc.of("net.neoforged.neoforge.transfer.item.ContainerOrHandler");
    private static final ClassDesc RESOURCE_HANDLER = ClassDesc.of("net.neoforged.neoforge.transfer.ResourceHandler");
    private static final ClassDesc LOCAL = ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/sugar/Local;");

    private HostHopperTransfer() {}

    /**
     * Whether {@code handler}, a {@code type} injection at {@code kind} {@code replaced}, which the host replaced with
     * {@code replacement}, is one this class bridges: an {@code @Inject} at {@code INVOKE_ASSIGN} of a call returning
     * {@code Container} that the host replaced with one returning {@code ContainerOrHandler}, whose handler returns
     * nothing and takes that container last, as its only MixinExtras sugar parameter.
     */
    static boolean fits(MethodModel handler, String type, String kind, String replaced, String replacement) {
        if (!type.equals(INJECT) || !kind.equals("INVOKE_ASSIGN") || !covers(replaced, replacement)) {
            return false;
        }
        MethodTypeDesc handlerType = handler.methodTypeSymbol();
        int last = handlerType.parameterCount() - 1;
        if (last < 0
                || !handlerType.returnType().equals(ConstantDescs.CD_void)
                || !handlerType.parameterType(last).equals(CONTAINER)
                || handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                        .isPresent()) {
            return false;
        }
        List<List<Annotation>> annotations = handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                .map(attribute -> attribute.parameterAnnotations())
                .orElse(List.of());
        if (annotations.size() != handlerType.parameterCount()) {
            return false;
        }
        for (int index = 0; index < last; index++) {
            if (!annotations.get(index).isEmpty()) {
                return false;
            }
        }
        List<Annotation> local = annotations.get(last);
        return local.size() == 1 && local.getFirst().className().stringValue().startsWith(SUGAR_PACKAGE + "Local");
    }

    /** Whether a call {@code replaced} the host replaced with {@code replacement} is a container lookup this bridges. */
    static boolean covers(String replaced, String replacement) {
        return replaced.endsWith(")" + CONTAINER.descriptorString())
                && replacement.endsWith(")" + CONTAINER_OR_HANDLER.descriptorString());
    }

    /**
     * Adds the bridge {@code plexus$hostCall$<handler>}: the handler's parameters with a {@code ContainerOrHandler} last,
     * captured by type with {@code @Local}, carrying {@code inject} (the handler's, which the handler loses) retargeted
     * at {@code replacement}.
     */
    static void bridgeMethod(
            ClassBuilder builder,
            ClassModel mixin,
            MethodModel handler,
            Annotation inject,
            String replaced,
            String replacement) {
        MethodTypeDesc handlerType = handler.methodTypeSymbol();
        int last = handlerType.parameterCount() - 1;
        MethodTypeDesc bridgeType = handlerType.changeParameterType(last, CONTAINER_OR_HANDLER);
        int flags = handler.flags().flagsMask();
        boolean isStatic = (flags & ClassFile.ACC_STATIC) != 0;
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        ClassDesc owner = mixin.thisClass().asSymbol();
        String name = handler.methodName().stringValue();
        int[] slots = new int[handlerType.parameterCount()];
        int next = isStatic ? 0 : 1;
        for (int index = 0; index < slots.length; index++) {
            slots[index] = next;
            next += TypeKind.from(handlerType.parameterType(index)).slotSize();
        }
        List<List<Annotation>> parameterAnnotations = new ArrayList<>();
        for (int index = 0; index < last; index++) {
            parameterAnnotations.add(List.of());
        }
        parameterAnnotations.add(List.of(Annotation.of(LOCAL)));
        builder.withMethod(HostReplacedCalls.PREFIX + name, bridgeType, flags, method -> {
            method.with(
                    RuntimeVisibleAnnotationsAttribute.of(HostReplacedCalls.retargeted(inject, replaced, replacement)));
            method.with(RuntimeInvisibleParameterAnnotationsAttribute.of(parameterAnnotations));
            method.withCode(code -> {
                Label skip = code.newLabel();
                code.aload(slots[last]);
                code.invokevirtual(CONTAINER_OR_HANDLER, "itemHandler", MethodTypeDesc.of(RESOURCE_HANDLER));
                code.ifnonnull(skip);
                if (!isStatic) {
                    code.aload(0);
                }
                for (int index = 0; index < last; index++) {
                    code.loadLocal(TypeKind.from(handlerType.parameterType(index)), slots[index]);
                }
                code.aload(slots[last]);
                code.invokevirtual(CONTAINER_OR_HANDLER, "container", MethodTypeDesc.of(CONTAINER));
                if (isStatic) {
                    code.invokestatic(owner, name, handlerType, isInterface);
                } else {
                    code.invokespecial(owner, name, handlerType, isInterface);
                }
                code.labelBinding(skip);
                code.return_();
            });
        });
    }
}

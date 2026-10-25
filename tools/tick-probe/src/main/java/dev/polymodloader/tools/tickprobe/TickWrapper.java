package dev.polymodloader.tools.tickprobe;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Optional;

/**
 * Wraps one method so each call is timed: the original body moves to a private method {@code
 * <name>$tickProbe}, and a new method with the original name and flags calls it between two clock
 * reads. The original code is copied unchanged and the new method has no branches, so no stack map
 * frame has to be computed and no other class has to be loaded.
 */
public final class TickWrapper {

    /** Suffix of the private method that keeps the original body. */
    public static final String RENAMED_SUFFIX = "$tickProbe";

    // By name: loading TickRecorder here could happen before the agent puts it on the boot class path.
    private static final ClassDesc RECORDER = ClassDesc.of("dev.polymodloader.tools.tickprobe.TickRecorder");
    private static final ClassDesc SYSTEM = ClassDesc.of("java.lang.System");

    private TickWrapper() {}

    /**
     * Returns {@code classFile} with {@code methodName}{@code descriptor} wrapped, or empty when the
     * class has no such non-static method with a body.
     */
    public static Optional<byte[]> wrap(byte[] classFile, String methodName, String descriptor) {
        ClassFile files = ClassFile.of();
        ClassModel model = files.parse(classFile);
        Optional<MethodModel> target = model.methods().stream()
                .filter(method -> method.methodName().equalsString(methodName)
                        && method.methodType().equalsString(descriptor)
                        && !method.flags().has(AccessFlag.STATIC)
                        && method.code().isPresent())
                .findFirst();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        MethodModel original = target.get();
        MethodTypeDesc type = MethodTypeDesc.ofDescriptor(descriptor);
        ClassDesc owner = model.thisClass().asSymbol();
        String renamed = methodName + RENAMED_SUFFIX;
        int privateFlags = (original.flags().flagsMask()
                        & ~(ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_PRIVATE))
                | ClassFile.ACC_PRIVATE
                | ClassFile.ACC_SYNTHETIC;
        byte[] wrapped = files.transformClass(model, (builder, element) -> {
            if (element != original) {
                builder.with(element);
                return;
            }
            // Copies the body and attributes; the flags element is left out so privateFlags apply.
            builder.withMethod(renamed, type, privateFlags, method -> original.forEach(part -> {
                if (!(part instanceof java.lang.classfile.AccessFlags)) {
                    method.with(part);
                }
            }));
            builder.withMethodBody(methodName, type, original.flags().flagsMask(), code -> {
                int start = code.allocateLocal(java.lang.classfile.TypeKind.LONG);
                code.invokestatic(SYSTEM, "nanoTime", MethodTypeDesc.of(ConstantDescs.CD_long));
                code.lstore(start);
                code.aload(0);
                int slot = 1;
                for (ClassDesc parameter : type.parameterList()) {
                    java.lang.classfile.TypeKind kind = java.lang.classfile.TypeKind.from(parameter);
                    code.loadLocal(kind, slot);
                    slot += kind.slotSize();
                }
                code.invokespecial(owner, renamed, type);
                java.lang.classfile.TypeKind returned = java.lang.classfile.TypeKind.from(type.returnType());
                int result = -1;
                if (returned != java.lang.classfile.TypeKind.VOID) {
                    result = code.allocateLocal(returned);
                    code.storeLocal(returned, result);
                }
                code.lload(start);
                code.invokestatic(RECORDER, "record", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_long));
                if (returned != java.lang.classfile.TypeKind.VOID) {
                    code.loadLocal(returned, result);
                }
                code.return_(returned);
            });
        });
        return Optional.of(wrapped);
    }
}

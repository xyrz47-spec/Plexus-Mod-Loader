package dev.plexusmodloader.internal.transform.engine;

import dev.plexusmodloader.internal.transform.AccessWidening;
import dev.plexusmodloader.internal.transform.AccessWidening.Access;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Applies {@link AccessWidening}s to a class: only access flags change, plus {@code invokespecial} calls
 * to methods made extendable, so code shape, stack and frames stay as they were. Widenings of one
 * target are joined first, so their order does not matter.
 */
final class AccessWideningApplier {

    private static final int VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;

    private AccessWideningApplier() {}

    /** Returns why {@code widening}'s target is not in {@code classNode}, or empty when it is. */
    static Optional<String> checkTarget(AccessWidening widening, ClassNode classNode) {
        return switch (widening.kind()) {
            case CLASS -> Optional.empty();
            case METHOD ->
                findMethod(classNode, widening.name(), widening.descriptor()).isPresent()
                        ? Optional.empty()
                        : Optional.of("no method " + widening.name() + widening.descriptor() + " in " + classNode.name
                                + sameName(classNode, widening.name(), true));
            case FIELD ->
                findField(classNode, widening.name(), widening.descriptor()).isPresent()
                        ? Optional.empty()
                        : Optional.of("no field " + widening.name() + ":" + widening.descriptor() + " in "
                                + classNode.name + sameName(classNode, widening.name(), false));
        };
    }

    /** Applies {@code widenings}, all of which target {@code classNode} and were checked by {@link #checkTarget}. */
    static void apply(ClassNode classNode, List<AccessWidening> widenings) {
        Set<Access> classAccess = EnumSet.noneOf(Access.class);
        Map<String, Set<Access>> methods = new LinkedHashMap<>();
        Map<String, Set<Access>> fields = new LinkedHashMap<>();
        for (AccessWidening widening : widenings) {
            switch (widening.kind()) {
                case CLASS -> classAccess.addAll(widening.access());
                case METHOD ->
                    methods.computeIfAbsent(
                                    widening.name() + widening.descriptor(), key -> EnumSet.noneOf(Access.class))
                            .addAll(widening.access());
                case FIELD ->
                    fields.computeIfAbsent(
                                    widening.name() + ":" + widening.descriptor(), key -> EnumSet.noneOf(Access.class))
                            .addAll(widening.access());
            }
        }
        if (!classAccess.isEmpty()) {
            widenClass(classNode, classAccess);
        }
        boolean isInterface = (classNode.access & Opcodes.ACC_INTERFACE) != 0;
        for (MethodNode method : classNode.methods) {
            Set<Access> access = methods.get(method.name + method.desc);
            if (access != null) {
                widenMethod(classNode, method, access, isInterface);
            }
        }
        for (FieldNode field : classNode.fields) {
            Set<Access> access = fields.get(field.name + ":" + field.desc);
            if (access != null) {
                if (access.contains(Access.ACCESSIBLE)) {
                    field.access = makePublic(field.access);
                }
                if (access.contains(Access.MUTABLE) && !isInterface) {
                    // Interface fields must stay final (JVMS 4.5).
                    field.access &= ~Opcodes.ACC_FINAL;
                }
            }
        }
    }

    private static void widenClass(ClassNode classNode, Set<Access> access) {
        boolean extendable = access.contains(Access.EXTENDABLE);
        classNode.access = makePublic(classNode.access);
        if (extendable) {
            classNode.access &= ~Opcodes.ACC_FINAL;
            classNode.permittedSubclasses = null;
        }
        // A nested class's access as seen by javac and reflection lives in its InnerClasses entry.
        for (InnerClassNode inner : classNode.innerClasses) {
            if (inner.name.equals(classNode.name)) {
                inner.access = makePublic(inner.access);
                if (extendable) {
                    inner.access &= ~Opcodes.ACC_FINAL;
                }
            }
        }
    }

    private static void widenMethod(ClassNode owner, MethodNode method, Set<Access> access, boolean isInterface) {
        boolean wasPrivate = (method.access & Opcodes.ACC_PRIVATE) != 0;
        boolean extendable = access.contains(Access.EXTENDABLE);
        if (access.contains(Access.ACCESSIBLE)) {
            method.access = makePublic(method.access);
            boolean canBeFinal =
                    !isInterface && (method.access & Opcodes.ACC_STATIC) == 0 && !method.name.equals("<init>");
            if (wasPrivate && !extendable && canBeFinal) {
                // Still unoverridable, as it was while private.
                method.access |= Opcodes.ACC_FINAL;
            }
        }
        if (extendable) {
            method.access &= ~Opcodes.ACC_FINAL;
            if ((method.access & Opcodes.ACC_PUBLIC) == 0) {
                method.access = (method.access & ~VISIBILITY) | Opcodes.ACC_PROTECTED;
            }
            if (method.name.equals("<init>")) {
                // Constructors are not inherited; subclasses call them with invokespecial, as the owner does.
                return;
            }
            // invokespecial would keep calling this exact method; overrides must take effect.
            int virtualCall = isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
            for (MethodNode caller : owner.methods) {
                for (AbstractInsnNode instruction : caller.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKESPECIAL
                            && call.owner.equals(owner.name)
                            && call.name.equals(method.name)
                            && call.desc.equals(method.desc)) {
                        call.setOpcode(virtualCall);
                    }
                }
            }
        }
    }

    private static int makePublic(int access) {
        return (access & ~VISIBILITY) | Opcodes.ACC_PUBLIC;
    }

    private static Optional<MethodNode> findMethod(ClassNode classNode, String name, String descriptor) {
        return classNode.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst();
    }

    private static Optional<FieldNode> findField(ClassNode classNode, String name, String descriptor) {
        return classNode.fields.stream()
                .filter(field -> field.name.equals(name) && field.desc.equals(descriptor))
                .findFirst();
    }

    private static String sameName(ClassNode classNode, String name, boolean method) {
        List<String> descriptors = method
                ? classNode.methods.stream()
                        .filter(candidate -> candidate.name.equals(name))
                        .map(candidate -> candidate.desc)
                        .toList()
                : classNode.fields.stream()
                        .filter(candidate -> candidate.name.equals(name))
                        .map(candidate -> candidate.desc)
                        .toList();
        return descriptors.isEmpty()
                ? " (nothing with that name exists)"
                : " (members with that name have descriptors " + descriptors + ")";
    }
}

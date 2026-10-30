package dev.polymodloader.compat.fabric.mixin;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Injections of one mixin class that work together through the mixin's own state (ADR-0026 32): when a host's patch
 * stops one of them, the others that read what it sets must not run alone. Fabric API's {@code RegistryDataLoaderMixin}
 * binds a {@code ScopedValue} around {@code RegistryDataLoader.load} in one injection and reads it in another; NeoForge
 * changed the method the first targets, and the second then threw {@code NoSuchElementException: ScopedValue not
 * bound} on every world load.
 *
 * <p>A field of the mixin couples two handlers when a handler that does not run writes it and a handler that runs
 * reads it; a {@code ScopedValue} or {@code ThreadLocal} field couples them whenever both use it (binding and reading
 * look the same). The code a handler reaches through the mixin's own methods and lambdas counts as the handler's.
 * Fields written only by the class initialiser (constants, loggers) couple nothing. The result is closed: a handler
 * left out for coupling can leave out others.
 *
 * <p>Thread: any; no state.
 */
final class CoupledInjections {

    private static final Set<String> STATE_HOLDERS =
            Set.of("Ljava/lang/ScopedValue;", "Ljava/lang/ThreadLocal;", "Ljava/lang/InheritableThreadLocal;");

    private CoupledInjections() {}

    /**
     * The running injection handlers of {@code mixin} (keys {@code name + descriptor}) that read state only handlers in
     * {@code notRunning} set, each with the reason.
     *
     * @param handlers every injection handler of the mixin (keys)
     * @param notRunning the handlers that do not run in the host's patched game
     */
    static Map<String, String> leftOut(ClassModel mixin, Set<String> handlers, Set<String> notRunning) {
        Map<String, String> leftOut = new LinkedHashMap<>();
        if (notRunning.isEmpty()) {
            return leftOut;
        }
        String owner = mixin.thisClass().asInternalName();
        Map<String, MethodModel> methods = new LinkedHashMap<>();
        for (MethodModel method : mixin.methods()) {
            methods.put(key(method), method);
        }
        Set<String> stopped = new HashSet<>(notRunning);
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, String> writers = new LinkedHashMap<>();
            for (String handler : stopped) {
                for (Use use : uses(owner, methods, handler)) {
                    if (use.write || STATE_HOLDERS.contains(use.type)) {
                        writers.putIfAbsent(use.field, handler);
                    }
                }
            }
            for (String handler : handlers) {
                if (stopped.contains(handler)) {
                    continue;
                }
                for (Use use : uses(owner, methods, handler)) {
                    String writer = writers.get(use.field);
                    if (writer != null && (!use.write || STATE_HOLDERS.contains(use.type))) {
                        leftOut.put(
                                handler,
                                "its handler " + name(handler) + " uses the mixin's " + use.field + ", which "
                                        + name(writer) + " sets, and that injection does not run; so this injection"
                                        + " does not run either");
                        stopped.add(handler);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return leftOut;
    }

    /**
     * The running injection handlers of {@code mixin} (keys {@code name + descriptor}) that use a type of {@code
     * leftOutTypes} (ADR-0026 39): an interface a left-out mixin adds to its target, or a left-out accessor mixin. Such a
     * handler casts the game's object to a type the object does not have in the host's game ({@code
     * ClassCastException}), so it must not run. A use is a cast, an {@code instanceof} or a call on the type, in the
     * handler or the mixin's own methods and lambdas it reaches.
     *
     * @param notRunning the handlers that do not run anyway
     * @param leftOutTypes each such type (internal name) to how it comes to be missing, for the reason
     */
    static Map<String, String> usingLeftOutTypes(
            ClassModel mixin, Set<String> handlers, Set<String> notRunning, Map<String, String> leftOutTypes) {
        Map<String, String> leftOut = new LinkedHashMap<>();
        if (leftOutTypes.isEmpty()) {
            return leftOut;
        }
        String owner = mixin.thisClass().asInternalName();
        Map<String, MethodModel> methods = methods(mixin);
        for (String handler : handlers) {
            if (notRunning.contains(handler)) {
                continue;
            }
            Optional<String> used = Optional.empty();
            for (MethodModel method : reachable(owner, methods, handler)) {
                used = usedType(method, leftOutTypes.keySet());
                if (used.isPresent()) {
                    break;
                }
            }
            used.ifPresent(type -> leftOut.put(
                    handler,
                    "its handler " + name(handler) + " uses " + type + ", " + leftOutTypes.get(type)
                            + ", and that mixin is left out; so this injection does not run"));
        }
        return leftOut;
    }

    /**
     * A type of {@code types} that {@code method} casts to or calls on without checking it with {@code instanceof} in
     * the same method: a checked use does not throw when the object lacks the type.
     */
    static Optional<String> usedType(MethodModel method, Set<String> types) {
        Set<String> checked = new HashSet<>();
        Set<String> used = new java.util.LinkedHashSet<>();
        for (CodeElement element : method.code().orElseThrow()) {
            if (element instanceof TypeCheckInstruction check) {
                String type = check.type().asInternalName();
                if (check.opcode() == Opcode.INSTANCEOF) {
                    checked.add(type);
                } else if (types.contains(type)) {
                    used.add(type);
                }
            } else if (element instanceof InvokeInstruction call
                    && types.contains(call.owner().asInternalName())) {
                used.add(call.owner().asInternalName());
            }
        }
        used.removeAll(checked);
        return used.stream().findFirst();
    }

    private static Map<String, MethodModel> methods(ClassModel mixin) {
        Map<String, MethodModel> methods = new LinkedHashMap<>();
        for (MethodModel method : mixin.methods()) {
            methods.put(key(method), method);
        }
        return methods;
    }

    /** {@code handler} and the mixin's own methods and lambdas it reaches, each with a body. */
    private static List<MethodModel> reachable(String owner, Map<String, MethodModel> methods, String handler) {
        List<MethodModel> reached = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(handler);
        while (!pending.isEmpty()) {
            String current = pending.poll();
            MethodModel method = methods.get(current);
            if (method == null || !seen.add(current) || method.code().isEmpty()) {
                continue;
            }
            reached.add(method);
            for (CodeElement element : method.code().orElseThrow()) {
                if (element instanceof InvokeInstruction call
                        && call.owner().asInternalName().equals(owner)) {
                    pending.add(call.name().stringValue() + call.type().stringValue());
                } else if (element instanceof InvokeDynamicInstruction indy) {
                    for (ConstantDesc argument : indy.bootstrapArgs()) {
                        if (argument instanceof DirectMethodHandleDesc handle
                                && handle.owner().descriptorString().equals("L" + owner + ";")) {
                            pending.add(handle.methodName() + handle.lookupDescriptor());
                        }
                    }
                }
            }
        }
        return reached;
    }

    /** The mixin's own fields {@code handler} uses, through the mixin's own methods and lambdas too. */
    private static Set<Use> uses(String owner, Map<String, MethodModel> methods, String handler) {
        Set<Use> uses = new HashSet<>();
        for (MethodModel method : reachable(owner, methods, handler)) {
            for (CodeElement element : method.code().orElseThrow()) {
                if (element instanceof FieldInstruction field
                        && field.owner().asInternalName().equals(owner)) {
                    boolean write = field.opcode() == Opcode.PUTFIELD || field.opcode() == Opcode.PUTSTATIC;
                    uses.add(new Use(field.name().stringValue(), field.type().stringValue(), write));
                }
            }
        }
        return uses;
    }

    static String key(MethodModel method) {
        return method.methodName().stringValue() + method.methodType().stringValue();
    }

    private static String name(String key) {
        return key.substring(0, key.indexOf('('));
    }

    private record Use(String field, String type, boolean write) {}
}

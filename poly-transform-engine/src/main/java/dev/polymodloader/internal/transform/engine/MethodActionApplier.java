package dev.polymodloader.internal.transform.engine;

import dev.polymodloader.internal.transform.HeadHookResult;
import dev.polymodloader.internal.transform.InjectStaticCallAtHead;
import dev.polymodloader.internal.transform.InvokeHookAtCall;
import dev.polymodloader.internal.transform.InvokeHookAtHead;
import dev.polymodloader.internal.transform.InvokeHookBeforeReturn;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.MethodTransformAction;
import dev.polymodloader.internal.transform.RedirectCall;
import dev.polymodloader.internal.transform.ReplaceConstantReturnWithStaticCall;
import dev.polymodloader.internal.transform.ReplaceReturnedStringConstant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Checks preconditions of and applies each {@link MethodTransformAction} to an ASM method node.
 * Stack map frames are never recomputed (ADR-0004): actions without branches leave existing frames
 * valid, and the branching head hooks add only frames that are exact by construction (the
 * method's initial frame, or the initial frame plus one stack value), followed by a {@code NOP} so
 * the original first frame never shares their offset. Every action raises {@code maxStack} by what
 * it pushes.
 */
final class MethodActionApplier {

    private static final String OBJECT_HOOK_DESCRIPTOR = "(Ljava/lang/Object;)V";

    private MethodActionApplier() {}

    /** Returns why {@code action} cannot be applied to {@code method}, or empty when it can. */
    static Optional<String> checkPrecondition(MethodTransformAction action, MethodNode method) {
        return switch (action) {
            case ReplaceReturnedStringConstant replace -> checkConstantReturn(replace.expectedConstant(), method);
            case ReplaceConstantReturnWithStaticCall redirect ->
                checkConstantReturn(redirect.expectedConstant(), method);
            case InjectStaticCallAtHead inject -> checkHeadInjection(method);
            case InvokeHookAtHead hook -> checkHeadHook(hook.result(), method);
            case InvokeHookBeforeReturn hook -> checkReturnHook(method);
            case RedirectCall redirect -> checkRedirect(redirect, method);
            case InvokeHookAtCall hook -> checkCallHook(hook, method);
        };
    }

    /**
     * Whether {@code action} depends on the whole body's shape, so it cannot share a method with
     * another rule.
     */
    static boolean isExclusive(MethodTransformAction action) {
        return action instanceof ReplaceReturnedStringConstant || action instanceof ReplaceConstantReturnWithStaticCall;
    }

    /**
     * Applies the actions of all rules on one method, given in declared order. Call redirects go first,
     * then return hooks, then head hooks in reverse, because each head hook is inserted in front of the
     * previous ones; so head hooks run in declared order, and return hooks run in declared order at
     * the method's own returns only. Callers must have checked every precondition on the original
     * method.
     */
    static void applyInOrder(List<MethodTransformAction> actions, String ownerInternalName, MethodNode method) {
        for (MethodTransformAction action : actions) {
            if (action instanceof RedirectCall || isExclusive(action)) {
                apply(action, ownerInternalName, method);
            }
        }
        // Observers before a call run in declared order; those after a call are inserted right behind it, so they
        // are applied in reverse to run in declared order too.
        for (MethodTransformAction action : actions) {
            if (action instanceof InvokeHookAtCall hook && hook.timing() == InvokeHookAtCall.Timing.BEFORE) {
                apply(action, ownerInternalName, method);
            }
        }
        for (MethodTransformAction action : actions.reversed()) {
            if (action instanceof InvokeHookAtCall hook && hook.timing() == InvokeHookAtCall.Timing.AFTER) {
                apply(action, ownerInternalName, method);
            }
        }
        for (MethodTransformAction action : actions) {
            if (action instanceof InvokeHookBeforeReturn) {
                apply(action, ownerInternalName, method);
            }
        }
        for (MethodTransformAction action : actions.reversed()) {
            if (action instanceof InvokeHookAtHead || action instanceof InjectStaticCallAtHead) {
                apply(action, ownerInternalName, method);
            }
        }
    }

    /** Applies {@code action}; callers must have checked the precondition on the same bytes. */
    static void apply(MethodTransformAction action, String ownerInternalName, MethodNode method) {
        switch (action) {
            case ReplaceReturnedStringConstant replace -> firstConstant(method).cst = replace.replacementConstant();
            case ReplaceConstantReturnWithStaticCall redirect -> {
                String hookDescriptor = Type.getMethodDescriptor(Type.getReturnType(method.desc));
                method.instructions.set(
                        firstConstant(method),
                        new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                redirect.hookOwnerInternalName(),
                                redirect.hookMethodName(),
                                hookDescriptor,
                                false));
            }
            case InjectStaticCallAtHead inject -> {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        inject.hookOwnerInternalName(),
                        inject.hookMethodName(),
                        OBJECT_HOOK_DESCRIPTOR,
                        false));
                method.instructions.insert(call);
                method.maxStack = Math.max(method.maxStack, 1);
            }
            case InvokeHookAtHead hook -> applyHeadHook(hook, ownerInternalName, method);
            case InvokeHookBeforeReturn hook -> applyReturnHook(hook, ownerInternalName, method);
            case RedirectCall redirect -> applyRedirect(redirect, method);
            case InvokeHookAtCall hook -> applyCallHook(hook, method);
        }
    }

    /** The descriptor an {@link InvokeHookAtCall} hook must have: the receiver (unless static) and the arguments. */
    static String callHookDescriptor(InvokeHookAtCall hook, boolean staticCall) {
        Type invoked = Type.getMethodType(hook.invoked().descriptor());
        List<Type> parameters = new ArrayList<>();
        if (!staticCall) {
            parameters.add(Type.getObjectType(hook.invoked().ownerInternalName()));
        }
        parameters.addAll(List.of(invoked.getArgumentTypes()));
        return Type.getMethodDescriptor(Type.VOID_TYPE, parameters.toArray(Type[]::new));
    }

    /** Operand stack slots the call takes: the receiver (unless static) and the arguments. */
    private static int callSlots(MethodInsnNode call) {
        int slots = call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1;
        for (Type argument : Type.getArgumentTypes(call.desc)) {
            slots += argument.getSize();
        }
        return slots;
    }

    /**
     * Duplicates the call's values and calls the hook with the copies, before the call or after it. The call stays;
     * the stack is the same around it, and nothing branches, so frames stay valid.
     */
    private static void applyCallHook(InvokeHookAtCall hook, MethodNode method) {
        List<MethodInsnNode> calls = matchingCalls(hook.invoked(), method);
        List<MethodInsnNode> chosen =
                hook.ordinal() == InvokeHookAtCall.ALL ? calls : List.of(calls.get(hook.ordinal()));
        for (MethodInsnNode call : chosen) {
            int slots = callSlots(call);
            MethodInsnNode hookCall = new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    hook.hookOwnerInternalName(),
                    hook.hookMethodName(),
                    callHookDescriptor(hook, call.getOpcode() == Opcodes.INVOKESTATIC),
                    false);
            InsnList before = new InsnList();
            if (slots == 1) {
                before.add(new InsnNode(Opcodes.DUP));
            } else if (slots == 2) {
                before.add(new InsnNode(Opcodes.DUP2));
            }
            if (hook.timing() == InvokeHookAtCall.Timing.BEFORE) {
                before.add(hookCall);
                method.instructions.insertBefore(call, before);
            } else {
                method.instructions.insertBefore(call, before);
                method.instructions.insert(call, hookCall);
            }
            method.maxStack += slots;
        }
    }

    private static Optional<String> checkCallHook(InvokeHookAtCall hook, MethodNode method) {
        Optional<String> body = checkHasBody(method);
        if (body.isPresent()) {
            return body;
        }
        List<MethodInsnNode> calls = matchingCalls(hook.invoked(), method);
        if (calls.isEmpty()) {
            return Optional.of("the method never calls " + hook.invoked());
        }
        for (MethodInsnNode call : calls) {
            if (call.getOpcode() == Opcodes.INVOKESPECIAL) {
                return Optional.of("the call to " + hook.invoked()
                        + " is invokespecial (a super or private call), which cannot be observed");
            }
            if (callSlots(call) > 2) {
                return Optional.of("the call to " + hook.invoked() + " takes " + callSlots(call)
                        + " operand stack slots; an observer can copy at most two");
            }
        }
        if (hook.timing() == InvokeHookAtCall.Timing.AFTER
                && !Type.getReturnType(hook.invoked().descriptor()).equals(Type.VOID_TYPE)) {
            return Optional.of("an observer after a call needs a void call; " + hook.invoked() + " returns a value");
        }
        if (hook.ordinal() != InvokeHookAtCall.ALL && hook.ordinal() >= calls.size()) {
            return Optional.of("call #" + hook.ordinal() + " to " + hook.invoked()
                    + " was asked for, but the method makes only " + calls.size() + " such call(s)");
        }
        return Optional.empty();
    }

    /** The descriptor a {@link RedirectCall} hook must have: the receiver (unless static) and the arguments. */
    static String redirectHookDescriptor(RedirectCall redirect, boolean staticCall) {
        Type invoked = Type.getMethodType(redirect.invoked().descriptor());
        List<Type> parameters = new ArrayList<>();
        if (!staticCall) {
            parameters.add(Type.getObjectType(redirect.invoked().ownerInternalName()));
        }
        parameters.addAll(List.of(invoked.getArgumentTypes()));
        return Type.getMethodDescriptor(invoked.getReturnType(), parameters.toArray(Type[]::new));
    }

    /** Replaces the chosen matching calls in place; the stack shape is unchanged, so no frame changes. */
    private static void applyRedirect(RedirectCall redirect, MethodNode method) {
        List<MethodInsnNode> calls = matchingCalls(redirect.invoked(), method);
        List<MethodInsnNode> chosen =
                redirect.ordinal() == RedirectCall.ALL ? calls : List.of(calls.get(redirect.ordinal()));
        for (MethodInsnNode call : chosen) {
            boolean staticCall = call.getOpcode() == Opcodes.INVOKESTATIC;
            method.instructions.set(
                    call,
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            redirect.hookOwnerInternalName(),
                            redirect.hookMethodName(),
                            redirectHookDescriptor(redirect, staticCall),
                            false));
        }
    }

    /** Calls in {@code method} whose instruction names exactly {@code invoked}, in order. */
    private static List<MethodInsnNode> matchingCalls(MethodTarget invoked, MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(invoked.ownerInternalName())
                    && call.name.equals(invoked.name())
                    && call.desc.equals(invoked.descriptor())) {
                calls.add(call);
            }
        }
        return calls;
    }

    private static Optional<String> checkRedirect(RedirectCall redirect, MethodNode method) {
        Optional<String> body = checkHasBody(method);
        if (body.isPresent()) {
            return body;
        }
        List<MethodInsnNode> calls = matchingCalls(redirect.invoked(), method);
        if (calls.isEmpty()) {
            return Optional.of("the method never calls " + redirect.invoked());
        }
        for (MethodInsnNode call : calls) {
            if (call.getOpcode() == Opcodes.INVOKESPECIAL) {
                return Optional.of("the call to " + redirect.invoked()
                        + " is invokespecial (a super or private call), which cannot be redirected");
            }
        }
        if (redirect.ordinal() != RedirectCall.ALL && redirect.ordinal() >= calls.size()) {
            return Optional.of("call #" + redirect.ordinal() + " to " + redirect.invoked()
                    + " was asked for, but the method makes only " + calls.size() + " such call(s)");
        }
        return Optional.empty();
    }

    /** The descriptor an {@link InvokeHookAtHead} hook must have for {@code method}. */
    static String headHookDescriptor(HeadHookResult result, String ownerInternalName, MethodNode method) {
        Type returnType = switch (result) {
            case CONTINUE -> Type.VOID_TYPE;
            case CANCEL_IF_TRUE -> Type.BOOLEAN_TYPE;
            case RETURN_IF_NON_NULL -> hookResultType(Type.getReturnType(method.desc));
        };
        return Type.getMethodDescriptor(returnType, receiverAndArguments(ownerInternalName, method));
    }

    /** The descriptor an {@link InvokeHookBeforeReturn} hook must have for {@code method}. */
    static String returnHookDescriptor(String ownerInternalName, MethodNode method) {
        Type returned = Type.getReturnType(method.desc);
        List<Type> parameters = new ArrayList<>(List.of(receiverAndArguments(ownerInternalName, method)));
        if (returned.getSort() != Type.VOID) {
            parameters.add(returned);
        }
        return Type.getMethodDescriptor(returned, parameters.toArray(Type[]::new));
    }

    private static void applyHeadHook(InvokeHookAtHead hook, String ownerInternalName, MethodNode method) {
        InsnList code = new InsnList();
        int pushed = 0;
        int local = 0;
        if (!isStatic(method)) {
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            local = 1;
            pushed = 1;
        }
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            code.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
            local += argument.getSize();
            pushed += argument.getSize();
        }
        code.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                hook.hookOwnerInternalName(),
                hook.hookMethodName(),
                headHookDescriptor(hook.result(), ownerInternalName, method),
                false));
        switch (hook.result()) {
            case CONTINUE -> {}
            case CANCEL_IF_TRUE -> {
                LabelNode proceed = new LabelNode();
                code.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                code.add(new InsnNode(Opcodes.RETURN));
                code.add(proceed);
                code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                code.add(new InsnNode(Opcodes.NOP));
            }
            case RETURN_IF_NON_NULL -> {
                LabelNode returnIt = new LabelNode();
                LabelNode proceed = new LabelNode();
                code.add(new InsnNode(Opcodes.DUP));
                code.add(new JumpInsnNode(Opcodes.IFNONNULL, returnIt));
                code.add(new InsnNode(Opcodes.POP));
                code.add(new JumpInsnNode(Opcodes.GOTO, proceed));
                code.add(returnIt);
                Type returned = Type.getReturnType(method.desc);
                Type hookResult = hookResultType(returned);
                code.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[] {frameType(hookResult)}));
                if (returned.getSort() != Type.OBJECT && returned.getSort() != Type.ARRAY) {
                    // Primitive targets: the hook returns the box; unbox it before returning.
                    code.add(new MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL,
                            hookResult.getInternalName(),
                            returned.getClassName() + "Value",
                            Type.getMethodDescriptor(returned),
                            false));
                }
                code.add(new InsnNode(returned.getOpcode(Opcodes.IRETURN)));
                code.add(proceed);
                code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                code.add(new InsnNode(Opcodes.NOP));
                pushed = Math.max(pushed, 2 + returned.getSize());
            }
        }
        method.instructions.insert(code);
        method.maxStack = Math.max(method.maxStack, Math.max(pushed, 1));
    }

    /**
     * Before each return: parks the returned value in a fresh local, pushes the receiver and the
     * arguments (never reassigned, see the precondition), reloads the value, and calls the hook,
     * whose result is returned instead. No branch is added, so no frame changes.
     */
    private static void applyReturnHook(InvokeHookBeforeReturn hook, String ownerInternalName, MethodNode method) {
        Type returned = Type.getReturnType(method.desc);
        int returnOpcode = returned.getOpcode(Opcodes.IRETURN);
        String descriptor = returnHookDescriptor(ownerInternalName, method);
        int parked = method.maxLocals;
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == returnOpcode) {
                returns.add(instruction);
            }
        }
        int pushed = 0;
        for (AbstractInsnNode returnInstruction : returns) {
            InsnList call = new InsnList();
            if (returned.getSort() != Type.VOID) {
                call.add(new VarInsnNode(returned.getOpcode(Opcodes.ISTORE), parked));
            }
            pushed = 0;
            int local = 0;
            if (!isStatic(method)) {
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                local = 1;
                pushed = 1;
            }
            for (Type argument : Type.getArgumentTypes(method.desc)) {
                call.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
                local += argument.getSize();
                pushed += argument.getSize();
            }
            if (returned.getSort() != Type.VOID) {
                call.add(new VarInsnNode(returned.getOpcode(Opcodes.ILOAD), parked));
                pushed += returned.getSize();
            }
            call.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, hook.hookOwnerInternalName(), hook.hookMethodName(), descriptor, false));
            method.instructions.insertBefore(returnInstruction, call);
        }
        method.maxLocals += returned.getSize();
        method.maxStack = Math.max(method.maxStack, pushed);
    }

    /** Local variable slots holding the receiver and the parameters. */
    private static int parameterSlots(MethodNode method) {
        return (Type.getArgumentsAndReturnSizes(method.desc) >> 2) - (isStatic(method) ? 1 : 0);
    }

    private static Type[] receiverAndArguments(String ownerInternalName, MethodNode method) {
        List<Type> parameters = new ArrayList<>();
        if (!isStatic(method)) {
            parameters.add(Type.getObjectType(ownerInternalName));
        }
        parameters.addAll(List.of(Type.getArgumentTypes(method.desc)));
        return parameters.toArray(Type[]::new);
    }

    /** What a RETURN_IF_NON_NULL hook returns: the target's reference type, or the box of a primitive. */
    private static Type hookResultType(Type returned) {
        return switch (returned.getSort()) {
            case Type.BOOLEAN -> Type.getType(Boolean.class);
            case Type.BYTE -> Type.getType(Byte.class);
            case Type.CHAR -> Type.getType(Character.class);
            case Type.SHORT -> Type.getType(Short.class);
            case Type.INT -> Type.getType(Integer.class);
            case Type.LONG -> Type.getType(Long.class);
            case Type.FLOAT -> Type.getType(Float.class);
            case Type.DOUBLE -> Type.getType(Double.class);
            default -> returned;
        };
    }

    /** Verification type of a reference value in a compressed frame. */
    private static Object frameType(Type type) {
        return type.getSort() == Type.ARRAY ? type.getDescriptor() : type.getInternalName();
    }

    private static boolean isStatic(MethodNode method) {
        return (method.access & Opcodes.ACC_STATIC) != 0;
    }

    private static Optional<String> checkHeadInjection(MethodNode method) {
        if (isStatic(method)) {
            return Optional.of("head injection passes 'this', but the method is static");
        }
        return checkHasBody(method).or(() -> checkNotConstructor(method));
    }

    private static Optional<String> checkHeadHook(HeadHookResult result, MethodNode method) {
        Optional<String> shape = checkHasBody(method).or(() -> checkNotConstructor(method));
        if (shape.isPresent()) {
            return shape;
        }
        int returnSort = Type.getReturnType(method.desc).getSort();
        return switch (result) {
            case CONTINUE -> Optional.empty();
            case CANCEL_IF_TRUE ->
                returnSort == Type.VOID
                        ? Optional.empty()
                        : Optional.of("CANCEL_IF_TRUE needs a void method, but it returns "
                                + Type.getReturnType(method.desc));
            case RETURN_IF_NON_NULL ->
                returnSort != Type.VOID
                        ? Optional.empty()
                        : Optional.of("RETURN_IF_NON_NULL needs a method that returns a value, but it returns void");
        };
    }

    private static Optional<String> checkReturnHook(MethodNode method) {
        Optional<String> body = checkHasBody(method);
        if (body.isPresent()) {
            return body;
        }
        if (method.name.equals("<clinit>")) {
            return Optional.of("return hooks in class initialisers are not allowed");
        }
        int parameterSlots = parameterSlots(method);
        for (AbstractInsnNode instruction : method.instructions) {
            int written = writtenLocal(instruction);
            if (written >= 0 && written < parameterSlots) {
                return Optional.of("the method writes to parameter slot " + written
                        + ", so the hook could not be given the original arguments");
            }
        }
        return Optional.empty();
    }

    /** The local slot an instruction writes, or -1. */
    private static int writtenLocal(AbstractInsnNode instruction) {
        if (instruction instanceof VarInsnNode variable
                && variable.getOpcode() >= Opcodes.ISTORE
                && variable.getOpcode() <= Opcodes.ASTORE) {
            return variable.var;
        }
        if (instruction instanceof IincInsnNode increment) {
            return increment.var;
        }
        return -1;
    }

    private static Optional<String> checkHasBody(MethodNode method) {
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return Optional.of("the method has no body (abstract or native)");
        }
        return Optional.empty();
    }

    private static Optional<String> checkNotConstructor(MethodNode method) {
        if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
            return Optional.of("head injection into constructors and class initialisers is not allowed");
        }
        return Optional.empty();
    }

    private static Optional<String> checkConstantReturn(String expectedConstant, MethodNode method) {
        List<AbstractInsnNode> instructions = realInstructions(method);
        boolean shapeMatches = instructions.size() == 2
                && instructions.get(0) instanceof LdcInsnNode
                && instructions.get(1).getOpcode() == Opcodes.ARETURN;
        if (!shapeMatches) {
            return Optional.of("expected body 'LDC \"" + expectedConstant + "\"; ARETURN' but found "
                    + instructions.size() + " instruction(s) with a different shape");
        }
        Object constant = ((LdcInsnNode) instructions.get(0)).cst;
        if (!expectedConstant.equals(constant)) {
            return Optional.of("expected constant \"" + expectedConstant + "\" but found " + constant);
        }
        return Optional.empty();
    }

    private static LdcInsnNode firstConstant(MethodNode method) {
        return (LdcInsnNode) realInstructions(method).getFirst();
    }

    /** Instructions without labels, line numbers, and frames. */
    private static List<AbstractInsnNode> realInstructions(MethodNode method) {
        List<AbstractInsnNode> real = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                real.add(instruction);
            }
        }
        return real;
    }
}

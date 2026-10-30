package dev.plexusmodloader.compat.fabric.mixin;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Arguments of a call that a host's patch computes itself (ADR-0026 33). NeoForge 26.1.0.19-beta passes its own list of
 * data pack registries to {@code RegistryDataLoader.load} in {@code WorldLoader.lambda$load$0}
 * ({@code DataPackRegistriesHooks.getDataPackRegistries()} where vanilla reads {@code
 * RegistryDataLoader.WORLDGEN_REGISTRIES}). Fabric API's {@code @ModifyArg} there replaces that argument with Fabric's
 * own list, built from the vanilla one, and NeoForge's registries ({@code neoforge:biome_modifier}) go missing: every
 * world load then stops. A mixin that replaces such an argument would undo the host's patch, so it does not run.
 *
 * <p>The argument's producer is found by walking back from the call over straight-line code whose stack effect is known
 * (loads, constants, field reads, casts and calls); anything else (branch targets, stack shuffles, other instructions)
 * gives no answer, and nothing is claimed then.
 *
 * <p>Thread: any; no state.
 */
final class HostArguments {

    private HostArguments() {}

    /**
     * The producer of argument {@code index} ({@code -1}: any argument) of the calls {@code target} (Mixin's notation
     * {@code Lowner;name(descriptor)}) in {@code patched}, when the host's own code produces it ({@code hostOwned}: a
     * class the vanilla game does not have) and {@code vanilla}'s calls get that argument from the game; empty
     * otherwise.
     */
    static Optional<String> hostProduced(
            MethodModel vanilla, MethodModel patched, String target, int index, Predicate<String> hostOwned) {
        List<Optional<Instruction>> before = producers(vanilla, target, index);
        List<Optional<Instruction>> after = producers(patched, target, index);
        for (int i = 0; i < after.size(); i++) {
            Optional<Instruction> produced = after.get(i).filter(HostArguments::source);
            Optional<String> owner = produced.flatMap(HostArguments::owner);
            if (owner.isEmpty() || !hostOwned.test(owner.get())) {
                continue;
            }
            Optional<String> vanillaOwner = i < before.size()
                    ? before.get(i).filter(HostArguments::source).flatMap(HostArguments::owner)
                    : Optional.empty();
            if (vanillaOwner.isPresent() && !hostOwned.test(vanillaOwner.get())) {
                return Optional.of(describe(produced.get()));
            }
        }
        return Optional.empty();
    }

    /** For each call {@code target} in {@code method}, in order, the instruction that pushes argument {@code index}. */
    private static List<Optional<Instruction>> producers(MethodModel method, String target, int index) {
        List<Optional<Instruction>> found = new ArrayList<>();
        if (method.code().isEmpty()) {
            return found;
        }
        List<CodeElement> code = method.code().get().elementList();
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i) instanceof InvokeInstruction call && matches(call, target)) {
                int arguments = call.typeSymbol().parameterCount();
                if (index >= arguments) {
                    found.add(Optional.empty());
                } else if (index < 0) {
                    // Any argument: the first one the host produces counts.
                    Optional<Instruction> any = Optional.empty();
                    for (int argument = 0; argument < arguments && any.isEmpty(); argument++) {
                        any = producer(code, i, arguments - 1 - argument);
                    }
                    found.add(any);
                } else {
                    found.add(producer(code, i, arguments - 1 - index));
                }
            }
        }
        return found;
    }

    /** The instruction before {@code call} that pushed the value {@code above} values below the top of the stack. */
    private static Optional<Instruction> producer(List<CodeElement> code, int call, int above) {
        int depth = above;
        for (int i = call - 1; i >= 0; i--) {
            CodeElement element = code.get(i);
            if (!(element instanceof Instruction instruction)) {
                if (element instanceof java.lang.classfile.instruction.LabelTarget) {
                    return Optional.empty(); // a branch may arrive here; the stack is not known
                }
                continue;
            }
            int[] effect = effect(instruction);
            if (effect == null) {
                return Optional.empty();
            }
            int pops = effect[0];
            int pushes = effect[1];
            if (depth < pushes) {
                return Optional.of(instruction);
            }
            depth = depth - pushes + pops;
        }
        return Optional.empty();
    }

    /** {pops, pushes} in values (not slots), or {@code null} when not known here. */
    private static int[] effect(Instruction instruction) {
        if (instruction instanceof LoadInstruction || instruction instanceof ConstantInstruction) {
            return new int[] {0, 1};
        }
        if (instruction instanceof FieldInstruction field) {
            return switch (field.opcode()) {
                case GETSTATIC -> new int[] {0, 1};
                case GETFIELD -> new int[] {1, 1};
                case PUTSTATIC -> new int[] {1, 0};
                case PUTFIELD -> new int[] {2, 0};
                default -> null;
            };
        }
        if (instruction instanceof TypeCheckInstruction) {
            return new int[] {1, 1};
        }
        if (instruction instanceof InvokeInstruction call && call.opcode() != Opcode.INVOKEDYNAMIC) {
            MethodTypeDesc type = call.typeSymbol();
            int pops = type.parameterCount() + (call.opcode() == Opcode.INVOKESTATIC ? 0 : 1);
            int pushes = TypeKind.from(type.returnType()) == TypeKind.VOID ? 0 : 1;
            return new int[] {pops, pushes};
        }
        return null;
    }

    /**
     * Whether {@code instruction} is a value's source of its own: a static field read or a static call without
     * arguments. A host call that takes the vanilla value and returns it changed (Forge's {@code
     * ForgeEventFactory.onResourceReload(listeners)} around the reload listeners) is no source: a mixin that changes the
     * argument changes the host's result too, as it changes the vanilla value without the host.
     */
    private static boolean source(Instruction instruction) {
        if (instruction instanceof FieldInstruction field) {
            return field.opcode() == Opcode.GETSTATIC;
        }
        return instruction instanceof InvokeInstruction call
                && call.opcode() == Opcode.INVOKESTATIC
                && call.typeSymbol().parameterCount() == 0;
    }

    private static Optional<String> owner(Instruction instruction) {
        if (instruction instanceof InvokeInstruction call) {
            return Optional.of(call.owner().asInternalName());
        }
        if (instruction instanceof FieldInstruction field) {
            return Optional.of(field.owner().asInternalName());
        }
        return Optional.empty();
    }

    private static String describe(Instruction instruction) {
        if (instruction instanceof InvokeInstruction call) {
            return call.owner().asInternalName() + "." + call.name().stringValue()
                    + call.typeSymbol().descriptorString();
        }
        FieldInstruction field = (FieldInstruction) instruction;
        return field.owner().asInternalName() + "." + field.name().stringValue();
    }

    private static boolean matches(InvokeInstruction call, String target) {
        if (!target.startsWith("L") || target.indexOf(';') < 0) {
            return false;
        }
        String owner = target.substring(1, target.indexOf(';'));
        String rest = target.substring(target.indexOf(';') + 1);
        int open = rest.indexOf('(');
        String name = open < 0 ? rest : rest.substring(0, open);
        String descriptor = open < 0 ? "" : rest.substring(open);
        return call.owner().asInternalName().equals(owner)
                && call.name().equalsString(name)
                && (descriptor.isEmpty() || call.typeSymbol().descriptorString().equals(descriptor));
    }
}

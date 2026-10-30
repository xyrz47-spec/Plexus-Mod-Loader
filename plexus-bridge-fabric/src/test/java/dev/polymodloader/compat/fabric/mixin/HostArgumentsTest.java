package dev.polymodloader.compat.fabric.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** Shapes of NeoForge 26.1's {@code WorldLoader.lambda$load$0} and Forge's reload listener wrapping, built here. */
class HostArgumentsTest {

    private static final ClassDesc LIST = ClassDesc.of("java.util.List");
    private static final ClassDesc LOADER = ClassDesc.of("net.minecraft.resources.RegistryDataLoader");
    private static final ClassDesc HOOKS = ClassDesc.of("net.neoforged.neoforge.registries.DataPackRegistriesHooks");
    private static final MethodTypeDesc LOAD = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object, LIST);
    private static final String TARGET =
            "Lnet/minecraft/resources/RegistryDataLoader;load(Ljava/lang/Object;Ljava/util/List;)V";
    private static final Predicate<String> HOST = owner -> owner.startsWith("net/neoforged/");

    @Test
    void aHostSourceInPlaceOfTheGamesIsHostProduced() {
        MethodModel vanilla = method(code -> code.aconst_null().getstatic(LOADER, "WORLDGEN_REGISTRIES", LIST));
        MethodModel patched = method(
                code -> code.aconst_null().invokestatic(HOOKS, "getDataPackRegistries", MethodTypeDesc.of(LIST)));

        Optional<String> produced = HostArguments.hostProduced(vanilla, patched, TARGET, 1, HOST);

        assertEquals(
                Optional.of(
                        "net/neoforged/neoforge/registries/DataPackRegistriesHooks.getDataPackRegistries()Ljava/util/List;"),
                produced);
        assertTrue(HostArguments.hostProduced(vanilla, patched, TARGET, 0, HOST).isEmpty());
    }

    @Test
    void aHostCallThatChangesTheGamesValueIsNot() {
        MethodModel vanilla = method(code -> code.aconst_null().getstatic(LOADER, "WORLDGEN_REGISTRIES", LIST));
        MethodModel patched = method(code -> code.aconst_null()
                .getstatic(LOADER, "WORLDGEN_REGISTRIES", LIST)
                .invokestatic(HOOKS, "wrap", MethodTypeDesc.of(LIST, LIST)));

        assertTrue(HostArguments.hostProduced(vanilla, patched, TARGET, 1, HOST).isEmpty());
    }

    @Test
    void aBranchBeforeTheCallGivesNoAnswer() {
        MethodModel vanilla = method(code -> code.aconst_null().getstatic(LOADER, "WORLDGEN_REGISTRIES", LIST));
        MethodModel patched = method(code -> {
            // A branch arrives between the producer and the call: the stack there is not followed.
            code.aconst_null();
            code.invokestatic(HOOKS, "getDataPackRegistries", MethodTypeDesc.of(LIST));
            java.lang.classfile.Label join = code.newLabel();
            code.iconst_0().ifeq(join);
            code.labelBinding(join);
        });

        assertTrue(HostArguments.hostProduced(vanilla, patched, TARGET, 1, HOST).isEmpty());
    }

    /** A static method that pushes the two arguments with {@code arguments} and calls the target. */
    private static MethodModel method(Consumer<CodeBuilder> arguments) {
        byte[] bytes = ClassFile.of()
                .build(
                        ClassDesc.of("example.Patched"),
                        builder -> builder.withMethodBody(
                                "run", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_STATIC, code -> {
                                    arguments.accept(code);
                                    code.invokestatic(LOADER, "load", LOAD).return_();
                                }));
        return ClassFile.of().parse(bytes).methods().getFirst();
    }
}

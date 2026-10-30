package dev.plexusmodloader.compat.forge;

import static java.lang.constant.ConstantDescs.CD_Throwable;
import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleReference;
import java.util.List;

/**
 * Closes the file night-config opens to start an empty config file, which Forge's own night-config (3.7.4, the one
 * Forge 26.2-65.1.5 ships) leaves open (ADR-0021 G6).
 *
 * <p>{@code ConfigFormat.initEmptyFile(WriterSupplier)} is {@code initEmptyFile(supplier.get())}: the writer it opens
 * on the file ({@code Files.newBufferedWriter}) is never closed (checked with {@code javap}). Forge calls it through
 * {@code initEmptyFile(Path)} for every config file it creates ({@code ConfigFileTypeHandler.setupConfigFile}). On
 * Windows a file with an open handle cannot be replaced, so the next save that replaces the file fails until the
 * garbage collector closes the writer: Forge Config API Port saves the configs it ports with {@code
 * WritingMode.REPLACE_ATOMIC} right after Forge creates them, and the server stopped at the world start with {@code
 * AccessDeniedException} (Open Parties and Claims' server config). The same steps with night-config alone fail the
 * same way, so this is not Poly Mod Loader's doing; a player's Forge on Windows meets it too.
 *
 * <p>The method becomes {@code try (Writer writer = supplier.get()) { initEmptyFile(writer); }}. Only a method whose
 * body is exactly the leaking one is changed; any other {@code ConfigFormat} (a night-config that closes the writer
 * itself) is left as it is.
 */
final class NightConfigEmptyFilePatch {

    /** night-config's core module. */
    static final String MODULE = "com.electronwill.nightconfig.core";

    static final String CLASS_FILE = "com/electronwill/nightconfig/core/ConfigFormat.class";

    private static final ClassDesc CONFIG_FORMAT = ClassDesc.of("com.electronwill.nightconfig.core.ConfigFormat");
    private static final ClassDesc WRITER_SUPPLIER =
            ClassDesc.of("com.electronwill.nightconfig.core.utils.WriterSupplier");
    private static final ClassDesc WRITER = ClassDesc.of("java.io.Writer");
    private static final String INIT_EMPTY_FILE = "initEmptyFile";
    private static final MethodTypeDesc FROM_SUPPLIER = MethodTypeDesc.of(CD_void, WRITER_SUPPLIER);
    private static final MethodTypeDesc FROM_WRITER = MethodTypeDesc.of(CD_void, WRITER);
    private static final MethodTypeDesc GET = MethodTypeDesc.of(WRITER);
    private static final MethodTypeDesc CLOSE = MethodTypeDesc.of(CD_void);
    private static final MethodTypeDesc ADD_SUPPRESSED = MethodTypeDesc.of(CD_void, CD_Throwable);

    private NightConfigEmptyFilePatch() {}

    /** {@code ConfigFormat}'s class file with the writer closed, or {@code original} when it is not the leaking one. */
    static byte[] apply(byte[] original) {
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(original);
        if (!model.thisClass().asSymbol().equals(CONFIG_FORMAT)
                || model.methods().stream()
                        .noneMatch(method -> isTarget(method)
                                && method.code()
                                        .map(NightConfigEmptyFilePatch::leaks)
                                        .orElse(false))) {
            return original;
        }
        MethodTransform closing = (builder, element) -> {
            if (element instanceof CodeModel) {
                builder.withCode(NightConfigEmptyFilePatch::closingBody);
            } else {
                builder.with(element);
            }
        };
        return classFile.transformClass(
                model, ClassTransform.transformingMethods(NightConfigEmptyFilePatch::isTarget, closing));
    }

    /** {@code reference} (night-config's core module) reading {@code ConfigFormat} with {@link #apply}. */
    static ModuleReference patching(ModuleReference reference) {
        return PatchedClassReference.of(reference, CLASS_FILE, NightConfigEmptyFilePatch::apply);
    }

    private static boolean isTarget(MethodModel method) {
        return method.methodName().equalsString(INIT_EMPTY_FILE)
                && method.methodTypeSymbol().equals(FROM_SUPPLIER);
    }

    /** Whether the body is {@code this.initEmptyFile(supplier.get()); return;} and nothing else. */
    private static boolean leaks(CodeModel code) {
        List<Instruction> instructions = code.elementStream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList();
        return instructions.size() == 5
                && instructions.get(0) instanceof LoadInstruction self
                && self.slot() == 0
                && instructions.get(1) instanceof LoadInstruction supplier
                && supplier.slot() == 1
                && instructions.get(2) instanceof InvokeInstruction get
                && get.owner().asSymbol().equals(WRITER_SUPPLIER)
                && get.name().equalsString("get")
                && get.typeSymbol().equals(GET)
                && instructions.get(3) instanceof InvokeInstruction init
                && init.owner().asSymbol().equals(CONFIG_FORMAT)
                && init.name().equalsString(INIT_EMPTY_FILE)
                && init.typeSymbol().equals(FROM_WRITER)
                && instructions.get(4) instanceof ReturnInstruction done
                && done.typeKind() == TypeKind.VOID;
    }

    /** {@code try (Writer writer = supplier.get()) { this.initEmptyFile(writer); }}, as javac writes it. */
    private static void closingBody(CodeBuilder code) {
        int writer = 2;
        int thrown = 3;
        int suppressed = 4;
        code.aload(1).invokeinterface(WRITER_SUPPLIER, "get", GET).astore(writer);
        code.trying(
                body -> body.aload(0).aload(writer).invokeinterface(CONFIG_FORMAT, INIT_EMPTY_FILE, FROM_WRITER),
                catches -> catches.catchingAll(handler -> {
                    handler.astore(thrown);
                    handler.aload(writer)
                            .ifThen(
                                    Opcode.IFNONNULL,
                                    close -> close.trying(
                                            closing -> closing.aload(writer).invokevirtual(WRITER, "close", CLOSE),
                                            closeCatches -> closeCatches.catchingAll(failed -> failed.astore(suppressed)
                                                    .aload(thrown)
                                                    .aload(suppressed)
                                                    .invokevirtual(CD_Throwable, "addSuppressed", ADD_SUPPRESSED))));
                    handler.aload(thrown).athrow();
                }));
        code.aload(writer).ifThen(Opcode.IFNONNULL, close -> close.aload(writer).invokevirtual(WRITER, "close", CLOSE));
        code.return_();
    }
}

package dev.polymodloader.compat.fabric.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mixins built for the test, shaped as Fabric API's {@code RegistryDataLoaderMixin} and simpler cases. */
class CoupledInjectionsTest {

    private static final ClassDesc MIXIN = ClassDesc.of("example.ExampleMixin");
    private static final ClassDesc SCOPED = ClassDesc.of("java.lang.ScopedValue");
    private static final ClassDesc LOGGER = ClassDesc.of("org.slf4j.Logger");
    private static final MethodTypeDesc VOID = MethodTypeDesc.of(ConstantDescs.CD_void);
    private static final String BIND = "bind()V";
    private static final String READ = "read()V";
    private static final String LOG = "log()V";
    private static final String COUNT = "count()V";
    private static final String USE_COUNT = "useCount()V";
    private static final String VIA_LAMBDA = "viaLambda()V";
    private static final Set<String> HANDLERS = Set.of(BIND, READ, LOG, COUNT, USE_COUNT, VIA_LAMBDA);

    @Test
    void aReaderOfAScopedValueThatAStoppedInjectionBindsStops() {
        Map<String, String> leftOut = CoupledInjections.leftOut(mixin(), HANDLERS, Set.of(BIND));

        assertEquals(Set.of(READ, VIA_LAMBDA), leftOut.keySet());
        assertTrue(leftOut.get(READ).contains("IS_SERVER, which bind sets"), leftOut.get(READ));
    }

    @Test
    void aWriterOfAFieldStopsItsReadersButNotTheOtherWay() {
        assertEquals(
                Set.of(USE_COUNT),
                CoupledInjections.leftOut(mixin(), HANDLERS, Set.of(COUNT)).keySet());
        assertEquals(
                Set.of(),
                CoupledInjections.leftOut(mixin(), HANDLERS, Set.of(USE_COUNT)).keySet());
    }

    @Test
    void constantsCoupleNothing() {
        assertEquals(
                Set.of(),
                CoupledInjections.leftOut(mixin(), HANDLERS, Set.of(LOG)).keySet());
    }

    /**
     * {@code bind} and {@code read} use the {@code ScopedValue} {@code IS_SERVER}; {@code viaLambda} reads it through a
     * private lambda method; {@code log} reads the constant {@code LOGGER}, as {@code read} does too; {@code count}
     * writes the int field {@code counter}, {@code useCount} reads it.
     */
    private static ClassModel mixin() {
        byte[] bytes = ClassFile.of().build(MIXIN, builder -> {
            builder.withField("IS_SERVER", SCOPED, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            builder.withField("LOGGER", LOGGER, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            builder.withField("counter", ConstantDescs.CD_int, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC);
            builder.withMethodBody(
                    "<clinit>",
                    VOID,
                    ClassFile.ACC_STATIC,
                    code -> code.aconst_null()
                            .putstatic(MIXIN, "IS_SERVER", SCOPED)
                            .aconst_null()
                            .putstatic(MIXIN, "LOGGER", LOGGER)
                            .return_());
            builder.withMethodBody(
                    "bind",
                    VOID,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.getstatic(MIXIN, "IS_SERVER", SCOPED).pop().return_());
            builder.withMethodBody(
                    "read",
                    VOID,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.getstatic(MIXIN, "IS_SERVER", SCOPED)
                            .pop()
                            .getstatic(MIXIN, "LOGGER", LOGGER)
                            .pop()
                            .return_());
            builder.withMethodBody(
                    "log",
                    VOID,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.getstatic(MIXIN, "LOGGER", LOGGER).pop().return_());
            builder.withMethodBody(
                    "count",
                    VOID,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.iconst_1()
                            .putstatic(MIXIN, "counter", ConstantDescs.CD_int)
                            .return_());
            builder.withMethodBody(
                    "useCount",
                    VOID,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.getstatic(MIXIN, "counter", ConstantDescs.CD_int)
                            .pop()
                            .return_());
            builder.withMethodBody(
                    "viaLambda",
                    VOID,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.invokestatic(MIXIN, "lambda$viaLambda$0", VOID).return_());
            builder.withMethodBody(
                    "lambda$viaLambda$0",
                    VOID,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC,
                    code -> code.getstatic(MIXIN, "IS_SERVER", SCOPED).pop().return_());
        });
        return ClassFile.of().parse(bytes);
    }
}

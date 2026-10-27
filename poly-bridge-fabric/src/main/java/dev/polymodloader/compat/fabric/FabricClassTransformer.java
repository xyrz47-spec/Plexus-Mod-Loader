package dev.polymodloader.compat.fabric;

import dev.polymodloader.compat.bridge.BridgeClassTransformer;
import dev.polymodloader.compat.bridge.GameClassBytes;
import dev.polymodloader.compat.fabric.mixin.AttributeBuilderBridge;
import dev.polymodloader.compat.fabric.mixin.HostInterfaceDefaults;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Fabric bridge's class stage, in Fabric Loader's order (ADR-0020): first what Fabric Loader's own transformer
 * does ({@code FabricTransformer}) — environment stripping of mod and library classes ({@link EnvironmentStripper})
 * interface injection and enum extension into Minecraft classes ({@link InterfaceInjector}, {@link EnumExtender};
 * access widenings were already applied by Poly Mod Loader's transformer) — then Mixin. Mixin reads classes as they are after the first part ({@link
 * #preMixinView}), as Fabric Loader hands them to it.
 */
final class FabricClassTransformer implements BridgeClassTransformer {

    private final GameClassBytes classes;
    private final EnvironmentStripper stripper;
    private final InterfaceInjector injector;
    private final EnumExtender extender;
    private final Optional<BridgeClassTransformer> mixin;
    /** Under a host loader: interfaces given conflicting defaults by the host's patch and a mod (ADR-0026 42). */
    private final Optional<HostInterfaceDefaults> hostDefaults;

    FabricClassTransformer(
            EnvironmentStripper stripper,
            InterfaceInjector injector,
            EnumExtender extender,
            Optional<BridgeClassTransformer> mixin) {
        this(null, stripper, injector, extender, mixin, Optional.empty());
    }

    FabricClassTransformer(
            GameClassBytes classes,
            EnvironmentStripper stripper,
            InterfaceInjector injector,
            EnumExtender extender,
            Optional<BridgeClassTransformer> mixin,
            Optional<HostInterfaceDefaults> hostDefaults) {
        this.classes = classes;
        this.stripper = stripper;
        this.injector = injector;
        this.extender = extender;
        this.mixin = mixin;
        this.hostDefaults = hostDefaults;
    }

    @Override
    public String name() {
        StringBuilder name = new StringBuilder("environment stripping");
        if (!injector.isEmpty()) {
            name.append(", interface injection (").append(injector.count()).append(" line(s))");
        }
        if (!extender.isEmpty()) {
            name.append(", enum extension (").append(extender.count()).append(" line(s))");
        }
        mixin.ifPresent(stage -> name.append(", then ").append(stage.name()));
        return name.toString();
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        byte[] prepared = preMixin(classes, stripper, injector, extender, className, bytes);
        byte[] transformed = mixin.isPresent() ? mixin.get().transform(className, prepared) : prepared;
        return hostDefaults.isPresent() ? hostDefaults.get().apply(className, bytes, transformed) : transformed;
    }

    /**
     * Mixin's synthetic classes, and the game class loader's own copy of {@link AttributeBuilderBridge}, which the
     * getBuilder() added to {@code AttributeSupplier$Builder} calls and which must link to the game's Guava.
     */
    @Override
    public byte[] generate(String className) {
        byte[] bridge = AttributeBuilderBridge.classFile(className);
        if (bridge != null) {
            return bridge;
        }
        return mixin.map(stage -> stage.generate(className)).orElse(null);
    }

    private static byte[] preMixin(
            GameClassBytes classes,
            EnvironmentStripper stripper,
            InterfaceInjector injector,
            EnumExtender extender,
            String className,
            byte[] bytes) {
        byte[] prepared = extender.apply(className, injector.apply(className, stripper.apply(className, bytes)));
        return adaptHostTargetClass(classes, className, prepared);
    }

    private static byte[] adaptHostTargetClass(GameClassBytes classes, String className, byte[] bytes) {
        if (!className.equals("net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder")
                && !className.equals("net/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder")) {
            return bytes;
        }
        if (classes == null
                || (classes.classBytes("net.fabricmc.fabric.mixin.object.builder.AttributeSupplierBuilderAccessor")
                                .isEmpty()
                        && classes.classBytes(
                                        "net/fabricmc/fabric/mixin/object/builder/AttributeSupplierBuilderAccessor")
                                .isEmpty())) {
            return bytes;
        }
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(bytes);
        // Only a host's patch makes the field a Map (Forge, NeoForge); vanilla's ImmutableMap.Builder field is what
        // Fabric's @Accessor reads, and a getBuilder() added here would make Mixin refuse that accessor.
        boolean hostMapField = model.fields().stream()
                .anyMatch(f ->
                        f.fieldName().equalsString("builder") && f.fieldType().equalsString("Ljava/util/Map;"));
        if (!hostMapField) {
            return bytes;
        }
        boolean hasGetBuilder = model.methods().stream()
                .anyMatch(m -> m.methodName().equalsString("getBuilder")
                        && m.methodType().equalsString("()Lcom/google/common/collect/ImmutableMap$Builder;"));
        if (hasGetBuilder) {
            return bytes;
        }
        ClassDesc accessorInterface =
                ClassDesc.of("net.fabricmc.fabric.mixin.object.builder.AttributeSupplierBuilderAccessor");
        ClassDesc targetClass = ClassDesc.of("net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder");
        ClassDesc mapDesc = ClassDesc.of("java.util.Map");
        ClassDesc immutableMapBuilderDesc = ClassDesc.of("com.google.common.collect.ImmutableMap$Builder");
        ClassDesc bridgeClass = ClassDesc.of("dev.polymodloader.compat.fabric.mixin.AttributeBuilderBridge");

        List<ClassDesc> allInterfaces = new ArrayList<>(
                model.interfaces().stream().map(ClassEntry::asSymbol).toList());
        if (!allInterfaces.contains(accessorInterface)) {
            allInterfaces.add(accessorInterface);
        }
        return classFile.transformClass(model, ClassTransform.endHandler(builder -> {
            builder.withInterfaceSymbols(allInterfaces);
            builder.withMethodBody(
                    "getBuilder", MethodTypeDesc.of(immutableMapBuilderDesc), ClassFile.ACC_PUBLIC, code -> {
                        code.aload(0);
                        code.getfield(targetClass, "builder", mapDesc);
                        code.invokestatic(bridgeClass, "wrap", MethodTypeDesc.of(immutableMapBuilderDesc, mapDesc));
                        code.areturn();
                    });
        }));
    }

    /**
     * What Mixin reads: the game class loader's classes after environment stripping and interface injection, as
     * Fabric Loader hands Mixin classes after its own transformer.
     */
    static GameClassBytes preMixinView(
            GameClassBytes classes, EnvironmentStripper stripper, InterfaceInjector injector, EnumExtender extender) {
        return new GameClassBytes() {
            @Override
            public Optional<byte[]> classBytes(String className) {
                return classes.classBytes(className)
                        .map(bytes -> preMixin(classes, stripper, injector, extender, className, bytes));
            }

            @Override
            public boolean isDefined(String className) {
                return classes.isDefined(className);
            }
        };
    }
}

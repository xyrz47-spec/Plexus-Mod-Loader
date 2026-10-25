package dev.polymodloader.compat.fabric;

import dev.polymodloader.compat.bridge.BridgeClassTransformer;
import dev.polymodloader.compat.bridge.GameClassBytes;
import dev.polymodloader.compat.fabric.mixin.HostInterfaceDefaults;
import java.util.Optional;

/**
 * The Fabric bridge's class stage, in Fabric Loader's order (ADR-0020): first what Fabric Loader's own transformer
 * does ({@code FabricTransformer}) — environment stripping of mod and library classes ({@link EnvironmentStripper})
 * interface injection and enum extension into Minecraft classes ({@link InterfaceInjector}, {@link EnumExtender};
 * access widenings were already applied by Poly Mod Loader's transformer) — then Mixin. Mixin reads classes as they are after the first part ({@link
 * #preMixinView}), as Fabric Loader hands them to it.
 */
final class FabricClassTransformer implements BridgeClassTransformer {

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
        this(stripper, injector, extender, mixin, Optional.empty());
    }

    FabricClassTransformer(
            EnvironmentStripper stripper,
            InterfaceInjector injector,
            EnumExtender extender,
            Optional<BridgeClassTransformer> mixin,
            Optional<HostInterfaceDefaults> hostDefaults) {
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
        byte[] prepared = preMixin(stripper, injector, extender, className, bytes);
        byte[] transformed = mixin.isPresent() ? mixin.get().transform(className, prepared) : prepared;
        return hostDefaults.isPresent() ? hostDefaults.get().apply(className, bytes, transformed) : transformed;
    }

    @Override
    public byte[] generate(String className) {
        return mixin.map(stage -> stage.generate(className)).orElse(null);
    }

    private static byte[] preMixin(
            EnvironmentStripper stripper,
            InterfaceInjector injector,
            EnumExtender extender,
            String className,
            byte[] bytes) {
        return extender.apply(className, injector.apply(className, stripper.apply(className, bytes)));
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
                        .map(bytes -> preMixin(stripper, injector, extender, className, bytes));
            }

            @Override
            public boolean isDefined(String className) {
                return classes.isDefined(className);
            }
        };
    }
}

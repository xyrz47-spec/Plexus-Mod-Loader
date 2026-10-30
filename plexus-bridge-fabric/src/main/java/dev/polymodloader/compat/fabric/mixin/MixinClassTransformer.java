package dev.polymodloader.compat.fabric.mixin;

import dev.polymodloader.compat.bridge.BridgeClassTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/**
 * Applies Mixin to every class the game class loader defines and creates Mixin's synthetic classes, the two calls
 * Fabric Loader's Knot makes ({@code transformClassBytes} with the class bytes, or with {@code null} for a name no
 * jar holds). On a host's patched game, a Mixin failure in a Fabric or Quilt mod's mixin is reported as Poly Mod
 * Loader's ({@link HostedMixinErrors#reportUnforeseen}) before it goes on as it is.
 */
final class MixinClassTransformer implements BridgeClassTransformer {

    private final IMixinTransformer transformer;
    private final String name;
    private final boolean hosted;

    MixinClassTransformer(IMixinTransformer transformer, String name, boolean hosted) {
        this.transformer = transformer;
        this.name = name;
        this.hosted = hosted;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        PolyMixinService.loading(className);
        try {
            return transformer.transformClassBytes(className, className, bytes);
        } catch (RuntimeException | Error e) {
            if (hosted) {
                HostedMixinErrors.reportUnforeseen(className, e);
            }
            throw e;
        }
    }

    @Override
    public byte[] generate(String className) {
        return transformer.transformClassBytes(className, className, null);
    }
}

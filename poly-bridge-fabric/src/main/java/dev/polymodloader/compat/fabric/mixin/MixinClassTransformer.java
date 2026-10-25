package dev.polymodloader.compat.fabric.mixin;

import dev.polymodloader.compat.bridge.BridgeClassTransformer;
import java.util.function.BiFunction;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/**
 * Applies Mixin to every class the game class loader defines and creates Mixin's synthetic classes, the two calls
 * Fabric Loader's Knot makes ({@code transformClassBytes} with the class bytes, or with {@code null} for a name no
 * jar holds). On a host's patched game, {@code hosted} then changes the few mixin interfaces whose accessors cannot
 * work there ({@link HostPatchAdjustments#runtime}).
 */
final class MixinClassTransformer implements BridgeClassTransformer {

    private final IMixinTransformer transformer;
    private final String name;
    private final BiFunction<String, byte[], byte[]> hosted;

    MixinClassTransformer(IMixinTransformer transformer, String name, BiFunction<String, byte[], byte[]> hosted) {
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
        return hosted.apply(className, transformer.transformClassBytes(className, className, bytes));
    }

    @Override
    public byte[] generate(String className) {
        return transformer.transformClassBytes(className, className, null);
    }
}

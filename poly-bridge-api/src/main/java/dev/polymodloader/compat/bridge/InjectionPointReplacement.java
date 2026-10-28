package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Where a host loader's patch moved an injection point that other loaders' Mixins name (ADR-0021 G8): in {@code
 * targetClass}'s method {@code method}, the call or field {@code replaced} (Mixin's target notation, such as {@code
 * Lnet/minecraft/server/Bootstrap;wrapStreams()V}) is gone, and {@code replacement} takes its place as {@code placement}
 * says. A bridge whose Mixin applies another loader's mixins to the host's patched game moves such injections there
 * instead of dropping them. It checks the patched classes first (the replacement is in the method, and a helper makes the
 * replaced call) and leaves an injection out when they do not have that shape.
 *
 * @param targetClass the patched class, internal name ({@code net/minecraft/server/Bootstrap})
 * @param method the method's name
 * @param replaced the vanilla injection target that the patch removed
 * @param replacement the target the patch put in its place
 * @param placement how {@code replacement} stands for {@code replaced}
 */
@ApiStability(StabilityLevel.INTERNAL)
public record InjectionPointReplacement(
        String targetClass, String method, String replaced, String replacement, Placement placement) {

    /** How the host's replacement stands for the injection point its patch removed. */
    public enum Placement {
        /**
         * {@code replacement} stands where {@code replaced} stood, with the same meaning: an injection that only needs
         * the place ({@code @Inject}) moves there, and one bound to the call itself ({@code @Redirect}, {@code
         * @WrapOperation}, ...) only when both have the same owner and descriptor.
         */
        SAME_PLACE,
        /**
         * {@code replacement} is a call, in {@code method}, of a method of {@code targetClass} that returns {@code
         * boolean}; that helper now makes the {@code replaced} call itself and returns true exactly when it did. An
         * {@code @Inject} that ran before {@code replaced} runs right after each such helper call that returned true,
         * with {@code method}'s parameters and locals at that call (Forge's {@code ServerPlayerGameMode.removeBlock},
         * which calls {@code Block.destroy} only when it removed the block).
         */
        AFTER_TRUE_RESULT,
        /**
         * {@code replacement} is the name and descriptor ({@code name(descriptor)}) of another method of {@code
         * targetClass} into which the host's patch moved {@code method}'s body, including {@code replaced} (NeoForge's
         * {@code ServerConfigurationPacketListenerImpl.runConfiguration()V} and {@code
         * Language.parseTranslations(BiConsumer, BiConsumer, String)V}). An injection into {@code method} at {@code
         * replaced} whose handler takes no target method parameters (or where {@code replacement} has the same
         * descriptor) moves to {@code replacement}.
         */
        IN_METHOD,
        /**
         * {@code replaced} is a static {@code List} field, and {@code replacement} a static method of the same class
         * without parameters that returns that field's elements, in order, followed by the host's own (as a {@code
         * List} or a {@code Stream}): MinecraftForge 26.3's {@code RegistryDataLoader.getWorldAndDimensionRegistries()}
         * in place of {@code WORLD_REGISTRIES}. A {@code @Redirect} of the field read, whose handler returns the list a
         * mod reads in its place, moves to {@code replacement} through a bridge that returns the handler's list
         * followed by the host's own elements, so that both loaders' additions are there.
         */
        FIELD_THEN_HOST_ENTRIES
    }

    /** A replacement at the same place ({@link Placement#SAME_PLACE}). */
    public InjectionPointReplacement(String targetClass, String method, String replaced, String replacement) {
        this(targetClass, method, replaced, replacement, Placement.SAME_PLACE);
    }

    public InjectionPointReplacement {
        Objects.requireNonNull(targetClass, "targetClass");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(replaced, "replaced");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(placement, "placement");
    }
}

package com.example.compat.mixin.mixins;

import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code @Invoker} for a private static 26.2 method. */
@Mixin(SharedConstants.class)
public interface SharedConstantsInvoker {

    @Invoker("prefixDebugFlagName")
    static String callPrefixDebugFlagName(String name) {
        throw new AssertionError("Mixin did not implement the invoker");
    }
}

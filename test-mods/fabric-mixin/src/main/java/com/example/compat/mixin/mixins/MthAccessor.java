package com.example.compat.mixin.mixins;

import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code @Accessor} for a private static final 26.2 field. */
@Mixin(Mth.class)
public interface MthAccessor {

    @Accessor("SIN")
    static float[] getSin() {
        throw new AssertionError("Mixin did not implement the accessor");
    }
}

package com.example.compat.mixin.mixins;

import com.example.compat.mixin.Calculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Turned off by the configuration plugin; if Mixin applied it anyway, {@code sum} would be -970. */
@Mixin(Calculator.class)
public abstract class CalculatorDisabledMixin {

    @ModifyArg(
            method = "sum",
            at = @At(value = "INVOKE", target = "Lcom/example/compat/mixin/Calculator;add(II)I"),
            index = 0)
    private static int fabricMixin$break(int a) {
        return -1000;
    }
}
